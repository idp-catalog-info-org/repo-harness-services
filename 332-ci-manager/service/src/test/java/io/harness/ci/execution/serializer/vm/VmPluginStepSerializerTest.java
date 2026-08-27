/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_GCP_OIDC_POOL_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_GCP_OIDC_PROJECT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_GCP_OIDC_PROVIDER_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_GCP_OIDC_SERVICE_ACCOUNT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.WORKSPACE_ID;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.cacheserviceclient.CacheServiceUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.CodebaseUtils;
import io.harness.ci.execution.serializer.HarnessRegistryUtils;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.AwsOidcAuthenticator;
import io.harness.utils.GcpOidcAuthenticator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CI)
public class VmPluginStepSerializerTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private IACMStepsUtils iacmStepsUtils;
  @Mock private HarnessImageUtils harnessImageUtils;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Spy private SerializerUtils serializerUtils;
  @Mock private GcpOidcAuthenticator gcpOidcAuthenticator;

  @Mock private AwsOidcAuthenticator awsOidcAuthenticator;
  @Mock private CacheServiceUtils cacheServiceUtils;
  @Mock private HarnessRegistryUtils harnessRegistryUtils;
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @Mock private BYOIPluginHandler byoiPluginHandler;
  @Mock private CodebaseUtils codebaseUtils;

  @InjectMocks private VmPluginStepSerializer vmPluginStepSerializer;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();
  private final StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    // Mock problematic methods to avoid NPE while preserving OIDC functionality
    doReturn(new HashMap<>()).when(serializerUtils).getStepStatusEnvVars(any());
    when(harnessRegistryUtils.isHarnessRegistryImage(any(), any())).thenReturn(false);
    when(byoiPluginHandler.isByoiBuilderImage(any())).thenReturn(false);

    // Manually inject mocked authenticators into the spy
    injectField(serializerUtils, "awsOidcAuthenticator", awsOidcAuthenticator);
    injectField(serializerUtils, "gcpOidcAuthenticator", gcpOidcAuthenticator);
  }

  private void injectField(Object target, String fieldName, Object value) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testPluginStepSerialize() throws IOException {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .runAsUser(ParameterField.createValueField(1000))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    ConnectorDTO connectorDTO = getConnectorDTO();
    when(connectorUtils.getConnector(any())).thenReturn(connectorDTO);
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any())).thenReturn("image");
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getRunAsUser()).isEqualTo("1000");
    assertThat(vmPluginStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testPluginStepSerializeGitClone() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "harness-git-clone", null, null, ambiance, null, null, null, null, false);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getEnvVariables())
        .isEqualTo(Map.of("key1", "val1", "key2", "val2", "PLUGIN_BUILD_TOOL_FILE", "plugin-build-tool.json"));
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPluginStepSerializeGitClone_V0LocalBuildWithRunner() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    ConnectorDetails connectorDetailsForMock =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().gitConnectionUrl("https://github.com/username/repo").build())
            .build();
    when(codebaseUtils.getGitConnector(any(), any(), any(), any())).thenReturn(connectorDetailsForMock);
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "harness-git-clone", null, null, ambiance, null, null, null, null, true);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getEnvVariables())
        .isEqualTo(Map.of("key1", "val1", "key2", "val2", "PLUGIN_BUILD_TOOL_FILE", "plugin-build-tool.json"));
    assertThat(vmPluginStep.getConnector()).isEqualTo(connectorDetailsForMock);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPluginStepSerializeGitClone_V0LocalBuildWithRunner_HarnessCodeRepo() {
    // Test for Harness Code repos where connectorRef is null but repoName is provided
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.ofNull())
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    // repoName is set via setter since it's not part of the constructor
    pluginStepInfo.setRepoName(ParameterField.createValueField("myrepo"));
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    ConnectorDetails harnessCodeConnector =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.HARNESS)
            .identifier("HARNESS_SCM")
            .connectorConfig(HarnessConnectorDTO.builder().url("http://localhost:3000/git/accountId").build())
            .build();
    when(codebaseUtils.getGitConnector(any(), eq(null), any(), eq("myrepo"))).thenReturn(harnessCodeConnector);
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "harness-git-clone", null, null, ambiance, null, null, null, null, true);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getConnector()).isEqualTo(harnessCodeConnector);
    assertThat(vmPluginStep.getConnector().getConnectorType()).isEqualTo(ConnectorType.HARNESS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testPluginStepSerializerCreatesIACMPluginStep() throws IOException {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .image(ParameterField.<String>builder().value("foobar").build())
            .envVariables(ParameterField.createValueField(Map.of(WORKSPACE_ID, ParameterField.createValueField("val1"),
                "PLUGIN_CONNECTOR_REF", ParameterField.createValueField("connectorRef"), "PLUGIN_PROVISIONER",
                ParameterField.createValueField("provisioner"))))
            .build();

    ConnectorDTO connectorDTO = getConnectorDTO();
    when(connectorUtils.getConnector(any())).thenReturn(connectorDTO);
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any())).thenReturn("foobar");
    when(iacmStepsUtils.isIACMStep(any())).thenReturn(true);

    VmStepInfo vmStepInfo = vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmStepInfo).isInstanceOf(VmPluginStep.class);
    VmPluginStep vmPluginStep = (VmPluginStep) vmStepInfo;
    assertThat(vmPluginStep.getImage()).isEqualTo("foobar");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testPluginStepSerializerWithUsesCreatesIACMPluginStep() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .privileged(ParameterField.createValueField(true))
            .uses(ParameterField.createValueField("faaa"))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(WORKSPACE_ID, ParameterField.createValueField("val1"),
                "PLUGIN_CONNECTOR_REF", ParameterField.createValueField("connectorRef"), "PLUGIN_PROVISIONER",
                ParameterField.createValueField("provisioner"))))
            .build();

    VmStepInfo vmStepInfo = vmPluginStepSerializer.serialize(pluginStepInfo, DliteVmStageInfraDetails.builder().build(),
        "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmStepInfo).isInstanceOf(VmRunStep.class);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testOIDCTokenCreationFromOIDCGCPPluginEnvVariables() throws IOException {
    PluginStepInfo pluginStepInfo = PluginStepInfo.builder()
                                        .image(ParameterField.createValueField("plugins/gcp-oidc"))
                                        .privileged(ParameterField.createValueField(true))
                                        .harnessManagedImage(true)
                                        .connectorRef(ParameterField.createValueField("connectorRef"))
                                        .reports(ParameterField.createValueField(null))
                                        .envVariables(ParameterField.createValueField(Map.of(PLUGIN_GCP_OIDC_POOL_ID,
                                            ParameterField.createValueField("var1"), PLUGIN_GCP_OIDC_PROJECT_ID,
                                            ParameterField.createValueField("var2"), PLUGIN_GCP_OIDC_PROVIDER_ID,
                                            ParameterField.createValueField("var3"), PLUGIN_GCP_OIDC_SERVICE_ACCOUNT_ID,
                                            ParameterField.createValueField("var4"))))
                                        .build();
    ConnectorDTO connectorDTO = getConnectorDTO();
    when(connectorUtils.getConnector(any())).thenReturn(connectorDTO);
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any())).thenReturn("plugins/gcp-oidc");
    when(gcpOidcAuthenticator.handleOidcAuthentication(any(), any(), any()))
        .thenReturn(Map.of(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "testToken"));
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmPluginStep.getImage()).isEqualTo("plugins/gcp-oidc");
    assertThat(vmPluginStep.getEnvVariables().containsKey("PLUGIN_OIDC_TOKEN_ID")).isEqualTo(true);
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OIDC_TOKEN_ID")).isEqualTo("testToken");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testOIDCTokenCreationFromOidcAwsPluginEnvVariables() throws IOException {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("plugins/aws-oidc"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of("PLUGIN_IAMROLEARN",
                ParameterField.createValueField("var1"), "PLUGIN_ROLE_SESSION_NAME",
                ParameterField.createValueField("var2"), "PLUGIN_DURATION", ParameterField.createValueField("var3"))))
            .build();
    ConnectorDTO connectorDTO = getConnectorDTO();
    when(connectorUtils.getConnector(any())).thenReturn(connectorDTO);
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any())).thenReturn("plugins/aws-oidc");
    when(awsOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("testToken");
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmPluginStep.getImage()).isEqualTo("plugins/aws-oidc");
    assertThat(vmPluginStep.getEnvVariables().containsKey("PLUGIN_OIDC_TOKEN_ID")).isTrue();
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OIDC_TOKEN_ID")).isEqualTo("testToken");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSerializeForHARConnector() throws IOException {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("plugins/aws-oidc"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(false)
            .registryRef(ParameterField.createValueField("registryRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of("PLUGIN_IAMROLEARN",
                ParameterField.createValueField("var1"), "PLUGIN_ROLE_SESSION_NAME",
                ParameterField.createValueField("var2"), "PLUGIN_DURATION", ParameterField.createValueField("var3"))))
            .build();

    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("RegistryURL").build())
            .connectorType(ConnectorType.DOCKER)
            .executeOnDelegate(false)
            .build();
    when(connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any())).thenReturn(connectorDetailsFoMock);
    when(ciFeatureFlagService.isEnabled(FeatureName.HAR_ENABLED, AmbianceUtils.getAccountId(ambiance)))
        .thenReturn(true);
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("plugins/aws-oidc");
    when(awsOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("testToken");
    when(harnessRegistryUtils.isHarnessRegistryImage(any(), any())).thenReturn(true);
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, stageInfraDetails, "id", null, null, ambiance, null, null, null, null, false);
    assertThat(vmPluginStep.getImageConnector().getConnectorConfig()).isInstanceOf(DockerConnectorDTO.class);
    assertThat(((DockerConnectorDTO) vmPluginStep.getImageConnector().getConnectorConfig()).getDockerRegistryUrl())
        .isEqualTo("RegistryURL");

    assertThat(vmPluginStep.getEnvVariables().containsKey("PLUGIN_OIDC_TOKEN_ID")).isTrue();
    assertThat(vmPluginStep.getEnvVariables().get("PLUGIN_OIDC_TOKEN_ID")).isEqualTo("testToken");
  }

  private ConnectorDTO getConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .accountIdentifier("accountId")
                           .orgIdentifier("orgId")
                           .projectIdentifier("projectId")
                           .name("connectorName")
                           .identifier("connectorId")
                           .build())
        .build();
  }
}
