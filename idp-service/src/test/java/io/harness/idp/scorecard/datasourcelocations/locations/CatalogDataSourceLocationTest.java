/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sundr.codegen.utils.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogDataSourceLocationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-123";
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String ENTITY_NAME = "test-service";
  private static final String ENTITY_KIND = "component";
  private static final String PARENT_UNIQUE_ID = "zEaak-FLS425IEO7OLzMUg";
  private static final String UNIQUE_ID = "abcd-FLS425IEO7OLzMUg";

  private CatalogDataSourceLocation catalogDataSourceLocation;

  @Before
  public void setUp() {
    catalogDataSourceLocation = new CatalogDataSourceLocation();
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_Datadog() {
    Map<String, Object> datadog = sampleDatadogProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("Datadog", datadog);

    List<Map.Entry<String, Object>> datadogDatapoints = List.of(
        Map.entry("catalog.metadata.integration_properties.Datadog.description", datadog.get("description")),
        Map.entry("catalog.metadata.integration_properties.Datadog.monitorCount", datadog.get("monitorCount")),
        Map.entry("catalog.metadata.integration_properties.Datadog.monitors_summary", datadog.get("monitors_summary")),
        Map.entry("catalog.metadata.integration_properties.Datadog.monitors_summary_count",
            datadog.get("monitors_summary_count")),
        Map.entry("catalog.metadata.integration_properties.Datadog.monitors_summary_count.red",
            ((Map<String, Object>) datadog.get("monitors_summary_count")).get("red")),
        Map.entry("catalog.metadata.integration_properties.Datadog.contacts", datadog.get("contacts")),
        Map.entry(
            "catalog.metadata.integration_properties.Datadog.contacts.size()", ((List) datadog.get("contacts")).size()),
        Map.entry("catalog.metadata.integration_properties.Datadog.docs", datadog.get("docs")),
        Map.entry("catalog.metadata.integration_properties.Datadog.docs.size()", ((List) datadog.get("docs")).size()),
        Map.entry("catalog.metadata.integration_properties.Datadog.downstreamServiceNames",
            datadog.get("downstreamServiceNames")),
        Map.entry("catalog.metadata.integration_properties.Datadog.githubHtmlUrl", datadog.get("githubHtmlUrl")),
        Map.entry("catalog.metadata.integration_properties.Datadog.githubHtmlUrl != null && "
                + "catalog.metadata.integration_properties.Datadog.githubHtmlUrl != \"\"",
            !StringUtils.isNullOrEmpty((String) datadog.get("githubHtmlUrl"))),
        Map.entry("catalog.metadata.integration_properties.Datadog.languages", datadog.get("languages")),
        Map.entry("catalog.metadata.integration_properties.Datadog", datadog));

    assertDatapoints(entity, Map.ofEntries(datadogDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_DynaTrace() {
    Map<String, Object> dynatrace = sampleDynaTraceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("DynaTrace", dynatrace);

    int monitorCount = ((Number) dynatrace.get("monitorCount")).intValue();
    int sloCount = ((Number) dynatrace.get("sloCount")).intValue();
    int problemCount = ((Number) dynatrace.get("problemCount")).intValue();

    List<Map.Entry<String, Object>> dynatraceDatapoints = List.of(
        Map.entry("catalog.metadata.integration_properties.DynaTrace.monitorCount", dynatrace.get("monitorCount")),
        Map.entry("catalog.metadata.integration_properties.DynaTrace.sloCount", dynatrace.get("sloCount")),
        Map.entry("catalog.metadata.integration_properties.DynaTrace.problemCount", dynatrace.get("problemCount")),
        Map.entry("catalog.metadata.integration_properties.DynaTrace.monitorCount > 0", monitorCount > 0),
        Map.entry("catalog.metadata.integration_properties.DynaTrace.sloCount > 0", sloCount > 0),
        Map.entry("catalog.metadata.integration_properties.DynaTrace.problemCount == 0", problemCount == 0));

    assertDatapoints(entity, Map.ofEntries(dynatraceDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_GitHub() {
    Map<String, Object> github = sampleGitHubProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("GitHub", github);

    Map<String, Object> latestRelease = (Map<String, Object>) github.get("latestRelease");
    String primaryLanguageName = (String) github.get("primaryLanguageName");
    String publishedAt = (String) latestRelease.get("publishedAt");

    List<Map.Entry<String, Object>> githubDatapoints =
        List.of(Map.entry("catalog.metadata.integration_properties.GitHub.url", github.get("url")),
            Map.entry("catalog.metadata.integration_properties.GitHub.primaryLanguageName", primaryLanguageName),
            Map.entry("catalog.metadata.integration_properties.GitHub.hasAgentsFile", github.get("hasAgentsFile")),
            Map.entry("catalog.metadata.integration_properties.GitHub.latestRelease", latestRelease),
            Map.entry("catalog.metadata.integration_properties.GitHub.latestRelease.publishedAt", publishedAt),
            Map.entry("catalog.metadata.integration_properties.GitHub.languages", github.get("languages")),
            Map.entry("catalog.metadata.integration_properties.GitHub.releases", github.get("releases")),
            // publishedAt is an ISO-8601 string; compare as non-null/non-empty (numeric > 0 is not valid for strings).
            Map.entry("catalog.metadata.integration_properties.GitHub.latestRelease.publishedAt != null && "
                    + "catalog.metadata.integration_properties.GitHub.latestRelease.publishedAt != \"\"",
                !StringUtils.isNullOrEmpty(publishedAt)),
            Map.entry("catalog.metadata.integration_properties.GitHub.primaryLanguageName != null && "
                    + "catalog.metadata.integration_properties.GitHub.primaryLanguageName != \"\"",
                !StringUtils.isNullOrEmpty(primaryLanguageName)),
            Map.entry("catalog.metadata.integration_properties.GitHub.hasAgentsFile == true",
                Boolean.TRUE.equals(github.get("hasAgentsFile"))));

    assertDatapoints(entity, Map.ofEntries(githubDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_BitbucketCloud() {
    Map<String, Object> bitbucketCloud = sampleBitbucketCloudProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("BitbucketCloud", bitbucketCloud);

    Map<String, Object> project = (Map<String, Object>) bitbucketCloud.get("project");
    String description = (String) bitbucketCloud.get("description");
    String defaultBranch = (String) bitbucketCloud.get("default_branch");
    String projectKey = (String) project.get("key");

    List<Map.Entry<String, Object>> bitbucketCloudDatapoints =
        List.of(Map.entry("catalog.metadata.integration_properties.BitbucketCloud.description", description),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.default_branch", defaultBranch),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.project", project),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.project.key", projectKey),
            Map.entry(
                "catalog.metadata.integration_properties.BitbucketCloud.is_private", bitbucketCloud.get("is_private")),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.tags", bitbucketCloud.get("tags")),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.description != null && "
                    + "catalog.metadata.integration_properties.BitbucketCloud.description != \"\"",
                !StringUtils.isNullOrEmpty(description)),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.default_branch == \"main\"",
                "main".equals(defaultBranch)),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.project.key != null && "
                    + "catalog.metadata.integration_properties.BitbucketCloud.project.key != \"\"",
                !StringUtils.isNullOrEmpty(projectKey)),
            Map.entry("catalog.metadata.integration_properties.BitbucketCloud.is_private == true",
                Boolean.TRUE.equals(bitbucketCloud.get("is_private"))));

    assertDatapoints(entity, Map.ofEntries(bitbucketCloudDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_SonarQube() {
    Map<String, Object> sonarqube = sampleSonarQubeProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("SonarQube", sonarqube);

    Map<String, Object> measures = (Map<String, Object>) sonarqube.get("measures");

    List<Map.Entry<String, Object>> sonarqubeDatapoints = List.of(
        Map.entry(
            "catalog.metadata.integration_properties.SonarQube.qualityGateStatus", sonarqube.get("qualityGateStatus")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.reliabilityRating",
            measures.get("reliabilityRating")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.securityRating",
            measures.get("securityRating")),
        Map.entry(
            "catalog.metadata.integration_properties.SonarQube.measures.sqaleRating", measures.get("sqaleRating")),
        Map.entry(
            "catalog.metadata.integration_properties.SonarQube.measures.lineCoverage", measures.get("lineCoverage")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.branchCoverage",
            measures.get("branchCoverage")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.duplicatedLinesDensity",
            measures.get("duplicatedLinesDensity")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.bugs", measures.get("bugs")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.vulnerabilities",
            measures.get("vulnerabilities")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.securityHotspots",
            measures.get("securityHotspots")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.codeSmells", measures.get("codeSmells")),
        Map.entry("catalog.metadata.integration_properties.SonarQube.measures.ncloc", measures.get("ncloc")));

    assertDatapoints(entity, Map.ofEntries(sonarqubeDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_PagerDutyService() {
    Map<String, Object> pagerDuty = samplePagerDutyServiceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("PagerDuty", pagerDuty);

    List<Map.Entry<String, Object>> pagerDutyDatapoints =
        List.of(Map.entry("catalog.metadata.integration_properties.PagerDuty.identifier", pagerDuty.get("identifier")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.name", pagerDuty.get("name")),
            Map.entry(
                "catalog.metadata.integration_properties.PagerDuty.pagerdutyStatus", pagerDuty.get("pagerdutyStatus")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.onCallName", pagerDuty.get("onCallName")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.analyticsMeanSecondsToFirstAck",
                pagerDuty.get("analyticsMeanSecondsToFirstAck")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.analyticsMeanSecondsToResolve",
                pagerDuty.get("analyticsMeanSecondsToResolve")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.analyticsTotalIncidents",
                pagerDuty.get("analyticsTotalIncidents")),
            Map.entry("catalog.metadata.integration_properties.PagerDuty.teams", pagerDuty.get("teams")));

    assertDatapoints(entity, Map.ofEntries(pagerDutyDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_PagerDutyTeam() {
    Map<String, Object> pagerDuty = samplePagerDutyTeamProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("PagerDuty", pagerDuty);

    Map<String, Object> pagerDutyDatapoints = Map.of("catalog.metadata.integration_properties.PagerDuty.identifier",
        pagerDuty.get("identifier"), "catalog.metadata.integration_properties.PagerDuty.name", pagerDuty.get("name"),
        "catalog.metadata.integration_properties.PagerDuty.defaultRole", pagerDuty.get("defaultRole"));

    assertDatapoints(entity, pagerDutyDatapoints);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_HarnessCD() {
    Map<String, Object> harnessCd = sampleHarnessCDProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("HarnessCD", harnessCd);

    Map<String, Object> harnessCdDatapoints =
        Map.of("catalog.metadata.integration_properties.HarnessCD.deploymentFrequencyPerSprint",
            harnessCd.get("deploymentFrequencyPerSprint"),
            "catalog.metadata.integration_properties.HarnessCD.changeFailureRatePercent",
            harnessCd.get("changeFailureRatePercent"),
            "catalog.metadata.integration_properties.HarnessCD.averageDeploymentDurationSeconds",
            harnessCd.get("averageDeploymentDurationSeconds"));

    assertDatapoints(entity, harnessCdDatapoints);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_GCP() {
    Map<String, Object> gcp = sampleGCPProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("GCP", gcp);

    List<Map.Entry<String, Object>> gcpDatapoints =
        List.of(Map.entry("catalog.metadata.integration_properties.GCP.assetType", gcp.get("assetType")),
            Map.entry("catalog.metadata.integration_properties.GCP.displayName", gcp.get("displayName")),
            Map.entry("catalog.metadata.integration_properties.GCP.resourceName", gcp.get("resourceName")),
            Map.entry("catalog.metadata.integration_properties.GCP.state", gcp.get("state")),
            Map.entry("catalog.metadata.integration_properties.GCP.location", gcp.get("location")),
            Map.entry("catalog.metadata.integration_properties.GCP.organization", gcp.get("organization")),
            Map.entry("catalog.metadata.integration_properties.GCP.createTime", gcp.get("createTime")),
            Map.entry("catalog.metadata.integration_properties.GCP.project", gcp.get("project")));

    assertDatapoints(entity, Map.ofEntries(gcpDatapoints.toArray(Map.Entry[] ::new)));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_EntityNotCatalogEntity() {
    CatalogDataSourceLocationEntity locationEntity =
        createCatalogLocationEntity("catalog.metadata.integration_properties.Datadog.description");

    Map<String, Object> result = catalogDataSourceLocation.fetchData(
        ACCOUNT_ID, new Object(), locationEntity, Collections.singletonList(createDataFetchDTO()));

    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("Entity must be of type CatalogEntity", result.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_NullEntity() {
    CatalogDataSourceLocationEntity locationEntity =
        createCatalogLocationEntity("catalog.metadata.integration_properties.Datadog.description");

    Map<String, Object> result = catalogDataSourceLocation.fetchData(
        ACCOUNT_ID, null, locationEntity, Collections.singletonList(createDataFetchDTO()));

    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("Entity must be of type CatalogEntity", result.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MissingLeafKeyResolvesToNull() {
    Map<String, Object> datadog = new HashMap<>();
    datadog.put("description", "Payments service dashboards");
    CatalogEntity catalogEntity = createEnrichedCatalogEntity("Datadog", datadog);

    // monitorCount is not present in this entity's Datadog properties
    Map<String, Object> result = fetch(catalogEntity, "catalog.metadata.integration_properties.Datadog.monitorCount");

    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertTrue(((String) result.get(ERROR_MESSAGE_KEY)).contains(MISSING_DATA));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MissingProcessedDataResolvesToNull() {
    // Entity was never enriched, so metadata has no integration_properties key at all. JEXL would throw when
    // dereferencing the missing intermediate segment; the DSL treats that undefined-property error as a null result.
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);

    Map<String, Object> result = fetch(catalogEntity, "catalog.metadata.integration_properties.Datadog.description");

    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertTrue(((String) result.get(ERROR_MESSAGE_KEY)).contains(MISSING_DATA));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_EmptyPathUsesDefaultJexl() {
    Map<String, Object> dynatrace = sampleDynaTraceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("DynaTrace", dynatrace);

    CatalogDataSourceLocationEntity locationEntity =
        createCatalogLocationEntity("catalog.metadata.integration_properties.DynaTrace.monitorCount");
    InputValue emptyPath = new InputValue();
    emptyPath.setKey("path");
    emptyPath.setValue("\"\"");

    Map<String, Object> result = catalogDataSourceLocation.fetchData(ACCOUNT_ID, entity, locationEntity,
        Collections.singletonList(createDataFetchDTO(Collections.singletonList(emptyPath))));

    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(dynatrace.get("monitorCount"), result.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MissingPathUsesDefaultJexl() {
    Map<String, Object> dynatrace = sampleDynaTraceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("DynaTrace", dynatrace);

    Map<String, Object> result = fetch(entity, "catalog.metadata.integration_properties.DynaTrace.monitorCount");

    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(dynatrace.get("monitorCount"), result.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_PathOverrideUsesProvidedJexl() {
    Map<String, Object> dynatrace = sampleDynaTraceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("DynaTrace", dynatrace);

    CatalogDataSourceLocationEntity locationEntity =
        createCatalogLocationEntity("catalog.metadata.integration_properties.DynaTrace.monitorCount");
    InputValue pathOverride = new InputValue();
    pathOverride.setKey("path");
    pathOverride.setValue("catalog.metadata.integration_properties.DynaTrace.sloCount");

    Map<String, Object> result = catalogDataSourceLocation.fetchData(ACCOUNT_ID, entity, locationEntity,
        Collections.singletonList(createDataFetchDTO(Collections.singletonList(pathOverride))));

    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(dynatrace.get("sloCount"), result.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_QuotedPathOverrideIsStripped() {
    Map<String, Object> dynatrace = sampleDynaTraceProperties();
    CatalogEntity entity = createEnrichedCatalogEntity("DynaTrace", dynatrace);

    CatalogDataSourceLocationEntity locationEntity =
        createCatalogLocationEntity("catalog.metadata.integration_properties.DynaTrace.monitorCount");
    InputValue pathOverride = new InputValue();
    pathOverride.setKey("path");
    pathOverride.setValue("\"catalog.metadata.integration_properties.DynaTrace.problemCount\"");

    Map<String, Object> result = catalogDataSourceLocation.fetchData(ACCOUNT_ID, entity, locationEntity,
        Collections.singletonList(createDataFetchDTO(Collections.singletonList(pathOverride))));

    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(dynatrace.get("problemCount"), result.get(DSL_RESPONSE));
  }

  // Helper methods

  /** Loads representative Datadog {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleDatadogProperties() {
    return loadIntegrationProperties("scorecard/datadog-integration-properties.json");
  }

  /** Loads representative DynaTrace {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleDynaTraceProperties() {
    return loadIntegrationProperties("scorecard/dynatrace-integration-properties.json");
  }

  /** Loads representative GitHub {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleGitHubProperties() {
    return loadIntegrationProperties("scorecard/github-integration-properties.json");
  }

  /** Loads representative BitbucketCloud {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleBitbucketCloudProperties() {
    return loadIntegrationProperties("scorecard/bitbucketcloud-integration-properties.json");
  }

  /** Loads representative SonarQube {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleSonarQubeProperties() {
    return loadIntegrationProperties("scorecard/sonarqube-integration-properties.json");
  }

  /** Loads representative PagerDuty service {@code integration_properties} from test resources. */
  private static Map<String, Object> samplePagerDutyServiceProperties() {
    return loadIntegrationProperties("scorecard/pagerduty-service-integration-properties.json");
  }

  /** Loads representative PagerDuty team {@code integration_properties} from test resources. */
  private static Map<String, Object> samplePagerDutyTeamProperties() {
    return loadIntegrationProperties("scorecard/pagerduty-team-integration-properties.json");
  }

  /** Loads representative HarnessCD {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleHarnessCDProperties() {
    return loadIntegrationProperties("scorecard/harnesscd-integration-properties.json");
  }

  /** Loads representative GCP {@code integration_properties} from test resources. */
  private static Map<String, Object> sampleGCPProperties() {
    return loadIntegrationProperties("scorecard/gcp-integration-properties.json");
  }

  private static Map<String, Object> loadIntegrationProperties(String resource) {
    try (InputStream in = CatalogDataSourceLocationTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull("missing test resource " + resource, in);
      return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load " + resource, e);
    }
  }

  private void assertDatapoints(CatalogEntity entity, Map<String, Object> jexlToExpected) {
    jexlToExpected.forEach((jexl, expected) -> {
      Map<String, Object> result = fetch(entity, jexl);
      assertNotNull("null result for " + jexl, result);
      assertTrue("expected DSL_RESPONSE for " + jexl + " but got: " + result, result.containsKey(DSL_RESPONSE));
      assertEquals("mismatch for " + jexl, expected, result.get(DSL_RESPONSE));
    });
  }

  private Map<String, Object> fetch(CatalogEntity catalogEntity, String jexl) {
    CatalogDataSourceLocationEntity locationEntity = createCatalogLocationEntity(jexl);
    return catalogDataSourceLocation.fetchData(
        ACCOUNT_ID, catalogEntity, locationEntity, Collections.singletonList(createDataFetchDTO()));
  }

  private CatalogDataSourceLocationEntity createCatalogLocationEntity(String jexl) {
    CatalogDataSourceLocationEntity entity = new CatalogDataSourceLocationEntity();
    entity.setJexl(jexl);
    entity.setIdentifier("test-catalog-location");
    entity.setType(DataSourceLocationType.CATALOG);
    return entity;
  }

  /**
   * Builds a catalog entity whose {@code provider} integration properties are stored under
   * {@code decorator._processed_data.metadata.integration_properties.<provider>}, mirroring how the integration
   * manager consumer enriches entities. These become resolvable via
   * {@code catalog.metadata.integration_properties.<provider>.<key>} through {@code getDecoratedEntityMap()}.
   */
  private CatalogEntity createEnrichedCatalogEntity(String provider, Map<String, Object> providerProperties) {
    InlineCatalogEntity entity = (InlineCatalogEntity) createCatalogEntity(ENTITY_NAME, ENTITY_KIND);

    Map<String, Object> integrationProperties = new HashMap<>();
    integrationProperties.put(provider, providerProperties);

    Map<String, Object> processedMetadata = new HashMap<>();
    processedMetadata.put("integration_properties", integrationProperties);

    Map<String, Object> processedData = new HashMap<>();
    processedData.put("metadata", processedMetadata);

    Map<String, Object> decorator = new HashMap<>();
    decorator.put(PROCESSED_DATA, processedData);

    entity.setDecorator(decorator);
    return entity;
  }

  private CatalogEntity createCatalogEntity(String name, String kind) {
    Map<String, Object> yamlContent = new HashMap<>();
    yamlContent.put("apiVersion", "v1");
    yamlContent.put("kind", kind);
    yamlContent.put("identifier", name);
    yamlContent.put("metadata", new HashMap<>(Map.of("name", name)));

    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(ACCOUNT_ID);
    entity.setApiVersion("v1");
    entity.setKind(kind);
    entity.setName(name);
    entity.setIdentifier(name);
    entity.setParentUniqueId(PARENT_UNIQUE_ID);
    entity.setUniqueId(UNIQUE_ID);
    entity.setMetadata(new HashMap<>(Map.of("name", name)));
    entity.setYaml(YamlUtils.writeObjectAsYaml(yamlContent));
    return entity;
  }

  private DataFetchDTO createDataFetchDTO() {
    return createDataFetchDTO(null);
  }

  private DataFetchDTO createDataFetchDTO(List<InputValue> inputValues) {
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).inputValues(inputValues).build();
  }
}
