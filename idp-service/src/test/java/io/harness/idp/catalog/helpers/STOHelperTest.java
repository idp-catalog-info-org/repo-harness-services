/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.rule.Owner;
import io.harness.sto.ArtifactInfo;
import io.harness.sto.ScanResults;
import io.harness.sto.Scope;
import io.harness.sto.VulnerabilityScan;
import io.harness.sto.beans.FrontendIssueCounts;
import io.harness.sto.beans.IssueCountsRequestDto;
import io.harness.sto.beans.ScanIssueCountsWithExecutionInfo;
import io.harness.sto.remote.STOServiceRestClient;
import io.harness.stoserviceclient.STOServiceUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class STOHelperTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_ORG_IDENTIFIER = "testOrg";
  private static final String TEST_PROJECT_IDENTIFIER = "testProject";
  private static final String TEST_IDENTIFIER = "testIdentifier";
  private static final String TEST_REPO_URL = "https://github.com/harness/Employee-Management-System";
  private static final String TEST_REPO_TARGET_NAME = "harness/employee-management-system";
  private static final String TEST_IMAGE_TARGET_NAME = "harness/idp-catalog";

  @InjectMocks private STOHelper stoHelper;
  @Mock private STOServiceRestClient stoServiceRestClient;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private STOServiceUtils stoServiceUtils;
  @Mock private CatalogServiceHelper catalogServiceHelper;

  @Captor private ArgumentCaptor<IssueCountsRequestDto> dtoCaptor;
  @Captor private ArgumentCaptor<List<CatalogEntity>> catalogEntitiesCaptor;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(stoServiceUtils.getSTOServiceToken(anyString(), anyList())).thenReturn("token123");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPopulateSTOData() throws IOException {
    CatalogEntity catalogEntity = mockCatalogEntity();
    List<ScanIssueCountsWithExecutionInfo> mockResponse = mockScanIssueCountsResponse();
    mockRestResponseForScanSummary(mockResponse);

    stoHelper.populateSTOData(catalogEntity);
    verify(stoServiceRestClient)
        .getArtifactScanSummary(anyString(), eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ORG_IDENTIFIER),
            eq(TEST_PROJECT_IDENTIFIER), dtoCaptor.capture());
    verify(catalogEntityRepository).save(catalogEntity);

    IssueCountsRequestDto capturedDto = dtoCaptor.getValue();
    assertThat(capturedDto.getTargetVariants()).isNotEmpty();

    Map<String, Object> decorator = catalogEntity.getDecorator();
    Map<String, Object> processedData = (Map<String, Object>) decorator.get("_processed_data");
    Map<String, Object> metadata = (Map<String, Object>) processedData.get("metadata");
    Map<String, Object> sto = (Map<String, Object>) metadata.get("sto");
    assertThat(sto).isNotNull();
    assertThat(sto.containsKey(TEST_IMAGE_TARGET_NAME)).isTrue();
    assertThat(sto.containsKey(TEST_REPO_TARGET_NAME)).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcessEvent() {
    VulnerabilityScan vulnerabilityScan = mockVulnerabilityScan();
    CatalogEntity catalogEntity = mockCatalogEntity();
    List<CatalogEntity> catalogEntities = List.of(catalogEntity);

    when(catalogEntityRepository.getEntitiesForArbitraryFields(anyString(), any(), anyString()))
        .thenReturn(catalogEntities);

    stoHelper.processEvent(vulnerabilityScan);

    verify(catalogEntityRepository).saveAll(catalogEntitiesCaptor.capture());

    List<CatalogEntity> savedEntities = catalogEntitiesCaptor.getValue();
    assertThat(savedEntities).hasSize(1);

    Map<String, Object> decorator = savedEntities.get(0).getDecorator();
    Map<String, Object> processedData = (Map<String, Object>) decorator.get("_processed_data");
    Map<String, Object> metadata = (Map<String, Object>) processedData.get("metadata");
    Map<String, Object> sto = (Map<String, Object>) metadata.get("sto");

    assertThat(sto).isNotNull();
    assertThat(sto.containsKey(TEST_REPO_TARGET_NAME)).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testGetSTOTestTargets() {
    CatalogEntity catalogEntity = mockCatalogEntity();
    Map<String, List<Pair<String, String>>> result = stoHelper.getSTOTestTargets(catalogEntity);
    assertThat(result).isNotNull().isNotEmpty();
    String scopeKey = TEST_ORG_IDENTIFIER + "." + TEST_PROJECT_IDENTIFIER;
    assertThat(result.containsKey(scopeKey)).isTrue();

    List<Pair<String, String>> targets = result.get(scopeKey);
    assertThat(targets).hasSize(2);
    assertThat(targets.stream().anyMatch(target -> target.getLeft().equals(TEST_IMAGE_TARGET_NAME))).isTrue();
    assertThat(targets.stream().anyMatch(target -> target.getLeft().equals(TEST_REPO_TARGET_NAME))).isTrue();
  }

  private CatalogEntity mockCatalogEntity() {
    Map<String, Object> metadata = new HashMap<>();
    Map<String, Object> annotations = new HashMap<>();

    List<Map<String, String>> stoTestTargets = new ArrayList<>();
    Map<String, String> testTarget = new HashMap<>();
    testTarget.put("name", "harness/IDP-Catalog");
    testTarget.put("variant", "latest");
    stoTestTargets.add(testTarget);
    annotations.put("harness.io/sto-test-target", stoTestTargets);
    metadata.put("annotations", annotations);

    String entityYaml = "apiVersion: harness.io/v1\n"
        + "kind: component\n"
        + "type: service\n"
        + "identifier: testIdentifier\n"
        + "name: testIdentifier\n"
        + "accountIdentifier: testAccount123\n"
        + "orgIdentifier: testOrg\n"
        + "projectIdentifier: testProject\n"
        + "owner: group:account/_account_all_users\n"
        + "metadata:\n"
        + "  annotations:\n"
        + "    backstage.io/source-location: url:https://github.com/harness/Employee-Management-System\n"
        + "    harness.io/sto-test-target:\n"
        + "    - name: harness/IDP-Catalog\n"
        + "      variant: latest\n";

    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .orgIdentifier(TEST_ORG_IDENTIFIER)
        .projectIdentifier(TEST_PROJECT_IDENTIFIER)
        .identifier(TEST_IDENTIFIER)
        .kind(COMPONENT_KIND)
        .type("service")
        .sourceLocation(TEST_REPO_URL)
        .metadata(metadata)
        .yaml(entityYaml)
        .build();
  }

  private List<ScanIssueCountsWithExecutionInfo> mockScanIssueCountsResponse() {
    ScanIssueCountsWithExecutionInfo info = ScanIssueCountsWithExecutionInfo.builder().build();
    info.setTargetName(TEST_REPO_TARGET_NAME);

    List<FrontendIssueCounts> scanners = new ArrayList<>();
    FrontendIssueCounts scannerCounts = FrontendIssueCounts.builder().build();
    scannerCounts.setScanner("scanner1");
    scannerCounts.setCritical(1);
    scannerCounts.setHigh(2);
    scannerCounts.setMedium(3);
    scannerCounts.setLow(4);
    scannerCounts.setInfo(5);
    scanners.add(scannerCounts);

    info.setScanners(scanners);

    ScanIssueCountsWithExecutionInfo info1 = ScanIssueCountsWithExecutionInfo.builder().build();
    info1.setTargetName(TEST_IMAGE_TARGET_NAME);
    info1.setScanners(scanners);
    return List.of(info, info1);
  }

  private VulnerabilityScan mockVulnerabilityScan() {
    Scope scope = Scope.newBuilder()
                      .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                      .setOrgIdentifier(TEST_ORG_IDENTIFIER)
                      .setProjectIdentifier(TEST_PROJECT_IDENTIFIER)
                      .build();

    ArtifactInfo artifactInfo = ArtifactInfo.newBuilder()
                                    .setTargetName(TEST_REPO_TARGET_NAME)
                                    .setVariant("")
                                    .setIsBaseline(true)
                                    .setTargetType("repository")
                                    .build();

    ScanResults scanResults = ScanResults.newBuilder()
                                  .setTool("snyk")
                                  .setCritical(2)
                                  .setHigh(2)
                                  .setMedium(1)
                                  .setLow(1)
                                  .setInfo(0)
                                  .setTotal(6)
                                  .build();
    return VulnerabilityScan.newBuilder()
        .setScope(scope)
        .setArtifactInfo(artifactInfo)
        .setScanResults(scanResults)
        .build();
  }

  private void mockRestResponseForScanSummary(List<ScanIssueCountsWithExecutionInfo> response) throws IOException {
    Response<List<ScanIssueCountsWithExecutionInfo>> restResponse = Response.success(response);
    Call<List<ScanIssueCountsWithExecutionInfo>> artifactScanSummaryCall = mock(Call.class);
    when(artifactScanSummaryCall.execute()).thenReturn(restResponse);
    when(stoServiceRestClient.getArtifactScanSummary(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(artifactScanSummaryCall);
  }
}
