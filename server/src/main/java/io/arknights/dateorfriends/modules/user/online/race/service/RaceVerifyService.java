package io.arknights.dateorfriends.modules.user.online.race.service;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceBetDO;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceBetMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceRoundMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceSettlementMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 双向台账校验：
 * 1) 轮次奖池 total_pool = 全部注单金额之和（未退款 + 已退款）
 * 2) 已发放 = 结算表 payout 之和 = 龙门币 RACE_PAYOUT 流水之和
 * 3) 龙门币 RACE_BET 流水之和 = -total_pool；RACE_REFUND 流水之和 = 已退款注单总额
 * 4) 位置彩池重算对账（RacePlacePool 共享实现）：注单派奖、(用户,对象) 结算行、名次与 Σpayout=奖池守恒；
 *    仅对无退款且 paid_total = 未退款奖池的已结算轮次重算（旧 60/30/10 轮次与退款轮次跳过份额重算）
 * 5) 轮次无遗留 ACTIVE 下注；结果 commit 与密文/种子一致
 */
@Service
public class RaceVerifyService {

    private static final Logger log = LoggerFactory.getLogger(RaceVerifyService.class);

    private static final String TX_BET = "RACE_BET";
    private static final String TX_PAYOUT = "RACE_PAYOUT";
    private static final String TX_REFUND = "RACE_REFUND";
    private static final String REF_ROUND = "HORSE_RACE_ROUND";

    private final RaceRoundMapper roundMapper;
    private final RaceBetMapper betMapper;
    private final RaceSettlementMapper settlementMapper;
    private final LmdTransactionMapper lmdTransactionMapper;
    private final RaceResultCrypto crypto;

    public RaceVerifyService(
            RaceRoundMapper roundMapper,
            RaceBetMapper betMapper,
            RaceSettlementMapper settlementMapper,
            LmdTransactionMapper lmdTransactionMapper,
            RaceResultCrypto crypto
    ) {
        this.roundMapper = roundMapper;
        this.betMapper = betMapper;
        this.settlementMapper = settlementMapper;
        this.lmdTransactionMapper = lmdTransactionMapper;
        this.crypto = crypto;
    }

    public record VerifyResult(boolean ok, List<String> problems) {
    }

