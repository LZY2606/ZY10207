package com.lamiprover.service;

import com.lamiprover.impedance.FormulaInfo;
import com.lamiprover.impedance.ImpedanceInput;
import com.lamiprover.impedance.ImpedanceResult;
import com.lamiprover.impedance.Formulas;
import com.lamiprover.model.Stackup;
import com.lamiprover.tol.Factor;
import com.lamiprover.tol.ToleranceEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 容差分析服务：最坏角落（遵守关联容差组）与固定种子抽样。
 *
 * <p>关键约束：
 * <ul>
 *   <li>{@code independentExtremes=true} 且存在多因子关联组时直接拒绝（不产生结论）；</li>
 *   <li>公式离开适用域时该点不返回阻抗数字，只保留越界指标。</li>
 * </ul>
 */
@Service
public class AnalysisService {

  public AnalysisReport analyze(Stackup stackup, AnalysisRequest req) {
    List<Factor> factors = FactorExtraction.extract(stackup);
    boolean correlated = ToleranceEngine.hasCorrelatedGroup(factors);
    if (req.independentExtremes()) {
      if (correlated) {
        return rejected(stackup, req,
            "拒绝独立取极值：该叠层存在关联容差组（同一压合批次的介质高度 h 与铜厚 t 等）。"
                + "这些量来自同一压合过程，不能各自独立取极值；请改用关联最坏角落。");
      }
    }

    List<Map<String, Double>> valueSets;
    List<Map<String, Integer>> directions;
    List<Map<String, Double>> rValues;
    List<String> labels;

    Map<String, Factor> index = ToleranceEngine.index(factors);
    switch (req.mode()) {
      case WORST_CORNERS -> {
        List<Map<String, Double>> corners = req.independentExtremes()
            ? ToleranceEngine.independentExtremes(factors)
            : ToleranceEngine.worstCorners(factors);
        valueSets = corners;
        directions = new ArrayList<>();
        rValues = new ArrayList<>();
        labels = new ArrayList<>();
        for (int c = 0; c < corners.size(); c++) {
          Map<String, Integer> dirs = new LinkedHashMap<>();
          corners.get(c).forEach((k, val) -> {
            Factor f = index.get(k);
            dirs.put(k, (int) Math.signum(val - f.nominal()));
          });
          directions.add(dirs);
          rValues.add(null);
          labels.add("corner#" + c);
        }
      }
      case SAMPLED -> {
        List<Map<String, Double>> rs =
            ToleranceEngine.sampleR(req.seedOrThrow(), req.sampleCountOrThrow(), factors);
        valueSets = new ArrayList<>();
        directions = new ArrayList<>();
        rValues = new ArrayList<>();
        labels = new ArrayList<>();
        for (int i = 0; i < rs.size(); i++) {
          Map<String, Double> vals = new LinkedHashMap<>();
          rs.get(i).forEach((k, r) -> vals.put(k, index.get(k).sampledValue(r)));
          valueSets.add(vals);
          directions.add(null);
          rValues.add(rs.get(i));
          labels.add("sample#" + i);
        }
      }
      default -> throw new IllegalStateException("未知分析模式: " + req.mode());
    }

    List<PointResult> points = new ArrayList<>();
    int pass = 0;
    int domainFail = 0;
    int specFail = 0;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < valueSets.size(); i++) {
      Map<String, Double> vals = valueSets.get(i);
      ImpedanceInput input = buildInput(stackup, vals);
      ImpedanceResult res = Formulas.evaluate(stackup.traceType(), input);

      Map<String, FactorTrace> traces = buildTraces(factors, vals,
          directions.get(i), rValues.get(i));

      PointResult pr;
      if (!res.inDomain()) {
        domainFail++;
        pr = PointResult.domain(labels.get(i), res.violations(), vals, traces);
      } else {
        double z = res.impedanceOhm();
        double dev = (z - req.targetOhm()) / req.targetOhm() * 100.0;
        boolean ok = Math.abs(dev) <= req.tolerancePercent();
        if (ok) {
          pass++;
        } else {
          specFail++;
        }
        min = Math.min(min, z);
        max = Math.max(max, z);
        pr = new PointResult(labels.get(i), ok, z, dev, true, List.of(),
            res.effectiveDk(), vals, traces, ok ? null : "IMPEDANCE_OUT_OF_SPEC");
      }
      points.add(pr);
    }

