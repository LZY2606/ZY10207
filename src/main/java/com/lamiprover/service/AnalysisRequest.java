package com.lamiprover.service;

import com.lamiprover.model.AnalysisMode;

/** 分析请求参数（随运行记录一并持久化，保证可重放）。 */
public record AnalysisRequest(
    AnalysisMode mode,
    /** 验收目标阻抗（Ω）。 */
    double targetOhm,
    /** 允许偏差（百分比）。 */
    double tolerancePercent,
    /** 固定种子（SAMPLED 模式）。 */
    Long seed,
    /** 抽样数量（SAMPLED 模式）。 */
    Integer sampleCount,
    /**
     * 反例开关：独立取极值，忽略关联容差组。
     * 当叠层存在关联组（多因子同组）时必须被拒绝。
     */
    boolean independentExtremes
) {

  public long seedOrThrow() {
    if (seed == null) {
      throw new IllegalArgumentException("SAMPLED 模式必须提供固定种子");
    }
    return seed;
  }

  public int sampleCountOrThrow() {
    if (sampleCount == null || sampleCount <= 0) {
      throw new IllegalArgumentException("SAMPLED 模式必须提供正的抽样数量");
    }
    return sampleCount;
  }
}
