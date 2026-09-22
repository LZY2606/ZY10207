package com.lamprover.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class RunRepository {

    private final JdbcTemplate jdbc;

    public RunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StoredRun insert(String boardCode, int version, long seed, int sampleCount,
                            boolean passed, String cornerPolicy, String resultJson, String inputHash) {
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO runs(board_code,version,seed,sample_count,passed,corner_policy,
                                         result_json,input_hash,created_at)
                        VALUES(?,?,?,?,?,?,?,?,?)""",
                boardCode, version, seed, sampleCount, passed ? 1 : 0,
                cornerPolicy, resultJson, inputHash, now.toString());
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return new StoredRun(id == null ? -1 : id, boardCode, version, seed, sampleCount,
                passed, cornerPolicy, resultJson, inputHash, now);
    }

    public StoredRun load(long id) {
        List<StoredRun> rows = jdbc.query("SELECT * FROM runs WHERE id=?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<StoredRun> list(String boardCode, Integer version) {
        if (boardCode == null) {
            return jdbc.query("SELECT * FROM runs ORDER BY id DESC", MAPPER);
        }
        if (version == null) {
            return jdbc.query("SELECT * FROM runs WHERE board_code=? ORDER BY id DESC",
                    MAPPER, boardCode);
        }
        return jdbc.query("SELECT * FROM runs WHERE board_code=? AND version=? ORDER BY id DESC",
                MAPPER, boardCode, version);
    }

    public List<StoredRun> loadAll() {
        return jdbc.query("SELECT * FROM runs ORDER BY id", MAPPER);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM runs WHERE id=?", id);
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM runs");
        jdbc.update("DELETE FROM stackup_versions");
        jdbc.update("DELETE FROM stackups");
        jdbc.execute("DELETE FROM sqlite_sequence WHERE name='runs'");
    }

    private static final org.springframework.jdbc.core.RowMapper<StoredRun> MAPPER = (rs, i) ->
            new StoredRun(rs.getLong("id"), rs.getString("board_code"), rs.getInt("version"),
                    rs.getLong("seed"), rs.getInt("sample_count"),
                    rs.getInt("passed") == 1, rs.getString("corner_policy"),
                    rs.getString("result_json"), rs.getString("input_hash"),
                    Instant.parse(rs.getString("created_at")));
}
