package com.lamiprover;

import static org.assertj.core.api.Assertions.assertThat;

import com.lamiprover.model.AnalysisMode;
import com.lamiprover.model.Stackup;
import com.lamiprover.model.TraceType;
import com.lamiprover.service.AnalysisReport;
import com.lamiprover.service.AnalysisRequest;
import com.lamiprover.service.AnalysisService;
import com.lamiprover.service.FixtureService;
import com.lamiprover.service.PointResult;
import com.lamiprover.tol.Distribution;
import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.Length;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

  private final AnalysisService service = new AnalysisService();

  private Stackup outerSe() {
    return FixtureService.seedStackups().get(0);
  }

  @Test
  void independentExtremesAreRejectedForCorrelatedPressGroup() {
    AnalysisRequest req = new AnalysisRequest(AnalysisMode.WORST_CORNERS,
        50, 10, null, null, true);
    AnalysisReport report = service.analyze(outerSe(), req);
    assertThat(report.rejected()).isTrue();
    assertThat(report.rejectionReason()).contains("关联容差组");
    assertThat(report.pointCount()).isZero();
    assertThat(report.failCount()).isZero();
  }

  @Test
  void correlatedWorstCornersRunAndAreTraceableToLots() {
    AnalysisRequest req = new AnalysisRequest(AnalysisMode.WORST_CORNERS,
        50, 10, null, null, false);
    AnalysisReport report = service.analyze(outerSe(), req);
    assertThat(report.rejected()).isFalse();
    // 组: PRESS / ETCH / DK / MASK / MEANDER => 5 => 32 个角落
    assertThat(report.pointCount()).isEqualTo(32);
    for (PointResult p : report.points()) {
      assertThat(p.traces().get("h").group()).isEqualTo("PRESS-LAM-01");
      assertThat(p.traces().get("h").sourceLot()).isEqualTo("LOT-PRESS-2026-09-A");
      assertThat(p.traces().get("t").group()).isEqualTo(p.traces().get("h").group());
      assertThat(p.traces().get("t").direction())
          .isEqualTo(p.traces().get("h").direction());
    }
  }

  @Test
  void sampledFailuresTraceBackToLotAndDrawIndex() {
    AnalysisRequest req = new AnalysisRequest(AnalysisMode.SAMPLED,
        50, 10, 20260922L, 200, false);
    AnalysisReport a = service.analyze(outerSe(), req);
    AnalysisReport b = service.analyze(outerSe(), req);
    assertThat(a.points()).isEqualTo(b.points()); // 固定种子可重放
    assertThat(a.points()).hasSize(200);
    assertThat(a.points().stream().filter(p -> !p.pass()).findFirst()).isPresent();
    PointResult failure = a.points().stream().filter(p -> !p.pass()).findFirst().orElseThrow();
    assertThat(failure.traces().get("h").sourceLot()).isNotBlank();
    assertThat(failure.label()).startsWith("sample#");
    if (!failure.inDomain()) {
      assertThat(failure.impedanceOhm()).isNull();
      assertThat(failure.violations()).isNotEmpty();
    } else {
      assertThat(failure.impedanceOhm()).isNotNull();
      assertThat(failure.failureReason()).isEqualTo("IMPEDANCE_OUT_OF_SPEC");
    }
  }

  @Test
  void domainViolationPointsReturnDiagnosticsButNoNumber() {
    // 构造离开 w/h 适用域的几何
    Stackup bad = new Stackup(
        null, "BAD", 1, null, "M/r1", TraceType.MICROSTRIP_SE,
        Length.of(0.5, "mm"), Length.of(70, "um"), Length.of(20, "um"), null,
        4.2, 3.3, Length.of(0, "um"),
        ToleranceSpec.relative("ETCH", "L", Distribution.UNIFORM, 5, false),
        ToleranceSpec.relative("PRESS", "L", Distribution.UNIFORM, 5, false),
        ToleranceSpec.relative("PRESS", "L", Distribution.UNIFORM, 5, false),
        null,
        ToleranceSpec.relative("DK", "L", Distribution.UNIFORM, 2, false),
        null, null, null);
    AnalysisReport report = service.analyze(bad,
        new AnalysisRequest(AnalysisMode.WORST_CORNERS, 50, 10, null, null, false));
    assertThat(report.domainFailureCount()).isGreaterThan(0);
    assertThat(report.points().stream().anyMatch(p -> !p.inDomain())).isTrue();
    for (PointResult p : report.points()) {
      if (!p.inDomain()) {
        assertThat(p.impedanceOhm()).isNull();
        assertThat(p.violations()).isNotEmpty();
      }
    }
  }

  @Test
  void meanderOnlyReducesEffectiveWidth() {
    Stackup s = outerSe();
    AnalysisReport report = service.analyze(s,
        new AnalysisRequest(AnalysisMode.WORST_CORNERS, 50, 10, null, null, false));
    for (PointResult p : report.points()) {
      double val = p.values().get("meander");
      assertThat(val).as("蛇形波动为非负的减宽量").isBetween(0.0, 8e-6 + 1e-15);
      // w 必须只被减小：有效线宽 = w - meander
      double effW = p.values().get("w") - val;
      assertThat(effW).isLessThanOrEqualTo(p.values().get("w") + 1e-18);
    }
  }
}
