/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rmg.helper;

import static io.harness.beans.FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
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

public class RmgSourcePrincipalRbacHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account-1";
  private static final String USER_ID = "user-uuid";

  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks private RmgSourcePrincipalRbacHelper rmgSourcePrincipalRbacHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.setContext((io.harness.security.dto.Principal) null);
    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getRmgSourceUserPrincipal_whenHybridAuthAndFlagEnabled_returnsUser() {
    SecurityContextBuilder.setContext(new ServicePrincipal(RmgConstants.RMG_SERVICE));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID));
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(true);

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceUserPrincipal())
        .isPresent()
        .get()
        .extracting(UserPrincipal::getName)
        .isEqualTo(USER_ID);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getRmgSourceUserPrincipal_whenFlagDisabled_returnsEmpty() {
    SecurityContextBuilder.setContext(new ServicePrincipal(RmgConstants.RMG_SERVICE));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID));
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(false);

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceUserPrincipal()).isEmpty();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getRmgSourceUserPrincipal_whenNotRmgService_returnsEmpty() {
    SecurityContextBuilder.setContext(new ServicePrincipal("other-service"));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID));

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceUserPrincipal()).isEmpty();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void toAccessControlPrincipal_mapsUserPrincipal() {
    UserPrincipal userPrincipal = new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID);

    Principal aclPrincipal = rmgSourcePrincipalRbacHelper.toAccessControlPrincipal(userPrincipal);

    assertThat(aclPrincipal.getPrincipalType()).isEqualTo(PrincipalType.USER);
    assertThat(aclPrincipal.getPrincipalIdentifier()).isEqualTo(USER_ID);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getRmgSourceExecutionPrincipalInfo_whenHybridAuthAndFlagEnabled_returnsExecutionPrincipalInfo() {
    SecurityContextBuilder.setContext(new ServicePrincipal(RmgConstants.RMG_SERVICE));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID));
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(true);

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo())
        .isPresent()
        .get()
        .satisfies(executionPrincipalInfo -> {
          assertThat(executionPrincipalInfo.getPrincipal()).isEqualTo(USER_ID);
          assertThat(executionPrincipalInfo.getPrincipalType())
              .isEqualTo(io.harness.pms.contracts.plan.PrincipalType.USER);
          assertThat(executionPrincipalInfo.getShouldValidateRbac()).isTrue();
          assertThat(executionPrincipalInfo.getPrincipalUniqueId()).isEqualTo(USER_ID);
        });
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void getRmgSourceExecutionPrincipalInfo_usesUniqueIdNotNameWhenDistinct() {
    String distinctUniqueId = "user-unique-id-different-from-name";
    SecurityContextBuilder.setContext(new ServicePrincipal(RmgConstants.RMG_SERVICE));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID, "", distinctUniqueId));
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)).thenReturn(true);

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo())
        .isPresent()
        .get()
        .satisfies(executionPrincipalInfo -> {
          assertThat(executionPrincipalInfo.getPrincipal()).isEqualTo(USER_ID);
          assertThat(executionPrincipalInfo.getPrincipalUniqueId()).isEqualTo(distinctUniqueId);
        });
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getRmgSourceExecutionPrincipalInfo_whenNotRmgSource_returnsEmpty() {
    SecurityContextBuilder.setContext(new ServicePrincipal("other-service"));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(USER_ID, "user@example.com", "user", ACCOUNT_ID));

    assertThat(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo()).isEmpty();
  }
}
