/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.SMCCONKEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.IACMAnsiblePluginInfo;
import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(IACM)
public class VmIACMStepSerializerTest extends CategoryTest {
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private HarnessImageUtils harnessImageUtils;
  @Mock IACMStepsUtils iacmStepsUtils;
  @Mock private SerializerUtils serializerUtils;
  @InjectMocks private VmIACMStepSerializer vmIACMPluginCompatibleStepSerializer;
  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  @Ignore("CI-8692: TI team to follow up")
  public void testIACMGetWorkspaceVariables() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("Key1", "Value1");
    envVars.put("Key2", "Value1");
    Map<String, String> tfVars = new HashMap<>();
    tfVars.put("tfvar1", "TfValue1");
    tfVars.put("tfvar2", "Value1");
    Map<String, String> env = new HashMap<>();
    env.put("command", "Apply");
    IACMTerraformPluginInfo stepInfo = IACMTerraformPluginInfo.builder()
                                           .envVariables(ParameterField.createValueField(env))
                                           .identifier("id")
                                           .name("name")
                                           .operation(ParameterField.<String>builder().build())
                                           .image(ParameterField.<String>builder().build())
                                           .build();

    Mockito.mockStatic(CIStepInfoUtils.class);
    when(CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any())).thenReturn("imageName");
    when(iacmStepsUtils.replaceExpressionFunctorToken(any(), any())).thenReturn(new HashMap<>() {
      {
        put("ENV_SECRETS_keytest1", "${ngSecretManager.obtain");
        put("PLUGIN_keytest2", "keyValue2");
        put("TFVARS_SECRETS_keytest3", "${ngSecretManager.obtain");
        put("TF_keytest4", "keyValue4");
        put("PLUGIN_CONNECTOR_REF", "connectorRef");
        put("PLUGIN_PROVISIONER", "provisioner");
      }
    });
    when(iacmStepsUtils.retrieveIACMConnectorDetails(ambiance, "connectorRef", "provisioner"))
        .thenReturn(ConnectorDetails.builder().build());
    Mockito.mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtility.getFullyQualifiedImageName(any(), any())).thenReturn("imageName");
    Mockito.mockStatic(PluginSettingUtils.class);
    when(PluginSettingUtils.getConnectorSecretEnvMap(any())).thenReturn(new HashMap<>());
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ConnectorDetails.builder().build());

    VmPluginStep vmPluginStep = vmIACMPluginCompatibleStepSerializer.serialize(ambiance, stepInfo, null, null);
    assertThat(vmPluginStep.getEnvVariables().size()).isEqualTo(5);
    assertThat(vmPluginStep.getEnvVariables().get("ENV_SECRETS_keytest1")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_keytest2")).isEqualTo("keyValue2");
    assertThat(vmPluginStep.getEnvVariables().get("TFVARS_SECRETS_keytest3")).contains("${ngSecretManager.obtain");
    assertThat(vmPluginStep.getEnvVariables().get("TF_keytest4")).isEqualTo("keyValue4");
  }

  @Test
  @Owner(developers = SMCCONKEY)
  @Category(UnitTests.class)
  public void testAnsibleSerializerMergesWrappedSecretVarsFromConnectorIntoEnvVars() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("PLUGIN_INVENTORY_TYPE", "plugin");

    Map<String, String> envVarsFromConnector = new HashMap<>();
    Map<String, String> secretVarsFromConnector = new HashMap<>();
    secretVarsFromConnector.put("PLUGIN_JSON_KEY", "account.StephenGCPConf");

    IACMAnsiblePluginInfo stepInfo = IACMAnsiblePluginInfo.builder()
                                         .identifier("ansible_step")
                                         .name("ansible_step")
                                         .envVariables(ParameterField.createValueField(envVars))
                                         .image(ParameterField.<String>builder().build())
                                         .operation(ParameterField.createValueField("apply"))
                                         .build();
    stepInfo.setConnectorRef(ParameterField.<String>builder().build());
    stepInfo.setEnvVariablesFromConnector(ParameterField.createValueField(envVarsFromConnector));
    stepInfo.setSecretVariablesFromConnector(ParameterField.createValueField(secretVarsFromConnector));

    Map<String, String> wrapped = new HashMap<>();
    wrapped.put("PLUGIN_JSON_KEY", "${ngSecretManager.obtain(\"account.StephenGCPConf\", functorToken)}");
    when(iacmStepsUtils.wrapSecretRefsAsFunctorExpressions(secretVarsFromConnector)).thenReturn(wrapped);

    when(iacmStepsUtils.replaceExpressionFunctorToken(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    when(iacmStepsUtils.populatePipelineIds(any(), any())).thenReturn("");
    when(ciExecutionConfigService.getPluginVersionForVM(any(), any())).thenReturn("ansible-image:latest");
    when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    when(serializerUtils.getStepStatusEnvVars(any())).thenReturn(new HashMap<>());

    try (MockedStatic<IntegrationStageUtility> mocked = Mockito.mockStatic(IntegrationStageUtility.class)) {
      mocked.when(() -> IntegrationStageUtility.getFullyQualifiedImageName(any(), any()))
          .thenReturn("ansible-image:latest");

      VmPluginStep vmPluginStep = vmIACMPluginCompatibleStepSerializer.serialize(ambiance, stepInfo, null, null);

      assertThat(vmPluginStep.getEnvVariables())
          .containsEntry("PLUGIN_JSON_KEY", "${ngSecretManager.obtain(\"account.StephenGCPConf\", functorToken)}");
      assertThat(vmPluginStep.getConnector()).isNull();
    }
  }

  @Test
  @Owner(developers = SMCCONKEY)
  @Category(UnitTests.class)
  public void testAnsibleSerializerWithNullSecretVarsFromConnector() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("PLUGIN_INVENTORY_TYPE", "plugin");

    IACMAnsiblePluginInfo stepInfo = IACMAnsiblePluginInfo.builder()
                                         .identifier("ansible_step")
                                         .name("ansible_step")
                                         .envVariables(ParameterField.createValueField(envVars))
                                         .image(ParameterField.<String>builder().build())
                                         .operation(ParameterField.createValueField("apply"))
                                         .build();
    stepInfo.setConnectorRef(ParameterField.<String>builder().build());
    stepInfo.setEnvVariablesFromConnector(ParameterField.createValueField(new HashMap<>()));
    // secretVariablesFromConnector intentionally left null to confirm null-safe handling

    when(iacmStepsUtils.wrapSecretRefsAsFunctorExpressions(isNull())).thenReturn(new HashMap<>());
    when(iacmStepsUtils.replaceExpressionFunctorToken(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    when(iacmStepsUtils.populatePipelineIds(any(), any())).thenReturn("");
    when(ciExecutionConfigService.getPluginVersionForVM(any(), any())).thenReturn("ansible-image:latest");
    when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    when(serializerUtils.getStepStatusEnvVars(any())).thenReturn(new HashMap<>());

    try (MockedStatic<IntegrationStageUtility> mocked = Mockito.mockStatic(IntegrationStageUtility.class)) {
      mocked.when(() -> IntegrationStageUtility.getFullyQualifiedImageName(any(), any()))
          .thenReturn("ansible-image:latest");

      VmPluginStep vmPluginStep = vmIACMPluginCompatibleStepSerializer.serialize(ambiance, stepInfo, null, null);

      assertThat(vmPluginStep).isNotNull();
      assertThat(vmPluginStep.getEnvVariables()).doesNotContainKey("PLUGIN_JSON_KEY");
    }
  }
}
