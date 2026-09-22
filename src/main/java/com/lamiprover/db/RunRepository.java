package com.lamiprover.db;

import com.lamiprover.service.AnalysisReport;
import com.lamiprover.service.AnalysisRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 分析运行记录仓储。 */
@Repository
public class RunRepository {

  private final JdbcTemplate jdbc;

  public RunRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean exists(String id) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM runs WHERE id = ?",
        Integer.class, id);
    return n != null && n > 0;
  }

  public java.util.Optional<RunRow> findById(String id) {
    return jdbc.query("SELECT * FROM runs WHERE id = ?", this::mapRow, id)
        .stream().findFirst();
  }

  public void insert(String id, String stackupId, int stackupVersion, Instant createdAt,
                     AnalysisRequest request, AnalysisReport report) {
    jdbc.update("""
        INSERT INTO runs (id, stackup_id, stackup_version, created_at, request_json, report_json)
        VALUES (?,?,?,?,?,?)
        """, id, stackupId, stackupVersion, createdAt.toString(),
        JsonCodec.write(request), JsonCodec.write(report));
  }

  public List<RunRow> findByStackup(String stackupId) {
    return jdbc.query("SELECT * FROM runs WHERE stackup_id = ? ORDER BY created_at",
        this::mapRow, stackupId);
  }

  public List<RunRow> findAll() {
    return jdbc.query("SELECT * FROM runs ORDER BY created_at", this::mapRow);
  }

  public int count() {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM runs", Integer.class);
    return n == null ? 0 : n;
  }

  private RunRow mapRow(ResultSet rs, int n) throws SQLException {
    return new RunRow(
        rs.getString("id"),
        rs.getString("stackup_id"),
        rs.getInt("stackup_version"),
        Instant.parse(rs.getString("created_at")),
        JsonCodec.read(rs.getString("request_json"), AnalysisRequest.class),
        JsonCodec.read(rs.getString("report_json"), AnalysisReport.class));
  }
}
