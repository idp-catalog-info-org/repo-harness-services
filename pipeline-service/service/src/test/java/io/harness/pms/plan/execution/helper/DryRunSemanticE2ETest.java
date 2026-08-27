/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.execution.ExecutionPlan;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidator;
import io.harness.pms.plan.execution.dryrun.semantic.rules.CloneCodebaseSanityRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.CloudCiDelegateConnectorRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.ConnectorTypeRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.ReferencedEntitiesExistRule;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineResponseBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;
import io.harness.utils.PmsFeatureFlagService;

import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * End-to-end integration test for dry-run semantic validation. Wires a real {@link SemanticValidator}
 * with the four real rules into {@link DryRunHelper}, drives demo-style
 * "Sales Quota" pipeline YAMLs through step 3.5, and asserts the aggregated findings + Plan A's
 * severity-aware {@code isValid}. This also serves as the Plan A verification (severity gating).
 */
public class DryRunSemanticE2ETest extends CategoryTest {
  private static final String ACCOUNT_ID = "acct";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";
  private static final String PIPELINE_ID = "sales_quota";

  private ConnectorResourceClient connectorClient;
  private PmsFeatureFlagService pmsFeatureFlagService;
  private DryRunHelper dryRunHelper;

  @Before
  public void setUp() {
    connectorClient = mock(ConnectorResourceClient.class);
    pmsFeatureFlagService = mock(PmsFeatureFlagService.class);

    Set<SemanticRule> rules = new LinkedHashSet<>();
    rules.add(new ReferencedEntitiesExistRule());
    rules.add(new ConnectorTypeRule());
    rules.add(new CloneCodebaseSanityRule());
    rules.add(new CloudCiDelegateConnectorRule());
    SemanticValidator semanticValidator = new SemanticValidator(connectorClient, rules);

    // @AllArgsConstructor field order: executionHelper, pipelineExecutor, pmsFeatureFlagService,
    // filterCreatorMergeService, pipelineGovernanceService, pipelineRbacHelper, principalInfoHelper,
    // metricService, semanticValidator, pmsPipelineServiceHelper.
    dryRunHelper = new DryRunHelper(mock(ExecutionHelper.class), mock(PipelineExecutor.class), pmsFeatureFlagService,
        mock(FilterCreatorMergeService.class), mock(PipelineGovernanceService.class), mock(PipelineRbacHelper.class),
        mock(PrincipalInfoHelper.class), mock(MetricService.class), semanticValidator,
        mock(PMSPipelineServiceHelper.class));
  }

  // ---- Demo YAMLs -----------------------------------------------------------------------------

  /** Valid demo: Harness Code clone (empty connectorRef + repoName), Cloud CI, docker push. */
  private static final String VALID_HARNESS_CODE_YAML = "pipeline:\n"
      + "  identifier: " + PIPELINE_ID + "\n"
      + "  properties:\n"
      + "    ci:\n"
      + "      codebase:\n"
      + "        repoName: sales-quota-app\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: build\n"
      + "        type: CI\n"
      + "        spec:\n"
      + "          cloneCodebase: true\n"
      + "          runtime:\n"
      + "            type: Cloud\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: push\n"
      + "                  type: BuildAndPushDockerRegistry\n"
      + "                  spec:\n"
      + "                    connectorRef: account.harnessImage\n";

  /** CI clone from a real git connectorRef that will not resolve (Rule 1 must flag it). */
  private static final String MISSING_CODEBASE_CONNECTOR_YAML = "pipeline:\n"
      + "  identifier: " + PIPELINE_ID + "\n"
      + "  properties:\n"
      + "    ci:\n"
      + "      codebase:\n"
      + "        connectorRef: account.myGitConnector\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: build\n"
      + "        type: CI\n"
      + "        spec:\n"
      + "          cloneCodebase: true\n"
      + "          runtime:\n"
      + "            type: Cloud\n"
      + "          execution:\n"
      + "            steps: []\n";

