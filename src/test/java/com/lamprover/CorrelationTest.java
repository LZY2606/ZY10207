package com.lamprover;

import com.lamprover.core.Analyzer;
import com.lamprover.core.Keys;
import com.lamprover.core.LotVariable;
import com.lamprover.core.ToleranceMachine;
import com.lamprover.domain.Stackup;
import com.lamprover.domain.Trace;
import com.lamprover.service.Fixture;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationTest {

    private final Stackup stackup = Fixture.demoStackup();
    private final Trace se = stackup.traces().get(0);

    @Test
    void dielectricAndCopperThicknessSharePressLot() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        LotVariable pp = byKey(vars, Keys.layerThickness("PP-7628"));
        LotVariable copper = byKey(vars, Keys.copperThickness("L1"));
        assertEquals("press-top", pp.lotId());
        assertEquals("press-top", copper.lotId());
    }

    @Test
    void worstCornersNeverSplitOnePressBatch() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        List<ToleranceMachine.Corner> corners = ToleranceMachine.correlatedCorners(vars);
        assertEquals(8, corners.size());
        for (ToleranceMachine.Corner c : corners) {
            double pp = c.values().get(Keys.layerThickness("PP-7628"));
            double cu = c.values().get(Keys.copperThickness("L1"));
            LotVariable ppVar = byKey(vars, Keys.layerThickness("PP-7628"));
            LotVariable cuVar = byKey(vars, Keys.copperThickness("L1"));
            boolean ppHigh = Math.abs(pp - ppVar.upperSi()) < 1e-18;
            boolean ppLow = Math.abs(pp - ppVar.lowerSi()) < 1e-18;
            boolean cuHigh = Math.abs(cu - cuVar.upperSi()) < 1e-18;
            boolean cuLow = Math.abs(cu - cuVar.lowerSi()) < 1e-18;
            assertTrue((ppHigh && cuHigh) || (ppLow && cuLow)
                    || (Math.abs(pp - ppVar.nominal()) < 1e-18 && Math.abs(cu - cuVar.nominal()) < 1e-18),
                    "同一压合批次的介质厚度与铜厚必须同向: " + c.id());
        }
    }

    @Test
    void independentExtremaWithinSameLotAreRejected() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        Map<String, Integer> signs = new HashMap<>();
        signs.put(Keys.layerThickness("PP-7628"), +1);
        signs.put(Keys.copperThickness("L1"), -1);
        String conflict = ToleranceMachine.independentExtremaConflict(vars, signs);
        assertNotNull(conflict);
        assertTrue(conflict.contains("press-top"));
    }

    @Test
    void correlatedHighLowAssignmentIsAccepted() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        Map<String, Integer> signs = new HashMap<>();
        signs.put(Keys.layerThickness("PP-7628"), +1);
        signs.put(Keys.copperThickness("L1"), +1);
        signs.put(Keys.layerDk("PP-7628"), -1);
        assertNull(ToleranceMachine.independentExtremaConflict(vars, signs));
    }

    @Test
    void seededSamplesAreDeterministicAndTraceable() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        var a = ToleranceMachine.samples(vars, 42, 50);
        var b = ToleranceMachine.samples(vars, 42, 50);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).values(), b.get(i).values());
            assertNotNull(a.get(i).trace().get(Keys.layerThickness("PP-7628")));
        }
    }

    @Test
    void pressBatchMovesTogetherInSamplesToo() {
        List<LotVariable> vars = Analyzer.buildVariables(stackup, se);
        var samples = ToleranceMachine.samples(vars, 7, 100);
        LotVariable pp = byKey(vars, Keys.layerThickness("PP-7628"));
        LotVariable cu = byKey(vars, Keys.copperThickness("L1"));
        for (var smp : samples) {
            double ppVal = smp.values().get(pp.key());
            double cuVal = smp.values().get(cu.key());
            boolean ppUp = ppVal >= pp.nominal();
            boolean cuUp = cuVal >= cu.nominal();
            assertEquals(ppUp, cuUp, "同批压合抽样方向必须一致");
        }
    }

    private static LotVariable byKey(List<LotVariable> vars, String key) {
        return vars.stream().filter(v -> v.key().equals(key)).findFirst().orElseThrow();
    }
}
