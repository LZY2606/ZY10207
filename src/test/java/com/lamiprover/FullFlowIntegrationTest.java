package com.lamiprover;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullFlowIntegrationTest {

  @TempDir
  static Path tempDir;

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> "jdbc:sqlite:" + tempDir.resolve("test.db"));
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> 1);
    registry.add("spring.datasource.hikari.keepalive-time", () -> 0);
  }

  @LocalServerPort
  int port;

  @Autowired
  TestRestTemplate rest;

  @Autowired
  ObjectMapper mapper;

  private static HttpEntity<String> jsonEntity(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  private String idOf(String namePrefix) {
    JsonNode stackups = rest.getForObject(url("/api/stackups"), JsonNode.class);
    for (JsonNode row : stackups) {
      if (row.get("name").asText().startsWith(namePrefix)) {
        return row.get("id").asText();
      }
    }
    throw new IllegalStateException("缺少 fixture: " + namePrefix);
  }

  @Test
  void fixtureLoadsAndHomePageShowsTitle() throws Exception {
    ResponseEntity<String> home = rest.getForEntity(url("/"), String.class);
    assertThat(home.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(home.getBody()).contains("层压证明器");

    JsonNode stackups = rest.getForObject(url("/api/stackups"), JsonNode.class);
    assertThat(stackups.size()).isGreaterThanOrEqualTo(3);

    String firstId = idOf("外层单端");
    JsonNode runs = rest.getForObject(url("/api/stackups/" + firstId + "/runs"), JsonNode.class);
    assertThat(runs.size()).isGreaterThanOrEqualTo(2);
    // fixture 运行中最坏角落结论包含 32 个关联角落
    JsonNode worst = null;
    for (JsonNode r : runs) {
      if ("WORST_CORNERS".equals(r.get("report").get("mode").asText())) {
        worst = r;
      }
    }
    assertThat(worst).isNotNull();
    assertThat(worst.get("report").get("pointCount").asInt()).isEqualTo(32);
  }

  @Test
  void independentExtremesRejectedOverHttp() {
    String id = idOf("外层单端");
    String body = """
        {"mode":"WORST_CORNERS","targetOhm":50,"tolerancePercent":10,
         "independentExtremes":true}
        """;
    JsonNode resp = rest.postForObject(url("/api/stackups/" + id + "/analyze"),
        jsonEntity(body), JsonNode.class);
    assertThat(resp.get("report").get("rejected").asBoolean()).isTrue();
    assertThat(resp.get("report").get("rejectionReason").asText()).contains("关联");
  }

  @Test
  void newRevisionDoesNotRecomputeOldRuns() throws Exception {
    String firstId = idOf("外层单端");
    JsonNode before = rest.getForObject(url("/api/stackups/" + firstId), JsonNode.class);
    int oldRuns = rest.getForObject(url("/api/stackups/" + firstId + "/runs"),
        JsonNode.class).size();

    String revision = mapper.writeValueAsString(before);
    // 直接复用旧 spec 提交草稿（创建新版本）
    JsonNode created = rest.postForObject(
        url("/api/stackups/" + firstId + "/revision"),
        jsonEntity(revision), JsonNode.class);
    String newId = created.get("id").asText();
    assertThat(newId).isNotEqualTo(firstId);
    assertThat(created.get("parentVersionId").asText()).isEqualTo(firstId);

    int runsAfter = rest.getForObject(url("/api/stackups/" + firstId + "/runs"),
        JsonNode.class).size();
    assertThat(runsAfter).isEqualTo(oldRuns); // 旧结论不自动重算
    assertThat(rest.getForObject(url("/api/stackups/" + newId + "/runs"),
        JsonNode.class).size()).isZero();
  }

  @Test
  void exportResetAndReimportReproducesEverything() throws Exception {
    JsonNode exported = rest.getForObject(url("/api/export"), JsonNode.class);
    int stackCount = exported.get("stackups").size();
    int runCount = exported.get("runs").size();
    assertThat(stackCount).isGreaterThanOrEqualTo(3);
    assertThat(runCount).isGreaterThanOrEqualTo(6);

    rest.postForObject(url("/api/reset"), jsonEntity("{}"), JsonNode.class);
    assertThat(rest.getForObject(url("/api/stackups"), JsonNode.class).size()).isZero();
    assertThat(rest.getForObject(url("/api/export"), JsonNode.class).get("runs").size()).isZero();

    // 原样重导
    JsonNode summary = rest.exchange(url("/api/import"), HttpMethod.POST,
        jsonEntity(exported.toString()), JsonNode.class).getBody();
    assertThat(summary.get("importedStackups").asInt()).isEqualTo(stackCount);
    assertThat(summary.get("importedRuns").asInt()).isEqualTo(runCount);

    JsonNode reExported = rest.getForObject(url("/api/export"), JsonNode.class);
    ObjectMapper plain = new ObjectMapper();
    JsonNode a = plain.readTree(plain.writeValueAsString(exported));
    JsonNode b = plain.readTree(plain.writeValueAsString(reExported));
    // 逐条比对 runs 的 report（结论逐点一致）
    assertThat(b.get("runs").size()).isEqualTo(a.get("runs").size());
    for (int i = 0; i < a.get("runs").size(); i++) {
      assertThat(b.get("runs").get(i).get("id").asText())
          .isEqualTo(a.get("runs").get(i).get("id").asText());
      assertThat(b.get("runs").get(i).get("reportJson").toString())
          .isEqualTo(a.get("runs").get(i).get("reportJson").toString());
    }
  }

  @Test
  void svgSectionIsRendered() {
    String id = idOf("外层单端");
    ResponseEntity<String> svg = rest.getForEntity(
        url("/api/stackups/" + id + "/section.svg"), String.class);
    assertThat(svg.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(svg.getBody()).contains("<svg").contains("w=");
  }
}
