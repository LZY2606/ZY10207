package com.lamiprover.db;

import com.lamiprover.model.Stackup;
import com.lamiprover.service.AnalysisReport;
import com.lamiprover.service.AnalysisRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行记录导出 / 清空 / 原样重导。
 *
 * <p>重导按原 id 幂等 upsert，spec/request/report 的 JSON 原样保留，
 * 因此清空后复核时版本谱系、运行参数与每个样本点的批次回查完全一致。
 */
@Service
public class TransferService {

  private static final String EXPORT_VERSION = "lamiprover-1";

  private final JdbcTemplate jdbc;
  private final StackupRepository stackupRepository;
  private final RunRepository runRepository;

  public TransferService(JdbcTemplate jdbc, StackupRepository stackupRepository,
                         RunRepository runRepository) {
    this.jdbc = jdbc;
    this.stackupRepository = stackupRepository;
    this.runRepository = runRepository;
  }

  @Transactional
  public ExportBundle exportAll() {
    List<ExportBundle.StackupEntry> stackups = jdbc.query("""
            SELECT id, name, version, parent_version_id, material_version, created_at, spec_json
            FROM stackups ORDER BY name, version
            """,
        (rs, n) -> new ExportBundle.StackupEntry(
            rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4),
            rs.getString(5), rs.getString(6), rs.getString(7)));
    List<ExportBundle.RunEntry> runs = jdbc.query("""
            SELECT id, stackup_id, stackup_version, created_at, request_json, report_json
            FROM runs ORDER BY created_at
            """,
        (rs, n) -> new ExportBundle.RunEntry(
            rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4),
            rs.getString(5), rs.getString(6)));
    return new ExportBundle(EXPORT_VERSION, Instant.now().toString(), stackups, runs);
  }

  @Transactional
  public void clearAll() {
    jdbc.update("DELETE FROM runs");
    jdbc.update("DELETE FROM stackups");
    jdbc.update("DELETE FROM meta");
  }

  @Transactional
  public ImportSummary importBundle(ExportBundle bundle) {
    if (bundle == null || !EXPORT_VERSION.equals(bundle.exportVersion())) {
      throw new IllegalArgumentException(
          "导出包版本不匹配，期望 " + EXPORT_VERSION + "，拒绝导入以防口径混用");
    }
    int stacks = 0;
    int runs = 0;
    if (bundle.stackups() != null) {
      for (ExportBundle.StackupEntry e : bundle.stackups()) {
        // 校验 JSON 可按当前模型解析，防止静默导入坏数据
        Stackup parsed = JsonCodec.read(e.specJson(), Stackup.class);
        jdbc.update("""
            INSERT INTO stackups (id, name, version, parent_version_id, material_version,
                                  created_at, spec_json)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, version=excluded.version,
              parent_version_id=excluded.parent_version_id,
              material_version=excluded.material_version,
              created_at=excluded.created_at, spec_json=excluded.spec_json
            """,
            e.id(), e.name(), e.version(), e.parentVersionId(), e.materialVersion(),
            e.createdAt(), e.specJson());
        if (!parsed.id().equals(e.id())) {
          throw new IllegalArgumentException("叠层 id 与行键不一致: " + e.id());
        }
        stacks++;
      }
    }
    if (bundle.runs() != null) {
      for (ExportBundle.RunEntry e : bundle.runs()) {
        JsonCodec.read(e.requestJson(), AnalysisRequest.class);
        JsonCodec.read(e.reportJson(), AnalysisReport.class);
        if (!stackupRepository.exists(e.stackupId())) {
          throw new IllegalArgumentException(
              "运行 " + e.id() + " 引用了不存在的叠层版本 " + e.stackupId() + "，拒绝导入");
        }
        jdbc.update("""
            INSERT INTO runs (id, stackup_id, stackup_version, created_at,
                              request_json, report_json)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              stackup_id=excluded.stackup_id,
              stackup_version=excluded.stackup_version,
              created_at=excluded.created_at,
              request_json=excluded.request_json,
              report_json=excluded.report_json
            """,
            e.id(), e.stackupId(), e.stackupVersion(), e.createdAt(),
            e.requestJson(), e.reportJson());
        runs++;
      }
    }
    return new ImportSummary(stacks, runs);
  }

  public record ImportSummary(int stackups, int runs) {
  }
}
