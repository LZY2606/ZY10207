package com.lamprover;

import com.lamprover.core.Analyzer;
import com.lamprover.domain.Stackup;
import com.lamprover.service.Fixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureAcceptanceTest {

    private final Stackup stackup = Fixture.demoStackup();

    @Test
    void fixtureUsesMilMicronAndMillimeterUnits() {
        var units = stackup.layers().stream()
                .map(l -> l.thickness().unit().name()).toList();
        assertTrue(units.contains("MIL"), units.toString());
        assertTrue(units.contains("UM"), units.toString());
        assertTrue(units.contains("MM"), units.toString());
    }

    @Test
    void fixtureIsSymmetricAndValid() {
        assertTrue(stackup.symmetric());
        assertEquals(List.of(), stackup.validationErrors());
    }

    @Test
    void singleEndedTracePassesCorrelatedCorners() {
        var result = Analyzer.analyze(stackup, Fixture.fixedSeed(), Fixture.fixedSampleCount());
        var se = result.traces().get(0);
        assertEquals("SE-50-L1", se.traceName());
        assertTrue(se.passed(), "关联角落与固定抽样应全部落在 48..52 Ω");
        assertTrue(se.nominal().inDomain());
    }

    @Test
    void differentialPairPassesTargetBand() {
        var result = Analyzer.analyze(stackup, Fixture.fixedSeed(), Fixture.fixedSampleCount());
        var diff = result.traces().get(1);
        assertTrue(diff.passed());
        assertTrue(diff.zMin() >= 90 && diff.zMax() <= 110,
                "差分阻抗范围应在 90..110 Ω: " + diff.zMin() + ".." + diff.zMax());
    }

    @Test
    void wideTraceIsOutOfDomainWithDiagnosticsOnly() {
        var result = Analyzer.analyze(stackup, Fixture.fixedSeed(), 10);
        var wide = result.traces().get(2);
        assertFalse(wide.nominal().inDomain());
        assertNull(wide.nominal().zOhm());
        assertNotNull(wide.nominal().violations());
        assertTrue(wide.nominal().violations().stream().anyMatch(v -> v.parameter().equals("w/h")));
    }

    @Test
    void meanderToleranceDoesNotEnterImpedanceVariables() {
        var se = stackup.traces().get(0);
        var vars = com.lamprover.core.Analyzer.buildVariables(stackup, se);
        assertTrue(vars.stream().noneMatch(v -> v.key().contains("meander")));
    }
}
