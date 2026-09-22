package com.lamprover.domain;

/**
 * 蛇形（等长绕线）容差。蛇形只影响走线长度与相位，不参与阻抗计算，
 * 因此这里保留输入用于显示与长度诊断，但不作为阻抗批次进入角落分析。
 */
public record MeanderTolerance(
        Quantity amplitude,
        ToleranceSpec amplitudeTol,
        Quantity pitch,
        ToleranceSpec pitchTol,
        /** 绕线段总长度。 */
        Quantity length) {

    public boolean present() {
        return amplitude != null || pitch != null || length != null;
    }
}
