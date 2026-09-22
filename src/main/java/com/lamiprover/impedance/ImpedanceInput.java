package com.lamiprover.impedance;

/**
 * 已归一化为 SI 的几何/材料输入。
 *
 * @param w     走线宽度（米）
 * @param t     成品铜厚（米）
 * @param h     走线到参考平面的介质高度（米；带状线为到较近平面，且对称居中）
 * @param s     差分对边沿间距（米），单端为 NaN
 * @param er    介质相对介电常数 Dk
 * @param maskDk 阻焊层 Dk（微带线使用；带状线为 NaN）
 * @param maskT  阻焊层厚度（米）；0 表示裸微带
 */
public record ImpedanceInput(double w, double t, double h, double s,
                             double er, double maskDk, double maskT) {

  public static ImpedanceInput singleEnded(double w, double t, double h,
                                           double er, double maskDk, double maskT) {
    return new ImpedanceInput(w, t, h, Double.NaN, er, maskDk, maskT);
  }

  public static ImpedanceInput differential(double w, double t, double h, double s,
                                            double er, double maskDk, double maskT) {
    return new ImpedanceInput(w, t, h, s, er, maskDk, maskT);
  }
}
