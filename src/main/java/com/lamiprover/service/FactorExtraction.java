package com.lamiprover.service;

import com.lamiprover.model.Stackup;
import com.lamiprover.tol.Factor;
import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.Length;
import com.lamiprover.unit.LengthUnit;
import java.util.ArrayList;
import java.util.List;

/** 从叠层定义提取参与容差分析的因子（标称值已归一化为 SI）。 */
final class FactorExtraction {

  private FactorExtraction() {
  }

  static List<Factor> extract(Stackup s) {
    List<Factor> factors = new ArrayList<>();
    addLength(factors, "w", "线宽 w", s.traceWidth(), s.widthTolerance());
    addLength(factors, "t", "铜厚 t", s.copperThickness(), s.copperThicknessTolerance());
    addLength(factors, "h", "介质高度 h", s.dielectricHeight(), s.dielectricHeightTolerance());
    if (s.traceType().differential()) {
      addLength(factors, "s", "差分间距 s", s.pairSpacingOrZero(), s.pairSpacingTolerance());
    }
    addScalar(factors, "dk", "介电常数 Dk", s.dielectricConstant(),
        s.dielectricConstantTolerance());

    boolean outer = !s.traceType().stripline();
    double maskT = s.maskThicknessOrZero().metres();
    if (outer && maskT > 0.0) {
      addScalar(factors, "maskDk", "阻焊层 Dk", s.solderMaskDk(), s.solderMaskDkTolerance());
      addLength(factors, "maskT", "阻焊厚度", s.maskThicknessOrZero(), s.maskThicknessTolerance());
    }
    if (s.meanderTolerance() != null) {
      ToleranceSpec mt = s.meanderTolerance();
      if (mt.absolute() == null) {
        throw new IllegalArgumentException("蛇形(扇贝)容差必须给绝对量（单侧有效线宽波动），"
            + "不能对零标称值使用相对容差");
      }
      if (!mt.oneSided()) {
        throw new IllegalArgumentException("蛇形(扇贝)容差必须标记为单侧（只会减小有效线宽）");
      }
      factors.add(new Factor("meander", "蛇形边缘波动（单侧减宽）", 0.0, mt, true,
          mt.absolute().unit()));
    }
    return factors;
  }

  private static void addLength(List<Factor> factors, String key, String label,
                                 Length nominal, ToleranceSpec spec) {
    if (spec == null) {
      return;
    }
    if (spec.absolute() != null && !spec.absolute().unit().equals(nominal.unit())) {
      throw new IllegalArgumentException(
          label + " 的绝对容差单位 (" + spec.absolute().unit().symbol()
              + ") 必须与标称单位 (" + nominal.unit().symbol() + ") 一致");
    }
    factors.add(new Factor(key, label, nominal.metres(), spec, true, nominal.unit()));
  }

  private static void addScalar(List<Factor> factors, String key, String label,
                                double nominal, ToleranceSpec spec) {
    if (spec == null) {
      return;
    }
    if (spec.absolute() != null) {
      throw new IllegalArgumentException(label + " 是无量纲量，只能给相对(%)容差");
    }
    factors.add(new Factor(key, label, nominal, spec, false, null));
  }

  static LengthUnit displayUnit(Stackup s, String key) {
    return switch (key) {
      case "w" -> s.traceWidth().unit();
      case "t" -> s.copperThickness().unit();
      case "h" -> s.dielectricHeight().unit();
      case "s" -> s.pairSpacingOrZero().unit();
      case "maskT" -> s.maskThicknessOrZero().unit();
      case "meander" -> s.meanderTolerance() != null ? s.meanderTolerance().absolute().unit()
          : LengthUnit.MM;
      default -> null;
    };
  }
}
