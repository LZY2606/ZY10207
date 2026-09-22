package com.lamiprover.tol;

/** 制板厂给出的容差分布形式。 */
public enum Distribution {
  /** 均匀分布 U[nom - delta, nom + delta]。 */
  UNIFORM("均匀分布"),
  /** 截断正态 N(nom, sigma=delta/3)，±3σ 截断。 */
  NORMAL("正态分布(±3σ截断)"),
  /** 只做最坏角落，抽样时按极值 ±1 取值。 */
  EXTREME("仅极值");

  private final String label;

  Distribution(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
