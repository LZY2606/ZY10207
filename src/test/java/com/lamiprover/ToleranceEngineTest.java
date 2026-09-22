package com.lamiprover;

import static org.assertj.core.api.Assertions.assertThat;

import com.lamiprover.tol.DeterministicRng;
import com.lamiprover.tol.Distribution;
import com.lamiprover.tol.Factor;
import com.lamiprover.tol.ToleranceEngine;
import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.LengthUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToleranceEngineTest {

  private Factor len(String key, String group, double nominal, double pct, String lot) {
    return new Factor(key, key, nominal,
        ToleranceSpec.relative(group, lot, Distribution.UNIFORM, pct, false),
        true, LengthUnit.MM);
  }

  @Test
  void correlatedFactorsShareCornerSign() {
    // h 与 t 属于同一压合组 PRESS；w 独立组
    Factor h = len("h", "PRESS", 100.0, 10.0, "LOT-P");
    Factor t = len("t", "PRESS", 10.0, 10.0, "LOT-P");
    Factor w = len("w", "ETCH", 20.0, 10.0, "LOT-E");
    List<Factor> factors = List.of(h, t, w);

    List<Map<String, Double>> corners = ToleranceEngine.worstCorners(factors);
    assertThat(corners).hasSize(4); // 2 个组 => 2^2
    for (Map<String, Double> c : corners) {
      boolean hUp = c.get("h") > 100.0;
      boolean tUp = c.get("t") > 10.0;
      assertThat(hUp).as("同压合批次 h/t 必须同号").isEqualTo(tUp);
    }
  }

  @Test
  void independentExtremesEnumeratesEveryFactorAndViolatesCorrelation() {
    Factor h = len("h", "PRESS", 100.0, 10.0, "LOT-P");
    Factor t = len("t", "PRESS", 10.0, 10.0, "LOT-P");
    Factor w = len("w", "ETCH", 20.0, 10.0, "LOT-E");
    List<Map<String, Double>> independent =
        ToleranceEngine.independentExtremes(List.of(h, t, w));
    assertThat(independent).hasSize(8); // 3 个因子独立 => 2^3
    // 反例基线中必然出现 h 与 t 反号的角落（物理上不可能独立取得）
    boolean antiCorrelated = independent.stream().anyMatch(c ->
        (c.get("h") > 100.0) != (c.get("t") > 10.0));
    assertThat(antiCorrelated).isTrue();
    assertThat(ToleranceEngine.hasCorrelatedGroup(List.of(h, t, w))).isTrue();
  }

  @Test
  void seededSamplingIsDeterministicAndGroupLocked() {
    Factor h = len("h", "PRESS", 100.0, 10.0, "LOT-P");
    Factor t = len("t", "PRESS", 10.0, 10.0, "LOT-P");
    Factor w = len("w", "ETCH", 20.0, 10.0, "LOT-E");
    List<Factor> factors = List.of(h, t, w);

    List<Map<String, Double>> a = ToleranceEngine.sampleR(42L, 50, factors);
    List<Map<String, Double>> b = ToleranceEngine.sampleR(42L, 50, factors);
    assertThat(a).isEqualTo(b);
    List<Map<String, Double>> c = ToleranceEngine.sampleR(43L, 50, factors);
    assertThat(c).isNotEqualTo(a);
    // 同组的 h 与 t 共享归一化抽样位置
    for (Map<String, Double> row : a) {
      assertThat(row.get("h")).isEqualTo(row.get("t"));
    }
  }

  @Test
  void rngStaysInRange() {
    DeterministicRng rng = new DeterministicRng(7);
    for (int i = 0; i < 1000; i++) {
      double u = rng.nextUnit();
      assertThat(u).isBetween(0.0, 1.0);
      double z = rng.nextGaussianClipped();
      assertThat(z).isBetween(-3.0, 3.0);
    }
  }
}
