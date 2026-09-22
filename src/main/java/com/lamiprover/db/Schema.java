package com.lamiprover.db;

/** SQLite 建表语句。 */
public final class Schema {

  private Schema() {
  }

  public static final String[] DDL = {
      """
      CREATE TABLE IF NOT EXISTS stackups (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        version INTEGER NOT NULL,
        parent_version_id TEXT,
        material_version TEXT NOT NULL,
        created_at TEXT NOT NULL,
        spec_json TEXT NOT NULL
      )
      """,
      """
      CREATE TABLE IF NOT EXISTS runs (
        id TEXT PRIMARY KEY,
        stackup_id TEXT NOT NULL,
        stackup_version INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        request_json TEXT NOT NULL,
        report_json TEXT NOT NULL,
        FOREIGN KEY (stackup_id) REFERENCES stackups(id)
      )
      """,
      """
      CREATE TABLE IF NOT EXISTS meta (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
      """,
      "CREATE INDEX IF NOT EXISTS idx_runs_stackup ON runs(stackup_id)"
  };
}
