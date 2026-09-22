package com.lamiprover.db;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 建表动作（由 {@code StartupBootstrap} 在 fixture 装载前显式调用，避免 runner 顺序不确定性）。 */
@Component
public class DatabaseInitializer {

  private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

  private final DataSource dataSource;
  private final String jdbcUrl;

  public DatabaseInitializer(DataSource dataSource,
                             @Value("${spring.datasource.url}") String jdbcUrl) {
    this.dataSource = dataSource;
    this.jdbcUrl = jdbcUrl;
  }

  public void initialize() {
    ensureParentDirectory();
    try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
      st.execute("PRAGMA foreign_keys = ON");
      for (String ddl : Schema.DDL) {
        st.execute(ddl);
      }
      log.info("SQLite schema ready");
    } catch (Exception e) {
      throw new IllegalStateException("SQLite 初始化失败", e);
    }
  }

  /** 从 jdbc:sqlite:<path> 解析文件路径并创建父目录；内存库跳过。 */
  private void ensureParentDirectory() {
    String prefix = "jdbc:sqlite:";
    if (!jdbcUrl.startsWith(prefix)) {
      return;
    }
    String path = jdbcUrl.substring(prefix.length());
    if (path.isEmpty() || path.startsWith(":") || path.startsWith("file:")) {
      return;
    }
    File file = new File(path);
    File parent = file.getAbsoluteFile().getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IllegalStateException("无法创建数据库目录: " + parent);
    }
  }
}