  /** CI codebase slot pointing at a connector that resolves as DOCKER (Rule 2 must flag it). */
  private static final String CODEBASE_WRONG_TYPE_YAML = "pipeline:\n"
      + "  identifier: " + PIPELINE_ID + "\n"
      + "  properties:\n"
      + "    ci:\n"
      + "      codebase:\n"
      + "        connectorRef: account.dockerAsGit\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: build\n"
      + "        type: CI\n"
      + "        spec:\n"
      + "          cloneCodebase: true\n"
      + "          runtime:\n"
      + "            type: Cloud\n"
      + "          execution:\n"
      + "            steps: []\n";

  /** Cloud CI stage with a delegate-routed docker push connector (Rule 4 must flag it). */
  private static final String CLOUD_CI_DELEGATE_CONNECTOR_YAML = "pipeline:\n"
      + "  identifier: " + PIPELINE_ID + "\n"
      + "  properties:\n"
      + "    ci:\n"
      + "      codebase:\n"
      + "        repoName: sales-quota-app\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: build\n"
      + "        type: CI\n"
      + "        spec:\n"
      + "          cloneCodebase: true\n"
      + "          runtime:\n"
      + "            type: Cloud\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: push\n"
      + "                  type: BuildAndPushDockerRegistry\n"
      + "                  spec:\n"
      + "                    connectorRef: account.harnessImage\n";

  /** V1: bare stage, Cloud runtime, a docker container connector that will not resolve (Rule 1). */
  private static final String V1_MISSING_CONNECTOR_YAML = "pipeline:\n"
      + "  stages:\n"
      + "    - id: build\n"
      + "      runtime: cloud\n"
      + "      steps:\n"
      + "        - id: push\n"
      + "          run:\n"
      + "            container:\n"
      + "              connector: account.missingV1\n";

  // ---- Fixtures -------------------------------------------------------------------------------

  private ExecutionPlan planWith(String yaml) {
    return ExecutionPlan.builder()
        .planExecutionMetadataWithContext(
            PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(yaml).build())
        .build();
  }

