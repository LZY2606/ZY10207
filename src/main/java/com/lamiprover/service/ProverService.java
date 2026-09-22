package com.lamiprover.service;

import com.lamiprover.db.RunRepository;
import com.lamiprover.db.RunRow;
import com.lamiprover.db.StackupRepository;
import com.lamiprover.model.Stackup;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 叠层版本化 + 分析执行编排。
 *
 * <p>叠层变更只追加新版本；新版本不会触发旧版本上已有运行结论的重算，
 * 历史运行记录仍指向当时的 stackup 版本 id。
 */
@Service
public class ProverService {

  private final StackupRepository stackups;
  private final RunRepository runs;
  private final AnalysisService analysis;

  public ProverService(StackupRepository stackups, RunRepository runs,
                       AnalysisService analysis) {
    this.stackups = stackups;
    this.runs = runs;
    this.analysis = analysis;
  }

  /** 新建首个版本（fixture 或手工创建）。 */
  public Stackup createFirstVersion(Stackup spec, Instant createdAt) {
    int version = stackups.nextVersionNumber(spec.name());
    if (version != spec.version()) {
      throw new IllegalArgumentException("版本号应由仓储分配，期望 v" + version);
    }
    String id = versionId(spec.name(), version);
    Stackup stored = spec.withIdAndVersion(id, version);
    stackups.insert(stored, id, createdAt);
    return stored;
  }

  /** 基于旧版本创建新版本：parentVersionId 指向旧版本 id。 */
  public Stackup createRevision(String parentId, Stackup edited, Instant createdAt) {
    Stackup parent = stackups.findSpec(parentId)
        .orElseThrow(() -> new IllegalArgumentException("父版本不存在: " + parentId));
    int version = stackups.nextVersionNumber(parent.name());
    String id = versionId(parent.name(), version);
    Stackup withParent = new Stackup(
        id, parent.name(), version, parent.id(),
        edited.materialVersion() != null ? edited.materialVersion() : parent.materialVersion(),
        edited.traceType(),
        edited.traceWidth(), edited.copperThickness(), edited.dielectricHeight(),
        edited.pairSpacing(), edited.dielectricConstant(), edited.solderMaskDk(),
        edited.maskThickness(),
        edited.widthTolerance(), edited.copperThicknessTolerance(),
        edited.dielectricHeightTolerance(), edited.pairSpacingTolerance(),
        edited.dielectricConstantTolerance(), edited.solderMaskDkTolerance(),
        edited.maskThicknessTolerance(), edited.meanderTolerance());
    stackups.insert(withParent, id, createdAt);
    return withParent;
  }

  /** 在指定（旧）版本上执行分析并持久化；结论只属于该版本。 */
  public RunRow runAnalysis(String stackupId, AnalysisRequest request, Instant createdAt) {
    Stackup spec = stackups.findSpec(stackupId)
        .orElseThrow(() -> new IllegalArgumentException("叠层版本不存在: " + stackupId));
    AnalysisReport report = analysis.analyze(spec, request);
    String runId = runId(stackupId, request, report, createdAt);
    if (runs.exists(runId)) {
      // 同版本 + 同参数 = 同结论（可重放）：直接返回历史记录，不重复落库
      return runs.findById(runId).orElseThrow();
    }
    runs.insert(runId, spec.id(), spec.version(), createdAt, request, report);
    return new RunRow(runId, spec.id(), spec.version(), createdAt, request, report);
  }

  public List<RunRow> runsOf(String stackupId) {
    return runs.findByStackup(stackupId);
  }

  static String versionId(String name, int version) {
    // 名称可能是中文：保留 ASCII slug，追加名称的短哈希保证不同中文名不撞 id
    String ascii = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    if (ascii.isEmpty()) {
      ascii = "stack";
    }
    return ascii + "-" + shortHash(name) + "-v" + version;
  }

  private static String runId(String stackupId, AnalysisRequest req, AnalysisReport report,
                              Instant createdAt) {
    // 运行 id 由“叠层版本 + 请求参数”决定；同一版本同参数重放结果一致（幂等键）
    String basis = stackupId + "|" + req.mode() + "|" + req.targetOhm() + "|"
        + req.tolerancePercent() + "|" + req.seed() + "|" + req.sampleCount() + "|"
        + req.independentExtremes();
    return "run-" + shortHash(basis);
  }

  private static String shortHash(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(h).substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
