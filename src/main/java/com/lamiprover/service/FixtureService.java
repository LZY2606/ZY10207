package com.lamiprover.service;

import com.lamiprover.db.StackupRepository;
import com.lamiprover.model.Stackup;
import com.lamiprover.model.TraceType;
import com.lamiprover.tol.Distribution;
import com.lamiprover.tol.ToleranceSpec;
import com.lamiprover.unit.Length;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 固定 fixture：随空库启动时导入。三个样例故意混用 mil / µm / mm，
 * 且介质高度 h 与铜厚 t 归属同一压合批次的关联容差组。
 */
@Service
public class FixtureService {

  private static final Logger log = LoggerFactory.getLogger(FixtureService.class);

  // 关联组与批次（页面/导出可见）
  private static final String G_PRESS = "PRESS-LAM-01";
  private static final String LOT_PRESS = "LOT-PRESS-2026-09-A";
  private static final String G_ETCH = "ETCH-PROCESS-07";
  private static final String LOT_ETCH = "LOT-ETCH-2026-37";
  private static final String G_DK = "MATERIAL-DK-S1000";
  private static final String LOT_DK = "LOT-S1000-2B-2026-08";
  private static final String G_MEANDER = "MEANDER-AOI-03";
  private static final String LOT_MEANDER = "LOT-AOI-2026-37";
  private static final String G_MASK = "SOLDER-MASK-G100";
  private static final String LOT_MASK = "LOT-MASK-2026-09";

  private final StackupRepository stackups;
  private final ProverService prover;

  public FixtureService(StackupRepository stackups, ProverService prover) {
    this.stackups = stackups;
    this.prover = prover;
  }

  public boolean ensureFixtures() {
    if (stackups.count() > 0) {
      return false;
    }
    Instant ts = Instant.parse("2026-09-01T00:00:00Z");
    for (Stackup seed : seedStackups()) {
      Stackup stored = prover.createFirstVersion(seed, ts);
      for (AnalysisRequest req : defaultRuns(stored)) {
        prover.runAnalysis(stored.id(), req, ts);
      }
      log.info("fixture loaded: {} v{}", stored.name(), stored.version());
    }
    return true;
  }

  private List<AnalysisRequest> defaultRuns(Stackup s) {
    List<AnalysisRequest> reqs = new ArrayList<>();
    double target = s.traceType().differential() ? 100.0 : 50.0;
    double tol = s.traceType().differential() ? 10.0 : 10.0;
    reqs.add(new AnalysisRequest(com.lamiprover.model.AnalysisMode.WORST_CORNERS,
        target, tol, null, null, false));
    reqs.add(new AnalysisRequest(com.lamiprover.model.AnalysisMode.SAMPLED,
        target, tol, 20260922L, 200, false));
    return reqs;
  }

  public static List<Stackup> seedStackups() {
    // 1) 外层单端微带线：mil + µm + mm 混用；h 与 t 同一压合批关联
    ToleranceSpec wTol = ToleranceSpec.relative(G_ETCH, LOT_ETCH,
        Distribution.NORMAL, 10.0, false);
    ToleranceSpec tTol = ToleranceSpec.relative(G_PRESS, LOT_PRESS,
        Distribution.UNIFORM, 8.0, false);
    ToleranceSpec hTol = ToleranceSpec.relative(G_PRESS, LOT_PRESS,
        Distribution.UNIFORM, 7.0, false);
    ToleranceSpec dkTol = ToleranceSpec.relative(G_DK, LOT_DK,
        Distribution.NORMAL, 3.0, false);
    ToleranceSpec maskDkTol = ToleranceSpec.relative(G_MASK, LOT_MASK,
        Distribution.UNIFORM, 5.0, false);
    ToleranceSpec maskTTol = ToleranceSpec.relative(G_MASK, LOT_MASK,
        Distribution.UNIFORM, 15.0, false);
    ToleranceSpec meanderTol = ToleranceSpec.absolute(G_MEANDER, LOT_MEANDER,
        Distribution.UNIFORM, Length.of(8, "um"), true);

    Stackup outerSe = new Stackup(
        null, "外层单端-SE50", 1, null, "S1000-2B@10GHz/r3",
        TraceType.MICROSTRIP_SE,
        Length.of(7.0, "mil"),     // 线宽 7 mil
        Length.of(35, "um"),       // 1 oz 铜厚 35 µm
        Length.of(0.12, "mm"),    // 介质高度 0.12 mm
        null, // 差分间距（单端不使用）
        4.20, 3.30, Length.of(15, "um"),
        wTol, tTol, hTol, null, dkTol, maskDkTol, maskTTol, meanderTol);

    // 2) 外层差分微带线：同样混用单位、h/t 关联
    Stackup outerDiff = new Stackup(
        null, "外层差分-DI100", 1, null, "S1000-2B@10GHz/r3",
        TraceType.MICROSTRIP_DIFF,
        Length.of(0.12, "mm"),    // 线宽 0.12 mm
        Length.of(1.2, "mil"),    // 铜厚 1.2 mil
        Length.of(100, "um"),     // 介质高度 100 µm
        Length.of(0.20, "mm"),    // 边沿间距 0.20 mm
        4.20, 3.30, Length.of(12, "um"),
        ToleranceSpec.relative(G_ETCH, LOT_ETCH, Distribution.NORMAL, 10.0, false),
        ToleranceSpec.relative(G_PRESS, LOT_PRESS, Distribution.UNIFORM, 8.0, false),
        ToleranceSpec.relative(G_PRESS, LOT_PRESS, Distribution.UNIFORM, 7.0, false),
        ToleranceSpec.relative(G_ETCH, LOT_ETCH, Distribution.NORMAL, 12.0, false),
        dkTol, maskDkTol, maskTTol, meanderTol);

    // 3) 内层带状线差分：无阻焊；t/h 关联；故意制造一个离开适用域的反例版本素材
    Stackup innerDiff = new Stackup(
        null, "内层带状线-DI100", 1, null, "IT-180A@10GHz/r2",
        TraceType.STRIPLINE_DIFF,
        Length.of(6.0, "mil"),
        Length.of(17.5, "um"),    // 半盎司 17.5 µm
        Length.of(0.42, "mm"),
        Length.of(0.25, "mm"),
        3.90, 0.0, Length.of(0, "um"),
        ToleranceSpec.relative(G_ETCH, LOT_ETCH, Distribution.NORMAL, 10.0, false),
        ToleranceSpec.relative(G_PRESS, LOT_PRESS, Distribution.UNIFORM, 8.0, false),
        ToleranceSpec.relative(G_PRESS, LOT_PRESS, Distribution.UNIFORM, 7.0, false),
        ToleranceSpec.relative(G_ETCH, LOT_ETCH, Distribution.NORMAL, 12.0, false),
        ToleranceSpec.relative(G_DK, "LOT-IT180A-2026-08", Distribution.NORMAL, 3.0, false),
        null, null,
        ToleranceSpec.absolute(G_MEANDER, LOT_MEANDER, Distribution.UNIFORM,
            Length.of(6, "um"), true));

    return List.of(outerSe, outerDiff, innerDiff);
  }
}
