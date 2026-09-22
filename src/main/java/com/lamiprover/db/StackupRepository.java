package com.lamiprover.db;

import com.lamiprover.model.Stackup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 叠层版本仓储（只追加；新版本不修改旧行，旧结论不自动重算）。 */
@Repository
public class StackupRepository {

  private final JdbcTemplate jdbc;

  public StackupRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Stackup spec, String id, Instant createdAt) {
    jdbc.update("""
        INSERT INTO stackups (id, name, version, parent_version_id, material_version,
                              created_at, spec_json)
        VALUES (?,?,?,?,?,?,?)
        """,
        id, spec.name(), spec.version(), spec.parentVersionId(), spec.materialVersion(),
        createdAt.toString(), JsonCodec.write(spec));
  }

  public boolean exists(String id) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM stackups WHERE id = ?",
        Integer.class, id);
    return n != null && n > 0;
  }

  public Optional<Stackup> findSpec(String id) {
    List<Stackup> list = jdbc.query(
        "SELECT spec_json FROM stackups WHERE id = ?",
        (rs, n) -> JsonCodec.read(rs.getString(1), Stackup.class), id);
    return list.stream().findFirst();
  }

  public List<StackupRow> findAllRows() {
    return jdbc.query("SELECT id, name, version, parent_version_id, material_version, created_at "
        + "FROM stackups ORDER BY name, version", this::mapRow);
  }

  public List<Stackup> findAllSpecs() {
    return jdbc.query("SELECT spec_json FROM stackups ORDER BY name, version",
        (rs, n) -> JsonCodec.read(rs.getString(1), Stackup.class));
  }

  public int count() {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM stackups", Integer.class);
    return n == null ? 0 : n;
  }

  public int nextVersionNumber(String name) {
    Integer n = jdbc.queryForObject(
        "SELECT COALESCE(MAX(version), 0) FROM stackups WHERE name = ?",
        Integer.class, name);
    return (n == null ? 0 : n) + 1;
  }

  public List<Stackup> versionsOf(String name) {
    return jdbc.query(
        "SELECT spec_json FROM stackups WHERE name = ? ORDER BY version",
        (rs, n) -> JsonCodec.read(rs.getString(1), Stackup.class), name);
  }

  private StackupRow mapRow(ResultSet rs, int n) throws SQLException {
    return new StackupRow(rs.getString("id"), rs.getString("name"), rs.getInt("version"),
        rs.getString("parent_version_id"), rs.getString("material_version"),
        Instant.parse(rs.getString("created_at")));
  }
}
