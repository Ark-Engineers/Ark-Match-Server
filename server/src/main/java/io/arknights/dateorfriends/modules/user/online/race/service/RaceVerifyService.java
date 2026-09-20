package io.arknights.dateorfriends.modules.user.online.race.service;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceBetMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceRoundMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceSettlementMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 双向台账校验：
 * 1) 轮次奖池 = 非退款下注金额之和
 * 2) 已发放 = 结算表 payout 之和 = 龙门币 RACE_PAYOUT 流水之和
 * 3) 龙门币 RACE_BET 流水之和 = -奖池；RACE_REFUND 流水之和 = -退款总额
 * 4) 各名次份额符合 60/30/10，同名次多人之间差额 <= 1，单人 payout = 其赢单 payout 之和
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
        if (betPool != totalPool) {
            problems.add("round.total_pool=" + totalPool + " != bet sum=" + betPool);
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
        if (ledgerBet != -betPool) {
            problems.add("RACE_BET ledger=" + ledgerBet + " != -betPool=" + (-betPool));
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

        // 结算与份额校验（仅对已结算轮次）
        if (betPool > 0) {
            var settlements = settlementMapper.selectByRoundId(roundId);
            var wonPayoutById = new HashMap<Long, Long>();
            for (var b : bets) {
                if ("WON".equals(b.getStatus()) && b.getPayout() != null) {
                    wonPayoutById.put(b.getId(), b.getPayout());
                }
            }
            long s2 = betPool * 30 / 100;
            long s3 = betPool * 10 / 100;
            long s1 = betPool - s2 - s3;
            long[] shareByRank = {s1, s2, s3};

            var payoutByRank = new long[3];
            var minByRank = new long[]{Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
            var maxByRank = new long[]{Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
            var userSettlementPayout = new HashMap<Long, Long>();
            for (var s : settlements) {
                int rank = s.getRankNo() == null ? 0 : s.getRankNo();
                if (rank < 1 || rank > 3) {
                    problems.add("settlement rank out of range: " + s.getRankNo());
                    continue;
                }
                long payout = s.getPayout() == null ? 0 : s.getPayout();
                payoutByRank[rank - 1] += payout;
                minByRank[rank - 1] = Math.min(minByRank[rank - 1], payout);
                maxByRank[rank - 1] = Math.max(maxByRank[rank - 1], payout);
                userSettlementPayout.merge(s.getUserId(), payout, Long::sum);
            }
            for (int rank = 0; rank < 3; rank++) {
                if (payoutByRank[rank] != shareByRank[rank]) {
                    problems.add("rank " + (rank + 1) + " payout=" + payoutByRank[rank] + " != share=" + shareByRank[rank]);
                }
                if (maxByRank[rank] != Long.MIN_VALUE && maxByRank[rank] - minByRank[rank] > 1) {
                    problems.add("rank " + (rank + 1) + " share diff > 1 (max=" + maxByRank[rank] + " min=" + minByRank[rank] + ")");
                }
            }
            for (var e : userSettlementPayout.entrySet()) {
                long wonSum = bets.stream()
                        .filter(b -> "WON".equals(b.getStatus()) && e.getKey().equals(b.getUserId()))
                        .mapToLong(b -> b.getPayout() == null ? 0 : b.getPayout())
                        .sum();
                if (wonSum != e.getValue()) {
                    problems.add("user " + e.getKey() + " settlement=" + e.getValue() + " != won bet payout=" + wonSum);
                }
            }
        }

        // commit 一致性（密文未被篡改）
        if (round.getResultCipher() != null && round.getSeed() != null && round.getResultCommit() != null) {
            try {
                String plain = crypto.decrypt(round.getResultCipher());
                if (!crypto.verifyCommit(plain, round.getSeed(), round.getId(), round.getResultCommit())) {
                    problems.add("result commit mismatch");
                }
            } catch (Exception e) {
                problems.add("result decrypt failed: " + e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            log.warn("race verify round={} problems={}", roundId, problems);
        }
        return new VerifyResult(problems.isEmpty(), problems);
    }
}
