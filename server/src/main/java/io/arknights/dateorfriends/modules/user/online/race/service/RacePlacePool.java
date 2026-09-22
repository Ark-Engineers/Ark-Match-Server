package io.arknights.dateorfriends.modules.user.online.race.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 位置彩池分配（纯函数，结算与双向校验共用同一实现）：
 * 前三名均视为中奖，奖池 P 按名次分三份（每人平分）：
 * - 三匹都被押中：各得 P/3，除不尽的余数给第一名；
 * - 部分名次无人押中：空份额按押中马匹彩池加权分摊（最大余数法，并列按名次先后）；
 * - 前三名全部无人押中且 P>0：无人中奖，不派发、不退款（注单全部 LOST）。
 * 名次系数：冠军 100%、亚军 80%、季军 60% 发放份额，未发放的 20% 不派发（系统回收）。
 * 同一马匹内按注单金额比例派奖：floor(amount*名次派发额/hp)，余数 +1 依次给最早的注单（注单 id 升序），
 * 保证该名次 Σpayout = 名次派发额。
 */
public final class RacePlacePool {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 名次发放系数（百分数）：冠军 100%、亚军 80%、季军 60% */
    public static final int[] RATE_PCT = {100, 80, 60};

    private RacePlacePool() {
    }

    public record BetInput(long id, long userId, long participantId, long amount) {
    }

    public record Distribution(long[] shareByRank, Map<Long, Long> betPayouts) {
    }

    /** 名次解析（与结算落库格式一致）：{"roundId":..,"ranking":[5 个对象 id]} */
    public static List<Long> parseRanking(String resultJson, long roundId) {
        try {
            JsonNode node = JSON.readTree(resultJson);
            var arr = node.get("ranking");
            if (arr == null || !arr.isArray() || arr.size() != RaceEngineService.RACER_COUNT) {
                throw new IllegalStateException("bad ranking payload");
            }
            var list = new ArrayList<Long>(RaceEngineService.RACER_COUNT);
            for (var v : arr) {
                list.add(v.asLong());
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("race result parse failed round=" + roundId, e);
        }
    }

    /** 前三名各名次份额（下标 0..2 对应第 1..3 名），总和恒等于 pool；无可派发时返回全 0 */
    public static long[] sharesByRank(long pool, List<Long> topThree, Map<Long, Long> horsePools) {
        long[] share = new long[3];
        if (pool <= 0) return share;
        var hp = new long[3];
        long backedSum = 0;
        int backedCount = 0;
        for (int i = 0; i < 3; i++) {
            hp[i] = horsePools.getOrDefault(topThree.get(i), 0L);
            if (hp[i] > 0) {
                backedSum += hp[i];
                backedCount++;
            }
        }
        if (backedCount == 0) return share;
        if (backedCount == 3) {
            long s = pool / 3;
            share[1] = s;
            share[2] = s;
            share[0] = pool - 2 * s;
            return share;
        }
        long paid = 0;
        var frac = new long[3];
        for (int i = 0; i < 3; i++) {
            if (hp[i] <= 0) continue;
            share[i] = pool * hp[i] / backedSum;
            frac[i] = pool * hp[i] % backedSum;
            paid += share[i];
        }
        long rem = pool - paid;
        var order = new ArrayList<Integer>();
        for (int i = 0; i < 3; i++) {
            if (hp[i] > 0) order.add(i);
        }
        order.sort((a, b) -> {
            int c = Long.compare(frac[b], frac[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        for (int i = 0; i < order.size() && rem > 0; i++, rem--) {
            share[order.get(i)] += 1;
        }
        return share;
    }

    /**
     * 完整分配。bets 为该轮全部未退款注单（结算时为 ACTIVE，校验时为 WON/LOST），
     * 参与该马匹彩池与派奖计算；amount 为 0 的注单不参与比例分摊但可能因余数 +1 获得 1 币（视为最早下注者优先）。
     * 份额按名次系数发放（冠军100%/亚军80%/季军60%，剩余不发放）；前三名全部无人押中且 pool>0 时无人中奖，betPayouts 为空（不退款）。
     */
    public static Distribution distribute(long pool, List<Long> ranking, List<BetInput> bets) {
        var topThree = ranking.subList(0, Math.min(3, ranking.size()));
        var horsePools = new LinkedHashMap<Long, Long>();
        for (var b : bets) {
            if (topThree.contains(b.participantId())) {
                horsePools.merge(b.participantId(), b.amount(), Long::sum);
            }
        }
        var share = sharesByRank(pool, topThree, horsePools);
        var payouts = new LinkedHashMap<Long, Long>();
        for (int rank = 0; rank < 3; rank++) {
            long pid = topThree.get(rank);
            long hp = horsePools.getOrDefault(pid, 0L);
            long rankPayout = share[rank] * RATE_PCT[rank] / 100;
            if (hp <= 0 || rankPayout <= 0) continue;
            var rankBets = new ArrayList<BetInput>();
            for (var b : bets) {
                if (b.participantId() == pid) rankBets.add(b);
            }
            rankBets.sort((x, y) -> Long.compare(x.id(), y.id()));
            long paid = 0;
            var pay = new long[rankBets.size()];
            for (int j = 0; j < rankBets.size(); j++) {
                pay[j] = rankBets.get(j).amount() * rankPayout / hp;
                paid += pay[j];
            }
            long rem = rankPayout - paid;
            for (int j = 0; j < rankBets.size() && rem > 0; j++, rem--) {
                pay[j] += 1;
            }
            for (int j = 0; j < rankBets.size(); j++) {
                payouts.put(rankBets.get(j).id(), pay[j]);
            }
        }
        return new Distribution(share, payouts);
    }
}
