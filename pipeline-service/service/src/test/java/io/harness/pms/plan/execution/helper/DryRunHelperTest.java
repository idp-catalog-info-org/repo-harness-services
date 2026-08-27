/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.FJUNIOR;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.execution.ExecutionPlan;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.metrics.service.api.MetricService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidator;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineRequestBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineResponseBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;
import io.harness.utils.PmsFeatureFlagService;

import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DryRunHelperTest {
  @Mock private ExecutionHelper executionHelper;
  @Mock private PipelineExecutor pipelineExecutor;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private FilterCreatorMergeService filterCreatorMergeService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private PipelineGovernanceService pipelineGovernanceService;
  @Mock private PrincipalInfoHelper principalInfoHelper;
  @Mock private MetricService metricService;
  @Mock private SemanticValidator semanticValidator;
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @InjectMocks private DryRunHelper dryRunHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String BRANCH = "main";
  private static final String PIPELINE_YAML = "pipeline:\n  name: test\n  identifier: " + PIPELINE_ID;

  private PipelineEntity pipelineEntity;
  private ScopeInfo scopeInfo;
  private DryRunPipelineRequestBody requestBody;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_ID)
                         .projectIdentifier(PROJECT_ID)
                         .identifier(PIPELINE_ID)
                         .name("Test Pipeline")
                         .yaml(PIPELINE_YAML)
                         .build();

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();

    requestBody = new DryRunPipelineRequestBody();
    requestBody.setPipelineIdentifier(PIPELINE_ID);
    requestBody.setPipelineYaml(PIPELINE_YAML);
    requestBody.setBranch(BRANCH);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformRBACChecks_NoReferredEntities() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    when(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .thenReturn(ExecutionPrincipalInfo.newBuilder().setPrincipal("test-user").build());

    // Test with empty list
    dryRunHelper.performRBACChecks(ACCOUNT_ID, new ArrayList<>(), results);

    assertThat(results).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformRBACChecks_Success() {
    IdentifierRefProtoDTO identifierRef = IdentifierRefProtoDTO.newBuilder()
                                              .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                              .setOrgIdentifier(StringValue.of(ORG_ID))
                                              .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                              .setIdentifier(StringValue.of("connectorId"))
                                              .build();

    EntityDetailProtoDTO entityDetail = EntityDetailProtoDTO.newBuilder()
                                            .setIdentifierRef(identifierRef)
                                            .setType(EntityTypeProtoEnum.CONNECTORS)
                                            .build();

    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    when(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .thenReturn(ExecutionPrincipalInfo.newBuilder().setPrincipal("test-user").build());

    // No exception thrown means success
    dryRunHelper.performRBACChecks(ACCOUNT_ID, List.of(entityDetail), results);

    assertThat(results).isEmpty();
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(Ambiance.class), anySet());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformRBACChecks_WithAccessDenied() {
    IdentifierRefProtoDTO identifierRef = IdentifierRefProtoDTO.newBuilder()
                                              .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                              .setOrgIdentifier(StringValue.of(ORG_ID))
                                              .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                              .setIdentifier(StringValue.of("connectorId"))
                                              .build();

    EntityDetailProtoDTO entityDetail = EntityDetailProtoDTO.newBuilder()
                                            .setIdentifierRef(identifierRef)
                                            .setType(EntityTypeProtoEnum.CONNECTORS)
                                            .build();

    PermissionCheckDTO failedCheck = PermissionCheckDTO.builder()
                                         .permission("core_connector_access")
                                         .resourceType("CONNECTOR")
                                         .resourceIdentifier("connectorId")
                                         .build();

    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    when(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .thenReturn(ExecutionPrincipalInfo.newBuilder().setPrincipal("test-user").build());

    doThrow(new NGAccessDeniedException(
                "Access denied", EnumSet.noneOf(WingsException.ReportTarget.class), List.of(failedCheck)))
        .when(pipelineRbacHelper)
        .checkRuntimePermissions(any(Ambiance.class), anySet());

    dryRunHelper.performRBACChecks(ACCOUNT_ID, List.of(entityDetail), results);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getValidationType()).isEqualTo("PERMISSIONS");
    assertThat(results.get(0).getEntityType()).isEqualTo("CONNECTOR");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo("connectorId");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_NoPolicyViolation() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();

    when(pipelineGovernanceService.validateGovernanceRules(
             anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(governanceMetadata);

    // Create ExecutionPlan with resolved YAML
    ExecutionPlan executionPlan =
        ExecutionPlan.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
            .build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    assertThat(results).isEmpty();
    // Verify both OnSave and OnRun policies are evaluated
    verify(pipelineGovernanceService, times(2))
        .validateGovernanceRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity), eq(PIPELINE_YAML), anyString());
    verify(pipelineGovernanceService, times(1))
        .validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity),
            eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE));
    verify(pipelineGovernanceService, times(1))
        .validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity),
            eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_WithPolicyViolation() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();
    PolicyMetadata policyMetadata = PolicyMetadata.newBuilder().addDenyMessages("Policy violation message").build();
    PolicySetMetadata policySetMetadata = PolicySetMetadata.newBuilder()
                                              .setIdentifier("policySetId")
                                              .setPolicySetName("Test Policy Set")
                                              .setDeny(true)
                                              .addPolicyMetadata(policyMetadata)
                                              .build();
    GovernanceMetadata governanceMetadata =
        GovernanceMetadata.newBuilder().setDeny(true).addDetails(policySetMetadata).build();

    when(pipelineGovernanceService.validateGovernanceRules(
             anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(governanceMetadata);

    // Create ExecutionPlan with resolved YAML
    ExecutionPlan executionPlan =
        ExecutionPlan.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
            .build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    // Both OnSave and OnRun policies fail, so expect 2 validation results
    assertThat(results).hasSize(2);

    // First result is from OnSave policy
    assertThat(results.get(0).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(0).getErrorMessage()).containsIgnoringCase("onsave");
    assertThat(results.get(0).getErrorMessage()).contains("Test Policy Set");
    assertThat(results.get(0).getErrorMessage()).contains("Policy violation message");

    // Second result is from OnRun policy
    assertThat(results.get(1).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(1).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(1).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(1).getErrorMessage()).containsIgnoringCase("onrun");
    assertThat(results.get(1).getErrorMessage()).contains("Test Policy Set");
    assertThat(results.get(1).getErrorMessage()).contains("Policy violation message");

    // Verify both policy types were evaluated
    verify(pipelineGovernanceService, times(2))
        .validateGovernanceRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity), eq(PIPELINE_YAML), anyString());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_WithInvalidRequestException() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    when(pipelineGovernanceService.validateGovernanceRules(
             anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .thenThrow(new InvalidRequestException("Invalid policy configuration"));

    // Create ExecutionPlan with resolved YAML
    ExecutionPlan executionPlan =
        ExecutionPlan.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
            .build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    // Both OnSave and OnRun policy evaluation throw exception, so expect 2 validation results
    assertThat(results).hasSize(2);

    // First result is from OnSave policy
    assertThat(results.get(0).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(0).getErrorMessage()).containsIgnoringCase("onsave");
    assertThat(results.get(0).getErrorMessage()).contains("Invalid policy configuration");

    // Second result is from OnRun policy
    assertThat(results.get(1).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(1).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(1).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(1).getErrorMessage()).containsIgnoringCase("onrun");
    assertThat(results.get(1).getErrorMessage()).contains("Invalid policy configuration");

    // Verify both policy types were attempted
    verify(pipelineGovernanceService, times(2))
        .validateGovernanceRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity), eq(PIPELINE_YAML), anyString());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_WithMultiplePolicyViolations() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    PolicyMetadata policyMetadata1 = PolicyMetadata.newBuilder().addDenyMessages("Violation 1").build();
    PolicySetMetadata policySetMetadata1 = PolicySetMetadata.newBuilder()
                                               .setIdentifier("policySet1")
                                               .setPolicySetName("Policy Set 1")
                                               .setDeny(true)
                                               .addPolicyMetadata(policyMetadata1)
                                               .build();

    PolicyMetadata policyMetadata2 = PolicyMetadata.newBuilder().addDenyMessages("Violation 2").build();
    PolicySetMetadata policySetMetadata2 = PolicySetMetadata.newBuilder()
                                               .setIdentifier("policySet2")
                                               .setPolicySetName("Policy Set 2")
                                               .setDeny(true)
                                               .addPolicyMetadata(policyMetadata2)
                                               .build();

    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder()
                                                .setDeny(true)
                                                .addDetails(policySetMetadata1)
                                                .addDetails(policySetMetadata2)
                                                .build();

    when(pipelineGovernanceService.validateGovernanceRules(
             anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(governanceMetadata);

    // Create ExecutionPlan with resolved YAML
    ExecutionPlan executionPlan =
        ExecutionPlan.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
            .build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    // 2 policy sets × 2 policy types (OnSave, OnRun) = 4 validation results
    assertThat(results).hasSize(4);

    // OnSave violations
    assertThat(results.get(0).getErrorMessage()).containsIgnoringCase("onsave");
    assertThat(results.get(0).getErrorMessage()).contains("Policy Set 1");
    assertThat(results.get(1).getErrorMessage()).containsIgnoringCase("onsave");
    assertThat(results.get(1).getErrorMessage()).contains("Policy Set 2");

    // OnRun violations
    assertThat(results.get(2).getErrorMessage()).containsIgnoringCase("onrun");
    assertThat(results.get(2).getErrorMessage()).contains("Policy Set 1");
    assertThat(results.get(3).getErrorMessage()).containsIgnoringCase("onrun");
    assertThat(results.get(3).getErrorMessage()).contains("Policy Set 2");

    // Verify both policy types were evaluated
    verify(pipelineGovernanceService, times(2))
        .validateGovernanceRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity), eq(PIPELINE_YAML), anyString());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_OnSavePassesOnRunFails() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    // OnSave passes
    GovernanceMetadata onSaveMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();

    // OnRun fails
    PolicyMetadata policyMetadata = PolicyMetadata.newBuilder().addDenyMessages("OnRun policy violation").build();
    PolicySetMetadata policySetMetadata = PolicySetMetadata.newBuilder()
                                              .setIdentifier("onRunPolicySet")
                                              .setPolicySetName("OnRun Policy Set")
                                              .setDeny(true)
                                              .addPolicyMetadata(policyMetadata)
                                              .build();
    GovernanceMetadata onRunMetadata =
        GovernanceMetadata.newBuilder().setDeny(true).addDetails(policySetMetadata).build();

    // Mock different responses for OnSave vs OnRun
    when(pipelineGovernanceService.validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH),
             eq(pipelineEntity), eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE)))
        .thenReturn(onSaveMetadata);
    when(pipelineGovernanceService.validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH),
             eq(pipelineEntity), eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN)))
        .thenReturn(onRunMetadata);

    // Create ExecutionPlan with resolved YAML
    ExecutionPlan executionPlan =
        ExecutionPlan.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
            .build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    // Only OnRun policy fails, so expect 1 validation result
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(0).getErrorMessage()).containsIgnoringCase("onrun");
    assertThat(results.get(0).getErrorMessage()).contains("OnRun Policy Set");
    assertThat(results.get(0).getErrorMessage()).contains("OnRun policy violation");
    assertThat(results.get(0).getHint()).containsIgnoringCase("onrun");

    // Verify both policy types were evaluated
    verify(pipelineGovernanceService, times(1))
        .validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity),
            eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE));
    verify(pipelineGovernanceService, times(1))
        .validateGovernanceRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(BRANCH), eq(pipelineEntity),
            eq(PIPELINE_YAML), eq(OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_WithNullExecutionPlan() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    // Pass null execution plan (extractResolvedYaml will return null)
    dryRunHelper.performPolicyChecks(pipelineEntity, null, BRANCH, results);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(0).getErrorMessage()).contains("Policy checks skipped");
    assertThat(results.get(0).getErrorMessage()).contains("Resolved pipeline YAML is not available");
    assertThat(results.get(0).getHint()).contains("Ensure the pipeline has valid YAML");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformPolicyChecks_WithNullResolvedYaml() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    // Create ExecutionPlan without planExecutionMetadataWithContext (so resolvedYaml will be null)
    ExecutionPlan executionPlan = ExecutionPlan.builder().build();

    dryRunHelper.performPolicyChecks(pipelineEntity, executionPlan, BRANCH, results);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getValidationType()).isEqualTo("POLICY");
    assertThat(results.get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(results.get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(results.get(0).getErrorMessage()).contains("Policy checks skipped");
    assertThat(results.get(0).getErrorMessage()).contains("Resolved pipeline YAML is not available");
    assertThat(results.get(0).getHint()).contains("Ensure the pipeline has valid YAML");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testStartDryRun_WithFilterCreatorException() throws Exception {
    when(executionHelper.fetchPipelineEntity(anyString(), anyString(), anyString(), anyString(), any(ScopeInfo.class)))
        .thenReturn(pipelineEntity);
    when(filterCreatorMergeService.getReferredEntities(any()))
        .thenThrow(new FilterCreatorException("Failed to resolve template references"));
    when(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .thenReturn(ExecutionPrincipalInfo.newBuilder().setPrincipal("test-user").build());
    when(pipelineGovernanceService.validateGovernanceRules(
             anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(GovernanceMetadata.newBuilder().setDeny(false).build());

    DryRunPipelineResponseBody response =
        dryRunHelper.startDryRun(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, requestBody, false);

    assertThat(response).isNotNull();
    assertThat(response.isIsValid()).isFalse();
    assertThat(response.getValidation()).hasSize(1);
    assertThat(response.getValidation().get(0).getValidationType()).isEqualTo("REFERRED_ENTITIES");
    assertThat(response.getValidation().get(0).getEntityType()).isEqualTo("PIPELINE");
    assertThat(response.getValidation().get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(response.getValidation().get(0).getErrorMessage()).contains("Failed to resolve pipeline references");
    assertThat(response.getValidation().get(0).getHint())
        .contains("Ensure all templates, connectors, and other referenced entities are valid and accessible");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testStartDryRun_MissingPipelineIdentifier_ReturnsSchemaError() {
    requestBody.setPipelineIdentifier(null);

    DryRunPipelineResponseBody response =
        dryRunHelper.startDryRun(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, requestBody, false);

    assertThat(response).isNotNull();
    assertThat(response.isIsValid()).isFalse();
    assertThat(response.getValidation()).hasSize(1);
    assertThat(response.getValidation().get(0).getValidationType()).isEqualTo("SCHEMA");
    assertThat(response.getValidation().get(0).getEntityIdentifier()).isEqualTo("UNKNOWN");
    assertThat(response.getValidation().get(0).getErrorMessage()).contains("pipeline_identifier");
    verify(metricService, times(1)).recordMetric(anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testStartDryRun_MissingPipelineYaml_ReturnsSchemaError() {
    requestBody.setPipelineYaml(null);

    DryRunPipelineResponseBody response =
        dryRunHelper.startDryRun(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, requestBody, false);

    assertThat(response).isNotNull();
    assertThat(response.isIsValid()).isFalse();
    assertThat(response.getValidation()).hasSize(1);
    assertThat(response.getValidation().get(0).getValidationType()).isEqualTo("SCHEMA");
    assertThat(response.getValidation().get(0).getEntityIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(response.getValidation().get(0).getErrorMessage()).contains("pipeline_yaml");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testStartDryRun_MissingBranch_ReturnsSchemaError() {
    requestBody.setBranch(null);

    DryRunPipelineResponseBody response =
        dryRunHelper.startDryRun(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, requestBody, false);

    assertThat(response).isNotNull();
    assertThat(response.isIsValid()).isFalse();
    assertThat(response.getValidation()).hasSize(1);
    assertThat(response.getValidation().get(0).getValidationType()).isEqualTo("SCHEMA");
    assertThat(response.getValidation().get(0).getErrorMessage()).contains("branch");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testStartDryRun_MissingMultipleFields_ListsAllMissingFields() {
    requestBody.setBranch(null);
    requestBody.setPipelineYaml(null);

    DryRunPipelineResponseBody response =
        dryRunHelper.startDryRun(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, requestBody, false);

    assertThat(response).isNotNull();
    assertThat(response.isIsValid()).isFalse();
    assertThat(response.getValidation()).hasSize(1);
    String errorMessage = response.getValidation().get(0).getErrorMessage();
    assertThat(errorMessage).contains("branch");
    assertThat(errorMessage).contains("pipeline_yaml");
    assertThat(errorMessage).doesNotContain("pipeline_identifier");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testValidateRequiredFields_AllFieldsPresent_ReturnsNull() {
    assertThat(dryRunHelper.validateRequiredFields(requestBody)).isNull();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void buildValidationResponse_warningsOnly_isValidTrue() {
    DryRunPipelineValidationResult w = new DryRunPipelineValidationResult();
    w.setValidationType("SEMANTIC");
    w.setSeverity("WARNING");
    w.setEntityIdentifier("p1");
    w.setErrorMessage("soft");
    DryRunPipelineResponseBody r = dryRunHelper.buildValidationResponseForTest("p1", "a", "o", "p", List.of(w));
    assertThat(r.isIsValid()).isTrue();
    assertThat(r.getValidation()).hasSize(1);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void buildValidationResponse_missingSeverity_isError() {
    DryRunPipelineValidationResult n = new DryRunPipelineValidationResult();
    n.setValidationType("REFERRED_ENTITIES");
    n.setEntityIdentifier("p1");
    n.setErrorMessage("legacy"); // severity intentionally unset
    DryRunPipelineResponseBody r = dryRunHelper.buildValidationResponseForTest("p1", "a", "o", "p", List.of(n));
    assertThat(r.isIsValid()).isFalse();
  }

  private ExecutionPlan executionPlanWithYaml() {
    return ExecutionPlan.builder()
        .planExecutionMetadataWithContext(
            PlanExecutionMetadataWithContext.builder().pipelineYamlWithTemplateRef(PIPELINE_YAML).build())
        .build();
  }

  private DryRunPipelineValidationResult semanticResult(String severity) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType("SEMANTIC");
    result.setSeverity(severity);
    result.setEntityIdentifier("conn1");
    result.setErrorMessage("semantic finding");
    return result;
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runSemanticValidation_ffOff_validatorNotInvoked() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(false);
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, executionPlanWithYaml(), new ArrayList<>(),
        results, PipelineEntity.builder().harnessVersion("0").build());

    assertThat(results).isEmpty();
    verify(semanticValidator, never()).validate(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runSemanticValidation_ffOn_errorFinding_isValidFalse() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(true);
    when(semanticValidator.validate(eq(PIPELINE_YAML), any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("0")))
        .thenReturn(List.of(semanticResult("ERROR")));
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, executionPlanWithYaml(), new ArrayList<>(),
        results, PipelineEntity.builder().harnessVersion("0").build());

    assertThat(results).hasSize(1);
    DryRunPipelineResponseBody r =
        dryRunHelper.buildValidationResponseForTest(PIPELINE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, results);
    assertThat(r.isIsValid()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runSemanticValidation_ffOn_warningFinding_isValidTrue() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(true);
    when(semanticValidator.validate(eq(PIPELINE_YAML), any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("0")))
        .thenReturn(List.of(semanticResult("WARNING")));
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, executionPlanWithYaml(), new ArrayList<>(),
        results, PipelineEntity.builder().harnessVersion("0").build());

    assertThat(results).hasSize(1);
    DryRunPipelineResponseBody r =
        dryRunHelper.buildValidationResponseForTest(PIPELINE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, results);
    assertThat(r.isIsValid()).isTrue();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runSemanticValidation_ffOn_nullExecutionPlan_notInvoked() {
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, new ArrayList<>(), results,
        PipelineEntity.builder().harnessVersion("0").build());

    assertThat(results).isEmpty();
    verify(semanticValidator, never()).validate(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void preProcessV1YamlForFilters_v0_returnsSameEntity() {
    PipelineEntity v0 = PipelineEntity.builder().harnessVersion("0").yaml("pipeline:\n  identifier: p\n").build();
    PipelineEntity result = dryRunHelper.preProcessV1YamlForFilters(v0);
    assertThat(result).isSameAs(v0);
    verifyNoInteractions(pmsPipelineServiceHelper);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void preProcessV1YamlForFilters_v1_injectsTypeAndReturnsCopy() {
    String rawV1 = "pipeline:\n  stages:\n    - name: s\n      steps: []\n";
    String injected = "pipeline:\n  stages:\n    - name: s\n      type: unified\n      steps: []\n";
    when(pmsPipelineServiceHelper.injectTypeField(rawV1)).thenReturn(injected);
    PipelineEntity v1 = PipelineEntity.builder().harnessVersion("1").yaml(rawV1).build();

    PipelineEntity result = dryRunHelper.preProcessV1YamlForFilters(v1);

    assertThat(result).isNotSameAs(v1);
    assertThat(result.getYaml()).isEqualTo(injected);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void preProcessV1YamlForFilters_v1BlankYaml_returnsSameEntity() {
    PipelineEntity v1 = PipelineEntity.builder().harnessVersion("1").yaml("  ").build();
    PipelineEntity result = dryRunHelper.preProcessV1YamlForFilters(v1);
    assertThat(result).isSameAs(v1);
    verifyNoInteractions(pmsPipelineServiceHelper);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runSemanticValidation_ffOn_validatorThrows_failOpen() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)).thenReturn(true);
    when(semanticValidator.validate(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("validator blew up"));
    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    dryRunHelper.runSemanticValidation(ACCOUNT_ID, ORG_ID, PROJECT_ID, executionPlanWithYaml(), new ArrayList<>(),
        results, PipelineEntity.builder().harnessVersion("0").build());

    assertThat(results).isEmpty();
  }
}
