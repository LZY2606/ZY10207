package com.lamiprover;

import static org.assertj.core.api.Assertions.assertThat;

import com.lamiprover.impedance.Formulas;
import com.lamiprover.impedance.ImpedanceInput;
import com.lamiprover.impedance.ImpedanceResult;
import com.lamiprover.model.TraceType;
import org.junit.jupiter.api.Test;

class FormulaDomainTest {

  private ImpedanceInput se(double w, double t, double h, double er,
                            double maskDk, double maskT) {
    return ImpedanceInput.singleEnded(w, t, h, er, maskDk, maskT);
  }

  @Test
  void nominalMicrostripIsAboutFiftyOhms() {
    // 7 mil, 35 µm, 0.18 mm, Dk 4.2 + 15 µm 阻焊
    ImpedanceResult r = Formulas.evaluate(TraceType.MICROSTRIP_SE,
        se(7 * 25.4e-6, 35e-6, 120e-6, 4.2, 3.3, 15e-6));
    assertThat(r.inDomain()).isTrue();
    assertThat(r.impedanceOhm()).isBetween(45.0, 60.0);
  }

  @Test
  void outOfDomainReturnsNoImpedanceOnlyDiagnostics() {
    // w/h = 100um/10um = 10 > 4，越界；t/h=3.5 > 0.5，越界
    ImpedanceResult r = Formulas.evaluate(TraceType.MICROSTRIP_SE,
        se(100e-6, 35e-6, 10e-6, 4.2, 3.3, 0));
    assertThat(r.inDomain()).isFalse();
    assertThat(Double.isNaN(r.impedanceOhm())).isTrue();
    assertThat(r.violations()).isNotEmpty();
    assertThat(r.violations().stream().anyMatch(v -> v.check().startsWith("w/h"))).isTrue();
    assertThat(r.violations().stream().anyMatch(v -> v.check().startsWith("t/h"))).isTrue();
  }

  @Test
  void striplineRejectsSolderMaskConfiguration() {
    ImpedanceResult r = Formulas.evaluate(TraceType.STRIPLINE_SE,
        ImpedanceInput.singleEnded(150e-6, 17.5e-6, 200e-6, 3.9, 3.5, 0));
    assertThat(r.inDomain()).isFalse();
    assertThat(r.violations()).extracting(v -> v.check()).contains("mask");
    assertThat(Double.isNaN(r.impedanceOhm())).isTrue();
  }

  @Test
  void differentialMicrostripAndStriplineSanity() {
    ImpedanceResult md = Formulas.evaluate(TraceType.MICROSTRIP_DIFF,
        ImpedanceInput.differential(120e-6, 1.2 * 25.4e-6, 100e-6, 200e-6, 4.2, 3.3, 12e-6));
    assertThat(md.inDomain()).isTrue();
    assertThat(md.impedanceOhm()).isBetween(80.0, 130.0);

    ImpedanceResult sd = Formulas.evaluate(TraceType.STRIPLINE_DIFF,
        ImpedanceInput.differential(6 * 25.4e-6, 17.5e-6, 420e-6, 250e-6, 3.9,
            Double.NaN, 0));
    assertThat(sd.inDomain()).isTrue();
    assertThat(sd.impedanceOhm()).isBetween(80.0, 130.0);
  }

  @Test
  void bareVsMaskedMicrostripMaskLowersImpedance() {
    double h = 120e-6;
    ImpedanceResult bare = Formulas.evaluate(TraceType.MICROSTRIP_SE,
        se(7 * 25.4e-6, 35e-6, h, 4.2, 3.3, 0));
    ImpedanceResult masked = Formulas.evaluate(TraceType.MICROSTRIP_SE,
        se(7 * 25.4e-6, 35e-6, h, 4.2, 3.3, 15e-6));
    assertThat(masked.impedanceOhm()).isLessThan(bare.impedanceOhm());
    assertThat(masked.effectiveDk()).isGreaterThan(bare.effectiveDk());
  }
}