  private EntityDetailProtoDTO accountConnectorEntity(String identifier) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(EntityTypeProtoEnum.CONNECTORS)
        .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                              .setScope(ScopeProtoEnum.ACCOUNT)
                              .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                              .setIdentifier(StringValue.of(identifier))
                              .build())
        .build();
  }

  private ConnectorResponseDTO dockerConnectorResponse(String identifier, Boolean executeOnDelegate) {
    ConnectorInfoDTO info = ConnectorInfoDTO.builder()
                                .identifier(identifier)
                                .accountIdentifier(ACCOUNT_ID)
                                .connectorType(ConnectorType.DOCKER)
                                .connectorConfig(DockerConnectorDTO.builder()
                                                     .dockerRegistryUrl("https://index.docker.io")
                                                     .executeOnDelegate(executeOnDelegate)
                                                     .build())
                                .build();
    return ConnectorResponseDTO.builder().connector(info).build();
  }

  private DryRunPipelineResponseBody responseFor(List<DryRunPipelineValidationResult> results) {
    return dryRunHelper.buildValidationResponseForTest(PIPELINE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, results);
  }

  private void ffOn() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(true);
  }

  // ---- Tests ----------------------------------------------------------------------------------

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void validHarnessCodePipeline_noSemanticFinding() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      // account.harnessImage resolves as a Cloud-safe docker connector (executeOnDelegate=false).
      mocked.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(List.of(dockerConnectorResponse("harnessImage", Boolean.FALSE)));

      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(VALID_HARNESS_CODE_YAML),
          List.of(accountConnectorEntity("harnessImage")), results,
          PipelineEntity.builder().harnessVersion("0").build());
    }
    assertThat(results).isEmpty();
    assertThat(responseFor(results).isIsValid()).isTrue();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void missingCodebaseConnector_semanticError_isValidFalse() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      // The referenced git connector does not resolve.
      mocked.when(() -> NGRestUtils.getResponse(any())).thenReturn(List.of());

      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(MISSING_CODEBASE_CONNECTOR_YAML),
          List.of(accountConnectorEntity("myGitConnector")), results,
          PipelineEntity.builder().harnessVersion("0").build());
    }
    assertThat(results).anyMatch(r
        -> "SEMANTIC".equals(r.getValidationType()) && "ERROR".equals(r.getSeverity())
            && "CONNECTOR".equals(r.getEntityType()) && "account.myGitConnector".equals(r.getEntityIdentifier()));
    assertThat(responseFor(results).isIsValid()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void codebaseConnectorWrongType_semanticError_isValidFalse() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      // The codebase slot expects a git connector, but this one resolves as DOCKER.
      mocked.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(List.of(dockerConnectorResponse("dockerAsGit", Boolean.FALSE)));

      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(CODEBASE_WRONG_TYPE_YAML),
          List.of(accountConnectorEntity("dockerAsGit")), results,
          PipelineEntity.builder().harnessVersion("0").build());
    }
    assertThat(results).anyMatch(r
        -> "SEMANTIC".equals(r.getValidationType()) && "ERROR".equals(r.getSeverity())
            && "CONNECTOR".equals(r.getEntityType()) && "account.dockerAsGit".equals(r.getEntityIdentifier()));
    assertThat(responseFor(results).isIsValid()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloudCiDelegateConnector_semanticError_isValidFalse() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      // Cloud CI stage, but the docker push connector is delegate-routed (executeOnDelegate=true).
      mocked.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(List.of(dockerConnectorResponse("harnessImage", Boolean.TRUE)));

      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(CLOUD_CI_DELEGATE_CONNECTOR_YAML),
          List.of(accountConnectorEntity("harnessImage")), results,
          PipelineEntity.builder().harnessVersion("0").build());
    }
    assertThat(results).anyMatch(r
        -> "SEMANTIC".equals(r.getValidationType()) && "ERROR".equals(r.getSeverity())
            && "CONNECTOR".equals(r.getEntityType()) && "account.harnessImage".equals(r.getEntityIdentifier())
            && r.getErrorMessage().contains("delegate"));
    assertThat(responseFor(results).isIsValid()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1MissingConnector_semanticError_isValidFalse() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any())).thenReturn(List.of());

      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(V1_MISSING_CONNECTOR_YAML), List.of(),
          results, PipelineEntity.builder().harnessVersion("1").build());
    }
    assertThat(results).anyMatch(r
        -> "SEMANTIC".equals(r.getValidationType()) && "ERROR".equals(r.getSeverity())
            && "account.missingV1".equals(r.getEntityIdentifier()));
    assertThat(responseFor(results).isIsValid()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1ValidCloudPipeline_noSemanticFinding() {
    ffOn();
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(List.of(dockerConnectorResponse("cloudDocker", Boolean.FALSE)));

      String yaml = "pipeline:\n"
          + "  stages:\n"
          + "    - id: build\n"
          + "      runtime: cloud\n"
          + "      steps:\n"
          + "        - id: push\n"
          + "          run:\n"
          + "            container:\n"
          + "              connector: account.cloudDocker\n";
      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(yaml), List.of(), results,
          PipelineEntity.builder().harnessVersion("1").build());
    }
    assertThat(results).isEmpty();
    assertThat(responseFor(results).isIsValid()).isTrue();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void ffOff_noSemanticResults_regressionGuard() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(false);
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(List.of(dockerConnectorResponse("dockerAsGit", Boolean.FALSE)));

      // Same broken YAML that produces an ERROR when the FF is on.
      dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, planWith(CODEBASE_WRONG_TYPE_YAML),
          List.of(accountConnectorEntity("dockerAsGit")), results,
          PipelineEntity.builder().harnessVersion("0").build());
    }
    assertThat(results).isEmpty();
    assertThat(responseFor(results).isIsValid()).isTrue();
  }
}
