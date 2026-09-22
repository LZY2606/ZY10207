package com.lamprover.web;

import com.lamprover.core.Analyzer;
import com.lamprover.core.ImpedanceFormulas;
import com.lamprover.domain.Stackup;
import com.lamprover.persistence.RunRepository;
import com.lamprover.persistence.StoredRun;
import com.lamprover.persistence.StackupRepository;
import com.lamprover.persistence.StoredVersion;
import com.lamprover.service.AnalysisService;
import com.lamprover.service.Fixture;
import com.lamprover.service.StackupService;
import com.lamprover.service.SvgRenderer;
import com.lamprover.service.TransferService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StackupService stackupService;
    private final AnalysisService analysisService;
    private final TransferService transferService;
    private final RunRepository runRepository;
    private final SvgRenderer svg;

    public ApiController(StackupService stackupService, AnalysisService analysisService,
                         TransferService transferService, RunRepository runRepository,
                         SvgRenderer svg) {
        this.stackupService = stackupService;
        this.analysisService = analysisService;
        this.transferService = transferService;
        this.runRepository = runRepository;
        this.svg = svg;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "laminate-prover", "ok", true,
                "formulas", ImpedanceFormulas.documentation().size());
    }

    @GetMapping("/formulas")
    public List<ImpedanceFormulas.FormulaDoc> formulas() {
        return ImpedanceFormulas.documentation();
    }

    @GetMapping("/fixture")
    public Stackup fixture() {
        return Fixture.demoStackup();
    }

    @GetMapping("/stackups")
    public List<StackupRepository.VersionSummary> listStackups() {
        return stackupService.all();
    }

    @GetMapping("/stackups/{code}")
    public List<StackupRepository.VersionSummary> versions(@PathVariable String code) {
        return stackupService.versions(code);
    }

    @GetMapping("/stackups/{code}/versions/{version}")
    public StoredVersion version(@PathVariable String code, @PathVariable int version) {
        return stackupService.require(code, version);
    }

    @PostMapping("/stackups")
    public StoredVersion save(@RequestBody Stackup stackup) {
        return stackupService.save(stackup);
    }

    @GetMapping("/stackups/{code}/versions/{version}/svg")
    public ResponseEntity<String> svg(@PathVariable String code, @PathVariable int version,
                                      @RequestParam(required = false) String trace) {
        StoredVersion v = stackupService.require(code, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml;charset=utf-8")
                .body(svg.render(v.stackup(), trace));
    }

    /** 编辑器实时预览：不持久化叠层，只回计算结果。 */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody PreviewRequest request) {
        List<String> errors = request.stackup().validationErrors();
        if (!errors.isEmpty()) {
            throw new StackupService.ValidationException(errors);
        }
        long seed = request.seed() == null ? Fixture.fixedSeed() : request.seed();
        int samples = request.samples() == null ? Fixture.fixedSampleCount() : request.samples();
        Analyzer.AnalysisResult result = Analyzer.analyze(request.stackup(), seed, samples);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seed", seed);
        out.put("sampleCount", samples);
        out.put("cornerPolicy", AnalysisService.POLICY_CORRELATED);
        out.put("passed", result.passed());
        out.put("traces", result.traces());
        return out;
    }

    @PostMapping("/preview/svg")
    public ResponseEntity<String> previewSvg(@RequestBody PreviewRequest request,
                                             @RequestParam(required = false) String trace) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml;charset=utf-8")
                .body(svg.render(request.stackup(), trace));
    }

    public record PreviewRequest(Stackup stackup, Long seed, Integer samples) {
    }

    @PostMapping("/stackups/{code}/versions/{version}/analyze")
    public AnalysisService.RunEnvelope analyze(@PathVariable String code, @PathVariable int version,
                                               @RequestParam(required = false) Long seed,
                                               @RequestParam(required = false) Integer samples,
                                               @RequestParam(required = false,
                                                       defaultValue = AnalysisService.POLICY_CORRELATED)
                                               String policy) {
        return analysisService.runAnalysis(code, version, seed, samples, policy, true);
    }

    /** 验收对照接口：独立取极值在有关联批次时必须被拒绝。 */
    @PostMapping("/stackups/{code}/versions/{version}/independent-extrema-check")
    public Map<String, Object> independentCheck(@PathVariable String code, @PathVariable int version,
                                                @RequestBody Map<String, Integer> signs) {
        return analysisService.diagnoseIndependentExtrema(code, version, signs == null ? Map.of() : signs);
    }

    @GetMapping("/runs")
    public List<StoredRun> runs(@RequestParam(required = false) String boardCode,
                                @RequestParam(required = false) Integer version) {
        return analysisService.listRuns(boardCode, version);
    }

    @GetMapping("/runs/{id}")
    public StoredRun run(@PathVariable long id) {
        return analysisService.getRun(id);
    }

    @DeleteMapping("/runs/{id}")
    public Map<String, Object> deleteRun(@PathVariable long id) {
        runRepository.delete(id);
        return Map.of("deleted", id);
    }

    @GetMapping("/export")
    public Map<String, Object> export() {
        return transferService.exportBundle();
    }

    @PostMapping("/import")
    public TransferService.ImportReport importBundle(@RequestBody Map<String, Object> bundle,
                                                     @RequestParam(required = false,
                                                             defaultValue = "true") boolean replaceAll) {
        return transferService.importBundle(bundle, replaceAll);
    }

    @DeleteMapping("/admin/database")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void clearDatabase() {
        runRepository.deleteAll();
    }

    @ExceptionHandler(StackupService.ValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(StackupService.ValidationException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "VALIDATION_FAILED", "messages", e.errors()));
    }

    @ExceptionHandler(AnalysisService.RejectedPolicyException.class)
    public ResponseEntity<Map<String, Object>> rejected(AnalysisService.RejectedPolicyException e) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "POLICY_REJECTED",
                        "message", e.getMessage(),
                        "requiredPolicy", AnalysisService.POLICY_CORRELATED));
    }

    @ExceptionHandler(StackupService.NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(StackupService.NotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", String.valueOf(e.getMessage())));
    }
}
