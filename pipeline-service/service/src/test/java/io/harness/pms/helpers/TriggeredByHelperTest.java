/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggeredByHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account-1";
  private static final String USER_ID = "user-uuid";
  private static final String USER_EMAIL = "user@example.com";

  @Mock private CurrentUserHelper currentUserHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks private TriggeredByHelper triggeredByHelper;

  private GlobalContextGuard globalContextGuard;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC))).thenReturn(false);
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.setContext((Principal) null);
    if (globalContextGuard != null) {
      globalContextGuard.close();
    } else {
      GlobalContextManager.unset();
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext() {
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(null);
    assertThat(triggeredByHelper.getFromSecurityContext()).isNotNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPrincipalFromSecurityContext() {
    Principal principal = new UserPrincipal("1", "e", "u", "acc");
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(principal);
    assertThat(triggeredByHelper.getFromSecurityContext()).isNotNull();
    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();
    assertThat(triggeredBy.getIdentifier()).isEqualTo("u");
    assertThat(triggeredBy.getExtraInfoCount()).isEqualTo(2);
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo("e");
    assertThat(triggeredBy.getExtraInfoMap().get("uniqueId")).isEqualTo("");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetPrincipalFromSecurityContext_ImpersonationUser() {
    Principal principal =
        new UserPrincipal("1", "e", "u", "acc", "", new UserPrincipal("1", "admin@harness.io", "admin", "account"));
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(principal);
    assertThat(triggeredByHelper.getFromSecurityContext()).isNotNull();
    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();
    assertThat(triggeredBy.getIdentifier()).isEqualTo("u");
    assertThat(triggeredBy.getExtraInfoCount()).isEqualTo(2);
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo("e");
    assertThat(triggeredBy.getExtraInfoMap().get("uniqueId")).isEqualTo("");
    assertThat(triggeredBy.getImpersonateEmail()).isEqualTo("admin@harness.io");
    assertThat(triggeredBy.getImpersonateUsername()).isEqualTo("admin");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_WithRMGServicePrincipal_ShouldReturnReleaseOrchestrationIdentifier() {
    // Test that when principal is ServicePrincipal with name "rmservice",
    // the identifier should be "ReleaseOrchestration"
    ServicePrincipal rmgServicePrincipal = new ServicePrincipal("rmservice");

    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(rmgServicePrincipal);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy).isNotNull();
    assertThat(triggeredBy.getIdentifier()).isEqualTo("ReleaseOrchestration");
    assertThat(triggeredBy.getIdentifier()).isEqualTo(TriggeredByHelper.RMG_SERVICE_IDENTIFIER);
    // RMG service principal should not have extra info (no email, etc.)
    assertThat(triggeredBy.getExtraInfoCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_WithDifferentServicePrincipal_ShouldFallbackToDefaultLogic() {
    // Test that when principal is ServicePrincipal with a different name (not "rmservice"),
    // it should fall through to regular logic
    ServicePrincipal otherServicePrincipal = new ServicePrincipal("otherservice");

    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(otherServicePrincipal);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy).isNotNull();
    // Should NOT be ReleaseOrchestration identifier
    assertThat(triggeredBy.getIdentifier()).isNotEqualTo("ReleaseOrchestration");
    // Should be empty string as service principal doesn't have username
    assertThat(triggeredBy.getIdentifier()).isEqualTo("");
    // Should have extra info for non-RMG service principals
    assertThat(triggeredBy.getExtraInfoCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_VerifyRMGServiceConstant() {
    // Test to verify the RMG_SERVICE constant value matches expected service name
    ServicePrincipal rmgServicePrincipal = new ServicePrincipal(TriggeredByHelper.RMG_SERVICE);

    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(rmgServicePrincipal);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy).isNotNull();
    // When using RMG_SERVICE constant, should get RMG_SERVICE_IDENTIFIER
    assertThat(triggeredBy.getIdentifier()).isEqualTo(TriggeredByHelper.RMG_SERVICE_IDENTIFIER);
    assertThat(triggeredBy.getIdentifier()).isEqualTo("ReleaseOrchestration");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_rmgHybridAuthWithFlagEnabled_usesSourceUserDetails() {
    UserPrincipal sourceUser = new UserPrincipal(USER_ID, USER_EMAIL, "user", ACCOUNT_ID);
    SecurityContextBuilder.setContext(new ServicePrincipal(TriggeredByHelper.RMG_SERVICE));
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(sourceUser);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(true);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy.getIdentifier()).isEqualTo(TriggeredByHelper.RMG_SERVICE_IDENTIFIER);
    assertThat(triggeredBy.getUuid()).isEqualTo(USER_ID);
    assertThat(triggeredBy.getExtraInfoCount()).isEqualTo(2);
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo(USER_EMAIL);
    assertThat(triggeredBy.getExtraInfoMap().get("uniqueId")).isEqualTo("");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_rmgHybridAuthWithFlagDisabled_fallsBackToUserIdentifier() {
    UserPrincipal sourceUser = new UserPrincipal(USER_ID, USER_EMAIL, "user", ACCOUNT_ID);
    SecurityContextBuilder.setContext(new ServicePrincipal(TriggeredByHelper.RMG_SERVICE));
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(sourceUser);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(false);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy.getIdentifier()).isEqualTo("user");
    assertThat(triggeredBy.getUuid()).isEqualTo(USER_ID);
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo(USER_EMAIL);
    assertThat(triggeredBy.getExtraInfoMap().get(TriggeredByHelper.SOURCE_SERVICE))
        .isEqualTo(TriggeredByHelper.RMG_SERVICE);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetFromSecurityContext_flagEnabledButAuthNotRmgService_fallsBackToDefaultUserLogic() {
    UserPrincipal sourceUser = new UserPrincipal(USER_ID, USER_EMAIL, "user", ACCOUNT_ID);
    SecurityContextBuilder.setContext(new ServicePrincipal("other-service"));
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(sourceUser);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(true);

    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

    assertThat(triggeredBy.getIdentifier()).isEqualTo("user");
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo(USER_EMAIL);
  }
}
