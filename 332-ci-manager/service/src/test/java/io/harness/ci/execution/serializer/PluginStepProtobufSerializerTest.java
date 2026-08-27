/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.utils.ci.CIInitStripStageVarHelper;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CISweepingOutputEvaluator;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.repositories.CIStepStatusRepository;
import io.harness.rule.Owner;
import io.harness.utils.AwsOidcAuthenticator;
import io.harness.utils.GcpOidcAuthenticator;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CI)
@RunWith(MockitoJUnitRunner.class)
public class PluginStepProtobufSerializerTest {
  private static final String TOKEN = "token";
  private static final String LOG_KEY = "logkey";
  private static final String CALLBACK = "callback";
  private static final String CLONE_CODEBASE_STEP = "cloneCodebaseStep";
  private static final String ACCOUNT_ID = "accountID";
  private static final String PLUGIN_DEPTH = "PLUGIN_DEPTH";
  private static final Integer PORT = 2023;
  private static final Long TIMEOUT = 700000L;

  @Mock Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Spy private SerializerUtils serializerUtils;
  @Mock CIFeatureFlagService featureFlagService;

  @Mock CISweepingOutputEvaluator sweepingOutputEvaluator;
  @Mock private CIInitStripStageVarHelper ciInitStripStageVarHelper;
  @Mock private AwsOidcAuthenticator awsOidcAuthenticator;
  @Mock private GcpOidcAuthenticator gcpOidcAuthenticator;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIStepStatusRepository ciStepStatusRepository;

  @InjectMocks private PluginStepProtobufSerializer pluginStepProtobufSerializer;

  @Before
  public void setup() throws Exception {
    // Mock the problematic method to avoid NPE while preserving OIDC functionality
    doReturn(new HashMap<>()).when(serializerUtils).getStepStatusEnvVars(any());

    // Manually inject mocked authenticators into the spy
    injectField(serializerUtils, "awsOidcAuthenticator", awsOidcAuthenticator);
    injectField(serializerUtils, "gcpOidcAuthenticator", gcpOidcAuthenticator);
  }

