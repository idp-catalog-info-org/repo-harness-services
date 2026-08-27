/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.catalog;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class HarnessCDIntegrationSyncRequestTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccount123";
  private static final String TEST_ORG_ID = "testOrg123";
  private static final String TEST_PROJECT_ID = "testProject123";
  private static final String TEST_IDENTIFIER = "testIdentifier";
  private static final String TEST_ACTION = "CREATE";
  private static final String TEST_SCOPE = "account.org.project";
  private static final String TEST_SCOPE_UNIQUE_ID = "uniqueId123";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilder() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .orgIdentifier(TEST_ORG_ID)
                                                  .projectIdentifier(TEST_PROJECT_ID)
                                                  .scope(TEST_SCOPE)
                                                  .scopeUniqueId(TEST_SCOPE_UNIQUE_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .build();

    assertThat(request).isNotNull();
    assertThat(request.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(request.getOrgIdentifier()).isEqualTo(TEST_ORG_ID);
    assertThat(request.getProjectIdentifier()).isEqualTo(TEST_PROJECT_ID);
    assertThat(request.getScope()).isEqualTo(TEST_SCOPE);
    assertThat(request.getScopeUniqueId()).isEqualTo(TEST_SCOPE_UNIQUE_ID);
    assertThat(request.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(request.getAction()).isEqualTo(TEST_ACTION);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithMinimalFields() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .build();

    assertThat(request).isNotNull();
    assertThat(request.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(request.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(request.getAction()).isEqualTo(TEST_ACTION);
    assertThat(request.getOrgIdentifier()).isNull();
    assertThat(request.getProjectIdentifier()).isNull();
    assertThat(request.getScope()).isNull();
    assertThat(request.getScopeUniqueId()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    HarnessCDIntegrationSyncRequest request1 = HarnessCDIntegrationSyncRequest.builder()
                                                   .accountIdentifier(TEST_ACCOUNT_ID)
                                                   .orgIdentifier(TEST_ORG_ID)
                                                   .projectIdentifier(TEST_PROJECT_ID)
                                                   .identifier(TEST_IDENTIFIER)
                                                   .action(TEST_ACTION)
                                                   .build();

    HarnessCDIntegrationSyncRequest request2 = HarnessCDIntegrationSyncRequest.builder()
                                                   .accountIdentifier(TEST_ACCOUNT_ID)
                                                   .orgIdentifier(TEST_ORG_ID)
                                                   .projectIdentifier(TEST_PROJECT_ID)
                                                   .identifier(TEST_IDENTIFIER)
                                                   .action(TEST_ACTION)
                                                   .build();

    HarnessCDIntegrationSyncRequest request3 = HarnessCDIntegrationSyncRequest.builder()
                                                   .accountIdentifier("different")
                                                   .identifier(TEST_IDENTIFIER)
                                                   .action(TEST_ACTION)
                                                   .build();

    assertThat(request1).isEqualTo(request2);
    assertThat(request1).isNotEqualTo(request3);
    assertThat(request1).isNotEqualTo(null);
    assertThat(request1).isEqualTo(request1);

    assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    assertThat(request1.hashCode()).isNotEqualTo(request3.hashCode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToString() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .build();

    String toString = request.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(TEST_ACCOUNT_ID);
    assertThat(toString).contains(TEST_IDENTIFIER);
    assertThat(toString).contains(TEST_ACTION);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInheritance() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .build();

    assertThat(request).isInstanceOf(CatalogIntegrationSyncRequest.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithNullValues() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(null)
                                                  .orgIdentifier(null)
                                                  .projectIdentifier(null)
                                                  .scope(null)
                                                  .scopeUniqueId(null)
                                                  .identifier(null)
                                                  .action(null)
                                                  .build();

    assertThat(request).isNotNull();
    assertThat(request.getAccountIdentifier()).isNull();
    assertThat(request.getOrgIdentifier()).isNull();
    assertThat(request.getProjectIdentifier()).isNull();
    assertThat(request.getScope()).isNull();
    assertThat(request.getScopeUniqueId()).isNull();
    assertThat(request.getIdentifier()).isNull();
    assertThat(request.getAction()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAccountLevelScope() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .scope("account")
                                                  .build();

    assertThat(request.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(request.getOrgIdentifier()).isNull();
    assertThat(request.getProjectIdentifier()).isNull();
    assertThat(request.getScope()).isEqualTo("account");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testOrgLevelScope() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .orgIdentifier(TEST_ORG_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .scope("account.org")
                                                  .build();

    assertThat(request.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(request.getOrgIdentifier()).isEqualTo(TEST_ORG_ID);
    assertThat(request.getProjectIdentifier()).isNull();
    assertThat(request.getScope()).isEqualTo("account.org");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testProjectLevelScope() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_ID)
                                                  .orgIdentifier(TEST_ORG_ID)
                                                  .projectIdentifier(TEST_PROJECT_ID)
                                                  .identifier(TEST_IDENTIFIER)
                                                  .action(TEST_ACTION)
                                                  .scope(TEST_SCOPE)
                                                  .build();

    assertThat(request.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(request.getOrgIdentifier()).isEqualTo(TEST_ORG_ID);
    assertThat(request.getProjectIdentifier()).isEqualTo(TEST_PROJECT_ID);
    assertThat(request.getScope()).isEqualTo(TEST_SCOPE);
  }
}
