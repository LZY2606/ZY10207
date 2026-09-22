package com.lamiprover.db;

import java.util.List;

/**
 * 运行记录导出包（整库快照）。清空数据库后可用同一份 bundle 重新导入复核；
 * id 与 JSON 原样回写，因此重算结果与历史结论逐点一致。
 */
public record ExportBundle(
    String exportVersion,
    String exportedAt,
    List<StackupEntry> stackups,
    List<RunEntry> runs
) {

  public record StackupEntry(String id, String name, int version, String parentVersionId,
                             String materialVersion, String createdAt, String specJson) {
  }

  public record RunEntry(String id, String stackupId, int stackupVersion, String createdAt,
                         String requestJson, String reportJson) {
  }
}
