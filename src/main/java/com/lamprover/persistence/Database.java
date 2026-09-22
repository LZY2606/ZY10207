package com.lamprover.persistence;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Database {

    private final JdbcTemplate jdbc;

    public Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("PRAGMA foreign_keys=ON");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS stackups (
                    board_code TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    current_version INTEGER NOT NULL
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS stackup_versions (
                    board_code TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    material_revision TEXT,
                    payload TEXT NOT NULL,
                    payload_hash TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (board_code, version),
                    FOREIGN KEY (board_code) REFERENCES stackups(board_code)
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    board_code TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    seed INTEGER NOT NULL,
                    sample_count INTEGER NOT NULL,
                    passed INTEGER NOT NULL,
                    corner_policy TEXT NOT NULL,
                    result_json TEXT NOT NULL,
                    input_hash TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_runs_board ON runs(board_code, version)");
    }
}
