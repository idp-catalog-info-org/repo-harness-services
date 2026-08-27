/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CodeBaseStepTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks private CodeBaseStep codeBaseStep;
  private Ambiance ambiance;
  private StepInputPackage stepInputPackage;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")

                   .build();
    stepInputPackage = StepInputPackage.builder().build();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainDelegateChildBranch() {
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().branch("main").build())
            .build();

    ConnectorDetails connectorDetails = ConnectorDetails.builder().executeOnDelegate(Boolean.TRUE).build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    ChildExecutableResponse childExecutableResponse =
        codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("delegateTaskId");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainDelegateChildForPR() {
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().prNumber("1").build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    ChildExecutableResponse childExecutableResponse =
        codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("delegateTaskId");

    when(connectorUtils.hasApiAccess(any())).thenReturn(false);
    childExecutableResponse = codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("syncTaskId");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainSyncChildForOthers() {
    CodeBaseStepParameters codeBaseStepParameters = CodeBaseStepParameters.builder()
                                                        .codeBaseDelegateTaskId("delegateTaskId")
                                                        .codeBaseSyncTaskId("syncTaskId")
                                                        .connectorRef(ParameterField.createValueField("connectorRef"))
                                                        .executionSource(WebhookExecutionSource.builder().build())
                                                        .build();
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    ChildExecutableResponse childExecutableResponse =
        codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("syncTaskId");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldObtainSyncChildForPlatformConnectorWithoutPrivateConnect() {
    // executeOnDelegate=false and private-connect off → manager-side sync task.
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().branch("main").build())
            .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(Boolean.FALSE)
            .connectorConfig(GithubConnectorDTO.builder().url("https://github.example.com").proxy(true).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_PRIVATE_CONNECT), any())).thenReturn(false);

    ChildExecutableResponse response = codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(response.getChildNodeId()).isEqualTo("syncTaskId");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldObtainDelegateChildForPlatformConnectorWithPrivateConnect() {
    // executeOnDelegate=false, proxy=true, FF on → route via delegate.
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().branch("main").build())
            .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(Boolean.FALSE)
            .connectorConfig(GithubConnectorDTO.builder().url("https://github.example.com").proxy(true).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_PRIVATE_CONNECT), any())).thenReturn(true);

    ChildExecutableResponse response = codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(response.getChildNodeId()).isEqualTo("delegateTaskId");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldObtainDelegateChildForPlatformConnectorWithCloudPrivateConnectivity() {
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().branch("main").build())
            .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(Boolean.FALSE)
            .connectorConfig(GithubConnectorDTO.builder().url("https://github.example.com").proxy(true).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY), any())).thenReturn(true);

    ChildExecutableResponse response = codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);

    assertThat(response.getChildNodeId()).isEqualTo("delegateTaskId");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldObtainSyncChildWhenPrivateConnectOnButProxyFalse() {
    // FF on but connector doesn't have proxy=true → no harness-cloud routing.
    CodeBaseStepParameters codeBaseStepParameters =
        CodeBaseStepParameters.builder()
            .codeBaseDelegateTaskId("delegateTaskId")
            .codeBaseSyncTaskId("syncTaskId")
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .executionSource(ManualExecutionSource.builder().branch("main").build())
            .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(Boolean.FALSE)
            .connectorConfig(GithubConnectorDTO.builder().url("https://github.example.com").proxy(false).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_PRIVATE_CONNECT), any())).thenReturn(true);

    ChildExecutableResponse response = codeBaseStep.obtainChild(ambiance, codeBaseStepParameters, stepInputPackage);
    assertThat(response.getChildNodeId()).isEqualTo("syncTaskId");
  }
}
