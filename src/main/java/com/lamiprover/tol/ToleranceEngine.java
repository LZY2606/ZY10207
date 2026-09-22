package com.lamiprover.tol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 容差分析引擎。
 *
 * <p>关联规则：同一 {@code group} 的因子共享一个符号（最坏角落）与一条随机流（抽样），
 * 例如同一压合批次的介质高度 h 与铜厚 t 不允许独立取极值。
 */
public final class ToleranceEngine {

  private ToleranceEngine() {
  }

  /** 按因子 key 收集（保留输入顺序，保证可重放）。 */
  public static Map<String, Factor> index(List<Factor> factors) {
    Map<String, Factor> map = new LinkedHashMap<>();
    for (Factor f : factors) {
      map.put(f.key(), f);
    }
    return map;
  }

  public static List<String> groupOrder(List<Factor> factors) {
    return new ArrayList<>(new TreeSet<>(factors.stream().map(f -> f.spec().group()).toList()));
  }

  public static boolean hasCorrelatedGroup(List<Factor> factors) {
    Map<String, Integer> count = new LinkedHashMap<>();
    factors.forEach(f -> count.merge(f.spec().group(), 1, Integer::sum));
    return count.values().stream().anyMatch(c -> c > 1);
  }

  /**
   * 最坏角落：每个关联组一个方向（±1），共 2^G 个角落；同组成员同号。
   * 每个角落给出 key -> 因子值（SI）。
   */
  public static List<Map<String, Double>> worstCorners(List<Factor> factors) {
    List<String> groups = groupOrder(factors);
    int g = groups.size();
    if (g > 20) {
      throw new IllegalArgumentException("关联组过多(" + g + ")，最坏角落枚举 2^G 不可行");
    }
    int corners = 1 << g;
    List<Map<String, Double>> out = new ArrayList<>(corners);
    for (int c = 0; c < corners; c++) {
      Map<String, Integer> signByGroup = new LinkedHashMap<>();
      for (int gi = 0; gi < g; gi++) {
        // 二进制位决定该组方向，枚举顺序固定以保证可重放
        signByGroup.put(groups.get(gi), ((c >> gi) & 1) == 0 ? -1 : +1);
      }
      Map<String, Double> values = new LinkedHashMap<>();
      for (Factor f : factors) {
        values.put(f.key(), f.cornerValue(signByGroup.get(f.spec().group())));
      }
      out.add(values);
    }
    return out;
  }

  /**
   * 独立极值（反例基线）：忽略关联组，每个因子独立 ±1，共 2^F 个点。
   * 验收要求：存在关联组时，分析器必须拒绝该模式。
   */
  public static List<Map<String, Double>> independentExtremes(List<Factor> factors) {
    int n = factors.size();
    if (n > 20) {
      throw new IllegalArgumentException("因子过多，独立极值枚举不可行");
    }
    List<Map<String, Double>> out = new ArrayList<>(1 << n);
    for (int c = 0; c < (1 << n); c++) {
      Map<String, Double> values = new LinkedHashMap<>();
      for (int i = 0; i < n; i++) {
        Factor f = factors.get(i);
        int sign = ((c >> i) & 1) == 0 ? -1 : +1;
        values.put(f.key(), f.cornerValue(sign));
      }
      out.add(values);
    }
    return out;
  }

  /**
   * 固定种子抽样。每个组一条确定性 LCG 随机流（Box–Muller 生成正态），
   * 同组因子共享同一个 r。样本顺序与结果只依赖 (seed, N, 因子定义)。
   *
   * @return N 个样本，每个样本含 key -> r（-1..1，单侧为 0..1）
   */
  public static List<Map<String, Double>> sampleR(long seed, int n, List<Factor> factors) {
    if (n <= 0) {
      throw new IllegalArgumentException("抽样数量必须为正");
    }
    List<String> groups = groupOrder(factors);
    Map<String, DeterministicRng> rngByGroup = new LinkedHashMap<>();
    for (String grp : groups) {
      rngByGroup.put(grp, new DeterministicRng(mix(seed, grp.hashCode())));
    }
    List<Map<String, Double>> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      Map<String, Double> rByGroup = new LinkedHashMap<>();
      for (String grp : groups) {
        Factor representative = factors.stream()
            .filter(f -> f.spec().group().equals(grp)).findFirst().orElseThrow();
        rByGroup.put(grp, drawR(rngByGroup.get(grp), representative, i));
      }
      Map<String, Double> rs = new LinkedHashMap<>();
      for (Factor f : factors) {
        rs.put(f.key(), rByGroup.get(f.spec().group()));
      }
      out.add(rs);
    }
    return out;
  }

  private static double drawR(DeterministicRng rng, Factor f, int index) {
    Distribution d = f.spec().distribution();
    boolean oneSided = f.spec().oneSided();
    switch (d) {
      case UNIFORM -> {
        double u = rng.nextUnit();
        return oneSided ? u : (u * 2.0 - 1.0);
      }
      case NORMAL -> {
        double z = rng.nextGaussianClipped() / 3.0;
        if (oneSided) {
          return Math.abs(z);
        }
        return z;
      }
      case EXTREME -> {
        if (oneSided) {
          return (index & 1) == 0 ? 1.0 : 0.0;
        }
        return (index & 1) == 0 ? -1.0 : +1.0;
      }
      default -> throw new IllegalStateException("未知分布: " + d);
    }
  }

  private static long mix(long seed, int hash) {
    long z = seed ^ (long) hash * 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
