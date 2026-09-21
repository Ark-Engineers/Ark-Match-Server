package io.arknights.dateorfriends.modules.user.online.race.service;

import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetDO;
import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdWalletService;
import io.arknights.dateorfriends.modules.user.notification.service.SiteNotificationService;
import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceBetDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceBetMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceRoundDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceRoundMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceSettlementDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceSettlementMapper;
import io.arknights.dateorfriends.modules.user.online.ws.OnlineWebSocketHandlerV2;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import tools.jackson.databind.ObjectMapper;

/**
 * 赛马竞猜核心引擎：
 * - 参赛者不建表：直接从 spine_asset（明日方舟小人，type 2/3）抓取，
 *   待机/移动动画与显示缩放均沿用 spine_asset 配置；每轮阵容以 lineup_json 存 asset id 列表
 * - 模式实例创建/关闭（仅管理员；每房间一个 ACTIVE 实例）
 * - 下注（龙门币唯一结算货币；单用户单场 100-3000；Boss 不可重复下注；赛前 30 秒自动关闭）
 * - 轮次状态机 BETTING -> RACING -> PODIUM -> FINISHED ->（下一轮 / 关闭）
 * - 名次后端独立生成、AES-GCM 加密落库、确定性种子保证前端动画 100% 一致
 * - 结算：位置彩池（无抽水）——前三名均中奖，奖池按名次均分三份，同马匹注单按赔率比例派奖；
 *   前三名全部无人押中时全额退款；分配算法见 RacePlacePool（与双向校验共用）
 * - 双向台账校验（结算表 vs 龙门币流水）与系统通知
 */
@Service
public class RaceEngineService {

    private static final Logger log = LoggerFactory.getLogger(RaceEngineService.class);

    public static final int RACER_COUNT = 5;
    public static final long MIN_TOTAL_BET = 100;
    public static final long MAX_TOTAL_BET = 3000;
    public static final long MIN_BET_DURATION_SECONDS = 60;
    public static final long DEFAULT_BET_DURATION_SECONDS = 120;
    public static final long RACE_DURATION_SECONDS = 60;
    public static final long PODIUM_DURATION_SECONDS = 60;
    public static final long PRE_RACE_SECONDS = 30;
    public static final int MAX_LIMITED_ROUNDS = 100;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String TX_BET = "RACE_BET";
    private static final String TX_PAYOUT = "RACE_PAYOUT";
    private static final String TX_REFUND = "RACE_REFUND";
    private static final String REF_ROUND = "HORSE_RACE_ROUND";
    private static final long TICK_ERROR_LOG_INTERVAL_MS = 60_000;
    private static volatile long lastTickErrorLogAt;

    private final SqlSessionFactory sqlSessionFactory;
    private final PlatformTransactionManager transactionManager;
    private final RaceMapper raceMapper;
    private final RaceRoundMapper roundMapper;
    private final RaceBetMapper betMapper;
    private final RaceSettlementMapper settlementMapper;
    private final LmdTransactionMapper lmdTransactionMapper;
    private final SpineAssetMapper spineAssetMapper;
    private final OnlineRoomMapper onlineRoomMapper;
    private final LmdWalletService lmdWalletService;
    private final SiteNotificationService siteNotificationService;
    private final RaceResultCrypto crypto;
    private final RaceVerifyService raceVerifyService;
    private final OnlineWebSocketHandlerV2 onlineWs;
    private final ObjectMapper objectMapper;

