package com.lamprover.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条待审查的走线几何。
 *
 * @param signalLayerName 走线所在铜层名称
 * @param type 单端 / 差分
 * @param width 线宽
 * @param widthTol 线宽容差（蚀刻批次）
 * @param spacing 差分线边缘到边缘间距（单端可空）
 * @param spacingTol 间距容差
 * @param copperThicknessTol 允许在走线层铜厚之外覆写铜厚容差（一般为空，取铜层厚度容差）
 * @param meander 蛇形容差，仅记录不参与阻抗
 * @param target 目标阻抗带（欧姆）
 */
public record Trace(
        String name,
        String signalLayerName,
        TraceType type,
        Quantity width,
        ToleranceSpec widthTol,
        Quantity spacing,
        ToleranceSpec spacingTol,
        ToleranceSpec copperThicknessTol,
        MeanderTolerance meander,
        ImpedanceTarget target) {

    public List<String> validationErrors() {
        List<String> errs = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errs.add("走线名称不能为空");
        }
        if (signalLayerName == null || signalLayerName.isBlank()) {
            errs.add(name + ": 必须指定信号层");
        }
        if (type == null) {
            errs.add(name + ": 走线类型不能为空");
        }
        if (width == null || width.si() <= 0) {
            errs.add(name + ": 线宽必须为正");
        }
        if (type == TraceType.DIFFERENTIAL && (spacing == null || spacing.si() <= 0)) {
            errs.add(name + ": 差分走线必须给正的线间距");
        }
        if (target == null || target.lower() <= 0) {
            errs.add(name + ": 目标阻抗带必须为正");
        }
        return errs;
    }
}