    List<Map<String, String>> groups = groupInfo(factors);
    FormulaInfo info = Formulas.info(stackup.traceType());
    return new AnalysisReport(
        req.mode().name(), req.independentExtremes(), false, null,
        info.id(), info, req.targetOhm(), req.tolerancePercent(),
        points.size(), pass, domainFail + specFail, domainFail, specFail,
        min == Double.POSITIVE_INFINITY ? null : min,
        max == Double.NEGATIVE_INFINITY ? null : max,
        groups, points,
        req.seed() == null ? null : req.seed().intValue(),
        req.sampleCount());
  }

  private AnalysisReport rejected(Stackup s, AnalysisRequest req, String reason) {
    FormulaInfo info = Formulas.info(s.traceType());
    return new AnalysisReport(req.mode().name(), req.independentExtremes(), true, reason,
        info.id(), info, req.targetOhm(), req.tolerancePercent(),
        0, 0, 0, 0, 0, null, null, List.of(), List.of(),
        req.seed() == null ? null : req.seed().intValue(), req.sampleCount());
  }

  private ImpedanceInput buildInput(Stackup s, Map<String, Double> vals) {
    double w = vals.getOrDefault("w", s.traceWidth().metres());
    double t = vals.getOrDefault("t", s.copperThickness().metres());
    double h = vals.getOrDefault("h", s.dielectricHeight().metres());
    double dk = vals.getOrDefault("dk", s.dielectricConstant());
    double meander = vals.getOrDefault("meander", 0.0);
    double effW = w - meander; // 蛇形扇贝单侧减小有效线宽

    if (s.traceType().stripline()) {
      double sv = s.traceType().differential()
          ? vals.getOrDefault("s", s.pairSpacingOrZero().metres()) : Double.NaN;
      return s.traceType().differential()
          ? ImpedanceInput.differential(effW, t, h, sv, dk, Double.NaN, 0.0)
          : ImpedanceInput.singleEnded(effW, t, h, dk, Double.NaN, 0.0);
    }
    double maskDk = vals.containsKey("maskDk") ? vals.get("maskDk")
        : (s.maskThicknessOrZero().metres() > 0 ? s.solderMaskDk() : 1.0);
    double maskT = vals.getOrDefault("maskT", s.maskThicknessOrZero().metres());
    if (s.traceType().differential()) {
      double sv = vals.getOrDefault("s", s.pairSpacingOrZero().metres());
      return ImpedanceInput.differential(effW, t, h, sv, dk, maskDk, maskT);
    }
    return ImpedanceInput.singleEnded(effW, t, h, dk, maskDk, maskT);
  }

  private Map<String, FactorTrace> buildTraces(List<Factor> factors, Map<String, Double> vals,
                                               Map<String, Integer> dirs,
                                               Map<String, Double> rs) {
    Map<String, FactorTrace> traces = new LinkedHashMap<>();
    for (Factor f : factors) {
      Integer dir = dirs == null ? null : dirs.get(f.key());
      Double r = rs == null ? null : rs.get(f.key());
      traces.put(f.key(), new FactorTrace(
          f.spec().group(), f.spec().sourceLot(), dir, r, f.label()));
    }
    return traces;
  }

  private List<Map<String, String>> groupInfo(List<Factor> factors) {
    Map<String, List<Factor>> byGroup = new LinkedHashMap<>();
    for (Factor f : factors) {
      byGroup.computeIfAbsent(f.spec().group(), k -> new ArrayList<>()).add(f);
    }
    List<Map<String, String>> out = new ArrayList<>();
    byGroup.forEach((grp, fs) -> {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("group", grp);
      row.put("sourceLot", fs.get(0).spec().sourceLot());
      row.put("members", String.join(", ", fs.stream().map(Factor::label).toList()));
      row.put("correlated", String.valueOf(fs.size() > 1));
      out.add(row);
    });
    return out;
  }
}
