/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.TARUN_ACHARYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.qwietserviceclient.QwietServiceUtils;
import io.harness.qwietserviceclient.dto.QwietTokenData;
import io.harness.rule.Owner;
import io.harness.sto.beans.entities.QwietServiceConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.STO)
public class QwietFunctorTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_BASE_URL = "https://app.stg.shiftleft.io";
  private static final String TEST_GLOBAL_TOKEN = "test-global-token";
  private static final String TEST_ACCESS_TOKEN = "test-access-token-value";
  private static final String TEST_ORG_ID = "test-organization-id";

  private Ambiance ambianceV0;
  private Ambiance ambianceV1;

  private QwietFunctor qwietFunctor;
  private QwietServiceUtils qwietServiceUtils;
  private QwietServiceConfig qwietServiceConfig;

  @Before
  public void setUp() {
    ambianceV0 =
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

    qwietServiceUtils = mock(QwietServiceUtils.class);
    qwietServiceConfig = QwietServiceConfig.builder().baseUrl(TEST_BASE_URL).globalToken(TEST_GLOBAL_TOKEN).build();

    qwietFunctor = QwietFunctor.builder().ambiance(ambianceV1).build();
    on(qwietFunctor).set("qwietServiceUtils", qwietServiceUtils);
    on(qwietFunctor).set("qwietServiceConfig", qwietServiceConfig);
  }

  private QwietTokenData buildTokenData() {
    return QwietTokenData.builder().value(TEST_ACCESS_TOKEN).organizationId(TEST_ORG_ID).build();
  }

  // ==================== supportsKey() Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testSupportsKeyWithQwietKey() {
    assertThat(qwietFunctor.supportsKey("qwiet")).isTrue();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testSupportsKeyWithOtherKeys() {
    assertThat(qwietFunctor.supportsKey("accessToken")).isFalse();
    assertThat(qwietFunctor.supportsKey("shiftleft")).isFalse();
    assertThat(qwietFunctor.supportsKey("")).isFalse();
    assertThat(qwietFunctor.supportsKey("QWIET")).isFalse();
  }

  // ==================== get() - Happy Path Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetAccessTokenReturnsCachedValue() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    Object result = qwietFunctor.get("accessToken");

    assertThat(result).isEqualTo(TEST_ACCESS_TOKEN);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetOrganizationIdReturnsCachedValue() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    Object result = qwietFunctor.get("organizationId");

    assertThat(result).isEqualTo(TEST_ORG_ID);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetServiceEndpointReturnsBaseUrl() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    Object result = qwietFunctor.get("serviceEndpoint");

    assertThat(result).isEqualTo(TEST_BASE_URL);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetIsActiveLicenseTrueOnSuccess() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    Object result = qwietFunctor.get("isActiveLicense");

    assertThat(result).isEqualTo("true");
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetErrorIsNullOnSuccess() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    Object result = qwietFunctor.get("error");

    assertThat(result).isNull();
  }

  // ==================== get() - Fresh Fetch Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testTokenFetchedOnEveryTokenBackedAccess() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    qwietFunctor.get("accessToken");
    qwietFunctor.get("organizationId");
    qwietFunctor.get("accessToken");

    verify(qwietServiceUtils, times(3)).getQwietTokenAndOrgIdForAccount(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testServiceEndpointDoesNotTriggerTokenFetch() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    qwietFunctor.get("serviceEndpoint");
    qwietFunctor.get("serviceEndpoint");

    verify(qwietServiceUtils, times(0)).getQwietTokenAndOrgIdForAccount(anyString());
  }

  // ==================== get() - Failure Path Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetAccessTokenReturnsNullOnFetchFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("UNAUTHORIZED: Please contact sales to use Harness Security Scanners"));

    Object result = qwietFunctor.get("accessToken");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetOrganizationIdReturnsNullOnFetchFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("network error"));

    Object result = qwietFunctor.get("organizationId");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetIsActiveLicenseFalseOnFetchFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("UNAUTHORIZED"));

    Object result = qwietFunctor.get("isActiveLicense");

    assertThat(result).isEqualTo("false");
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetErrorPopulatedOnFetchFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("UNAUTHORIZED"));

    Object result = qwietFunctor.get("error");

    assertThat(result).isInstanceOf(String.class);
    assertThat((String) result).contains("RuntimeException");
    assertThat((String) result).contains("UNAUTHORIZED");
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testServiceEndpointStillResolvesOnFetchFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("UNAUTHORIZED"));

    Object result = qwietFunctor.get("serviceEndpoint");

    assertThat(result).isEqualTo(TEST_BASE_URL);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testFetchAttemptedOnEveryAccessOnFailure() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString()))
        .thenThrow(new RuntimeException("UNAUTHORIZED"));

    qwietFunctor.get("accessToken");
    qwietFunctor.get("organizationId");
    qwietFunctor.get("error");

    verify(qwietServiceUtils, times(3)).getQwietTokenAndOrgIdForAccount(TEST_ACCOUNT_ID);
  }

  // ==================== get() - Module Not Installed Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetAccessTokenWhenQwietServiceUtilsNotInjected() {
    QwietFunctor functorWithoutUtils = QwietFunctor.builder().ambiance(ambianceV1).build();
    on(functorWithoutUtils).set("qwietServiceConfig", qwietServiceConfig);

    Object result = functorWithoutUtils.get("accessToken");

    assertThat(result).isNull();
    assertThat((String) functorWithoutUtils.get("error")).contains("QwietServiceUtils not injected");
    assertThat(functorWithoutUtils.get("isActiveLicense")).isEqualTo("false");
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetAccessTokenWhenQwietServiceConfigNotInjected() {
    QwietFunctor functorWithoutConfig = QwietFunctor.builder().ambiance(ambianceV1).build();
    on(functorWithoutConfig).set("qwietServiceUtils", qwietServiceUtils);

    Object result = functorWithoutConfig.get("accessToken");

    assertThat(result).isNull();
    assertThat((String) functorWithoutConfig.get("error")).contains("QwietServiceConfig not injected");
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetServiceEndpointWhenConfigNotInjectedReturnsNull() {
    QwietFunctor functorWithoutConfig = QwietFunctor.builder().ambiance(ambianceV1).build();
    on(functorWithoutConfig).set("qwietServiceUtils", qwietServiceUtils);

    Object result = functorWithoutConfig.get("serviceEndpoint");

    assertThat(result).isNull();
  }

  // ==================== get() - Unknown / Invalid Key Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetWithUnknownKeyReturnsNull() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(buildTokenData());

    assertThat(qwietFunctor.get("unknownKey")).isNull();
    assertThat(qwietFunctor.get("token")).isNull();
    assertThat(qwietFunctor.get("")).isNull();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetWithNonStringKeyReturnsNull() {
    Object result = qwietFunctor.get(123);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetWithNullKeyReturnsNull() {
    Object result = qwietFunctor.get(null);

    assertThat(result).isNull();
  }

  // ==================== get() - Null Token Data Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetAccessTokenWhenTokenDataIsNull() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(null);

    Object result = qwietFunctor.get("accessToken");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testGetOrganizationIdWhenTokenDataIsNull() {
    when(qwietServiceUtils.getQwietTokenAndOrgIdForAccount(anyString())).thenReturn(null);

    Object result = qwietFunctor.get("organizationId");

    assertThat(result).isNull();
  }

  // ==================== containsKey() Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testContainsKeyWithV1YamlAlwaysReturnsTrue() {
    QwietFunctor functorV1 = QwietFunctor.builder().ambiance(ambianceV1).build();

    assertThat(functorV1.containsKey("accessToken")).isTrue();
    assertThat(functorV1.containsKey("organizationId")).isTrue();
    assertThat(functorV1.containsKey("unknown")).isTrue();
    assertThat(functorV1.containsKey("")).isTrue();
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testContainsKeyWithV0YamlUsesDefaultBehavior() {
    QwietFunctor functorV0 = QwietFunctor.builder().ambiance(ambianceV0).build();

    assertThat(functorV0.containsKey("accessToken")).isFalse();
    assertThat(functorV0.containsKey("organizationId")).isFalse();
    assertThat(functorV0.containsKey("unknown")).isFalse();
  }

  // ==================== Builder Tests ====================

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testBuilderCreatesInstance() {
    QwietFunctor functor = QwietFunctor.builder().ambiance(ambianceV1).build();

    assertThat(functor).isNotNull();
  }
}
