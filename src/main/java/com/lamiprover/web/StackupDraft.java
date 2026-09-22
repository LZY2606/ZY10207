package com.lamiprover.web;

import com.lamiprover.model.TraceType;
import com.lamiprover.tol.Distribution;
import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.Length;

/** 页面提交的叠层草稿（v1 创建 / 新版本编辑共用）。单位为显式符号字符串。 */
public record StackupDraft(
    String name,
    String materialVersion,
    TraceType traceType,
    LengthValue traceWidth,
    LengthValue copperThickness,
    LengthValue dielectricHeight,
    LengthValue pairSpacing,
    Double dielectricConstant,
    Double solderMaskDk,
    LengthValue maskThickness,
    TolDraft widthTolerance,
    TolDraft copperThicknessTolerance,
    TolDraft dielectricHeightTolerance,
    TolDraft pairSpacingTolerance,
    TolDraft dielectricConstantTolerance,
    TolDraft solderMaskDkTolerance,
    TolDraft maskThicknessTolerance,
    TolDraft meanderTolerance
) {

  public record LengthValue(double value, String unit) {
    Length toLength() {
      return Length.of(value, unit);
    }
  }

  public record TolDraft(String group, String sourceLot, Distribution distribution,
                         Double relativePercent, LengthValue absolute, boolean oneSided) {
    ToleranceSpec toSpec() {
      if (relativePercent != null) {
        return ToleranceSpec.relative(group, sourceLot, distribution, relativePercent, oneSided);
      }
      if (absolute != null) {
        return ToleranceSpec.absolute(group, sourceLot, distribution, absolute.toLength(),
            oneSided);
      }
      throw new IllegalArgumentException("容差必须给 relativePercent 或 absolute");
    }
  }

  com.lamiprover.model.Stackup toModel() {
    return new com.lamiprover.model.Stackup(
        null, name, 1, null, materialVersion, traceType,
        traceWidth.toLength(), copperThickness.toLength(), dielectricHeight.toLength(),
        pairSpacing != null ? pairSpacing.toLength() : null,
        dielectricConstant,
        solderMaskDk != null ? solderMaskDk : 0.0,
        maskThickness != null ? maskThickness.toLength() : Length.of(0, "um"),
        specOrNull(widthTolerance), specOrNull(copperThicknessTolerance),
        specOrNull(dielectricHeightTolerance), specOrNull(pairSpacingTolerance),
        specOrNull(dielectricConstantTolerance), specOrNull(solderMaskDkTolerance),
        specOrNull(maskThicknessTolerance), specOrNull(meanderTolerance));
  }

  private static ToleranceSpec specOrNull(TolDraft d) {
    return d == null ? null : d.toSpec();
  }
}
