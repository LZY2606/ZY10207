package com.lamiprover.impedance;

import com.lamiprover.model.TraceType;
import java.util.ArrayList;
import java.util.List;

/**
 * IPC-2141 族工程阻抗公式（输入单位均为 SI；公式内部为无量纲比值）。
 *
 * <p>所有公式先做适用域校验；越界返回 {@link ImpedanceResult#out}，
 * 不给出阻抗数值，只保留越界指标。
 */
public final class Formulas {

  private Formulas() {
  }

  public static ImpedanceResult evaluate(TraceType type, ImpedanceInput in) {
    return switch (type) {
      case MICROSTRIP_SE -> microstripSe(in);
      case MICROSTRIP_DIFF -> microstripDiff(in);
      case STRIPLINE_SE -> striplineSe(in);
      case STRIPLINE_DIFF -> striplineDiff(in);
    };
  }

  private static List<Violation> baseChecks(ImpedanceInput in, double tOverHMax) {
    List<Violation> v = new ArrayList<>();
    positive(in.w(), "w 线宽", v);
    positive(in.t(), "t 铜厚", v);
    positive(in.h(), "h 介质高度", v);
    range(in.er(), 1.0, 10.0, "Dk 介电常数", v);
    ratio(in.t() / in.h(), 0.0, tOverHMax, "t/h 铜厚/介质高度", v);
    if (in.t() >= in.h()) {
      v.add(new Violation("t<h", in.t() / in.h(), 0.0, 1.0,
          "铜厚 t 必须小于介质高度 h（走线嵌满介质层，公式物理前提失效）"));
    }
    return v;
  }

  private static void positive(double value, String name, List<Violation> out) {
    if (!(value > 0)) {
      out.add(new Violation(name, value, 0.0, Double.POSITIVE_INFINITY,
          name + " 必须为正"));
    }
  }

  private static void range(double value, double min, double max, String name,
                            List<Violation> out) {
    if (Double.isNaN(value) || value < min || value > max) {
      out.add(new Violation(name, value, min, max, name + " 超出公式适用范围"));
    }
  }

  private static void ratio(double value, double min, double max, String name,
                            List<Violation> out) {
    if (Double.isNaN(value) || value < min || value > max) {
      out.add(new Violation(name, value, min, max, name + " 超出公式适用范围"));
    }
  }

  // ---- 1. 表层微带线·单端 ----
  private static ImpedanceResult microstripSe(ImpedanceInput in) {
    List<Violation> v = baseChecks(in, 0.5);
    ratio(in.w() / in.h(), 0.05, 4.0, "w/h 线宽/介质高度", v);
    microstripMaskChecks(in, v);
    if (!v.isEmpty()) {
      return ImpedanceResult.out("IPC2141-MICROSTRIP-SE", "表层微带线·单端", v);
    }
    double effEr = effectiveMicrostripEr(in);
    double weff = effectiveWidth(in);
    double z = 87.0 / Math.sqrt(effEr + 1.41)
        * Math.log(5.98 * in.h() / (0.8 * weff + in.t()));
    return ImpedanceResult.ok("IPC2141-MICROSTRIP-SE", "表层微带线·单端", z, effEr);
  }

  // ---- 2. 表层微带线·差分 ----
  private static ImpedanceResult microstripDiff(ImpedanceInput in) {
    List<Violation> v = baseChecks(in, 0.5);
    ratio(in.w() / in.h(), 0.05, 4.0, "w/h 线宽/介质高度", v);
    positive(in.s(), "s 差分对边沿间距", v);
    ratio(in.s() / in.h(), 0.05, 4.0, "s/h 间距/介质高度", v);
    microstripMaskChecks(in, v);
    if (!v.isEmpty()) {
      return ImpedanceResult.out("IPC2141-MICROSTRIP-DIFF", "表层微带线·差分", v);
    }
    double effEr = effectiveMicrostripEr(in);
    double weff = effectiveWidth(in);
    double z0 = 87.0 / Math.sqrt(effEr + 1.41)
        * Math.log(5.98 * in.h() / (0.8 * weff + in.t()));
    double zDiff = 2.0 * z0 * (1.0 - 0.48 * Math.exp(-0.96 * in.s() / in.h()));
    return ImpedanceResult.ok("IPC2141-MICROSTRIP-DIFF", "表层微带线·差分", zDiff, effEr);
  }

  // ---- 3. 带状线·单端（对称，走线居中，上下平面等距）----
  private static ImpedanceResult striplineSe(ImpedanceInput in) {
    List<Violation> v = baseChecks(in, 0.35);
    ratio(in.w() / in.h(), 0.05, 6.0, "w/h 线宽/平面间距", v);
    striplineMaskChecks(in, v);
    if (!v.isEmpty()) {
      return ImpedanceResult.out("IPC2141-STRIPLINE-SE", "带状线·单端", v);
    }
    double z = 60.0 / Math.sqrt(in.er())
        * Math.log(4.0 * in.h() / (0.67 * Math.PI * (0.8 * in.w() + in.t())));
    return ImpedanceResult.ok("IPC2141-STRIPLINE-SE", "带状线·单端", z, in.er());
  }

  // ---- 4. 带状线·差分（对称）----
  private static ImpedanceResult striplineDiff(ImpedanceInput in) {
    List<Violation> v = baseChecks(in, 0.35);
    ratio(in.w() / in.h(), 0.05, 6.0, "w/h 线宽/平面间距", v);
    positive(in.s(), "s 差分对边沿间距", v);
    ratio(in.s() / in.h(), 0.05, 4.0, "s/h 间距/平面间距", v);
    striplineMaskChecks(in, v);
    if (!v.isEmpty()) {
      return ImpedanceResult.out("IPC2141-STRIPLINE-DIFF", "带状线·差分", v);
    }
    double zSe = 60.0 / Math.sqrt(in.er())
        * Math.log(4.0 * in.h() / (0.67 * Math.PI * (0.8 * in.w() + in.t())));
    double zDiff = 2.0 * zSe * (1.0 - 0.347 * Math.exp(-2.9 * in.s() / in.h()));
    return ImpedanceResult.ok("IPC2141-STRIPLINE-DIFF", "带状线·差分", zDiff, in.er());
  }

