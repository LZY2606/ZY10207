package com.lamiprover.web;

import com.lamiprover.db.ExportBundle;
import com.lamiprover.db.RunRow;
import com.lamiprover.db.StackupRepository;
import com.lamiprover.db.StackupRow;
import com.lamiprover.db.TransferService;
import com.lamiprover.impedance.FormulaInfo;
import com.lamiprover.impedance.Formulas;
import com.lamiprover.impedance.ImpedanceInput;
import com.lamiprover.impedance.ImpedanceResult;
import com.lamiprover.model.Stackup;
import com.lamiprover.model.TraceType;
import com.lamiprover.service.AnalysisRequest;
import com.lamiprover.service.ProverService;
import com.lamiprover.service.SectionSvgService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

  private final StackupRepository stackupRepository;
  private final ProverService prover;
  private final SectionSvgService svg;
  private final TransferService transfer;

  public ApiController(StackupRepository stackupRepository, ProverService prover,
                       SectionSvgService svg, TransferService transfer) {
    this.stackupRepository = stackupRepository;
    this.prover = prover;
    this.svg = svg;
    this.transfer = transfer;
  }

  @GetMapping("/stackups")
  public List<StackupRow> listStackups() {
    return stackupRepository.findAllRows();
  }

  @GetMapping("/stackups/{id}")
  public Stackup getStackup(@PathVariable String id) {
    return stackupRepository.findSpec(id)
        .orElseThrow(() -> new NotFoundException("叠层版本不存在: " + id));
  }

  @GetMapping("/stackups/name/{name}")
  public List<Stackup> versions(@PathVariable String name) {
    return stackupRepository.versionsOf(name);
  }

  @PostMapping("/stackups")
  public Stackup create(@RequestBody StackupDraft draft) {
    validate(draft);
    return prover.createFirstVersion(draft.toModel(), Instant.now());
  }

  @PostMapping("/stackups/{id}/revision")
  public Stackup revise(@PathVariable String id, @RequestBody StackupDraft draft) {
    validate(draft);
    return prover.createRevision(id, draft.toModel(), Instant.now());
  }

  @PostMapping("/stackups/{id}/analyze")
  public RunRow analyze(@PathVariable String id, @RequestBody AnalysisRequest request) {
    return prover.runAnalysis(id, request, Instant.now());
  }

  @GetMapping("/stackups/{id}/runs")
  public List<RunRow> runs(@PathVariable String id) {
    return prover.runsOf(id);
  }

  @GetMapping("/stackups/{id}/section.svg")
  public ResponseEntity<String> section(@PathVariable String id) {
    Stackup s = stackupRepository.findSpec(id)
        .orElseThrow(() -> new NotFoundException("叠层版本不存在: " + id));
    return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
        .body(svg.render(s));
  }

  @GetMapping("/stackups/{id}/nominal")
  public Map<String, Object> nominal(@PathVariable String id) {
    Stackup s = stackupRepository.findSpec(id)
        .orElseThrow(() -> new NotFoundException("叠层版本不存在: " + id));
    return nominalFor(s);
  }

  @PostMapping("/drafts/nominal")
  public Map<String, Object> draftNominal(@RequestBody StackupDraft draft) {
    validate(draft);
    return nominalFor(draft.toModel());
  }

  @GetMapping("/formulas/{type}")
  public FormulaInfo formula(@PathVariable TraceType type) {
    return Formulas.info(type);
  }

  @GetMapping("/export")
  public ExportBundle exportAll() {
    return transfer.exportAll();
  }

  @PostMapping("/import")
  public Map<String, Object> importBundle(@RequestBody ExportBundle bundle) {
    TransferService.ImportSummary summary = transfer.importBundle(bundle);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("importedStackups", summary.stackups());
    out.put("importedRuns", summary.runs());
    return out;
  }

  @PostMapping("/reset")
  public Map<String, Object> reset() {
    int stacks = stackupRepository.findAllRows().size();
    transfer.clearAll();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("clearedStackups", stacks);
    out.put("status", "OK");
    return out;
  }

  private Map<String, Object> nominalFor(Stackup s) {
    boolean diff = s.traceType().differential();
    ImpedanceInput in;
    if (s.traceType().stripline()) {
      in = diff
          ? ImpedanceInput.differential(s.traceWidth().metres(), s.copperThickness().metres(),
              s.dielectricHeight().metres(), s.pairSpacingOrZero().metres(),
              s.dielectricConstant(), Double.NaN, 0.0)
          : ImpedanceInput.singleEnded(s.traceWidth().metres(), s.copperThickness().metres(),
              s.dielectricHeight().metres(), s.dielectricConstant(), Double.NaN, 0.0);
    } else {
      in = diff
          ? ImpedanceInput.differential(s.traceWidth().metres(), s.copperThickness().metres(),
              s.dielectricHeight().metres(), s.pairSpacingOrZero().metres(),
              s.dielectricConstant(), s.solderMaskDk(), s.maskThicknessOrZero().metres())
          : ImpedanceInput.singleEnded(s.traceWidth().metres(), s.copperThickness().metres(),
              s.dielectricHeight().metres(), s.dielectricConstant(),
              s.solderMaskDk(), s.maskThicknessOrZero().metres());
    }
    ImpedanceResult result = Formulas.evaluate(s.traceType(), in);
    FormulaInfo info = Formulas.info(s.traceType());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("formula", info);
    out.put("inDomain", result.inDomain());
    out.put("impedanceOhm", result.inDomain() ? result.impedanceOhm() : null);
    out.put("effectiveDk", result.inDomain() ? result.effectiveDk() : null);
    out.put("violations", result.violations());
    Map<String, Double> si = new LinkedHashMap<>();
    si.put("w_m", s.traceWidth().metres());
    si.put("t_m", s.copperThickness().metres());
    si.put("h_m", s.dielectricHeight().metres());
    out.put("siInputs", si);
    return out;
  }

  private static void validate(StackupDraft d) {
    if (d == null || d.name() == null || d.name().isBlank()) {
      throw new IllegalArgumentException("叠层名称不能为空");
    }
    if (d.traceType() == null) {
      throw new IllegalArgumentException("走线类型不能为空");
    }
    if (d.materialVersion() == null || d.materialVersion().isBlank()) {
      throw new IllegalArgumentException("材料版本不能为空");
    }
    if (d.dielectricConstant() == null) {
      throw new IllegalArgumentException("介电常数不能为空");
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", e.getMessage() == null ? "参数非法" : e.getMessage()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
  }

  static class NotFoundException extends RuntimeException {
    NotFoundException(String m) {
      super(m);
    }
  }
}
