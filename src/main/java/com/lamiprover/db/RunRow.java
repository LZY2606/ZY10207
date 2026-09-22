package com.lamiprover.db;

import com.lamiprover.service.AnalysisReport;
import com.lamiprover.service.AnalysisRequest;
import java.time.Instant;

/** runs 表一行（请求参数 + 结论均持久化，支持重放与复核）。 */
public record RunRow(
    String id,
    String stackupId,
    int stackupVersion,
    Instant createdAt,
    AnalysisRequest request,
    AnalysisReport report
) {
}
