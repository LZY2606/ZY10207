package com.lamiprover.tol;

import com.lamiprover.unit.Length;

/**
 * 一个标称量的容差规格（制板厂口径）。
 *
 * @param group          关联容差组 id；同组因子（如同一压合批次的介质高度与铜厚）
 *                       在最坏角落必须取同一方向，抽样必须使用同一随机流
 * @param sourceLot      可回查的材料批次 / 工艺批（如压合批 LOT-PRESS-2026-09）
 * @param distribution   分布形式
 * @param relativePercent 相对容差（百分比，相对标称值）；与 absolute 二选一
 * @param absolute       绝对容差（带显式单位）；与 relativePercent 二选一
 * @param oneSided       true 表示该容差只使目标量单向减小（蛇形边缘扇贝：有效线宽只变小）
 */
public record ToleranceSpec(
    String group,
    String sourceLot,
    Distribution distribution,
    Double relativePercent,
    Length absolute,
    boolean oneSided
) {

  public ToleranceSpec {
    if (distribution == null) {
      throw new IllegalArgumentException("容差分布形式不能为空");
    }
    boolean rel = relativePercent != null;
    boolean abs = absolute != null;
    if (rel == abs) {
      throw new IllegalArgumentException(
          "容差必须且只能指定 relativePercent 或 absolute 之一");
    }
    if (rel && relativePercent <= 0) {
      throw new IllegalArgumentException("relativePercent 必须为正");
    }
    if (group == null || group.isBlank()) {
      throw new IllegalArgumentException("容差必须显式归属一个组（独立量各自成组）");
    }
  }

  public double magnitude(double nominalMetres) {
    if (relativePercent != null) {
      return Math.abs(nominalMetres) * relativePercent / 100.0;
    }
    return absolute.metres();
  }

  public static ToleranceSpec relative(String group, String lot, Distribution d,
                                       double percent, boolean oneSided) {
    return new ToleranceSpec(group, lot, d, percent, null, oneSided);
  }

  public static ToleranceSpec absolute(String group, String lot, Distribution d,
                                       Length abs, boolean oneSided) {
    return new ToleranceSpec(group, lot, d, null, abs, oneSided);
  }
}
