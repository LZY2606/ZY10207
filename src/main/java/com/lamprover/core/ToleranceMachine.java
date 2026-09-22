package com.lamprover.core;

import com.lamprover.domain.Distribution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容差机器：把同一压合批次的变量绑成关联组。
 *
 * <p>最坏角落只在“批次方向”上取极值：同一 lot 的所有变量共享
 * 方向（压合偏厚 / Dk 偏高 或反之），每个变量仍取自己的界，
 * 绝不会出现“介质取最薄、同批铜却取最厚”的物理不可能组合。</p>
 *
 * <p>固定种子抽样：为每个 lot 抽取一个共享标准化偏移 u∈(0,1)，
 * 同 lot 的所有变量用相同的 u 与各自分布，结果只取决于种子与批次顺序，可重放。</p>
 */
public final class ToleranceMachine {

    private ToleranceMachine() {
    }

    public record Batch(String lotId, List<LotVariable> variables) {
    }

    public static Map<String, Batch> groupByLot(List<LotVariable> vars) {
        Map<String, Batch> batches = new LinkedHashMap<>();
        int anonymous = 0;
        for (LotVariable v : vars) {
            String lot = v.lotId() == null || v.lotId().isBlank()
                    ? "independent:" + (anonymous++) : v.lotId();
            batches.computeIfAbsent(lot, k -> new Batch(k, new ArrayList<>())).variables().add(v);
        }
        return batches;
    }

    public record Corner(String id, Map<String, String> lotDirections, Map<String, Double> values) {
    }

    /**
     * 枚举关联角落。k 个含容差批次产生 2^k 个角落；
     * 无容差批次/变量只贡献标称值，不产生额外角落。
     */
    public static List<Corner> correlatedCorners(List<LotVariable> vars) {
        Map<String, Batch> batches = groupByLot(vars);
        List<Batch> active = new ArrayList<>();
        for (Batch b : batches.values()) {
            if (b.variables().stream().anyMatch(v -> v.spec().hasTolerance())) {
                active.add(b);
            }
        }
        int k = active.size();
        if (k > 20) {
            throw new IllegalArgumentException("关联批次数量 " + k + " 超过 20，角落数将超过 2^20");
        }
        List<Corner> corners = new ArrayList<>();
        int total = 1 << k;
        int width = Math.max(1, k);
        for (int mask = 0; mask < total; mask++) {
            Map<String, String> dirs = new LinkedHashMap<>();
            Map<String, Double> vals = new LinkedHashMap<>();
            for (LotVariable v : vars) {
                vals.put(v.key(), v.nominal());
            }
            for (int i = 0; i < k; i++) {
                Batch b = active.get(i);
                boolean high = ((mask >> i) & 1) == 1;
                dirs.put(b.lotId(), high ? "+high" : "-low");
                for (LotVariable v : b.variables()) {
                    if (v.spec().hasTolerance()) {
                        vals.put(v.key(), high ? v.upperSi() : v.lowerSi());
                    }
                }
            }
            String bits = k == 0 ? "0" : String.format("%" + width + "s",
                    Integer.toBinaryString(mask)).replace(' ', '0');
            corners.add(new Corner("C" + bits, dirs, vals));
        }
        return corners;
    }

    /**
     * 诊断“逐变量独立取极值”赋值：同一 lot 内出现方向冲突时返回描述；
     * 验收要求拒绝这种角落。无冲突返回 null。
     *
     * @param signs 变量 key → +1（取上界）/-1（取下界）
     */
    public static String independentExtremaConflict(List<LotVariable> vars, Map<String, Integer> signs) {
        Map<String, String> lotHighVar = new LinkedHashMap<>();
        Map<String, String> lotLowVar = new LinkedHashMap<>();
        for (LotVariable v : vars) {
            if (v.lotId() == null || v.lotId().isBlank() || !v.spec().hasTolerance()) {
                continue;
            }
            Integer s = signs.get(v.key());
            if (s == null) {
                continue;
            }
            if (s > 0) {
                lotHighVar.put(v.lotId(), v.role() + "[" + v.key() + "]");
            } else {
                lotLowVar.put(v.lotId(), v.role() + "[" + v.key() + "]");
            }
        }
        for (String lot : lotHighVar.keySet()) {
            if (lotLowVar.containsKey(lot)) {
                return "批次 " + lot + " 内方向冲突：" + lotHighVar.get(lot)
                        + " 取上界而 " + lotLowVar.get(lot) + " 取下界；同一次压合的量禁止独立取极值";
            }
        }
        return null;
    }

