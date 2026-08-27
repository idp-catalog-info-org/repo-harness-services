/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SUMEET_RAI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.rule.Owner;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.STO)
public class StoFunctorTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_BASE_URL = "https://app.harness.io/sto";
  private static final String TEST_TOKEN = "minted-sto-token-value";
  private static final List<String> STO_PLUGIN_AUDIENCE = List.of("sto-plugin");

  private Ambiance ambianceV0;
  private Ambiance ambianceV1;
  private STOServiceUtils stoServiceUtils;
  private STOServiceConfig stoServiceConfig;

  @Before
  public void setUp() {
    ambianceV0 = buildAmbiance(HarnessYamlVersion.V0);
    ambianceV1 = buildAmbiance(HarnessYamlVersion.V1);

    stoServiceUtils = mock(STOServiceUtils.class);
    stoServiceConfig = STOServiceConfig.builder().baseUrl(TEST_BASE_URL).globalToken("global").build();
  }

  private Ambiance buildAmbiance(String harnessVersion) {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", "test-org")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setHarnessVersion(harnessVersion)
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                         .build())
        .build();
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetServiceTokenMintsTokenOnV0() {
    when(stoServiceUtils.getSTOServiceToken(eq(TEST_ACCOUNT_ID), anyList())).thenReturn(TEST_TOKEN);
    StoFunctor functor = new StoFunctor(ambianceV0, stoServiceUtils);

    assertThat(functor.get("serviceToken")).isEqualTo(TEST_TOKEN);
    verify(stoServiceUtils, times(1)).getSTOServiceToken(TEST_ACCOUNT_ID, STO_PLUGIN_AUDIENCE);
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetTokenAliasMintsTokenOnV1() {
    when(stoServiceUtils.getSTOServiceToken(eq(TEST_ACCOUNT_ID), anyList())).thenReturn(TEST_TOKEN);
    StoFunctor functor = new StoFunctor(ambianceV1, stoServiceUtils);

    assertThat(functor.get("token")).isEqualTo(TEST_TOKEN);
    verify(stoServiceUtils, times(1)).getSTOServiceToken(TEST_ACCOUNT_ID, STO_PLUGIN_AUDIENCE);
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetServiceEndpointReturnsBaseUrl() {
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(stoServiceConfig);
    StoFunctor functor = new StoFunctor(ambianceV0, stoServiceUtils);

    assertThat(functor.get("serviceEndpoint")).isEqualTo(TEST_BASE_URL);
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetUnknownKeyReturnsNull() {
    StoFunctor functor = new StoFunctor(ambianceV0, stoServiceUtils);

    assertThat(functor.get("unknown")).isNull();
    assertThat(functor.get(123)).isNull();
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testContainsKeyAlwaysTrueForBothVersions() {
    assertThat(new StoFunctor(ambianceV0, stoServiceUtils).containsKey("serviceToken")).isTrue();
    assertThat(new StoFunctor(ambianceV1, stoServiceUtils).containsKey("serviceToken")).isTrue();
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetServiceTokenReturnsNullWhenUtilsAbsent() {
    StoFunctor functor = new StoFunctor(ambianceV0, null);

    assertThat(functor.get("serviceToken")).isNull();
  }

  @Test
  @Owner(developers = SUMEET_RAI)
  @Category(UnitTests.class)
  public void testGetServiceTokenReturnsNullOnException() {
    when(stoServiceUtils.getSTOServiceToken(eq(TEST_ACCOUNT_ID), anyList()))
        .thenThrow(new RuntimeException("STO core unreachable"));
    StoFunctor functor = new StoFunctor(ambianceV0, stoServiceUtils);

    assertThat(functor.get("serviceToken")).isNull();
  }
}
