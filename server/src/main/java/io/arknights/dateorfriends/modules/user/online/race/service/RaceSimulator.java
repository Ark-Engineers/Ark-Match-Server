package io.arknights.dateorfriends.modules.user.online.race.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 赛马动画确定性模拟器。
 * <p>
 * 设计目标（与前端 src/views/user/online/race/raceSim.ts 逐位一致）：
 * 后端先独立生成随机名次排序（SecureRandom 洗牌），再拒绝采样寻找一个 32 位种子，
 * 使得按该种子生成的 60 秒速度曲线仿真出的冲线顺序恰好等于预生成名次。
 * 前端在比赛开始时仅收到种子与开赛时间戳，用同一算法重建曲线，从而保证
 * 前端动画冲线名次与后端名次 100% 一致；名次本身加密存储于服务端，任何阶段不传输前端。
 * <p>
 * 速度模型：每 50ms 一个 tick（共 1200 tick = 60s），
 * 每名参赛者每 tick 速度 = BASE_SPEED * m * jitter，
 * m ∈ [1.03, 1.15]（每场每人恒定），jitter ∈ [0.98, 1.02]（每 tick 随机），
 * 保证所有参赛者在 51~59 秒之间陆续冲线，且速度始终处于限定区间内。
 */
public final class RaceSimulator {

    public static final int RACER_COUNT = 5;
    public static final double TRACK_LENGTH = 1000.0;
    public static final int TICK_MS = 50;
    public static final int TOTAL_TICKS = 1200;

    public static final double BASE_SPEED = TRACK_LENGTH / (TOTAL_TICKS * TICK_MS / 1000.0);
    public static final double M_MIN = 1.03;
    public static final double M_RANGE = 0.12;
    public static final double JITTER = 0.02;

    private static final int MIX = 0x9E3779B9;

    private RaceSimulator() {
    }

    /** mulberry32：与前端 TS 实现逐位一致的 32 位 PRNG */
    public static final class Mulberry32 {
        private int state;

        public Mulberry32(int seed) {
            this.state = seed;
        }

        public double next() {
            state += 0x6D2B79F5;
            int t = state;
            t = (t ^ (t >>> 15)) * (1 | t);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
        }
    }

    /** 由种子生成 5 名参赛者的累计位置曲线（profiles[i][t] = 第 i 名在 t tick 的位置） */
    public static double[][] buildProfiles(String seedHex) {
        int baseSeed = (int) Long.parseUnsignedLong(seedHex.trim(), 16);
        double[][] profiles = new double[RACER_COUNT][TOTAL_TICKS + 1];
        for (int i = 0; i < RACER_COUNT; i++) {
            var rng = new Mulberry32(baseSeed + i * MIX);
            double m = M_MIN + rng.next() * M_RANGE;
            double pos = 0.0;
            profiles[i][0] = 0.0;
            for (int t = 0; t < TOTAL_TICKS; t++) {
                double jitter = 1.0 + (rng.next() * 2.0 - 1.0) * JITTER;
                pos += BASE_SPEED * m * jitter * (TICK_MS / 1000.0);
                profiles[i][t + 1] = pos;
            }
        }
        return profiles;
    }

    /** 由曲线计算冲线顺序（道次下标 0-4；冲线 tick 早者在前，并列按道次） */
    public static int[] finishOrder(double[][] profiles) {
        int[] finishTick = new int[RACER_COUNT];
        for (int i = 0; i < RACER_COUNT; i++) {
            int t = TOTAL_TICKS;
            for (int k = 1; k <= TOTAL_TICKS; k++) {
                if (profiles[i][k] >= TRACK_LENGTH) {
                    t = k;
                    break;
                }
            }
            finishTick[i] = t;
        }
        Integer[] idx = {0, 1, 2, 3, 4};
        Arrays.sort(idx, (a, b) -> finishTick[a] != finishTick[b]
                ? Integer.compare(finishTick[a], finishTick[b])
                : Integer.compare(a, b));
        int[] order = new int[RACER_COUNT];
        for (int i = 0; i < RACER_COUNT; i++) {
            order[i] = idx[i];
        }
        return order;
    }

    /** 独立生成随机名次排序（道次下标数组，第 0 位为第一名） */
    public static int[] randomRanking() {
        var list = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        Collections.shuffle(list, new SecureRandom());
        int[] ranking = new int[RACER_COUNT];
        for (int i = 0; i < RACER_COUNT; i++) {
            ranking[i] = list.get(i);
        }
        return ranking;
    }

    /** 拒绝采样寻找使仿真冲线顺序等于目标名次的种子（8 位 hex） */
    public static String findSeedForRanking(int[] ranking) {
        var random = new SecureRandom();
        int seed = random.nextInt();
        for (int attempt = 0; attempt < 1_000_000; attempt++) {
            String hex = String.format("%08x", seed);
            int[] order = finishOrder(buildProfiles(hex));
            if (Arrays.equals(order, ranking)) return hex;
            seed += MIX;
        }
        throw new IllegalStateException("race simulator: cannot match ranking within attempts");
    }

    /** 8 位 hex 种子解析为带符号 32 位（与前端 parseInt(hex,16)|0 等价） */
    public static int parseSeed(String seedHex) {
        return (int) Long.parseUnsignedLong(seedHex.trim(), 16);
    }
}
