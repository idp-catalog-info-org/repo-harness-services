/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.rmg.helper.RmgSourcePrincipalRbacHelper;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PrincipalInfoHelperTest extends CategoryTest {
  @InjectMocks PrincipalInfoHelper principalInfoHelper;
  @Mock RmgSourcePrincipalRbacHelper rmgSourcePrincipalRbacHelper;

  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    SecurityContextBuilder.unsetCompleteContext();
    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBuildRetryInfo() {
    for (io.harness.security.dto.PrincipalType value : io.harness.security.dto.PrincipalType.values()) {
      PrincipalType principalType = principalInfoHelper.fromSecurityPrincipalType(value);
      assertThat(principalType).isNotNull();
    }
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoFromSecurityContext() {
    assertThatThrownBy(() -> principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Principal cannot be null");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoUsesSourcePrincipalForTriggerExecutor() {
    ServiceAccountPrincipal executor =
        new ServiceAccountPrincipal("testService", "sa@test.com", "Test Service", ACCOUNT_ID, "saUniqueId");
    SourcePrincipalContextBuilder.setSourcePrincipal(executor);
    SecurityContextBuilder.setContext(new ServicePrincipal("pipeline-service"));

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("testService");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.SERVICE_ACCOUNT);
    assertThat(principalInfo.getShouldValidateRbac()).isTrue();
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("saUniqueId");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoUsesSecurityContextWhenBothPrincipalsMatch() {
    UserPrincipal user = new UserPrincipal("testUser", "user@test.com", "Test User", ACCOUNT_ID);
    SecurityContextBuilder.setContext(user);
    SourcePrincipalContextBuilder.setSourcePrincipal(user);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("testUser");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.USER);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("testUser");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoUsesSecurityContextWhenSourcePrincipalIsUnset() {
    UserPrincipal user = new UserPrincipal("testUser", "user@test.com", "Test User", ACCOUNT_ID);
    SecurityContextBuilder.setContext(user);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("testUser");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.USER);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("testUser");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoUsesSecurityContextForLegacyTriggerFlow() {
    ServicePrincipal pipelineService = new ServicePrincipal("pipeline-service");
    SecurityContextBuilder.setContext(pipelineService);
    SourcePrincipalContextBuilder.setSourcePrincipal(pipelineService);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("pipeline-service");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.SERVICE);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("pipeline-service");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoFromSecurityContext_usesRmgSourceUserWhenPresent() {
    ExecutionPrincipalInfo rmgSourceExecutionPrincipal = ExecutionPrincipalInfo.newBuilder()
                                                             .setPrincipal("user-uuid")
                                                             .setPrincipalType(PrincipalType.USER)
                                                             .setShouldValidateRbac(true)
                                                             .build();
    when(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo())
        .thenReturn(Optional.of(rmgSourceExecutionPrincipal));

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo).isEqualTo(rmgSourceExecutionPrincipal);
    assertThat(principalInfo.getPrincipal()).isEqualTo("user-uuid");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.USER);
    assertThat(principalInfo.getShouldValidateRbac()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoFromSecurityContext_fallsBackToSecurityContext() {
    when(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo()).thenReturn(Optional.empty());
    SecurityContextBuilder.setContext(new ServicePrincipal("pipeline-service"));

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("pipeline-service");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.SERVICE);
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testExtractUniqueId_serviceAccountWithNullUniqueId_returnsEmptyString() {
    when(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo()).thenReturn(Optional.empty());
    ServiceAccountPrincipal sa = new ServiceAccountPrincipal("my-sa", "sa@test.com", "SA", ACCOUNT_ID);
    SecurityContextBuilder.setContext(sa);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("my-sa");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.SERVICE_ACCOUNT);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testExtractUniqueId_serviceAccountWithUniqueId_returnsUniqueId() {
    when(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo()).thenReturn(Optional.empty());
    ServiceAccountPrincipal sa =
        new ServiceAccountPrincipal("my-sa", "sa@test.com", "SA", ACCOUNT_ID, "sa-unique-id-123");
    SecurityContextBuilder.setContext(sa);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("my-sa");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.SERVICE_ACCOUNT);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("sa-unique-id-123");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testExtractUniqueId_userWithExplicitUniqueId_returnsUniqueId() {
    when(rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo()).thenReturn(Optional.empty());
    UserPrincipal user = new UserPrincipal("userId", "user@test.com", "User", ACCOUNT_ID, "role", "user-unique-id-456");
    SecurityContextBuilder.setContext(user);

    ExecutionPrincipalInfo principalInfo = principalInfoHelper.getPrincipalInfoFromSecurityContext();

    assertThat(principalInfo.getPrincipal()).isEqualTo("userId");
    assertThat(principalInfo.getPrincipalType()).isEqualTo(PrincipalType.USER);
    assertThat(principalInfo.getPrincipalUniqueId()).isEqualTo("user-unique-id-456");
  }
}
