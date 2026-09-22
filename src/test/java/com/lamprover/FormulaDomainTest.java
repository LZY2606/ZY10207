package com.lamprover;

import com.lamprover.core.GeometryInputs;
import com.lamprover.core.ImpedanceFormulas;
import com.lamprover.core.ImpedanceResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaDomainTest {

    private GeometryInputs micro(double w, double t, double h, double er, double s, double hm) {
        return new GeometryInputs(GeometryInputs.Topology.MICROSTRIP, w, t, h, 0, er, s, hm, 3.8);
    }

    @Test
    void nominalMicrostripIsCloseToFiftyOhms() {
        ImpedanceResult r = ImpedanceFormulas.singleEnded(
                micro(0.30e-3, 35e-6, 0.25e-3, 4.2, 0, 12.7e-6));
        assertTrue(r.inDomain(), () -> r.violations().toString());
        assertEquals(50.0, r.zOhm(), 2.0);
    }

    @Test
    void differentialPairNear95Ohms() {
        ImpedanceResult se = ImpedanceFormulas.singleEnded(
                micro(0.22e-3, 35e-6, 0.25e-3, 4.2, 0, 12.7e-6));
        ImpedanceResult diff = ImpedanceFormulas.differential(
                micro(0.22e-3, 35e-6, 0.25e-3, 4.2, 0.25e-3, 12.7e-6));
        assertTrue(diff.inDomain());
        assertTrue(diff.zOhm() > 2 * se.zOhm() * 0.82);
        assertTrue(diff.zOhm() < 2 * se.zOhm());
        assertEquals(95.5, diff.zOhm(), 8.0);
    }

    @Test
    void outOfDomainReportsDiagnosticsButNoImpedance() {
        ImpedanceResult r = ImpedanceFormulas.singleEnded(
                micro(3.0e-3, 35e-6, 0.25e-3, 4.2, 0, 12.7e-6));
        assertFalse(r.inDomain());
        assertTrue(Double.isNaN(r.zOhm()));
        assertTrue(r.violations().stream().anyMatch(v -> v.parameter().equals("w/h")));
    }

    @Test
    void dielectricConstantOutsideDomainRejected() {
        ImpedanceResult r = ImpedanceFormulas.singleEnded(
                micro(0.2e-3, 35e-6, 0.25e-3, 20.0, 0, 0));
        assertFalse(r.inDomain());
        assertTrue(r.violations().stream().anyMatch(v -> v.parameter().equals("er")));
    }

    @Test
    void thickCopperOutsideCorrectionRangeRejected() {
        ImpedanceResult r = ImpedanceFormulas.singleEnded(
                micro(0.05e-3, 0.05e-3, 0.25e-3, 4.2, 0, 0));
        assertFalse(r.inDomain());
        assertTrue(r.violations().stream().anyMatch(v -> v.parameter().equals("t/w")));
    }

    @Test
    void thickMaskRejected() {
        ImpedanceResult r = ImpedanceFormulas.singleEnded(
                micro(0.3e-3, 35e-6, 0.25e-3, 4.2, 0, 0.1e-3));
        assertFalse(r.inDomain());
        assertTrue(r.violations().stream().anyMatch(v -> v.parameter().equals("hm/h")));
    }

    @Test
    void uncenteredStriplineRejectedWithoutFakeNumber() {
        GeometryInputs g = new GeometryInputs(GeometryInputs.Topology.STRIPLINE,
                0.2e-3, 35e-6, 0.1e-3, 0.5e-3, 4.3, 0, 0, 0);
        ImpedanceResult r = ImpedanceFormulas.singleEnded(g);
        assertFalse(r.inDomain());
        assertTrue(Double.isNaN(r.zOhm()));
        assertTrue(r.violations().stream().anyMatch(v -> v.parameter().equals("centering")));
    }

    @Test
    void documentationCoversAllFourFormulas() {
        assertEquals(4, ImpedanceFormulas.documentation().size());
    }
}