    public VerifyResult verifyRound(long roundId) {
        var problems = new ArrayList<String>();
        var round = roundMapper.selectById(roundId);
        if (round == null) {
            return new VerifyResult(false, List.of("round not found: " + roundId));
        }

        var bets = betMapper.selectAllByRound(roundId);
        long betPool = bets.stream()
                .filter(b -> !"REFUNDED".equals(b.getStatus()))
                .mapToLong(b -> b.getAmount() == null ? 0 : b.getAmount())
                .sum();
        long refundSum = bets.stream()
                .filter(b -> "REFUNDED".equals(b.getStatus()))
                .mapToLong(b -> b.getAmount() == null ? 0 : b.getAmount())
                .sum();

        long totalPool = round.getTotalPool() == null ? 0 : round.getTotalPool();
        if (betPool + refundSum != totalPool) {
            problems.add("round.total_pool=" + totalPool + " != bet sum=" + (betPool + refundSum)
                    + " (active=" + betPool + " refunded=" + refundSum + ")");
        }

        long paidTotal = round.getPaidTotal() == null ? 0 : round.getPaidTotal();
        long settlementSum = settlementMapper.sumPayoutByRound(roundId);
        long ledgerPayout = lmdTransactionMapper.sumByTypeAndRef(TX_PAYOUT, REF_ROUND, roundId);
        if (settlementSum != paidTotal) {
            problems.add("paid_total=" + paidTotal + " != settlement sum=" + settlementSum);
        }
        if (ledgerPayout != paidTotal) {
            problems.add("paid_total=" + paidTotal + " != RACE_PAYOUT ledger=" + ledgerPayout);
        }

        long ledgerBet = lmdTransactionMapper.sumByTypeAndRef(TX_BET, REF_ROUND, roundId);
        if (ledgerBet != -(betPool + refundSum)) {
            problems.add("RACE_BET ledger=" + ledgerBet + " != -total_bet=" + (-(betPool + refundSum)));
        }
        long ledgerRefund = lmdTransactionMapper.sumByTypeAndRef(TX_REFUND, REF_ROUND, roundId);
        if (ledgerRefund != refundSum) {
            problems.add("RACE_REFUND ledger=" + ledgerRefund + " != refundSum=" + refundSum);
        }

        long activeCount = bets.stream().filter(b -> "ACTIVE".equals(b.getStatus())).count();
        if (activeCount > 0 && ("PODIUM".equals(round.getStatus()) || "FINISHED".equals(round.getStatus()))) {
            problems.add("remaining ACTIVE bets=" + activeCount + " after settle");
        }

        long settledCount = bets.stream()
                .filter(b -> !"ACTIVE".equals(b.getStatus()))
                .count();
        if (settledCount > 0 && ("BETTING".equals(round.getStatus()) || "RACING".equals(round.getStatus()))) {
            problems.add("round status=" + round.getStatus() + " but settled bets=" + settledCount);
        }

        // 结果密文与承诺校验（密文未被篡改；早期回退密钥轮次会在此报告解密失败）
        String plain = null;
        if (round.getResultCipher() != null && round.getSeed() != null && round.getResultCommit() != null) {
            try {
                plain = crypto.decrypt(round.getResultCipher());
                if (!crypto.verifyCommit(plain, round.getSeed(), round.getId(), round.getResultCommit())) {
                    problems.add("result commit mismatch");
                }
            } catch (Exception e) {
                problems.add("result decrypt failed: " + e.getMessage());
            }
        }

        var settlements = settlementMapper.selectByRoundId(roundId);
        if (refundSum > 0 && !settlements.isEmpty()) {
            problems.add("refund=" + refundSum + " coexists with payouts=" + settlementSum);
        }

        // 位置彩池重算：仅对按新规则全额派发（无退款且 paid_total = 未退款奖池）的已结算轮次；
        // 密文不可解密时跳过重算（上方已报告解密失败）
        boolean settled = "PODIUM".equals(round.getStatus()) || "FINISHED".equals(round.getStatus());
        if (settled && betPool > 0 && refundSum == 0 && paidTotal == betPool && plain != null) {
            try {
                var ranking = RacePlacePool.parseRanking(plain, round.getId());
                var dist = RacePlacePool.distribute(betPool, ranking, toInputs(bets));

                for (var b : bets) {
                    Long expected = dist.betPayouts().get(b.getId());
                    String status = b.getStatus();
                    if (expected == null) {
                        if ("REFUNDED".equals(status)) continue;
                        if (!"LOST".equals(status)) {
                            problems.add("bet " + b.getId() + " status=" + status + " expected LOST");
                        } else if (b.getPayout() != null) {
                            problems.add("bet " + b.getId() + " LOST but payout=" + b.getPayout());
                        }
                    } else if (!"WON".equals(status)) {
                        problems.add("bet " + b.getId() + " expected WON but status=" + status);
                    } else {
                        long actual = b.getPayout() == null ? 0 : b.getPayout();
                        if (actual != expected) {
                            problems.add("bet " + b.getId() + " payout=" + actual + " != recomputed=" + expected);
                        }
                    }
                }

                long recomputed = dist.betPayouts().values().stream().mapToLong(Long::longValue).sum();
                if (recomputed != paidTotal) {
                    problems.add("recomputed payouts=" + recomputed + " != paid_total=" + paidTotal);
                }

                // (用户,对象) 结算行对账：payout=该对象注单派奖之和，betAmount=注额之和，rankNo=名次
                var expectedByUserHorse = new HashMap<Long, Map<Long, long[]>>();
                for (var b : bets) {
                    Long p = dist.betPayouts().get(b.getId());
                    if (p == null) continue;
                    var byHorse = expectedByUserHorse.computeIfAbsent(b.getUserId(), k -> new HashMap<>());
                    var acc = byHorse.computeIfAbsent(b.getAssetId(), k -> new long[2]);
                    acc[0] += b.getAmount() == null ? 0 : b.getAmount();
                    acc[1] += p;
                }
                var seen = new HashMap<Long, HashSet<Long>>();
                for (var s : settlements) {
                    var exp = expectedByUserHorse.getOrDefault(s.getUserId(), Map.<Long, long[]>of()).get(s.getAssetId());
                    if (exp == null) {
                        problems.add("settlement user=" + s.getUserId() + " horse=" + s.getAssetId() + " has no winning bets");
                        continue;
                    }
                    if (!seen.computeIfAbsent(s.getUserId(), k -> new HashSet<>()).add(s.getAssetId())) {
                        problems.add("duplicate settlement user=" + s.getUserId() + " horse=" + s.getAssetId());
                        continue;
                    }
                    long payout = s.getPayout() == null ? 0 : s.getPayout();
                    if (payout != exp[1]) {
                        problems.add("settlement user=" + s.getUserId() + " horse=" + s.getAssetId()
                                + " payout=" + payout + " != recomputed=" + exp[1]);
                    }
                    long betAmount = s.getBetAmount() == null ? 0 : s.getBetAmount();
                    if (betAmount != exp[0]) {
                        problems.add("settlement user=" + s.getUserId() + " horse=" + s.getAssetId()
                                + " betAmount=" + betAmount + " != " + exp[0]);
                    }
                    int expectedRank = ranking.indexOf(s.getAssetId()) + 1;
                    if (s.getRankNo() == null || s.getRankNo() != expectedRank) {
                        problems.add("settlement user=" + s.getUserId() + " horse=" + s.getAssetId()
                                + " rankNo=" + s.getRankNo() + " != " + expectedRank);
                    }
                }
                for (var e : expectedByUserHorse.entrySet()) {
                    for (var pid : e.getValue().keySet()) {
                        if (!seen.getOrDefault(e.getKey(), new HashSet<>()).contains(pid)) {
                            problems.add("missing settlement user=" + e.getKey() + " horse=" + pid);
                        }
                    }
                }
            } catch (Exception e) {
                problems.add("place pool recompute failed: " + e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            log.warn("race verify round={} problems={}", roundId, problems);
        }
        return new VerifyResult(problems.isEmpty(), problems);
    }

    private static List<RacePlacePool.BetInput> toInputs(List<RaceBetDO> bets) {
        var inputs = new ArrayList<RacePlacePool.BetInput>(bets.size());
        for (var b : bets) {
            if ("REFUNDED".equals(b.getStatus())) continue;
            inputs.add(new RacePlacePool.BetInput(
                    b.getId() == null ? 0 : b.getId(),
                    b.getUserId() == null ? 0 : b.getUserId(),
                    b.getAssetId() == null ? -1 : b.getAssetId(),
                    b.getAmount() == null ? 0 : b.getAmount()
            ));
        }
        return inputs;
    }
}