    public RaceEngineService(
            SqlSessionFactory sqlSessionFactory,
            PlatformTransactionManager transactionManager,
            RaceMapper raceMapper,
            RaceRoundMapper roundMapper,
            RaceBetMapper betMapper,
            RaceSettlementMapper settlementMapper,
            LmdTransactionMapper lmdTransactionMapper,
            SpineAssetMapper spineAssetMapper,
            OnlineRoomMapper onlineRoomMapper,
            LmdWalletService lmdWalletService,
            SiteNotificationService siteNotificationService,
            RaceResultCrypto crypto,
            RaceVerifyService raceVerifyService,
            OnlineWebSocketHandlerV2 onlineWs,
            ObjectMapper objectMapper
    ) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.transactionManager = transactionManager;
        this.raceMapper = raceMapper;
        this.roundMapper = roundMapper;
        this.betMapper = betMapper;
        this.settlementMapper = settlementMapper;
        this.lmdTransactionMapper = lmdTransactionMapper;
        this.spineAssetMapper = spineAssetMapper;
        this.onlineRoomMapper = onlineRoomMapper;
        this.lmdWalletService = lmdWalletService;
        this.siteNotificationService = siteNotificationService;
        this.crypto = crypto;
        this.raceVerifyService = raceVerifyService;
        this.onlineWs = onlineWs;
        this.objectMapper = objectMapper;
    }

    // SpringManagedTransaction 不会通过 openSession(false) 关闭自动提交，必须先绑定 Spring 事务。
    private final class RaceTransaction implements AutoCloseable {
        private final TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));

        void commit() {
            transactionManager.commit(status);
        }

        @Override
        public void close() {
            if (!status.isCompleted()) transactionManager.rollback(status);
        }
    }

    // ---------- DTO ----------

    public record CreateRequest(
            String roomId,
            String name,
            int sessionType,
            Integer totalRounds,
            int participantMode,
            List<Long> participantAssetIds,
            Long betStartAtMs,
            Long betEndAtMs
    ) {
    }

    public record RaceBrief(
            long id,
            String roomId,
            String name,
            String status,
            int sessionType,
            int totalRounds,
            int participantMode,
            int betDurationSeconds
    ) {
    }

    public record ParticipantInfo(long id, int sortNo, String assetKey, String name, int type, String idleAnimation, String moveAnimation, Double displayScale) {
    }

    public record RoundInfo(
            long id,
            int roundNo,
            String status,
            long betStartAt,
            long betEndAt,
            long raceStartAt,
            long podiumEndAt,
            String seed,
            long totalPool,
            int betCount,
            long paidTotal,
            List<Long> ranking,
            boolean developerControlled
    ) {
    }

    public record MyBetInfo(long assetId, long amount) {
    }

    public record StateResponse(
            boolean exists,
            RaceBrief race,
            RoundInfo round,
            List<ParticipantInfo> participants,
            List<MyBetInfo> myBets,
            long myTotal,
            long minTotalBet,
            long maxTotalBet,
            Map<Long, Long> horsePools,
            long serverTs
    ) {
        public static StateResponse missing() {
            return new StateResponse(false, null, null, List.of(), List.of(), 0, MIN_TOTAL_BET, MAX_TOTAL_BET, Map.of(), System.currentTimeMillis());
        }
    }

    public record BetResult(long betId, long myTotal, long totalPool) {
    }

    public record AdminDetail(
            RaceBrief race, List<ParticipantInfo> participants, List<RoundInfo> rounds,
            Map<Long, List<ParticipantInfo>> roundParticipants
    ) {
    }

    public record DeveloperState(
            RaceBrief race, RoundInfo round, List<ParticipantInfo> participants,
            List<ParticipantInfo> nextParticipants, List<Long> plannedRanking, boolean canScheduleNext
    ) {
    }

    public record RaceAdminRow(RaceBrief race, RoundInfo round, int participantCount) {
    }

    public record AssetOption(long id, String assetKey, String name, int type) {
    }

    // ---------- 时间工具 ----------

    static LocalDateTime nowLdt() {
        return LocalDateTime.now(ZONE);
    }

    static long toMs(LocalDateTime t) {
        return t == null ? 0 : t.atZone(ZONE).toInstant().toEpochMilli();
    }

    static LocalDateTime fromMs(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE);
    }

    // ---------- 阵容（lineup_json）工具 ----------

    private String lineupJsonOf(List<Long> ids) {
        return objectMapper.writeValueAsString(ids);
    }

    private List<Long> lineupIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return Arrays.asList(objectMapper.readValue(json, Long[].class));
        } catch (Exception e) {
            log.warn("race lineup json parse failed: {}", json, e);
            return List.of();
        }
    }

    /** 按 lineup_json 中的 id 顺序抓取 spine_asset；已删除/缺失的资产跳过（顺序即 sortNo） */
    private List<SpineAssetDO> lineupAssets(SpineAssetMapper mapper, String lineupJson) {
        var ids = lineupIds(lineupJson);
        if (ids.isEmpty()) return List.of();
        var byId = new java.util.HashMap<Long, SpineAssetDO>();
        for (var a : mapper.selectByIds(ids)) {
            if (a.getId() != null) byId.put(a.getId(), a);
        }
        var result = new ArrayList<SpineAssetDO>(ids.size());
        for (var id : ids) {
            var asset = byId.get(id);
            if (asset != null) result.add(asset);
        }
        return result;
    }

    private List<SpineAssetDO> lineupAssets(SqlSession session, String lineupJson) {
        return lineupAssets(session.getMapper(SpineAssetMapper.class), lineupJson);
    }

    /** 无限循环随机模式下一场重新随机 5 名敌人/Boss；池不足 5 时返回 null（调用方沿用本场名单） */
    private String randomLineupJson(SqlSession session) {
        var pool = session.getMapper(SpineAssetMapper.class).listByTypes(List.of(2, 3), 500);
        if (pool.size() < RACER_COUNT) return null;
        Collections.shuffle(pool, new java.security.SecureRandom());
        return lineupJsonOf(pool.subList(0, RACER_COUNT).stream().map(a -> a.getId() == null ? 0L : a.getId()).toList());
    }

    private List<ParticipantInfo> toParticipantInfos(List<SpineAssetDO> assets) {
        var result = new ArrayList<ParticipantInfo>(assets.size());
        for (int i = 0; i < assets.size(); i++) {
            result.add(toParticipantInfo(assets.get(i), i + 1));
        }
        return result;
    }

    private ParticipantInfo toParticipantInfo(SpineAssetDO a, int sortNo) {
        String idle = a.getIdleAnimation();
        if (idle == null || idle.isBlank()) idle = "Idle";
        String move = a.getMoveAnimation();
        if (move == null || move.isBlank()) move = "Move";
        double scale = a.getDisplayScale() == null || a.getDisplayScale() <= 0 ? 1.0 : a.getDisplayScale();
        return new ParticipantInfo(
                a.getId() == null ? 0 : a.getId(),
                sortNo,
                a.getAssetKey(),
                a.getName(),
                a.getType() == null ? 2 : a.getType(),
                idle,
                move,
                scale
        );
    }

    // ---------- 管理员：创建 / 关闭 / 详情 / 目录 ----------

    public long createRace(long adminId, CreateRequest req) {
        var roomId = safeRoomId(req.roomId());
        var room = onlineRoomMapper.selectByRoomId(roomId);
        if (room == null || Integer.valueOf(1).equals(room.getDeleted())) {
            throw new BusinessException(ErrorCode.RACE_ROOM_NOT_FOUND);
        }
        if (raceMapper.selectByRoomIdAndStatus(roomId, "ACTIVE") != null) {
            throw new BusinessException(ErrorCode.RACE_ALREADY_EXISTS);
        }
        var sessionType = req.sessionType();
        if (sessionType < 1 || sessionType > 3) throw new BusinessException(ErrorCode.PARAM_INVALID);
        int totalRounds;
        if (sessionType == 1) {
            totalRounds = 1;
        } else if (sessionType == 2) {
            totalRounds = req.totalRounds() == null ? 0 : req.totalRounds();
            if (totalRounds < 1 || totalRounds > MAX_LIMITED_ROUNDS) throw new BusinessException(ErrorCode.RACE_ROUNDS_INVALID);
        } else {
            totalRounds = 0;
        }
        if (req.participantMode() != 1 && req.participantMode() != 2) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        LocalDateTime betStart;
        long betDurationSeconds;
        if (sessionType == 3) {
            // 无限循环：无需指定竞猜时间，创建后立即开始第一轮，单轮竞猜周期取默认值
            betStart = nowLdt();
            betDurationSeconds = DEFAULT_BET_DURATION_SECONDS;
        } else {
            if (req.betStartAtMs() == null || req.betEndAtMs() == null) {
                throw new BusinessException(ErrorCode.RACE_TIME_INVALID, "请指定竞猜开始与结束时间");
            }
            betStart = fromMs(req.betStartAtMs());
            var betEnd = fromMs(req.betEndAtMs());
            if (!betStart.isAfter(nowLdt())) {
                throw new BusinessException(ErrorCode.RACE_TIME_INVALID, "竞猜开始时间必须晚于当前时间");
            }
            betDurationSeconds = (betEnd.atZone(ZONE).toInstant().toEpochMilli() - betStart.atZone(ZONE).toInstant().toEpochMilli()) / 1000;
            if (betDurationSeconds < MIN_BET_DURATION_SECONDS) {
                throw new BusinessException(ErrorCode.RACE_TIME_INVALID);
            }
        }

        // 参赛者直接抓取 spine_asset（type 2/3），不落参赛者表
        List<SpineAssetDO> chosen = resolveParticipants(req);

        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            var race = new RaceDO();
            race.setRoomId(roomId);
            race.setName(req.name() == null || req.name().isBlank() ? null : req.name().trim());
            race.setStatus("ACTIVE");
            race.setSessionType(sessionType);
            race.setTotalRounds(totalRounds);
            race.setParticipantMode(req.participantMode());
            race.setBetDurationSeconds((int) betDurationSeconds);
            race.setCreatedBy(adminId);
            session.getMapper(RaceMapper.class).insert(race);

            var r = new RaceRoundDO();
            r.setRaceId(race.getId());
            r.setRoundNo(1);
            r.setLineupJson(lineupJsonOf(chosen.stream().map(a -> a.getId() == null ? 0L : a.getId()).toList()));
            r.setStatus("BETTING");
            r.setBetStartAt(betStart);
            r.setBetEndAt(betStart.plusSeconds(betDurationSeconds));
            r.setRaceStartAt(r.getBetEndAt().plusSeconds(PRE_RACE_SECONDS));
            r.setPodiumEndAt(r.getRaceStartAt().plusSeconds(RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS));
            session.getMapper(RaceRoundMapper.class).insert(r);
            session.commit();
            transaction.commit();
            broadcastRaceUpdate(roomId);
            return race.getId();
        }
    }

    private List<SpineAssetDO> resolveParticipants(CreateRequest req) {
        if (req.participantMode() == 1) return resolveAssets(req.participantAssetIds());
        var pool = spineAssetMapper.listByTypes(List.of(2, 3), 500);
        if (pool.size() < RACER_COUNT) throw new BusinessException(ErrorCode.RACE_ASSET_NOT_ENOUGH);
        Collections.shuffle(pool, new java.security.SecureRandom());
        return new ArrayList<>(pool.subList(0, RACER_COUNT));
    }

    private List<SpineAssetDO> resolveAssets(List<Long> ids) {
        if (ids == null || ids.size() != RACER_COUNT || ids.stream().distinct().count() != RACER_COUNT) {
            throw new BusinessException(ErrorCode.RACE_PARTICIPANT_COUNT);
        }
        for (var id : ids) {
            if (id == null || id <= 0) throw new BusinessException(ErrorCode.RACE_ASSET_TYPE_INVALID);
        }
        var assets = spineAssetMapper.selectByIds(ids);
        var byId = new java.util.HashMap<Long, SpineAssetDO>();
        for (var a : assets) {
            byId.put(a.getId(), a);
        }
        var result = new ArrayList<SpineAssetDO>();
        for (var id : ids) {
            var asset = byId.get(id);
            if (asset == null || asset.getType() == null || (asset.getType() != 2 && asset.getType() != 3)) {
                throw new BusinessException(ErrorCode.RACE_ASSET_TYPE_INVALID);
            }
            result.add(asset);
        }
        return result;
    }

    public void closeRace(long adminId, long raceId) {
        final RaceDO race;
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            race = requireActiveRace(session, raceId);
            var rm = session.getMapper(RaceRoundMapper.class);
            var bm = session.getMapper(RaceBetMapper.class);
            var current = rm.selectCurrentByRaceId(raceId);
            if (current != null) {
                rm.selectByIdForUpdate(current.getId());
            }
            var bets = bm.selectActiveByRace(raceId);
            for (var bet : bets) {
                lmdWalletService.applyCredit(
                        session, bet.getUserId(), bet.getAmount(), TX_REFUND, REF_ROUND, bet.getRoundId(),
                        "赛马模式关闭退款", "race-refund-" + raceId + "-" + shortUuid(), null, adminId
                );
                bm.updateStatusAndPayout(bet.getId(), "REFUNDED", null);
            }
            for (var r : rm.selectByRaceId(raceId)) rm.forceFinish(r.getId());
            session.getMapper(RaceMapper.class).updateStatus(raceId, "CLOSED");
            if (current != null) recordControl(session, raceId, current.getId(), adminId, "CLOSE_REFUND", Map.of());
            session.commit();
            transaction.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    public AdminDetail detail(long raceId) {
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            var race = session.getMapper(RaceMapper.class).selectByIdForUpdate(raceId);
            if (race == null) throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
            var rm = session.getMapper(RaceRoundMapper.class);
            var current = rm.selectCurrentByRaceId(raceId);
            var rounds = rm.selectByRaceId(raceId);
            var roundParticipants = new LinkedHashMap<Long, List<ParticipantInfo>>();
            for (var round : rounds) {
                roundParticipants.put(round.getId(), toParticipantInfos(lineupAssets(session, round.getLineupJson())));
            }
            return new AdminDetail(
                    toBrief(race),
                    current == null ? List.<ParticipantInfo>of() : toParticipantInfos(lineupAssets(session, current.getLineupJson())),
                    rounds.stream().map(this::toRoundInfo).toList(),
                    roundParticipants
            );
        }
    }

    public List<AssetOption> catalog() {
        return spineAssetMapper.listByTypes(List.of(2, 3), 500).stream()
                .map(a -> new AssetOption(
                        a.getId() == null ? 0 : a.getId(),
                        a.getAssetKey(),
                        a.getName(),
                        a.getType() == null ? 2 : a.getType()
                ))
                .toList();
    }

    /** 管理端：所有进行中模式的当前轮次概览 */
    public List<RaceAdminRow> listActiveRows() {
        var actives = raceMapper.selectActive();
        if (actives.isEmpty()) return List.of();

        var raceIds = actives.stream().map(RaceDO::getId).toList();
        var roundsByRaceId = new java.util.HashMap<Long, RaceRoundDO>();
        var rounds = roundMapper.selectCurrentByRaceIds(raceIds);
        for (var r : rounds) {
            roundsByRaceId.put(r.getRaceId(), r);
        }

        var countsByRaceId = new java.util.HashMap<Long, Integer>();
        for (var race : actives) {
            var round = roundsByRaceId.get(race.getId());
            if (round != null) {
                countsByRaceId.put(race.getId(), lineupAssets(spineAssetMapper, round.getLineupJson()).size());
            }
        }

        return actives.stream().map(race -> {
            var round = roundsByRaceId.get(race.getId());
            int count = countsByRaceId.getOrDefault(race.getId(), 0);
            return new RaceAdminRow(toBrief(race), round == null ? null : toRoundInfo(round), count);
        }).toList();
    }

    public DeveloperState developerState(long raceId) {
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            var race = session.getMapper(RaceMapper.class).selectByIdForUpdate(raceId);
            if (race == null) throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
            var round = session.getMapper(RaceRoundMapper.class).selectCurrentByRaceId(raceId);
            var participants = round == null ? List.<SpineAssetDO>of() : lineupAssets(session, round.getLineupJson());
            var next = round == null ? List.<SpineAssetDO>of() : lineupAssets(session, round.getNextLineupJson());
            List<Long> planned = null;
            if (round != null && Boolean.TRUE.equals(round.getDeveloperControlled()) && round.getResultCipher() != null) {
                try {
                    planned = RacePlacePool.parseRanking(crypto.decrypt(round.getResultCipher()), round.getId());
                } catch (Exception e) {
                    log.warn("developer state: planned ranking unavailable (cipher undecryptable or corrupt) round={}", round.getId(), e);
                }
            }
            return new DeveloperState(toBrief(race), round == null ? null : toRoundInfo(round),
                    toParticipantInfos(participants),
                    toParticipantInfos(next), planned,
                    "ACTIVE".equals(race.getStatus()) && round != null && hasNextRound(race, round));
        }
    }

    public void setRanking(long adminId, long raceId, long roundId, List<Long> ranking) {
        final RaceDO race;
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            race = requireActiveRace(session, raceId);
            var round = requireCurrentRound(session, raceId, roundId);
            if (!"BETTING".equals(round.getStatus()) || round.getBetCount() != 0) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "仅可为尚未开赛且无人下注的场次设置排名");
            }
            var participants = lineupAssets(session, round.getLineupJson());
            var result = prepareResult(roundId, participants, ranking);
            if (session.getMapper(RaceRoundMapper.class).setPlannedRanking(roundId, result.seed(), result.cipher(), result.commit()) != 1) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
            }
            recordControl(session, raceId, roundId, adminId, "SET_RANKING", Map.of("resultCommit", result.commit()));
            session.commit();
            transaction.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    public void setNextParticipants(long adminId, long raceId, long roundId, List<Long> assetIds) {
        if (assetIds == null) throw new BusinessException(ErrorCode.PARAM_INVALID);
        var chosen = assetIds.isEmpty() ? List.<SpineAssetDO>of() : resolveAssets(assetIds);
        final RaceDO race;
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            race = requireActiveRace(session, raceId);
            var round = requireCurrentRound(session, raceId, roundId);
            if (!hasNextRound(race, round) || "FINISHED".equals(round.getStatus())) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "该模式没有下一场可配置");
            }
            String nextJson = chosen.isEmpty()
                    ? null
                    : lineupJsonOf(chosen.stream().map(a -> a.getId() == null ? 0L : a.getId()).toList());
            if (session.getMapper(RaceRoundMapper.class).updateNextLineupJson(roundId, nextJson) != 1) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
            }
            recordControl(session, raceId, roundId, adminId, "NEXT_PARTICIPANTS", Map.of("assetIds", assetIds));
            session.commit();
            transaction.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    public void startNow(long adminId, long raceId, long roundId) {
        var race = raceMapper.selectById(raceId);
        var round = roundMapper.selectById(roundId);
        if (race == null || round == null || !round.getRaceId().equals(raceId)) {
            throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        }
        startRaceRound(race, round, adminId);
    }

    public void endNow(long adminId, long raceId, long roundId, String expectedStatus) {
        var race = raceMapper.selectById(raceId);
        var round = roundMapper.selectById(roundId);
        if (race == null || round == null || !round.getRaceId().equals(raceId)) {
            throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        }
        if (!round.getStatus().equals(expectedStatus)) {
            throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "阶段已变化，请刷新后重新确认");
        }
        switch (expectedStatus) {
            case "RACING" -> settleRound(race, round, adminId);
            case "PODIUM" -> finishRoundAndAdvance(race, round, adminId);
            default -> throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "仅可提前结算比赛或结束领奖台；取消竞猜请关闭模式并退款");
        }
    }

    private RaceDO requireActiveRace(SqlSession session, long raceId) {
        var race = session.getMapper(RaceMapper.class).selectByIdForUpdate(raceId);
        if (race == null || !"ACTIVE".equals(race.getStatus())) throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        return race;
    }

    private RaceRoundDO requireCurrentRound(SqlSession session, long raceId, long roundId) {
        var rm = session.getMapper(RaceRoundMapper.class);
        var current = rm.selectCurrentByRaceId(raceId);
        if (current == null || current.getId() != roundId) {
            throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "轮次已变化，请刷新后重试");
        }
        return rm.selectByIdForUpdate(roundId);
    }

    private boolean hasNextRound(RaceDO race, RaceRoundDO round) {
        return Integer.valueOf(3).equals(race.getSessionType())
                || (Integer.valueOf(2).equals(race.getSessionType()) && round.getRoundNo() < race.getTotalRounds());
    }

    private void recordControl(SqlSession session, long raceId, long roundId, long adminId, String action, Map<String, ?> payload) {
        session.getMapper(RaceMapper.class).insertControlLog(raceId, roundId, adminId, action, objectMapper.writeValueAsString(payload));
    }

    private record PreparedResult(String seed, String cipher, String commit) {
    }

    private PreparedResult prepareResult(long roundId, List<SpineAssetDO> participants, List<Long> ranking) {
        if (participants.size() != RACER_COUNT || ranking == null || ranking.size() != RACER_COUNT
                || ranking.stream().distinct().count() != RACER_COUNT) {
            throw new BusinessException(ErrorCode.RACE_PARTICIPANT_COUNT);
        }
        var ids = participants.stream().map(a -> a.getId() == null ? 0L : a.getId()).toList();
        if (!ids.containsAll(ranking)) throw new BusinessException(ErrorCode.RACE_PARTICIPANT_INVALID);
        int[] indices = ranking.stream().mapToInt(ids::indexOf).toArray();
        var seed = RaceSimulator.findSeedForRanking(indices);
        var json = objectMapper.writeValueAsString(Map.of("roundId", roundId, "ranking", ranking));
        return new PreparedResult(seed, crypto.encrypt(json), crypto.commit(json, seed, roundId));
    }

    // ---------- 用户：状态 / 下注 ----------

    public StateResponse getState(long userId, String roomId) {
        var race = raceMapper.selectByRoomIdAndStatus(safeRoomId(roomId), "ACTIVE");
        if (race == null) return StateResponse.missing();
        var round = roundMapper.selectCurrentByRaceId(race.getId());
        var participants = round == null ? List.<SpineAssetDO>of() : lineupAssets(spineAssetMapper, round.getLineupJson());
        RoundInfo roundInfo = null;
        List<MyBetInfo> myBets = List.of();
        long myTotal = 0;
        Map<Long, Long> horsePools = Map.of();
        if (round != null) {
            roundInfo = toRoundInfo(round);
            var bets = betMapper.selectByRoundAndUser(round.getId(), userId);
            var byAsset = new LinkedHashMap<Long, Long>();
            for (var b : bets) {
                byAsset.merge(b.getAssetId() == null ? 0L : b.getAssetId(), b.getAmount() == null ? 0 : b.getAmount(), Long::sum);
            }
            myBets = byAsset.entrySet().stream()
                    .map(e -> new MyBetInfo(e.getKey(), e.getValue()))
                    .toList();
            myTotal = byAsset.values().stream().mapToLong(Long::longValue).sum();
            horsePools = new LinkedHashMap<Long, Long>();
            for (var p : participants) {
                horsePools.put(p.getId(), betMapper.sumPoolByRoundAndAsset(round.getId(), p.getId()));
            }
        }
        return new StateResponse(
                true,
                toBrief(race),
                roundInfo,
                toParticipantInfos(participants),
                myBets,
                myTotal,
                MIN_TOTAL_BET,
                MAX_TOTAL_BET,
                horsePools,
                System.currentTimeMillis()
        );
    }

    public BetResult placeBet(long userId, String roomId, long assetId, long amount, String ip) {
        if (amount < 1) throw new BusinessException(ErrorCode.RACE_AMOUNT_INVALID);
        if (!onlineWs.isUserInRoom(roomId, userId)) {
            throw new BusinessException(ErrorCode.RACE_NOT_IN_ROOM);
        }
        var race = raceMapper.selectByRoomIdAndStatus(safeRoomId(roomId), "ACTIVE");
        if (race == null) throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        var round = roundMapper.selectCurrentByRaceId(race.getId());
        if (round == null || !"BETTING".equals(round.getStatus())) {
            throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
        }
        var asset = spineAssetMapper.selectById(assetId);
        if (asset == null || asset.getType() == null || (asset.getType() != 2 && asset.getType() != 3)) {
            throw new BusinessException(ErrorCode.RACE_PARTICIPANT_INVALID);
        }
        var assetType = asset.getType();

        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            requireActiveRace(session, race.getId());
            var rm = session.getMapper(RaceRoundMapper.class);
            var bm = session.getMapper(RaceBetMapper.class);
            var locked = requireCurrentRound(session, race.getId(), round.getId());
            if (!"BETTING".equals(locked.getStatus())) throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
            if (Boolean.TRUE.equals(locked.getDeveloperControlled())) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "指定排名的演示场不接受下注");
            }
            var lineupIds = lineupIds(locked.getLineupJson());
            if (!lineupIds.contains(assetId)) {
                throw new BusinessException(ErrorCode.RACE_PARTICIPANT_INVALID);
            }
            var now = nowLdt();
            if (now.isBefore(locked.getBetStartAt()) || !now.isBefore(locked.getBetEndAt())) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
            }
            long existing = bm.sumByRoundAndUser(round.getId(), userId);
            long newTotal = existing + amount;
            if (newTotal < MIN_TOTAL_BET || newTotal > MAX_TOTAL_BET) {
                throw new BusinessException(ErrorCode.RACE_AMOUNT_OUT_OF_RANGE);
            }
            if (assetType == 3 && bm.countActiveByRoundUserAsset(round.getId(), userId, assetId) > 0) {
                throw new BusinessException(ErrorCode.RACE_BOSS_DUPLICATE);
            }
            // 先查参赛者彩池（必须在 commit 前，否则 SqlSession 关闭导致 Connection is closed）
            var horsePools = new LinkedHashMap<Long, Long>();
            for (var id : lineupIds) {
                horsePools.put(id, bm.sumPoolByRoundAndAsset(round.getId(), id));
            }

            var bet = new RaceBetDO();
            bet.setRoundId(round.getId());
            bet.setRaceId(race.getId());
            bet.setUserId(userId);
            bet.setAssetId(assetId);
            bet.setAmount(amount);
            bet.setStatus("ACTIVE");
            bm.insert(bet);
            rm.addPool(round.getId(), amount);
            lmdWalletService.applyCredit(
                    session, userId, -amount, TX_BET, REF_ROUND, round.getId(),
                    "赛马竞猜下注", "race-bet-" + round.getId() + "-" + shortUuid(), ip, userId
            );
            session.commit();
            transaction.commit();
            long poolAfter = (locked.getTotalPool() == null ? 0 : locked.getTotalPool()) + amount;
            broadcastRace(roomId, "race_pool_update", Map.of(
                    "roundId", round.getId(),
                    "totalPool", poolAfter,
                    "horsePools", horsePools
            ));
            return new BetResult(bet.getId() == null ? 0 : bet.getId(), newTotal, poolAfter);
        }
    }

    // ---------- 调度：轮次流转 ----------

    public void tickOnce() {
        List<RaceDO> actives;
        try {
            actives = raceMapper.selectActive();
        } catch (Exception e) {
            var now = System.currentTimeMillis();
            if (now - lastTickErrorLogAt >= TICK_ERROR_LOG_INTERVAL_MS) {
                lastTickErrorLogAt = now;
                log.error("race tick: load active races failed", e);
            }
            return;
        }
        if (actives.isEmpty()) return;

        var raceIds = actives.stream().map(RaceDO::getId).toList();
        var roundsByRaceId = new java.util.HashMap<Long, RaceRoundDO>();
        try {
            var rounds = roundMapper.selectCurrentByRaceIds(raceIds);
            for (var r : rounds) {
                roundsByRaceId.put(r.getRaceId(), r);
            }
        } catch (Exception e) {
            var now = System.currentTimeMillis();
            if (now - lastTickErrorLogAt >= TICK_ERROR_LOG_INTERVAL_MS) {
                lastTickErrorLogAt = now;
                log.error("race tick: load current rounds failed", e);
            }
            return;
        }

        for (var race : actives) {
            try {
                var round = roundsByRaceId.get(race.getId());
                if (round == null) continue;
                var now = nowLdt();
                switch (round.getStatus() == null ? "" : round.getStatus()) {
                    case "BETTING" -> {
                        if (!now.isBefore(round.getBetEndAt())) startRaceRound(race, round, null);
                    }
                    case "RACING" -> {
                        if (!now.isBefore(round.getRaceStartAt().plusSeconds(RACE_DURATION_SECONDS))) settleRound(race, round, null);
                    }
                    case "PODIUM" -> {
                        if (!now.isBefore(round.getPodiumEndAt())) finishRoundAndAdvance(race, round, null);
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                log.error("race tick failed race={}", race.getId(), e);
            }
        }
    }

    /** 竞猜结束：后端生成随机名次并加密落库，广播种子与开赛时间戳（BETTING -> RACING） */
    private void startRaceRound(RaceDO race, RaceRoundDO round, Long adminId) {
        final RaceRoundDO started;
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            requireActiveRace(session, race.getId());
            var rm = session.getMapper(RaceRoundMapper.class);
            var locked = requireCurrentRound(session, race.getId(), round.getId());
            var now = nowLdt();
            if ("BETTING".equals(locked.getStatus())) {
                if (adminId == null && now.isBefore(locked.getBetEndAt())) return;
                PreparedResult result;
                if (Boolean.TRUE.equals(locked.getDeveloperControlled())) {
                    result = new PreparedResult(locked.getSeed(), locked.getResultCipher(), locked.getResultCommit());
                } else {
                    var participants = lineupAssets(session, locked.getLineupJson());
                    if (participants.size() != RACER_COUNT) throw new BusinessException(ErrorCode.RACE_PARTICIPANT_COUNT);
                    var ranking = new ArrayList<Long>(RACER_COUNT);
                    for (int idx : RaceSimulator.randomRanking()) ranking.add(participants.get(idx).getId());
                    result = prepareResult(round.getId(), participants, ranking);
                }
                if (rm.markRacing(round.getId(), result.seed(), result.cipher(), result.commit()) != 1) {
                    throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
                }
                locked.setSeed(result.seed());
            } else if (adminId == null) {
                return;
            } else if (!"RACING".equals(locked.getStatus()) || !now.isBefore(locked.getRaceStartAt())) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "本场已经开赛或结束，请刷新状态");
            }
            if (adminId != null) {
                now = nowLdt();
                rm.startImmediately(round.getId(), now);
                locked.setRaceStartAt(now);
                locked.setPodiumEndAt(now.plusSeconds(RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS));
                recordControl(session, race.getId(), round.getId(), adminId, "START_NOW", Map.of());
            }
            started = locked;
            session.commit();
            transaction.commit();
        }
        broadcastRace(race.getRoomId(), "race_start", Map.of(
                "roundId", started.getId(),
                "roundNo", started.getRoundNo(),
                "seed", started.getSeed(),
                "raceStartAt", toMs(started.getRaceStartAt()),
                "podiumEndAt", toMs(started.getPodiumEndAt()),
                "developerControlled", Boolean.TRUE.equals(started.getDeveloperControlled()),
                "durationMs", RACE_DURATION_SECONDS * 1000
        ));
    }

    /** 比赛结束：解密名次 -> 位置彩池结算（前三名按赔率瓜分奖池）-> 入账 + 台账 + 通知（RACING -> PODIUM） */
    private void settleRound(RaceDO race, RaceRoundDO round, Long adminId) {
        final List<Long> ranking;
        final long pool;
        final long paidTotal;
        final boolean refunded;
        final LocalDateTime podiumEndAt;
        final Map<Long, Long> horsePools;
        final Map<Long, Long> userBetTotal = new LinkedHashMap<>();
        final Map<Long, Long> userPayout = new LinkedHashMap<>();
        final Map<Long, List<Map<String, Object>>> userWins = new LinkedHashMap<>();

        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            requireActiveRace(session, race.getId());
            var rm = session.getMapper(RaceRoundMapper.class);
            var bm = session.getMapper(RaceBetMapper.class);
            var sm = session.getMapper(RaceSettlementMapper.class);
            var locked = requireCurrentRound(session, race.getId(), round.getId());
            if (!"RACING".equals(locked.getStatus())) {
                if (adminId != null) throw new BusinessException(ErrorCode.RACE_PHASE_INVALID, "本场已结算，请刷新状态");
                return;
            }
            if (adminId == null && nowLdt().isBefore(locked.getRaceStartAt().plusSeconds(RACE_DURATION_SECONDS))) return;

            String resultJson = crypto.decrypt(locked.getResultCipher());
            if (!crypto.verifyCommit(resultJson, locked.getSeed(), locked.getId(), locked.getResultCommit())) {
                throw new IllegalStateException("race result commit mismatch round=" + round.getId());
            }
            ranking = RacePlacePool.parseRanking(resultJson, round.getId());
            var participantsById = lineupAssets(session, locked.getLineupJson()).stream()
                    .collect(Collectors.toMap(SpineAssetDO::getId, a -> a));
            if (ranking.stream().distinct().count() != RACER_COUNT || !participantsById.keySet().containsAll(ranking)) {
                throw new IllegalStateException("race result participants mismatch round=" + round.getId());
            }
            var allBets = bm.selectActiveByRound(round.getId());
            pool = allBets.stream().mapToLong(b -> b.getAmount() == null ? 0 : b.getAmount()).sum();
            horsePools = new LinkedHashMap<>();
            for (var pid : ranking) horsePools.put(pid, 0L);
            for (var bet : allBets) {
                if (bet.getAssetId() != null) {
                    horsePools.merge(bet.getAssetId(), bet.getAmount() == null ? 0 : bet.getAmount(), Long::sum);
                }
            }
            var dist = RacePlacePool.distribute(pool, ranking, allBets.stream()
                    .map(b -> new RacePlacePool.BetInput(
                            b.getId() == null ? 0 : b.getId(),
                            b.getUserId() == null ? 0 : b.getUserId(),
                            b.getAssetId() == null ? -1 : b.getAssetId(),
                            b.getAmount() == null ? 0 : b.getAmount()))
                    .toList());
            refunded = dist.refunded();

            var settlements = new ArrayList<RaceSettlementDO>();
            if (refunded) {
                // 前三名全部无人押中且奖池非空：全额退款，不产生派奖与结算行
                for (var bet : allBets) {
                    bm.updateStatusAndPayout(bet.getId(), "REFUNDED", null);
                    lmdWalletService.applyCredit(
                            session, bet.getUserId(), bet.getAmount(), TX_REFUND, REF_ROUND, round.getId(),
                            "赛马竞猜退款（无人押中前三名）", "race-refund-" + round.getId() + "-" + bet.getId(), null, adminId
                    );
                }
            } else {
                var betById = new LinkedHashMap<Long, RaceBetDO>();
                for (var bet : allBets) betById.put(bet.getId(), bet);
                for (var e : dist.betPayouts().entrySet()) {
                    var bet = betById.get(e.getKey());
                    bm.updateStatusAndPayout(bet.getId(), "WON", e.getValue());
                    userPayout.merge(bet.getUserId(), e.getValue(), Long::sum);
                }
                for (var bet : allBets) {
                    if (!dist.betPayouts().containsKey(bet.getId())) {
                        bm.updateStatusAndPayout(bet.getId(), "LOST", null);
                    }
                }
                // 结算行：每用户每个中奖名次一行，payout 为其该马匹注单派奖之和
                var wonPayoutByUserHorse = new LinkedHashMap<Long, LinkedHashMap<Long, Long>>();
                var wonAmountByUserHorse = new LinkedHashMap<Long, LinkedHashMap<Long, Long>>();
                for (var e : dist.betPayouts().entrySet()) {
                    var bet = betById.get(e.getKey());
                    wonPayoutByUserHorse.computeIfAbsent(bet.getUserId(), k -> new LinkedHashMap<>())
                            .merge(bet.getAssetId(), e.getValue(), Long::sum);
                    wonAmountByUserHorse.computeIfAbsent(bet.getUserId(), k -> new LinkedHashMap<>())
                            .merge(bet.getAssetId(), bet.getAmount() == null ? 0 : bet.getAmount(), Long::sum);
                }
                for (int rank = 0; rank < 3; rank++) {
                    long pid = ranking.get(rank);
                    for (var entry : wonPayoutByUserHorse.entrySet()) {
                        Long payout = entry.getValue().get(pid);
                        if (payout == null) continue;
                        var uid = entry.getKey();
                        var p = participantsById.get(pid);
                        var s = new RaceSettlementDO();
                        s.setRoundId(round.getId());
                        s.setUserId(uid);
                        s.setAssetId(pid);
                        s.setRankNo(rank + 1);
                        s.setBetAmount(wonAmountByUserHorse.get(uid).get(pid));
                        s.setPayout(payout);
                        settlements.add(s);
                        userWins.computeIfAbsent(uid, k -> new ArrayList<>()).add(Map.of(
                                "assetId", pid,
                                "participantName", p == null || p.getName() == null ? "" : p.getName(),
                                "rankNo", rank + 1,
                                "payout", payout
                        ));
                    }
                }
                if (!settlements.isEmpty()) sm.insertBatch(settlements);
                for (var s : settlements) {
                    lmdWalletService.applyCredit(
                            session, s.getUserId(), s.getPayout(), TX_PAYOUT, REF_ROUND, round.getId(),
                            "赛马竞猜中奖", "race-pay-" + round.getId() + "-" + s.getUserId() + "-" + s.getRankNo(), null, null
                    );
                }
            }

            long paid = refunded ? 0 : dist.betPayouts().values().stream().mapToLong(Long::longValue).sum();
            for (var bet : allBets) {
                userBetTotal.merge(bet.getUserId(), bet.getAmount() == null ? 0 : bet.getAmount(), Long::sum);
            }
            var settledAt = nowLdt();
            podiumEndAt = settledAt.plusSeconds(PODIUM_DURATION_SECONDS);
            rm.markPodium(round.getId(), paid, settledAt, podiumEndAt);
            if (adminId != null) recordControl(session, race.getId(), round.getId(), adminId, "SETTLE_NOW", Map.of("paidTotal", paid));
            session.commit();
            transaction.commit();
            paidTotal = paid;
        }

        var verify = raceVerifyService.verifyRound(round.getId());
        if (!verify.ok()) {
            log.error("race settle verify FAILED race={} round={} problems={}", race.getId(), round.getId(), verify.problems());
        }

        broadcastRace(race.getRoomId(), "race_result", Map.of(
                "roundId", round.getId(),
                "roundNo", round.getRoundNo(),
                "ranking", ranking,
                "totalPool", pool,
                "paidTotal", paidTotal,
                "refunded", refunded,
                "horsePools", horsePools,
                "podiumEndAt", toMs(podiumEndAt)
        ));

        for (var uid : userBetTotal.keySet()) {
            long betTotal = userBetTotal.getOrDefault(uid, 0L);
            long payout = userPayout.getOrDefault(uid, 0L);
            var wins = userWins.getOrDefault(uid, List.of());
            broadcastRaceToUser(race.getRoomId(), uid, "race_my_result", Map.of(
                    "roundId", round.getId(),
                    "roundNo", round.getRoundNo(),
                    "payout", payout,
                    "betTotal", betTotal,
                    "wins", wins,
                    "refunded", refunded
            ));
            sendResultNotification(race, round, uid, betTotal, payout, wins, refunded);
        }
    }

    private void sendResultNotification(
            RaceDO race,
            RaceRoundDO round,
            long userId,
            long betTotal,
            long payout,
            List<Map<String, Object>> wins,
            boolean refunded
    ) {
        String content;
        if (refunded) {
            content = "赛马竞猜结果：你在「" + race.getRoomId() + "」第 " + round.getRoundNo()
                    + " 场竞猜无人押中前三名，奖池已全额退款，你的 " + betTotal + " 龙门币注金已退回账户。";
        } else if (payout > 0) {
            if (wins.size() == 1) {
                var w = wins.get(0);
                content = "赛马竞猜结果：你在「" + race.getRoomId() + "」第 " + round.getRoundNo() + " 场竞猜中命中「"
                        + w.get("participantName") + "」（第 " + w.get("rankNo") + " 名），获得 "
                        + payout + " 龙门币奖金（下注总额 " + betTotal + "）。";
            } else {
                content = "赛马竞猜结果：你在「" + race.getRoomId() + "」第 " + round.getRoundNo() + " 场竞猜中命中多个对象，共获得 "
                        + payout + " 龙门币奖金（下注总额 " + betTotal + "）。";
            }
        } else {
            content = "赛马竞猜结果：你在「" + race.getRoomId() + "」第 " + round.getRoundNo() + " 场竞猜中未中奖（下注总额 "
                    + betTotal + " 龙门币），再接再厉！";
        }
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "roundId", round.getId(),
                    "roundNo", round.getRoundNo(),
                    "roomId", race.getRoomId(),
                    "payout", payout,
                    "betTotal", betTotal,
                    "wins", wins,
                    "refunded", refunded
            ));
        } catch (Exception e) {
            payloadJson = "{}";
        }
        siteNotificationService.sendToUser(null, userId, "HORSE_RACE", "赛马竞猜结果", content, "NORMAL", "/online", payloadJson)
                .subscribe(v -> {
                }, e -> log.error("race notify failed user={} round={}", userId, round.getId(), e));
    }

    /** 领奖台结束：轮次收尾并开启下一轮或关闭模式（PODIUM -> FINISHED -> ...） */
    private void finishRoundAndAdvance(RaceDO race, RaceRoundDO round, Long adminId) {
        try (var transaction = new RaceTransaction();
             var session = sqlSessionFactory.openSession(false)) {
            var active = requireActiveRace(session, race.getId());
            var rm = session.getMapper(RaceRoundMapper.class);
            var locked = requireCurrentRound(session, race.getId(), round.getId());
            if (!"PODIUM".equals(locked.getStatus())) {
                if (adminId != null) throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
                return;
            }
            if (adminId == null && nowLdt().isBefore(locked.getPodiumEndAt())) return;
            rm.markFinished(round.getId());
            if (hasNextRound(active, locked)) {
                var next = rm.selectByRaceAndRoundNo(race.getId(), locked.getRoundNo() + 1);
                boolean insert = next == null;
                if (insert) next = new RaceRoundDO();
                next.setRaceId(race.getId());
                next.setRoundNo(locked.getRoundNo() + 1);
                String nextLineupJson = locked.getNextLineupJson();
                if (nextLineupJson == null && Integer.valueOf(3).equals(active.getSessionType())
                        && Integer.valueOf(2).equals(active.getParticipantMode())) {
                    // 无限循环+随机模式：下一场重新随机 5 名（开发者未指定下一场名单时）
                    nextLineupJson = randomLineupJson(session);
                }
                next.setLineupJson(nextLineupJson == null ? locked.getLineupJson() : nextLineupJson);
                var betStart = nowLdt();
                var betEnd = betStart.plusSeconds(active.getBetDurationSeconds());
                next.setStatus("BETTING");
                next.setBetStartAt(betStart);
                next.setBetEndAt(betEnd);
                next.setRaceStartAt(betEnd.plusSeconds(PRE_RACE_SECONDS));
                next.setPodiumEndAt(betEnd.plusSeconds(PRE_RACE_SECONDS + RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS));
                if (insert) rm.insert(next);
                else if (rm.reschedule(next) != 1) throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
            } else {
                session.getMapper(RaceMapper.class).updateStatus(race.getId(), "CLOSED");
            }
            if (adminId != null) recordControl(session, race.getId(), round.getId(), adminId, "END_PODIUM", Map.of());
            session.commit();
            transaction.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    // ---------- 工具 ----------

    private RaceBrief toBrief(RaceDO race) {
        return new RaceBrief(
                race.getId(),
                race.getRoomId(),
                race.getName(),
                race.getStatus(),
                race.getSessionType() == null ? 1 : race.getSessionType(),
                race.getTotalRounds() == null ? 1 : race.getTotalRounds(),
                race.getParticipantMode() == null ? 1 : race.getParticipantMode(),
                race.getBetDurationSeconds() == null ? 120 : race.getBetDurationSeconds()
        );
    }

    private RoundInfo toRoundInfo(RaceRoundDO r) {
        List<Long> ranking = null;
        var status = r.getStatus();
        if (("PODIUM".equals(status) || "FINISHED".equals(status)) && r.getResultCipher() != null) {
            try {
                ranking = RacePlacePool.parseRanking(crypto.decrypt(r.getResultCipher()), r.getId());
            } catch (Exception e) {
                log.warn("round info: result cipher undecryptable (legacy key or corrupt), ranking hidden round={}", r.getId(), e);
            }
        }
        return new RoundInfo(
                r.getId(),
                r.getRoundNo() == null ? 0 : r.getRoundNo(),
                status,
                toMs(r.getBetStartAt()),
                toMs(r.getBetEndAt()),
                toMs(r.getRaceStartAt()),
                toMs(r.getPodiumEndAt()),
                "BETTING".equals(status) ? null : r.getSeed(),
                r.getTotalPool() == null ? 0 : r.getTotalPool(),
                r.getBetCount() == null ? 0 : r.getBetCount(),
                r.getPaidTotal() == null ? 0 : r.getPaidTotal(),
                ranking,
                Boolean.TRUE.equals(r.getDeveloperControlled())
        );
    }

    private void broadcastRace(String roomId, String type, Map<String, Object> payload) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        if (payload != null) map.putAll(payload);
        try {
            onlineWs.broadcastToRoom(roomId, objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            log.error("race broadcast failed room={} type={}", roomId, type, e);
        }
    }

    private void broadcastRaceToUser(String roomId, long userId, String type, Map<String, Object> payload) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        if (payload != null) map.putAll(payload);
        try {
            onlineWs.sendToUserInRoom(roomId, userId, objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            log.error("race broadcast failed room={} user={} type={}", roomId, userId, type, e);
        }
    }

    private void broadcastRaceUpdate(String roomId) {
        broadcastRace(roomId, "race_update", Map.of());
    }

    private String safeRoomId(String raw) {
        var s = String.valueOf(raw == null ? "" : raw).trim();
        s = s.replaceAll("[^a-zA-Z0-9_-]", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
