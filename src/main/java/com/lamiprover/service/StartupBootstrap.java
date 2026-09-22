package com.lamiprover.service;

import com.lamiprover.db.DatabaseInitializer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 唯一启动 runner：先建表，再向空库装载固定 fixture。 */
@Component
@Order(0)
public class StartupBootstrap implements CommandLineRunner {

  private final DatabaseInitializer databaseInitializer;
  private final FixtureService fixtureService;

  public StartupBootstrap(DatabaseInitializer databaseInitializer, FixtureService fixtureService) {
    this.databaseInitializer = databaseInitializer;
    this.fixtureService = fixtureService;
  }

  @Override
  public void run(String... args) {
    databaseInitializer.initialize();
    fixtureService.ensureFixtures();
  }
}
