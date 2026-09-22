package com.lamprover;

import com.lamprover.domain.Quantity;
import com.lamprover.domain.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitConversionTest {

    @Test
    void siConversionsAreExplicitAndExact() {
        assertEquals(1e-6, new Quantity(1, Unit.UM).si(), 0);
        assertEquals(1e-3, new Quantity(1, Unit.MM).si(), 0);
        assertEquals(2.54e-5, new Quantity(1, Unit.MIL).si(), 0);
    }

    @Test
    void sameNumericValueDifferentUnitsNeverGuessed() {
        Quantity tenMil = Quantity.of(10, "mil");
        Quantity tenUm = Quantity.of(10, "um");
        assertEquals(2.54e-4, tenMil.si(), 1e-15);
        assertEquals(1e-5, tenUm.si(), 1e-18);
        assertEquals(25.4, tenMil.si() / tenUm.si(), 1e-9);
    }

    @Test
    void unknownUnitRejectedInsteadOfMagnitudeGuess() {
        assertThrows(IllegalArgumentException.class, () -> Unit.parse("furlong"));
    }

    @Test
    void roundTripPreservesValue() {
        double si = Quantity.of(7, "mil").si();
        assertEquals(7.0, Unit.MIL.fromSi(si), 1e-12);
    }
}
