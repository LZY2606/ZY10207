package com.lamiprover.tol;

import com.lamiprover.unit.LengthUnit;

/**
 * 参与容差分析的一个因子。nominal 已为 SI（长度为米，Dk 为无量纲）。
 * 因子经其 {@link ToleranceSpec} 归属到关联容差组与可回查的材料批次。
 */
public record Factor(
    String key,
    String label,
    double nominal,
    ToleranceSpec spec,
    boolean lengthFactor,
    LengthUnit displayUnit
) {

  public double magnitude() {
    if (spec.relativePercent() != null) {
      return Math.abs(nominal) * spec.relativePercent() / 100.0;
    }
    if (lengthFactor) {
      return spec.absolute().metres();
    }
    throw new IllegalStateException("无量纲因子只能使用相对容差: " + key);
  }

  /** cornerSign ∈ {-1,+1}；单侧因子在 +1 方向无扰动。 */
  public double cornerValue(int cornerSign) {
    if (cornerSign == 0) {
      return nominal;
    }
    if (spec.oneSided()) {
      return cornerSign < 0 ? magnitude() : 0.0;
    }
    return nominal + cornerSign * magnitude();
  }

  /** r ∈ [-1,1]；单侧因子 r ∈ [0,1]，其值直接是“减量幅度”（标称 0）。 */
  public double sampledValue(double r) {
    if (spec.oneSided()) {
      return Math.max(0.0, Math.min(1.0, r)) * magnitude();
    }
    return nominal + Math.max(-1.0, Math.min(1.0, r)) * magnitude();
  }
}
