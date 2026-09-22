package com.lamprover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-laminate-prover.db",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class LamProverApplicationTests {


    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private com.lamprover.persistence.RunRepository runRepository;
    @Autowired
    private com.lamprover.service.FixtureSeeder seeder;

    @org.junit.jupiter.api.BeforeEach
    void resetDatabase() throws Exception {
        runRepository.deleteAll();
        seeder.run(null);
    }

    @Test
    void indexPageShowsChineseTitle() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("层压证明器")));
    }

    @Test
    void healthAndFormulas() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/formulas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void fixtureSeededAsVersionOne() throws Exception {
        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.stackup.boardCode").value("DEMO-4L-001"));
    }

    @Test
    void analyzePersistsRunAndSvgRenders() throws Exception {
        mvc.perform(post("/api/stackups/DEMO-4L-001/versions/1/analyze?seed=20260922&samples=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.seed").value(20260922))
                .andExpect(jsonPath("$.result.passed").exists())
                .andExpect(jsonPath("$.result.traces.length()").value(3));
        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/1/svg"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")));
    }

    @Test
    void independentExtremaPolicyRejectedWith422() throws Exception {
        mvc.perform(post("/api/stackups/DEMO-4L-001/versions/1/analyze")
                        .param("policy", "independent-extrema"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POLICY_REJECTED"))
                .andExpect(jsonPath("$.requiredPolicy").value("correlated-press-batches"));
    }

    @Test
    void independentExtremaDiagnosticNamesPressConflict() throws Exception {
        String body = """
                {"layer:PP-7628:thickness":1,"layer:L1:thickness":-1}
                """;
        mvc.perform(post("/api/stackups/DEMO-4L-001/versions/1/independent-extrema-check")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(true))
                .andExpect(jsonPath("$['SE-50-L1'].rejected").value(true));
    }

    @Test
    void savingEditedStackupCreatesNewVersionWithoutRecomputingOld() throws Exception {
        String fixture = mvc.perform(get("/api/fixture")).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(fixture).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("notes", "edited in test");
        MvcResult saved = mvc.perform(post("/api/stackups").contentType("application/json")
                        .content(mapper.writeValueAsString(node)))
                .andExpect(status().isOk()).andReturn();
        int newVersion = mapper.readTree(saved.getResponse().getContentAsString()).get("version").asInt();
        org.junit.jupiter.api.Assertions.assertTrue(newVersion >= 2);
        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stackup.notes").value(org.hamcrest.Matchers.containsString("混合单位")));
        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/" + newVersion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stackup.notes").value("edited in test"));
    }

    @Test
    void exportClearImportReplayRoundTrip() throws Exception {
        MvcResult exported = mvc.perform(get("/api/export"))
                .andExpect(status().isOk()).andReturn();
        String bundle = exported.getResponse().getContentAsString();
        JsonNode tree = mapper.readTree(bundle);
        int versionsBefore = tree.get("versions").size();
        org.junit.jupiter.api.Assertions.assertTrue(versionsBefore >= 1);

        mvc.perform(delete("/api/admin/database")).andExpect(status().isNoContent());
        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/1"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/import?replaceAll=false").contentType("application/json").content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashMismatches").value(0))
                .andExpect(jsonPath("$.versionsImported").value(versionsBefore));

        mvc.perform(get("/api/stackups/DEMO-4L-001/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stackup.name").value("演示四层阻抗板"));
    }

    @Test
    void invalidStackupRejectedWithMessages() throws Exception {
        String bad = """
                {"name":"坏","boardCode":"BAD-1","materialRevision":"r","symmetric":false,
                 "layers":[{"name":"X","kind":"DIELECTRIC","thickness":{"value":0.1,"unit":"mm"},
                            "dk":{"value":1.0,"unit":"1"}}],
                 "traces":[{"name":"T1","signalLayerName":"NO-SUCH-LAYER","type":"SINGLE_ENDED",
                            "width":{"value":0.2,"unit":"mm"},"widthTol":null,
                            "spacing":null,"spacingTol":null,"copperThicknessTol":null,
                            "meander":null,
                            "target":{"nominal":50,"lower":45,"upper":55}}]}
                """;
        mvc.perform(post("/api/stackups").contentType("application/json").content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void tamperedBundleRejectedByHash() throws Exception {
        MvcResult exported = mvc.perform(get("/api/export")).andReturn();
        com.fasterxml.jackson.databind.node.ObjectNode bundle =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(exported.getResponse().getContentAsString());
        com.fasterxml.jackson.databind.node.ArrayNode versions =
                (com.fasterxml.jackson.databind.node.ArrayNode) bundle.get("versions");
        ((com.fasterxml.jackson.databind.node.ObjectNode) versions.get(0).get("payload"))
                .put("notes", "tampered");
        mvc.perform(post("/api/import?replaceAll=false").contentType("application/json")
                        .content(mapper.writeValueAsString(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashMismatches").value(1));
    }
}
