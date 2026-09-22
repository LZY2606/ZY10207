package com.lamiprover.model;

import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.Length;

/**
 * 对称叠层 + 走线几何 + 材料版本。
 *
 * <p>几何量全部带显式单位；阻抗计算前由服务层统一转为 SI（米）。
 * 模型描述的是“对称”结构：带状线上下参考平面等距，走线居中；
 * 微带线 h 为走线到其下参考平面的介质高度，maskThickness 为表层阻焊层厚度。
 */
public record Stackup(
    String id,
    String name,
    int version,
    String parentVersionId,
    String materialVersion,
    TraceType traceType,
    Length traceWidth,
    Length copperThickness,
    Length dielectricHeight,
    /** 仅差分型使用：两根走线边沿到边沿的间距（edge-to-edge spacing）。 */
    Length pairSpacing,
    /** 介质材料（半固化片/芯板）在工作频率下的相对介电常数 Dk。 */
    double dielectricConstant,
    /** 阻焊层（绿油）相对介电常数；仅微带线有效。 */
    double solderMaskDk,
    /** 阻焊层厚度；仅微带线有效，0 表示裸铜无覆盖。 */
    Length maskThickness,
    // ---- 容差（制板厂给出的标称值 + 分布）----
    ToleranceSpec widthTolerance,
    ToleranceSpec copperThicknessTolerance,
    ToleranceSpec dielectricHeightTolerance,
    ToleranceSpec pairSpacingTolerance,
    ToleranceSpec dielectricConstantTolerance,
    ToleranceSpec solderMaskDkTolerance,
    ToleranceSpec maskThicknessTolerance,
    /** 蛇形容差：走线边缘扇贝状波动（meander/scallop）的单侧有效线宽波动量。 */
    ToleranceSpec meanderTolerance
) {

  public Length pairSpacingOrZero() {
    return pairSpacing != null ? pairSpacing : Length.of(0, "mm");
  }

  public Length maskThicknessOrZero() {
    return maskThickness != null ? maskThickness : Length.of(0, "um");
  }

  /** 新版本必须指向旧版本 id；旧结论不自动重算。 */
  public Stackup withIdAndVersion(String newId, int newVersion) {
    return new Stackup(
        newId, name, newVersion, this.id, materialVersion, traceType,
        traceWidth, copperThickness, dielectricHeight, pairSpacing,
        dielectricConstant, solderMaskDk, maskThickness,
        widthTolerance, copperThicknessTolerance, dielectricHeightTolerance,
        pairSpacingTolerance, dielectricConstantTolerance, solderMaskDkTolerance,
        maskThicknessTolerance, meanderTolerance);
  }
}