  private void injectField(Object target, String fieldName, Object value) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private PluginStepInfo preparePluginStepInfo() {
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("testImage");
    return PluginStepInfo.builder()
        .identifier(GIT_CLONE_STEP_ID)
        .connectorRef(ParameterField.createValueField("connectorRef"))
        .image(ParameterField.createValueField("testImage"))
        .name(GIT_CLONE_STEP_NAME)
        .entrypoint(ParameterField.createValueField(Arrays.asList("gitclone")))
        .harnessManagedImage(true)
        .reports(ParameterField.createValueField(UnitTestReport.builder().build()))
        .settings(ParameterField.createValueField(new HashMap<>()))
        .build();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testDefaultCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo("50");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testSpecificCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("4"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo("4");
  }
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testFullCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("0"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo(null);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testFullCloneCodebaseDeptWebhook() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("0"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, WebhookExecutionSource.builder().triggerName("testtrigger").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo(null);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testCloneCodebaseNoDeptWebhook() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    pluginStepInfo.setSettings(null);

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, WebhookExecutionSource.builder().triggerName("testtrigger").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo(null);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testCloneCodebaseFixDeptWebhook() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("4"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, WebhookExecutionSource.builder().triggerName("testtrigger").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo("4");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testInvalidStepSerializer() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    assertThatThrownBy(
        ()
            -> pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, null, "abc", LOG_KEY,
                GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
                ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
                Ambiance.newBuilder().build()))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGcpOIDCTokenInjection() throws IOException {
    // Create plugin step info with GCP OIDC image
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .identifier("gcpOidcStep")
            .image(ParameterField.createValueField("plugins/gcp-oidc"))
            .name("GCP OIDC Step")
            .connectorRef(ParameterField.createValueField("gcpConnector"))
            .entrypoint(ParameterField.createValueField(Arrays.asList("gcpoidc")))
            .reports(ParameterField.createValueField(UnitTestReport.builder().build()))
            .settings(ParameterField.createValueField(new HashMap<String, JsonNode>() {
              {
                put("GCP_OIDC_PROJECT_ID", JsonNodeFactory.instance.textNode("test-project"));
                put("GCP_OIDC_POOL_ID", JsonNodeFactory.instance.textNode("test-pool"));
                put("GCP_OIDC_PROVIDER_ID", JsonNodeFactory.instance.textNode("test-provider"));
                put("GCP_OIDC_SERVICE_ACCOUNT_ID",
                    JsonNodeFactory.instance.textNode("test@test.iam.gserviceaccount.com"));
              }
            }))
            .build();

    // Setup mocks
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("plugins/gcp-oidc");
    when(gcpOidcAuthenticator.handleOidcAuthentication(any(), any(), any()))
        .thenReturn(Map.of(io.harness.delegate.beans.ci.pod.EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "gcpOidcToken"));

    // Execute
    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, "gcpOidcStep", ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, "GCP OIDC Step", ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());

    // Verify OIDC token is injected
    assertThat(unitStep.getPlugin().getEnvironmentMap().containsKey("PLUGIN_OIDC_TOKEN_ID")).isTrue();
    assertThat(unitStep.getPlugin().getEnvironmentMap().get("PLUGIN_OIDC_TOKEN_ID")).isEqualTo("gcpOidcToken");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testAwsOIDCTokenInjection() throws IOException {
    // Create plugin step info with AWS OIDC image
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .identifier("awsOidcStep")
            .image(ParameterField.createValueField("plugins/aws-oidc"))
            .name("AWS OIDC Step")
            .connectorRef(ParameterField.createValueField("awsConnector"))
            .entrypoint(ParameterField.createValueField(Arrays.asList("awsoidc")))
            .reports(ParameterField.createValueField(UnitTestReport.builder().build()))
            .settings(ParameterField.createValueField(new HashMap<String, JsonNode>() {
              {
                put("PLUGIN_IAMROLEARN", JsonNodeFactory.instance.textNode("arn:aws:iam::123456789012:role/test-role"));
                put("PLUGIN_ROLE_SESSION_NAME", JsonNodeFactory.instance.textNode("test-session"));
              }
            }))
            .build();

    // Setup mocks
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("plugins/aws-oidc");
    when(awsOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("awsOidcToken");

    // Execute
    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, "awsOidcStep", ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, "AWS OIDC Step", ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());

    // Verify OIDC token is injected
    assertThat(unitStep.getPlugin().getEnvironmentMap().containsKey("PLUGIN_OIDC_TOKEN_ID")).isTrue();
    assertThat(unitStep.getPlugin().getEnvironmentMap().get("PLUGIN_OIDC_TOKEN_ID")).isEqualTo("awsOidcToken");
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testPluginStepEnvironmentVariableResolution() {
    Map<String, ParameterField<String>> envVariables = new HashMap<>();
    envVariables.put("CICD_VERSION", ParameterField.createValueField("123"));
    envVariables.put("CUSTOM_ENV", ParameterField.createValueField("testValue"));

    PluginStepInfo pluginStepInfo = PluginStepInfo.builder()
                                        .identifier("buildxStep")
                                        .image(ParameterField.createValueField("ebtasamfaridy/buildx:regres8"))
                                        .name("Buildx Plugin Step")
                                        .connectorRef(ParameterField.createValueField("docker_pat"))
                                        .entrypoint(ParameterField.createValueField(Arrays.asList("buildx")))
                                        .envVariables(ParameterField.createValueField(envVariables))
                                        .reports(ParameterField.createValueField(UnitTestReport.builder().build()))
                                        .settings(ParameterField.createValueField(new HashMap<>()))
                                        .build();
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ebtasamfaridy/buildx:regres8");
    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, "buildxStep", ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, "Buildx Plugin Step", ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    Map<String, String> environmentMap = unitStep.getPlugin().getEnvironmentMap();
    assertThat(environmentMap.containsKey("CICD_VERSION")).isTrue();
    assertThat(environmentMap.get("CICD_VERSION")).isEqualTo("123");
    assertThat(environmentMap.containsKey("CUSTOM_ENV")).isTrue();
    assertThat(environmentMap.get("CUSTOM_ENV")).isEqualTo("testValue");
  }
}
