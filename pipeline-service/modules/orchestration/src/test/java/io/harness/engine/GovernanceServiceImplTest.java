/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NAVNEET_KHANDELWAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.ActionContext;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.TemplateOpaEvaluationContext;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.SourcePrincipalHelper;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({GovernanceServiceHelper.class, SafeHttpCall.class})
@OwnedBy(PIPELINE)
public class GovernanceServiceImplTest extends CategoryTest {
  GovernanceService governanceService;
  @Mock PmsFeatureFlagService featureFlagService;
  @Mock OpaServiceClientHelper opaServiceClientHelper;
  @Mock SourcePrincipalContextBuilder sourcePrincipalContextBuilder;
  @Mock SourcePrincipalHelper sourcePrincipalHelper;
  @Mock UserPrincipal principal;
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String action = "onSave";
  String planExecutionId = "";
  String principalType = "USER";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    governanceService = new GovernanceServiceImpl(featureFlagService, opaServiceClientHelper);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithFlagOff() {
    doReturn(false).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    GovernanceMetadata flagOffMetadata = governanceService.evaluateGovernancePolicies(
        null, accountId, null, null, null, null, HarnessYamlVersion.V0, null);
    assertThat(flagOffMetadata.getDeny()).isFalse();
    assertThat(flagOffMetadata.getMessage()).isEqualTo("FF: [OPA_PIPELINE_GOVERNANCE] is disabled for account: [acc]");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithInvalidYAML() throws IOException {
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);
    when(GovernanceServiceHelper.createEvaluationContext(eq("expandedJSON:"), any())).thenThrow(new IOException());
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        "expandedJSON:", accountId, null, null, null, null, HarnessYamlVersion.V0, null);
    assertThat(governanceMetadata.getDeny()).isTrue();
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePolicies() throws IOException {
    String expandedJSON = "pipeline:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    PipelineOpaEvaluationContext evaluationContext =
        PipelineOpaEvaluationContext.builder()
            .pipeline(Collections.singletonMap("pipeline", "yaml"))
            .actionContext(ActionContext.builder().rerun(false).executionId(1).build())
            .build();
    when(GovernanceServiceHelper.createEvaluationContext(eq(expandedJSON), any())).thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityString = "entityString";
    when(GovernanceServiceHelper.getEntityString(accountId, orgId, projectId, "myPipe")).thenReturn(entityString);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadataString("myPipe", "my pipe", planExecutionId))
        .thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
             orgId, projectId, action, entityString, entityMetadata, userID, principalType, "0", evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    ActionContext actionContext = ActionContext.builder().rerun(false).executionId(1).build();
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        expandedJSON, accountId, orgId, projectId, action, planExecutionId, HarnessYamlVersion.V0, actionContext);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithRerun() throws IOException {
    String expandedJSON = "pipeline:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    PipelineOpaEvaluationContext evaluationContext =
        PipelineOpaEvaluationContext.builder()
            .pipeline(Collections.singletonMap("pipeline", "yaml"))
            .actionContext(ActionContext.builder().rerun(true).executionId(2).build())
            .build();
    when(GovernanceServiceHelper.createEvaluationContext(eq(expandedJSON), any())).thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityString = "entityString";
    when(GovernanceServiceHelper.getEntityString(accountId, orgId, projectId, "myPipe")).thenReturn(entityString);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadataString("myPipe", "my pipe", planExecutionId))
        .thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
             orgId, projectId, action, entityString, entityMetadata, userID, principalType, "0", evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    ActionContext metadata = ActionContext.builder().rerun(true).executionId(2).build();
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        expandedJSON, accountId, orgId, projectId, action, planExecutionId, HarnessYamlVersion.V0, metadata);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesForV1Yaml() {
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        null, accountId, null, null, null, null, HarnessYamlVersion.V1, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithMetadata() throws IOException {
    String expandedJSON = "pipeline:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    PipelineOpaEvaluationContext evaluationContext =
        PipelineOpaEvaluationContext.builder()
            .pipeline(Collections.singletonMap("pipeline", "yaml"))
            .actionContext(ActionContext.builder().rerun(false).executionId(3).build())
            .build();
    when(GovernanceServiceHelper.createEvaluationContext(eq(expandedJSON), any())).thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityString = "entityString";
    when(GovernanceServiceHelper.getEntityString(accountId, orgId, projectId, "myPipe")).thenReturn(entityString);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadataString("myPipe", "my pipe", planExecutionId))
        .thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
             orgId, projectId, action, entityString, entityMetadata, userID, principalType, "0", evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    ActionContext actionContext = ActionContext.builder().rerun(false).executionId(3).build();
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        expandedJSON, accountId, orgId, projectId, action, planExecutionId, HarnessYamlVersion.V0, actionContext);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithNullMetadata() throws IOException {
    String expandedJSON = "pipeline:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    PipelineOpaEvaluationContext evaluationContext = PipelineOpaEvaluationContext.builder()
                                                         .pipeline(Collections.singletonMap("pipeline", "yaml"))
                                                         .actionContext(null)
                                                         .build();
    when(GovernanceServiceHelper.createEvaluationContext(eq(expandedJSON), eq(null))).thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityString = "entityString";
    when(GovernanceServiceHelper.getEntityString(accountId, orgId, projectId, "myPipe")).thenReturn(entityString);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadataString("myPipe", "my pipe", planExecutionId))
        .thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
             orgId, projectId, action, entityString, entityMetadata, userID, principalType, "0", evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        expandedJSON, accountId, orgId, projectId, action, planExecutionId, HarnessYamlVersion.V0, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesTemplateWithInvalidYAML() throws IOException {
    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);
    when(GovernanceServiceHelper.createEvaluationContextTemplate(eq("expandedJSON:"), any()))
        .thenThrow(new IOException());
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePoliciesForTemplate(
        "expandedJSON:", accountId, null, null, null, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, "0", null, null);
    assertThat(governanceMetadata.getDeny()).isTrue();
    mockSettings.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesTemplate() throws IOException {
    String expandedJSON = "template:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    TemplateOpaEvaluationContext evaluationContext =
        TemplateOpaEvaluationContext.builder().template(Collections.singletonMap("template", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContextTemplate(eq(expandedJSON), any()))
        .thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityString = "myPipe";
    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadata("my pipe")).thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, accountId,
             orgId, projectId, action, entityString, entityMetadata, userID, principalType, "0", evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder()
                                              .setDeny(false)
                                              .setId("someID")
                                              .addAllDetails(Collections.singletonList(
                                                  PolicySetMetadata.newBuilder().setDescription("description").build()))
                                              .build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePoliciesForTemplate(
        expandedJSON, accountId, orgId, projectId, action, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, "0", null, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    assertThat(governanceMetadata.getDetails(0).getDescription()).isEqualTo("description");
    mockSettings.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesForV1Template() throws IOException {
    String expandedJSON = "template:\n"
        + "  inputs:\n"
        + "    goos:\n"
        + "      type: string\n"
        + "      default: linux\n"
        + "  stage:\n"
        + "    steps:\n"
        + "      - run:\n"
        + "          script: echo test\n"
        + "spec:\n"
        + "  type: step";

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    TemplateOpaEvaluationContext evaluationContext =
        TemplateOpaEvaluationContext.builder().template(Collections.singletonMap("template", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContextTemplate(eq(expandedJSON), any()))
        .thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String templateIdentifier = "myV1Template";
    String templateName = "my v1 template";
    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadata(templateName)).thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, accountId,
             orgId, projectId, action, templateIdentifier, entityMetadata, userID, principalType, HarnessYamlVersion.V1,
             evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse =
        GovernanceMetadata.newBuilder()
            .setDeny(false)
            .setId("someID")
            .addAllDetails(
                Collections.singletonList(PolicySetMetadata.newBuilder().setDescription("v1 description").build()))
            .build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    TemplateMetadataParams metadataParams =
        TemplateMetadataParams.builder().name(templateName).identifier(templateIdentifier).build();

    GovernanceMetadata governanceMetadata =
        governanceService.evaluateGovernancePoliciesForTemplate(expandedJSON, accountId, orgId, projectId, action,
            OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, HarnessYamlVersion.V1, metadataParams, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    assertThat(governanceMetadata.getDetails(0).getDescription()).isEqualTo("v1 description");
    mockSettings.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesForV1TemplateWithNullNameAndIdentifier() throws IOException {
    String expandedJSON = "template:\n"
        + "  inputs:\n"
        + "    version:\n"
        + "      type: string\n"
        + "      default: \"1.20\"\n"
        + "spec:\n"
        + "  type: step";

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    TemplateOpaEvaluationContext evaluationContext =
        TemplateOpaEvaluationContext.builder().template(Collections.singletonMap("template", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContextTemplate(eq(expandedJSON), any()))
        .thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadata("")).thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, accountId,
             orgId, projectId, action, "", entityMetadata, userID, principalType, HarnessYamlVersion.V1,
             evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    GovernanceMetadata governanceMetadata =
        governanceService.evaluateGovernancePoliciesForTemplate(expandedJSON, accountId, orgId, projectId, action,
            OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, HarnessYamlVersion.V1, null, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesForV0TemplateFallback() throws IOException {
    String expandedJSON = "template:\n"
        + "  identifier: myTemplate\n"
        + "  name: my template";

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    TemplateOpaEvaluationContext evaluationContext =
        TemplateOpaEvaluationContext.builder().template(Collections.singletonMap("template", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContextTemplate(eq(expandedJSON), any()))
        .thenReturn(evaluationContext);

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(principal.getType()).thenReturn(PrincipalType.USER);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadata("my template")).thenReturn(entityMetadata);

    String userID = "user";
    when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn(userID);

    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, accountId,
             orgId, projectId, action, "myTemplate", entityMetadata, userID, principalType, HarnessYamlVersion.V0,
             evaluationContext))
        .thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    // Calling with v0 version and null metadata params should fallback to parse from YAML
    GovernanceMetadata governanceMetadata =
        governanceService.evaluateGovernancePoliciesForTemplate(expandedJSON, accountId, orgId, projectId, action,
            OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, HarnessYamlVersion.V0, null, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
  }
}
