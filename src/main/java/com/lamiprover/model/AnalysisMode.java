package com.lamiprover.model;

/** 容差分析模式。 */
public enum AnalysisMode {
  /** 枚举关联容差组的全部最坏角落（2^G，G=关联组数）。 */
  WORST_CORNERS,
  /** 固定种子蒙特卡洛抽样（每组一条确定性随机流）。 */
  SAMPLED
}
