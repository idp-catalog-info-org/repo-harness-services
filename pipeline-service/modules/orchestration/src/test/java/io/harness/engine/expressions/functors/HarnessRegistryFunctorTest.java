/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.beans.FeatureName.REGISTRY_VANITY_URL_ENABLED;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.utils.HarnessRegistryConnectorUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

@OwnedBy(HarnessTeam.PIPELINE)
public class HarnessRegistryFunctorTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_REGISTRY_URL = "https://registry.harness.io";
  private static final String TEST_REGISTRY_SECRET = "test-secret";
  private static final String TEST_JWT_TOKEN = "testPassword";
  private static final String TEST_VANITY_URL = "https://vanity.harness.io";

  private HarnessRegistryFunctor harnessRegistryFunctor;
  private PmsFeatureFlagService featureFlagService;
  private ListAppender<ILoggingEvent> listAppender;
  private Logger logger;

  private Ambiance ambiance;
  private Ambiance ambianceV1;
  private ServiceHttpClientConfig harnessRegistryClientConfig;

  @Before
  public void setUp() {
    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", "test-org")
            .putSetupAbstractions("projectIdentifier", "test-project")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V0)
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();

    ambianceV1 =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", "test-org")
            .putSetupAbstractions("projectIdentifier", "test-project")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V1)
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();

    harnessRegistryClientConfig = ServiceHttpClientConfig.builder()
                                      .baseUrl(TEST_REGISTRY_URL)
                                      .connectTimeOutSeconds(120)
                                      .readTimeOutSeconds(120)
                                      .build();

    harnessRegistryFunctor = HarnessRegistryFunctor.builder().ambiance(ambiance).build();

    featureFlagService = mock(PmsFeatureFlagService.class);
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(false);
    on(harnessRegistryFunctor).set("featureFlagService", featureFlagService);

    // Set up logger to capture log messages
    logger = (Logger) LoggerFactory.getLogger(HarnessRegistryFunctor.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @After
  public void tearDown() {
    logger.detachAppender(listAppender);
  }

  // ==================== supportsKey() Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSupportsKeyWithHarKey() {
    assertThat(harnessRegistryFunctor.supportsKey("har")).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSupportsKeyWithOtherKeys() {
    assertThat(harnessRegistryFunctor.supportsKey("registry")).isFalse();
    assertThat(harnessRegistryFunctor.supportsKey("token")).isFalse();
    assertThat(harnessRegistryFunctor.supportsKey("url")).isFalse();
    assertThat(harnessRegistryFunctor.supportsKey("username")).isFalse();
    assertThat(harnessRegistryFunctor.supportsKey("unknown")).isFalse();
    assertThat(harnessRegistryFunctor.supportsKey("")).isFalse();
  }

  // ==================== get() - Username Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUsernameReturnsMaskedExpression() {
    Object result = harnessRegistryFunctor.get("username");

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(String.class);
    String usernameResult = (String) result;
    // Verify it returns a sweeping output secret expression for masking
    assertThat(usernameResult).contains("sweepingOutputSecrets.obtain");
    assertThat(usernameResult).contains("harnessRegistryUsername");
  }

  // ==================== get() - URL Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithValidConfig() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(TEST_REGISTRY_URL);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithTrailingSlash() {
    ServiceHttpClientConfig configWithSlash =
        ServiceHttpClientConfig.builder().baseUrl(TEST_REGISTRY_URL + "/").build();
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", configWithSlash);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(TEST_REGISTRY_URL); // Trailing slash should be removed
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithNullConfig() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", null);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isNull();
    assertThat(listAppender.list.stream().anyMatch(event
                   -> event.getLevel() == Level.WARN
                       && event.getMessage().contains("harnessRegistryClientConfig is not available")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithNullBaseUrl() {
    ServiceHttpClientConfig configWithNullUrl = ServiceHttpClientConfig.builder().baseUrl(null).build();
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", configWithNullUrl);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isNull();
  }

  // ==================== get() - Token Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetTokenWithValidConfig() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    on(harnessRegistryFunctor).set("harnessRegistryServiceSecret", TEST_REGISTRY_SECRET);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils
          .when(()
                    -> HarnessRegistryConnectorUtils.getHarnessRegistryTokenWithClaims(
                        anyString(), anyString(), anyInt(), anyString()))
          .thenReturn(TEST_JWT_TOKEN);

      Object result = harnessRegistryFunctor.get("token");

      assertThat(result).isNotNull();
      assertThat(result).isInstanceOf(String.class);
      String tokenResult = (String) result;
      // Verify it returns a sweeping output secret expression for masking
      assertThat(tokenResult).contains("sweepingOutputSecrets.obtain");
      assertThat(tokenResult).contains("harnessRegistryToken");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetTokenWithEmptySecret() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    on(harnessRegistryFunctor).set("harnessRegistryServiceSecret", "");

    Object result = harnessRegistryFunctor.get("token");

    assertThat(result).isNull();
    assertThat(listAppender.list.stream().anyMatch(
                   event -> event.getLevel() == Level.WARN && event.getMessage().contains("secret or URL is empty")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetTokenWithNullSecret() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    on(harnessRegistryFunctor).set("harnessRegistryServiceSecret", null);

    Object result = harnessRegistryFunctor.get("token");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetTokenWithNullConfig() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", null);
    on(harnessRegistryFunctor).set("harnessRegistryServiceSecret", TEST_REGISTRY_SECRET);

    Object result = harnessRegistryFunctor.get("token");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetTokenExceptionHandling() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    on(harnessRegistryFunctor).set("harnessRegistryServiceSecret", TEST_REGISTRY_SECRET);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils
          .when(()
                    -> HarnessRegistryConnectorUtils.getHarnessRegistryTokenWithClaims(
                        anyString(), anyString(), anyInt(), anyString()))
          .thenThrow(new RuntimeException("Token generation failed"));

      Object result = harnessRegistryFunctor.get("token");

      assertThat(result).isNull();
      assertThat(listAppender.list.stream().anyMatch(
                     event -> event.getLevel() == Level.ERROR && event.getMessage().contains("Failed to generate")))
          .isTrue();
    }
  }

  // ==================== get() - Unknown Key Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetWithUnknownKey() {
    Object result = harnessRegistryFunctor.get("unknownKey");

    assertThat(result).isNull();
    assertThat(listAppender.list.stream().anyMatch(
                   event -> event.getLevel() == Level.WARN && event.getMessage().contains("Unknown key")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetWithNonStringKey() {
    Object result = harnessRegistryFunctor.get(123);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetWithNullKey() {
    Object result = harnessRegistryFunctor.get(null);

    assertThat(result).isNull();
  }

  // ==================== getRepo() Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithValidInput() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("account.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("account.myRegistry/my-image");

      assertThat(result).isNotNull();
      // Expected format: host/accountId/registryName/repo
      assertThat(result).isEqualTo("registry.harness.io/" + TEST_ACCOUNT_ID.toLowerCase() + "/myRegistry/my-image");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithNestedPath() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("myRegistry/path/to/my-image");

      assertThat(result).isNotNull();
      // Should correctly parse first "/" as separator and rest as repo path
      assertThat(result).contains("path/to/my-image");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithEmptyInput() {
    String result = harnessRegistryFunctor.getRepo("");

    assertThat(result).isNull();
    assertThat(
        listAppender.list.stream().anyMatch(
            event -> event.getLevel() == Level.WARN && event.getMessage().contains("registry and repo is empty")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithNullInput() {
    String result = harnessRegistryFunctor.getRepo(null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithNonStringInput() {
    String result = harnessRegistryFunctor.getRepo(12345);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithNoSlash() {
    String result = harnessRegistryFunctor.getRepo("noSlashInput");

    // Invalid format, should return original input
    assertThat(result).isEqualTo("noSlashInput");
    assertThat(listAppender.list.stream().anyMatch(
                   event -> event.getLevel() == Level.WARN && event.getMessage().contains("Invalid format")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithSlashAtStart() {
    String result = harnessRegistryFunctor.getRepo("/invalidStart");

    // Invalid format (slash at start), should return original input
    assertThat(result).isEqualTo("/invalidStart");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithSlashAtEnd() {
    String result = harnessRegistryFunctor.getRepo("registryRef/");

    // Invalid format (slash at end), should return original input
    assertThat(result).isEqualTo("registryRef/");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithNullRegistryConfig() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", null);

    String result = harnessRegistryFunctor.getRepo("myRegistry/my-image");

    // Should return just the repo when config is unavailable
    assertThat(result).isEqualTo("my-image");
    assertThat(listAppender.list.stream().anyMatch(
                   event -> event.getLevel() == Level.WARN && event.getMessage().contains("Registry base URL")))
        .isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithMalformedUrl() {
    ServiceHttpClientConfig invalidConfig = ServiceHttpClientConfig.builder().baseUrl("not-a-valid-url").build();
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", invalidConfig);

    assertThatThrownBy(() -> harnessRegistryFunctor.getRepo("myRegistry/my-image"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Malformed registryUrl");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithAccountPrefix() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("account.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("account.myRegistry/my-image");

      assertThat(result).contains("myRegistry");
      assertThat(result).doesNotContain("account.");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithOrgPrefix() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("org.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("org.myRegistry/my-image");

      assertThat(result).contains("myRegistry");
      assertThat(result).doesNotContain("org.");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testAccountIdIsLowercasedInRepo() {
    Ambiance ambianceUpperCase =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "TEST-ACCOUNT-ID")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V0)
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();
    HarnessRegistryFunctor functor = HarnessRegistryFunctor.builder().ambiance(ambianceUpperCase).build();
    on(functor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    PmsFeatureFlagService mockFFService = mock(PmsFeatureFlagService.class);
    when(mockFFService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(false);
    on(functor).set("featureFlagService", mockFFService);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("myRegistry"))
          .thenReturn("myRegistry");

      String result = functor.getRepo("myRegistry/my-image");

      assertThat(result).contains("test-account-id"); // Should be lowercase
      assertThat(result).doesNotContain("TEST-ACCOUNT-ID");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoFormatIsCorrect() {
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);

    try (MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("testRegistry"))
          .thenReturn("testRegistry");

      String result = harnessRegistryFunctor.getRepo("testRegistry/test-image");

      // Verify the format: host/accountId/registryName/repo
      String[] parts = result.split("/");
      assertThat(parts).hasSize(4);
      assertThat(parts[0]).isEqualTo("registry.harness.io");
      assertThat(parts[1]).isEqualTo(TEST_ACCOUNT_ID.toLowerCase());
      assertThat(parts[2]).isEqualTo("testRegistry");
      assertThat(parts[3]).isEqualTo("test-image");
    }
  }

  // ==================== containsKey() Tests ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testContainsKeyWithV1Yaml() {
    HarnessRegistryFunctor functorV1 = HarnessRegistryFunctor.builder().ambiance(ambianceV1).build();

    // For V1 YAML, containsKey should always return true
    assertThat(functorV1.containsKey("anyKey")).isTrue();
    assertThat(functorV1.containsKey("token")).isTrue();
    assertThat(functorV1.containsKey("unknown")).isTrue();
    assertThat(functorV1.containsKey("")).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testContainsKeyWithV0Yaml() {
    // For V0 YAML, containsKey should use default LateBindingMap behavior
    // Since we haven't added any entries, it should return false
    assertThat(harnessRegistryFunctor.containsKey("token")).isFalse();
    assertThat(harnessRegistryFunctor.containsKey("url")).isFalse();
    assertThat(harnessRegistryFunctor.containsKey("username")).isFalse();
  }

  // ==================== Edge Cases ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlPreservesProtocol() {
    ServiceHttpClientConfig httpConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://registry.harness.io").build();
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", httpConfig);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isEqualTo("http://registry.harness.io");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testBuilderCreatesInstance() {
    HarnessRegistryFunctor functor = HarnessRegistryFunctor.builder().ambiance(ambiance).build();

    assertThat(functor).isNotNull();
  }

  // ==================== Vanity URL Tests - handleRegistryURL ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlEnabled() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(TEST_VANITY_URL).build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_VANITY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlEnabledTrailingSlash() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(TEST_VANITY_URL + "/").build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_VANITY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlDisabledFallsBackToDefault() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(false);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(TEST_VANITY_URL).build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_REGISTRY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlEnabledButNullAccountDTO() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(null);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_REGISTRY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlEnabledButNullSubdomainUrl() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_REGISTRY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithVanityUrlEnabledButEmptySubdomainUrl() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL("").build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_REGISTRY_URL);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithNullAccountClientFallsBackToDefault() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    on(harnessRegistryFunctor).set("accountClient", null);

    Object result = harnessRegistryFunctor.get("url");

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(TEST_REGISTRY_URL);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetUrlWithAccountClientExceptionFallsBackToDefault() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenThrow(new RuntimeException("API call failed"));

      Object result = harnessRegistryFunctor.get("url");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(TEST_REGISTRY_URL);
      assertThat(listAppender.list.stream().anyMatch(event
                     -> event.getLevel() == Level.ERROR
                         && event.getMessage().contains("Unable to fetch the account vanity URL")))
          .isTrue();
    }
  }

  // ==================== Vanity URL Tests - getRepo ====================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithVanityUrlUsesVanityFormat() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(TEST_VANITY_URL).build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class);
         MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("account.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("account.myRegistry/my-image");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo("vanity.harness.io/oci/myRegistry/my-image");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithVanityUrlDisabledUsesStandardFormat() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(false);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(TEST_VANITY_URL).build();

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class);
         MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(accountDTO);
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("account.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("account.myRegistry/my-image");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo("registry.harness.io/" + TEST_ACCOUNT_ID.toLowerCase() + "/myRegistry/my-image");
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetRepoWithVanityUrlExceptionFallsBackToStandardFormat() {
    when(featureFlagService.isEnabled(anyString(), eq(REGISTRY_VANITY_URL_ENABLED))).thenReturn(true);
    on(harnessRegistryFunctor).set("harnessRegistryClientConfig", harnessRegistryClientConfig);
    AccountClient mockAccountClient = mock(AccountClient.class);
    on(harnessRegistryFunctor).set("accountClient", mockAccountClient);

    try (MockedStatic<CGRestUtils> mockedCGRestUtils = mockStatic(CGRestUtils.class);
         MockedStatic<HarnessRegistryConnectorUtils> mockedUtils = mockStatic(HarnessRegistryConnectorUtils.class)) {
      mockedCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenThrow(new RuntimeException("API call failed"));
      mockedUtils.when(() -> HarnessRegistryConnectorUtils.getRegistryNameFromRef("account.myRegistry"))
          .thenReturn("myRegistry");

      String result = harnessRegistryFunctor.getRepo("account.myRegistry/my-image");

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo("registry.harness.io/" + TEST_ACCOUNT_ID.toLowerCase() + "/myRegistry/my-image");
    }
  }
}
