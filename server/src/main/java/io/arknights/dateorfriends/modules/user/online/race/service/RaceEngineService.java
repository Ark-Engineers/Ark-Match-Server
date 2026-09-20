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
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceParticipantDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceParticipantMapper;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 赛马竞猜核心引擎：
 * - 模式实例创建/关闭（仅管理员；每房间一个 ACTIVE 实例）
 * - 下注（龙门币唯一结算货币；单用户单场 100-3000；Boss 不可重复下注；赛前 30 秒自动关闭）
 * - 轮次状态机 BETTING -> RACING -> PODIUM -> FINISHED ->（下一轮 / 关闭）
 * - 名次后端独立生成、AES-GCM 加密落库、确定性种子保证前端动画 100% 一致
 * - 结算：第一名 60%、第二名 30%、第三名 10%；同对象多人中奖平均分配；无人中奖份额不发放
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
    private final RaceMapper raceMapper;
    private final RaceParticipantMapper participantMapper;
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
            RaceMapper raceMapper,
            RaceParticipantMapper participantMapper,
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
        this.raceMapper = raceMapper;
        this.participantMapper = participantMapper;
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

    public record ParticipantInfo(long id, int sortNo, String assetKey, String name, int type) {
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
            List<Long> ranking
    ) {
    }

    public record MyBetInfo(long participantId, long amount) {
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
            long serverTs
    ) {
        public static StateResponse missing() {
            return new StateResponse(false, null, null, List.of(), List.of(), 0, MIN_TOTAL_BET, MAX_TOTAL_BET, System.currentTimeMillis());
        }
    }

    public record BetResult(long betId, long myTotal, long totalPool) {
    }

    public record AdminDetail(RaceBrief race, List<ParticipantInfo> participants, List<RoundInfo> rounds) {
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

        List<SpineAssetDO> chosen = resolveParticipants(req);

        try (var session = sqlSessionFactory.openSession(false)) {
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

            var participants = new ArrayList<RaceParticipantDO>();
            for (int i = 0; i < chosen.size(); i++) {
                var a = chosen.get(i);
                var p = new RaceParticipantDO();
                p.setRaceId(race.getId());
                p.setSortNo(i + 1);
                p.setSpineAssetId(a.getId());
                p.setAssetKey(a.getAssetKey());
                p.setName(a.getName());
                p.setType(a.getType() == null ? 2 : a.getType());
                participants.add(p);
            }
            session.getMapper(RaceParticipantMapper.class).insertBatch(participants);

            var rounds = buildRoundTimes(betStart, betDurationSeconds, totalRounds);
            var roundMapperTx = session.getMapper(RaceRoundMapper.class);
            for (int i = 0; i < rounds.size(); i++) {
                var r = new RaceRoundDO();
                r.setRaceId(race.getId());
                r.setRoundNo(i + 1);
                r.setStatus("BETTING");
                r.setBetStartAt(rounds.get(i)[0]);
                r.setBetEndAt(rounds.get(i)[1]);
                r.setRaceStartAt(rounds.get(i)[1].plusSeconds(PRE_RACE_SECONDS));
                r.setPodiumEndAt(rounds.get(i)[1].plusSeconds(PRE_RACE_SECONDS + RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS));
                r.setTotalPool(0L);
                r.setBetCount(0);
                r.setPaidTotal(0L);
                roundMapperTx.insert(r);
            }
            session.commit();
            broadcastRaceUpdate(roomId);
            return race.getId();
        }
    }

    private List<SpineAssetDO> resolveParticipants(CreateRequest req) {
        if (req.participantMode() == 1) {
            var ids = req.participantAssetIds();
            if (ids == null || ids.size() != RACER_COUNT) {
                throw new BusinessException(ErrorCode.RACE_PARTICIPANT_COUNT);
            }
            var distinct = ids.stream().distinct().count();
            if (distinct != RACER_COUNT) throw new BusinessException(ErrorCode.RACE_PARTICIPANT_COUNT);
            var result = new ArrayList<SpineAssetDO>();
            for (var id : ids) {
                var asset = spineAssetMapper.selectById(id);
                if (asset == null || asset.getType() == null || (asset.getType() != 2 && asset.getType() != 3)) {
                    throw new BusinessException(ErrorCode.RACE_ASSET_TYPE_INVALID);
                }
                result.add(asset);
            }
            return result;
        }
        var pool = spineAssetMapper.listByTypes(List.of(2, 3), 500);
        if (pool.size() < RACER_COUNT) throw new BusinessException(ErrorCode.RACE_ASSET_NOT_ENOUGH);
        Collections.shuffle(pool, new java.security.SecureRandom());
        return new ArrayList<>(pool.subList(0, RACER_COUNT));
    }

    private List<LocalDateTime[]> buildRoundTimes(LocalDateTime firstBetStart, long betDurationSeconds, int totalRounds) {
        var count = totalRounds <= 0 ? 1 : totalRounds;
        var rounds = new ArrayList<LocalDateTime[]>(count);
        var betStart = firstBetStart;
        for (int i = 0; i < count; i++) {
            var betEnd = betStart.plusSeconds(betDurationSeconds);
            rounds.add(new LocalDateTime[]{betStart, betEnd});
            betStart = betEnd.plusSeconds(PRE_RACE_SECONDS + RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS);
        }
        return rounds;
    }

    public void closeRace(long adminId, long raceId) {
        var race = raceMapper.selectById(raceId);
        if (race == null || !"ACTIVE".equals(race.getStatus())) {
            throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        }
        try (var session = sqlSessionFactory.openSession(false)) {
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
            if (current != null) {
                rm.forceFinish(current.getId());
            }
            session.getMapper(RaceMapper.class).updateStatus(raceId, "CLOSED");
            session.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    public AdminDetail detail(long raceId) {
        var race = raceMapper.selectById(raceId);
        if (race == null) throw new BusinessException(ErrorCode.RACE_NOT_FOUND);
        var participants = participantMapper.selectByRaceId(raceId);
        var rounds = roundMapper.selectByRaceId(raceId);
        var brief = toBrief(race);
        return new AdminDetail(
                brief,
                participants.stream().map(this::toParticipantInfo).toList(),
                rounds.stream().map(this::toRoundInfo).toList()
        );
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
        return raceMapper.selectActive().stream().map(race -> {
            var round = roundMapper.selectCurrentByRaceId(race.getId());
            int count = participantMapper.selectByRaceId(race.getId()).size();
            return new RaceAdminRow(toBrief(race), round == null ? null : toRoundInfo(round), count);
        }).toList();
    }

    // ---------- 用户：状态 / 下注 ----------

    public StateResponse getState(long userId, String roomId) {
        var race = raceMapper.selectByRoomIdAndStatus(safeRoomId(roomId), "ACTIVE");
        if (race == null) return StateResponse.missing();
        var participants = participantMapper.selectByRaceId(race.getId());
        var round = roundMapper.selectCurrentByRaceId(race.getId());
        RoundInfo roundInfo = null;
        List<MyBetInfo> myBets = List.of();
        long myTotal = 0;
        if (round != null) {
            roundInfo = toRoundInfo(round);
            var bets = betMapper.selectByRoundAndUser(round.getId(), userId);
            var byParticipant = new LinkedHashMap<Long, Long>();
            for (var b : bets) {
                byParticipant.merge(b.getParticipantId(), b.getAmount() == null ? 0 : b.getAmount(), Long::sum);
            }
            myBets = byParticipant.entrySet().stream()
                    .map(e -> new MyBetInfo(e.getKey(), e.getValue()))
                    .toList();
            myTotal = byParticipant.values().stream().mapToLong(Long::longValue).sum();
        }
        return new StateResponse(
                true,
                toBrief(race),
                roundInfo,
                participants.stream().map(this::toParticipantInfo).toList(),
                myBets,
                myTotal,
                MIN_TOTAL_BET,
                MAX_TOTAL_BET,
                System.currentTimeMillis()
        );
    }

    public BetResult placeBet(long userId, String roomId, long participantId, long amount, String ip) {
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
        var participant = participantMapper.selectById(participantId);
        if (participant == null || !participant.getRaceId().equals(race.getId())) {
            throw new BusinessException(ErrorCode.RACE_PARTICIPANT_INVALID);
        }
        var assetType = participant.getType() == null ? 2 : participant.getType();

        try (var session = sqlSessionFactory.openSession(false)) {
            var rm = session.getMapper(RaceRoundMapper.class);
            var bm = session.getMapper(RaceBetMapper.class);
            var locked = rm.selectByIdForUpdate(round.getId());
            if (locked == null || !"BETTING".equals(locked.getStatus())) {
                throw new BusinessException(ErrorCode.RACE_PHASE_INVALID);
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
            if (assetType == 3 && bm.countActiveByRoundUserParticipant(round.getId(), userId, participantId) > 0) {
                throw new BusinessException(ErrorCode.RACE_BOSS_DUPLICATE);
            }
            var bet = new RaceBetDO();
            bet.setRoundId(round.getId());
            bet.setRaceId(race.getId());
            bet.setUserId(userId);
            bet.setParticipantId(participantId);
            bet.setAmount(amount);
            bet.setStatus("ACTIVE");
            bm.insert(bet);
            rm.addPool(round.getId(), amount);
            lmdWalletService.applyCredit(
                    session, userId, -amount, TX_BET, REF_ROUND, round.getId(),
                    "赛马竞猜下注", "race-bet-" + round.getId() + "-" + shortUuid(), ip, userId
            );
            session.commit();
            long poolAfter = (locked.getTotalPool() == null ? 0 : locked.getTotalPool()) + amount;
            broadcastRace(roomId, "race_pool_update", Map.of(
                    "roundId", round.getId(),
                    "totalPool", poolAfter
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
        for (var race : actives) {
            try {
                var round = roundMapper.selectCurrentByRaceId(race.getId());
                if (round == null) continue;
                var now = nowLdt();
                switch (round.getStatus() == null ? "" : round.getStatus()) {
                    case "BETTING" -> {
                        if (!now.isBefore(round.getBetEndAt())) startRaceRound(race, round);
                    }
                    case "RACING" -> {
                        if (!now.isBefore(round.getRaceStartAt().plusSeconds(RACE_DURATION_SECONDS))) settleRound(race, round);
                    }
                    case "PODIUM" -> {
                        if (!now.isBefore(round.getPodiumEndAt())) finishRoundAndAdvance(race, round);
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
    private void startRaceRound(RaceDO race, RaceRoundDO round) {
        var participants = participantMapper.selectByRaceId(race.getId());
        if (participants.size() != RACER_COUNT) {
            log.error("race start: participant count {} != {} race={} round={}", participants.size(), RACER_COUNT, race.getId(), round.getId());
            return;
        }
        int[] rankingIdx = RaceSimulator.randomRanking();
        String seed = RaceSimulator.findSeedForRanking(rankingIdx);
        List<Long> rankingPids = new ArrayList<>(RACER_COUNT);
        for (int idx : rankingIdx) {
            rankingPids.add(participants.get(idx).getId());
        }
        String resultJson;
        String cipher;
        String commit;
        try {
            resultJson = objectMapper.writeValueAsString(Map.of("roundId", round.getId(), "ranking", rankingPids));
            cipher = crypto.encrypt(resultJson);
            commit = crypto.commit(resultJson, seed, round.getId());
        } catch (Exception e) {
            log.error("race start: encrypt result failed race={} round={}", race.getId(), round.getId(), e);
            return;
        }
        try (var session = sqlSessionFactory.openSession(false)) {
            var rm = session.getMapper(RaceRoundMapper.class);
            var locked = rm.selectByIdForUpdate(round.getId());
            if (locked == null || !"BETTING".equals(locked.getStatus())) return;
            if (nowLdt().isBefore(locked.getBetEndAt())) return;
            if (rm.markRacing(round.getId(), seed, cipher, commit) == 0) return;
            session.commit();
        }
        broadcastRace(race.getRoomId(), "race_start", Map.of(
                "roundId", round.getId(),
                "roundNo", round.getRoundNo(),
                "seed", seed,
                "raceStartAt", toMs(round.getRaceStartAt()),
                "durationMs", RACE_DURATION_SECONDS * 1000
        ));
    }

    /** 比赛结束：解密名次 -> 按 60/30/10 分成结算 -> 入账 + 台账 + 通知（RACING -> PODIUM） */
    private void settleRound(RaceDO race, RaceRoundDO round) {
        final List<Long> ranking;
        final long pool;
        final long paidTotal;
        final List<RaceSettlementDO> settlements;
        final Map<Long, Long> userBetTotal = new LinkedHashMap<>();
        final Map<Long, Long> userPayout = new LinkedHashMap<>();
        final Map<Long, List<Map<String, Object>>> userWins = new LinkedHashMap<>();
        final List<RaceBetDO> allBets;

        try (var session = sqlSessionFactory.openSession(false)) {
            var rm = session.getMapper(RaceRoundMapper.class);
            var bm = session.getMapper(RaceBetMapper.class);
            var sm = session.getMapper(RaceSettlementMapper.class);
            var locked = rm.selectByIdForUpdate(round.getId());
            if (locked == null || !"RACING".equals(locked.getStatus())) return;
            if (nowLdt().isBefore(locked.getRaceStartAt().plusSeconds(RACE_DURATION_SECONDS))) return;

            String resultJson = crypto.decrypt(locked.getResultCipher());
            if (!crypto.verifyCommit(resultJson, locked.getSeed(), locked.getId(), locked.getResultCommit())) {
                log.error("race settle: result commit mismatch race={} round={}", race.getId(), round.getId());
            }
            ranking = parseRanking(resultJson, round.getId());
            var participantsById = participantMapper.selectByRaceId(race.getId()).stream()
                    .collect(Collectors.toMap(RaceParticipantDO::getId, p -> p));
            allBets = bm.selectActiveByRound(round.getId());
            pool = allBets.stream().mapToLong(b -> b.getAmount() == null ? 0 : b.getAmount()).sum();

            long s2 = pool * 30 / 100;
            long s3 = pool * 10 / 100;
            long s1 = pool - s2 - s3;
            long[] shareByRank = {s1, s2, s3};

            settlements = new ArrayList<>();
            var betPayout = new LinkedHashMap<Long, Long>();

            for (int rank = 0; rank < 3; rank++) {
                long pid = ranking.get(rank);
                var rankBets = allBets.stream().filter(b -> pid == (b.getParticipantId() == null ? -1 : b.getParticipantId())).toList();
                if (rankBets.isEmpty()) continue;
                var byUser = rankBets.stream().collect(Collectors.groupingBy(RaceBetDO::getUserId));
                var users = new ArrayList<>(byUser.keySet());
                users.sort((a, b) -> {
                    long ia = byUser.get(a).stream().mapToLong(x -> x.getId() == null ? 0 : x.getId()).min().orElse(0);
                    long ib = byUser.get(b).stream().mapToLong(x -> x.getId() == null ? 0 : x.getId()).min().orElse(0);
                    return Long.compare(ia, ib);
                });
                long share = shareByRank[rank];
                long base = share / users.size();
                long rem = share % users.size();
                for (int i = 0; i < users.size(); i++) {
                    long uid = users.get(i);
                    long userShare = base + (i < rem ? 1 : 0);
                    var userBets = new ArrayList<>(byUser.get(uid));
                    userBets.sort((x, y) -> Long.compare(x.getId() == null ? 0 : x.getId(), y.getId() == null ? 0 : y.getId()));
                    long userTotal = userBets.stream().mapToLong(b -> b.getAmount() == null ? 0 : b.getAmount()).sum();
                    long perBet = userShare / userBets.size();
                    long perBetRem = userShare % userBets.size();
                    for (int j = 0; j < userBets.size(); j++) {
                        betPayout.put(userBets.get(j).getId(), perBet + (j < perBetRem ? 1 : 0));
                    }
                    var s = new RaceSettlementDO();
                    s.setRoundId(round.getId());
                    s.setUserId(uid);
                    s.setParticipantId(pid);
                    s.setRankNo(rank + 1);
                    s.setBetAmount(userTotal);
                    s.setPayout(userShare);
                    settlements.add(s);

                    userPayout.merge(uid, userShare, Long::sum);
                    var p = participantsById.get(pid);
                    userWins.computeIfAbsent(uid, k -> new ArrayList<>()).add(Map.of(
                            "participantId", pid,
                            "participantName", p == null || p.getName() == null ? "" : p.getName(),
                            "rankNo", rank + 1,
                            "payout", userShare
                    ));
                }
            }

            long paid = 0;
            for (var bet : allBets) {
                Long p = betPayout.get(bet.getId());
                if (p != null) {
                    bm.updateStatusAndPayout(bet.getId(), "WON", p);
                    paid += p;
                } else {
                    bm.updateStatusAndPayout(bet.getId(), "LOST", null);
                }
            }
            if (!settlements.isEmpty()) {
                sm.insertBatch(settlements);
            }
            for (var s : settlements) {
                lmdWalletService.applyCredit(
                        session, s.getUserId(), s.getPayout(), TX_PAYOUT, REF_ROUND, round.getId(),
                        "赛马竞猜中奖", "race-pay-" + round.getId() + "-" + s.getUserId(), null, null
                );
            }
            for (var bet : allBets) {
                userBetTotal.merge(bet.getUserId(), bet.getAmount() == null ? 0 : bet.getAmount(), Long::sum);
            }
            rm.markPodium(round.getId(), paid, nowLdt());
            session.commit();
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
                "paidTotal", paidTotal
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
                    "wins", wins
            ));
            sendResultNotification(race, round, uid, betTotal, payout, wins);
        }
    }

    private void sendResultNotification(
            RaceDO race,
            RaceRoundDO round,
            long userId,
            long betTotal,
            long payout,
            List<Map<String, Object>> wins
    ) {
        String content;
        if (payout > 0) {
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
                    "wins", wins
            ));
        } catch (Exception e) {
            payloadJson = "{}";
        }
        siteNotificationService.sendToUser(null, userId, "HORSE_RACE", "赛马竞猜结果", content, "NORMAL", "/online", payloadJson)
                .subscribe(v -> {
                }, e -> log.error("race notify failed user={} round={}", userId, round.getId(), e));
    }

    /** 领奖台结束：轮次收尾并开启下一轮或关闭模式（PODIUM -> FINISHED -> ...） */
    private void finishRoundAndAdvance(RaceDO race, RaceRoundDO round) {
        try (var session = sqlSessionFactory.openSession(false)) {
            var rm = session.getMapper(RaceRoundMapper.class);
            var locked = rm.selectByIdForUpdate(round.getId());
            if (locked == null || !"PODIUM".equals(locked.getStatus())) return;
            if (nowLdt().isBefore(locked.getPodiumEndAt())) return;
            rm.markFinished(round.getId());
            int sessionType = race.getSessionType() == null ? 1 : race.getSessionType();
            int totalRounds = race.getTotalRounds() == null ? 1 : race.getTotalRounds();
            boolean needNext = sessionType == 3 || (sessionType == 2 && locked.getRoundNo() < totalRounds);
            if (needNext) {
                var next = new RaceRoundDO();
                next.setRaceId(race.getId());
                next.setRoundNo(locked.getRoundNo() + 1);
                var betDuration = race.getBetDurationSeconds() == null ? DEFAULT_BET_DURATION_SECONDS : race.getBetDurationSeconds();
                var betStart = locked.getPodiumEndAt();
                var betEnd = betStart.plusSeconds(betDuration);
                next.setStatus("BETTING");
                next.setBetStartAt(betStart);
                next.setBetEndAt(betEnd);
                next.setRaceStartAt(betEnd.plusSeconds(PRE_RACE_SECONDS));
                next.setPodiumEndAt(betEnd.plusSeconds(PRE_RACE_SECONDS + RACE_DURATION_SECONDS + PODIUM_DURATION_SECONDS));
                next.setTotalPool(0L);
                next.setBetCount(0);
                next.setPaidTotal(0L);
                rm.insert(next);
            } else {
                session.getMapper(RaceMapper.class).updateStatus(race.getId(), "CLOSED");
            }
            session.commit();
        }
        broadcastRaceUpdate(race.getRoomId());
    }

    // ---------- 工具 ----------

    private List<Long> parseRanking(String resultJson, long roundId) {
        try {
            JsonNode node = objectMapper.readTree(resultJson);
            var arr = node.get("ranking");
            if (arr == null || !arr.isArray() || arr.size() != RACER_COUNT) {
                throw new IllegalStateException("bad ranking payload");
            }
            var list = new ArrayList<Long>(RACER_COUNT);
            for (var v : arr) {
                list.add(v.asLong());
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("race result parse failed round=" + roundId, e);
        }
    }

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

    private ParticipantInfo toParticipantInfo(RaceParticipantDO p) {
        return new ParticipantInfo(
                p.getId(),
                p.getSortNo() == null ? 0 : p.getSortNo(),
                p.getAssetKey(),
                p.getName(),
                p.getType() == null ? 2 : p.getType()
        );
    }

    private RoundInfo toRoundInfo(RaceRoundDO r) {
        List<Long> ranking = null;
        var status = r.getStatus();
        if (("PODIUM".equals(status) || "FINISHED".equals(status)) && r.getResultCipher() != null) {
            try {
                ranking = parseRanking(crypto.decrypt(r.getResultCipher()), r.getId());
            } catch (Exception e) {
                log.error("round info: decrypt ranking failed round={}", r.getId(), e);
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
                r.getSeed(),
                r.getTotalPool() == null ? 0 : r.getTotalPool(),
                r.getBetCount() == null ? 0 : r.getBetCount(),
                r.getPaidTotal() == null ? 0 : r.getPaidTotal(),
                ranking
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
