/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpCommonServiceFeatureFlagTest extends CategoryTest {
  static final String ACCOUNT_ID = "testAccount";

  @InjectMocks IdpCommonService idpCommonService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) AccountClient accountClient;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    SecurityContextBuilder.setContext(new ServicePrincipal("IDP_SERVICE"));
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  @SuppressWarnings("unchecked")
  private void mockFF(FeatureName ff, boolean value) {
    try {
      Call<RestResponse<Boolean>> call = org.mockito.Mockito.mock(Call.class);
      when(accountClient.isFeatureFlagEnabled(ff.name(), ACCOUNT_ID)).thenReturn(call);
      when(call.execute()).thenReturn(Response.success(new RestResponse<>(value)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testIdpScorecardTiersEnabledReturnsTrue() {
    mockFF(FeatureName.IDP_SCORECARD_TIERS, true);

    assertThat(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testIdpScorecardTiersEnabledReturnsFalse() {
    mockFF(FeatureName.IDP_SCORECARD_TIERS, false);

    assertThat(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsLegacyCDFlow_IntOff_CdAdOn_ReturnsTrue() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);

    assertThat(idpCommonService.isLegacyCDFlow(ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsLegacyCDFlow_IntOff_CdAdOff_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);

    assertThat(idpCommonService.isLegacyCDFlow(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsLegacyCDFlow_IntOn_CdAdOn_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);

    assertThat(idpCommonService.isLegacyCDFlow(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsLegacyCDFlow_IntOn_CdAdOff_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);

    assertThat(idpCommonService.isLegacyCDFlow(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsHarnessScopeEnabled_IntOn_AggOn_ReturnsTrue() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    assertThat(idpCommonService.isHarnessScopeEnabled(ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsHarnessScopeEnabled_IntOff_CdAdOff_AggOn_ReturnsTrue() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    assertThat(idpCommonService.isHarnessScopeEnabled(ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsHarnessScopeEnabled_IntOff_CdAdOn_AggOn_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    assertThat(idpCommonService.isHarnessScopeEnabled(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsHarnessScopeEnabled_IntOn_AggOff_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, false);

    assertThat(idpCommonService.isHarnessScopeEnabled(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testIsHarnessScopeEnabled_IntOff_CdAdOff_AggOff_ReturnsFalse() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, false);

    assertThat(idpCommonService.isHarnessScopeEnabled(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testNewFlowCheck_IntOn_DoesNotThrow() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);

    idpCommonService.newFlowCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testNewFlowCheck_IntOff_CdAdOff_DoesNotThrow() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);

    idpCommonService.newFlowCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testNewFlowCheck_IntOff_CdAdOn_Throws() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);

    assertThatThrownBy(() -> idpCommonService.newFlowCheck(ACCOUNT_ID)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testHarnessScopeCheck_IntOn_AggOn_DoesNotThrow() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    idpCommonService.harnessScopeCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testHarnessScopeCheck_IntOn_AggOff_Throws() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, false);

    assertThatThrownBy(() -> idpCommonService.harnessScopeCheck(ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testHarnessScopeCheck_LegacyFlow_Throws() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    assertThatThrownBy(() -> idpCommonService.harnessScopeCheck(ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAllowHierarchyKind_ScopeEnabled_DoesNotThrow() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(ACCOUNT_ID, true, false);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAllowHierarchyKind_ScopeDisabled_ThrowsException() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, true);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    assertThatThrownBy(() -> idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(ACCOUNT_ID, true, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAllowHierarchyKind_IntOn_AggOff_ThrowsException() {
    mockFF(FeatureName.IDP_INTEGRATIONS, true);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, false);

    assertThatThrownBy(() -> idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(ACCOUNT_ID, true, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAllowHierarchyKind_IntOff_CdAdOff_AggOn_DoesNotThrow() {
    mockFF(FeatureName.IDP_INTEGRATIONS, false);
    mockFF(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY, false);
    mockFF(FeatureName.IDP_AGGREGATION_RULES, true);

    idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(ACCOUNT_ID, true, false);
  }
}