  private static double effectiveWidth(ImpedanceInput in) {
    double ratio = (in.t() / in.h()) * (in.t() / (in.h() + 1.0)) + 0.8;
    return in.w() + (in.t() / Math.PI) * Math.log(4.0 * Math.E / Math.sqrt(ratio));
  }

  private static void microstripMaskChecks(ImpedanceInput in, List<Violation> v) {
    if (in.maskT() > 0) {
      range(in.maskDk(), 1.0, 10.0, "maskDk 阻焊层介电常数", v);
      ratio(in.maskT() / in.h(), 0.0, 1.0, "maskT/h 阻焊厚度/介质高度", v);
    }
  }

  private static void striplineMaskChecks(ImpedanceInput in, List<Violation> v) {
    if (!Double.isNaN(in.maskDk()) && in.maskDk() > 0) {
      // 带状线位于介质内部，不存在阻焊覆盖；给出越界指标而非静默忽略
      v.add(new Violation("mask", in.maskDk(), 0, 0,
          "带状线为内层结构，不应配置阻焊层参数（maskDk/maskThickness）"));
    }
  }

  /**
   * IPC-2141 闭合式直接以介质 εr 入式（公式本身已含半空间近似）。
   * 有阻焊覆盖时，以填充因子 q = maskT/(h+maskT) 对“面向走线的等效介电常数”
   * 做保守工程加权（系数 0.5），页面公式说明中明确标注为工程近似。
   */
  private static double effectiveMicrostripEr(ImpedanceInput in) {
    if (in.maskT() <= 0) {
      return in.er();
    }
    double q = in.maskT() / (in.h() + in.maskT());
    return in.er() + 0.5 * q * (in.maskDk() - 1.0);
  }

  // ---- 页面/报告用公式说明 ----
  public static FormulaInfo info(TraceType type) {
    return switch (type) {
      case MICROSTRIP_SE -> new FormulaInfo(
          "IPC2141-MICROSTRIP-SE", "表层微带线·单端",
          List.of(
              "Z0 = 87 / sqrt(εr + 1.41) · ln[ 5.98·h / (0.8·weff + t) ]",
              "weff = w + (t/π)·ln{ 4e / sqrt[(t/h)·(t/(h+1)) + 0.8] }",
              "阻焊覆盖: εr* = εr + 0.5·q·(maskDk − 1), q = maskT/(h+maskT)"),
          "外层走线，下侧为参考平面、上侧为空气（或阻焊层）；对称叠层的外层。",
          List.of("0.05 ≤ w/h ≤ 4", "0 < t/h ≤ 0.5", "t < h", "1 ≤ Dk ≤ 10",
              "有阻焊时 0 < maskT/h ≤ 1、1 ≤ maskDk ≤ 10"),
          List.of("阻焊覆盖采用填充因子工程加权 εe(阻焊)，属 IPC-2141 工程近似；"
              + "精密设计应以制板厂实测 Dk 与场求解器复核。"));
      case MICROSTRIP_DIFF -> new FormulaInfo(
          "IPC2141-MICROSTRIP-DIFF", "表层微带线·差分",
          List.of(
              "Zdiff = 2·Z0 · ( 1 − 0.48·exp(−0.96·s/h) )",
              "Z0 取表层微带线单端公式（含阻焊修正）"),
          "外层边缘耦合差分对；s 为两线边沿到边沿间距；参考平面在下。",
          List.of("0.05 ≤ w/h ≤ 4", "0.05 ≤ s/h ≤ 4", "0 < t/h ≤ 0.5", "t < h",
              "1 ≤ Dk ≤ 10", "有阻焊时 0 < maskT/h ≤ 1、1 ≤ maskDk ≤ 10"),
          List.of("耦合修正项为 IPC-2141 边缘耦合工程拟合（间距越小 Zdiff 越低）。"));
      case STRIPLINE_SE -> new FormulaInfo(
          "IPC2141-STRIPLINE-SE", "带状线·单端",
          List.of(
              "Z0 = 60/sqrt(εr) · ln[ 1.9·h / (0.8w + t) ]",
              "h = 走线到较近参考平面的距离（本工具要求上下平面等距、走线居中）"),
          "内层走线，完全嵌入均匀介质；上下参考平面等距（对称叠层）。",
          List.of("0.05 ≤ w/h ≤ 6", "0 < t/h ≤ 0.35", "t < h", "1 ≤ Dk ≤ 10",
              "不得配置阻焊层参数"),
          List.of("带状线无空气侧，等效 Dk 即介质 Dk；介质流胶不对称时本模型不适用。"));
      case STRIPLINE_DIFF -> new FormulaInfo(
          "IPC2141-STRIPLINE-DIFF", "带状线·差分",
          List.of(
              "Zdiff = 2·Z0 · ( 1 − 0.347·exp(−2.9·s/h) )"),
          "内层边缘耦合差分对；上下参考平面等距、走线居中。",
          List.of("0.05 ≤ w/h ≤ 6", "0.05 ≤ s/h ≤ 4", "0 < t/h ≤ 0.35", "t < h",
              "1 ≤ Dk ≤ 10", "不得配置阻焊层参数"),
          List.of("耦合修正系数为 IPC-2141 工程拟合值。"));
    };
  }
}
