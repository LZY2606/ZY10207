package com.lamprover.service;

import com.lamprover.persistence.StackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FixtureSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FixtureSeeder.class);

    private final StackupRepository stackups;
    private final StackupService stackupService;

    public FixtureSeeder(StackupRepository stackups, StackupService stackupService) {
        this.stackups = stackups;
        this.stackupService = stackupService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String code = Fixture.demoStackup().boardCode();
        if (stackups.currentVersion(code) == 0) {
            var saved = stackupService.save(Fixture.demoStackup());
            log.info("已播种固定 fixture：{} v{}（哈希 {}）",
                    saved.boardCode(), saved.version(),
                    saved.payloadHash().substring(0, 12));
        }
    }
}