    /** 固定种子抽样，结果只取决于 seed、批次顺序与样本序号。 */
    public static List<Sample> samples(List<LotVariable> vars, long seed, int count) {
        if (count <= 0 || count > 1_000_000) {
            throw new IllegalArgumentException("抽样数量必须在 1..1_000_000");
        }
        Map<String, Batch> batches = groupByLot(vars);
        List<Batch> order = new ArrayList<>(batches.values());
        List<Sample> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long rng = mix(seed, i);
            Map<String, Double> lotU = new LinkedHashMap<>();
            for (Batch b : order) {
                rng = nextLcg(rng);
                lotU.put(b.lotId(), toUnit(rng));
            }
            Map<String, Double> vals = new LinkedHashMap<>();
            Map<String, String> trace = new LinkedHashMap<>();
            for (Batch b : order) {
                double u = lotU.get(b.lotId());
                for (LotVariable v : b.variables()) {
                    if (!v.spec().hasTolerance()) {
                        vals.put(v.key(), v.nominal());
                        trace.put(v.key(), "lot=" + b.lotId() + " nominal");
                        continue;
                    }
                    double value;
                    String detail;
                    if (v.spec().dist() == Distribution.NORMAL) {
                        double z = normalQuantile(u);
                        double sigma = v.spec().lowerFraction();
                        double upperSigma = v.spec().upperFraction();
                        double sigmaUsed = z >= 0 ? upperSigma : sigma;
                        double fraction = clamp(z * sigmaUsed / 3.0, -sigma, upperSigma);
                        value = clamp(v.nominal() * (1 + fraction), v.lowerSi(), v.upperSi());
                        detail = String.format("lot=%s u=%.4f normal z=%.2f frac=%+.3f%%",
                                b.lotId(), u, z, fraction * 100);
                    } else {
                        double lo = v.lowerSi();
                        double hi = v.upperSi();
                        value = lo + u * (hi - lo);
                        detail = String.format("lot=%s u=%.4f uniform[%.6g,%.6g]",
                                b.lotId(), u, lo, hi);
                    }
                    vals.put(v.key(), value);
                    trace.put(v.key(), detail);
                }
            }
            out.add(new Sample("S" + (i + 1), vals, lotU, trace));
        }
        return out;
    }

    public record Sample(String id, Map<String, Double> values,
                         Map<String, Double> lotDraws, Map<String, String> trace) {
    }

    /* ---- 确定性 LCG，跨平台可重放 ---- */

    private static long mix(long seed, int sampleIndex) {
        long s = seed ^ 0x9E3779B97F4A7C15L;
        s ^= (long) (sampleIndex + 1) * 0xC2B2AE3D27D4EB4FL;
        s ^= s >>> 29;
        s *= 0xBF58476D1CE4E5B9L;
        s ^= s >>> 31;
        return s & Long.MAX_VALUE;
    }

    private static long nextLcg(long state) {
        return (state * 6364136223846793005L + 1442695040888963407L) & Long.MAX_VALUE;
    }

    private static double toUnit(long state) {
        return ((state >>> 11) + 1) / (double) ((1L << 53) + 2);
    }

    /** 逆正态 CDF 的 Acklam 近似。 */
    static double normalQuantile(double p) {
        double pp = Math.min(Math.max(p, 1e-12), 1 - 1e-12);
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02,
                -2.759285104469687e+02, 1.383577518672690e+02,
                -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02,
                -1.556989798598866e+02, 6.680131188771972e+01,
                -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01,
                -2.400758277161838e+00, -2.549732539343734e+00,
                4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01,
                2.445134137142996e+00, 3.754408661907416e+00};
        double plow = 0.02425, phigh = 1 - plow;
        double q, r;
        if (pp < plow) {
            q = Math.sqrt(-2 * Math.log(pp));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (pp <= phigh) {
            q = pp - 0.5;
            r = q * q;
            return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                    / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
        }
        q = Math.sqrt(-2 * Math.log(1 - pp));
        return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
