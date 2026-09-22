package com.lamiprover.service;

import com.lamiprover.impedance.FormulaInfo;
import java.util.List;
import java.util.Map;

/**
 * 一次容差分析的完整结论（持久化 + 页面呈现 + 导出复算用）。
 */
public record AnalysisReport(
    String mode,
    boolean independentExtremesRequested,
    boolean rejected,
    String rejectionReason,
    String formulaId,
    FormulaInfo formula,
    double targetOhm,
    double tolerancePercent,
    int pointCount,
    int passCount,
    int failCount,
    int domainFailureCount,
    int specFailureCount,
    Double minOhm,
    Double maxOhm,
    List<Map<String, String>> groups,
    List<PointResult> points,
    Integer seed,
    Integer sampleCount
) {

  public double passRate() {
    return pointCount == 0 ? 0.0 : (double) passCount / pointCount;
  }
}
