package com.lamprover.core;

/**
 * 解析后的几何（全部 SI），喂给公式层。
 *
 * @param topology microstrip（表面微带，带可选阻焊）或 stripline（带状线）
 * @param w 走线宽度
 * @param t 铜厚
 * @param h 到最近参考平面的介质距离（微带：走线层到平面；带状线：到较近平面）
 * @param b 两参考平面之间总介质距离（仅带状线）
 * @param er 传播路径上的等效相对介电常数（带状线为各介质加权值）
 * @param s 差分边缘间距（单端为 0）
 * @param hm 上方阻焊介质厚度（微带，无则 0）
 * @param erm 阻焊相对介电常数（无则 0）
 */
public record GeometryInputs(
        Topology topology,
        double w,
        double t,
        double h,
        double b,
        double er,
        double s,
        double hm,
        double erm) {

    public enum Topology {MICROSTRIP, STRIPLINE}
}
