package com.lamiprover.impedance;

import java.util.List;

/** 公式展示信息：所用公式、口径说明、适用范围（供页面与导出报告呈现）。 */
public record FormulaInfo(
    String id,
    String name,
    List<String> formulaLines,
    String applicability,
    List<String> domainChecks,
    List<String> notes
) {
}
