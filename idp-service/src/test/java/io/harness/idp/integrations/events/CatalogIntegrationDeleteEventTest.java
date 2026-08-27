/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.events;

import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_INTEGRATIONS;
import static io.harness.idp.integrations.events.CatalogIntegrationDeleteEvent.CATALOG_INTEGRATION_DELETED;
import static io.harness.ng.core.ResourceConstants.LABEL_KEY_RESOURCE_NAME;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.HarnessCDIntegrationEntity;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogIntegrationDeleteEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_INTEGRATION_IDENTIFIER = "test_integration";
  private static final String HARNESS_CD_IDENTIFIER = "_harness_cd";

  private HarnessCDIntegrationEntity harnessCDEntity;
  private HarnessCDIntegrationEntity regularEntity;

  @Before
  public void setup() {
    List<String> scopes = Arrays.asList("account", "org1", "project1");

    harnessCDEntity = HarnessCDIntegrationEntity.builder()
                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .identifier(HARNESS_CD_IDENTIFIER)
                          .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                          .integration(IntegrationEntity.Integration.CATALOG)
                          .scopesToSync(String.join(",", scopes))
                          .enabled(true)
                          .autoDeletion(false)
                          .build();

    regularEntity = HarnessCDIntegrationEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier(TEST_INTEGRATION_IDENTIFIER)
                        .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                        .integration(IntegrationEntity.Integration.CATALOG)
                        .scopesToSync(String.join(",", scopes))
                        .enabled(true)
                        .autoDeletion(false)
                        .build();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstructorWithHarnessCDEntity() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, harnessCDEntity);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(event.getEntity()).isEqualTo(harnessCDEntity);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent();
    assertThat(event).isNotNull();
    assertThat(event.getAccountIdentifier()).isNull();
    assertThat(event.getEntity()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceScopeWithHarnessCDEntity() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, harnessCDEntity);

    ResourceScope scope = event.getResourceScope();

    assertThat(scope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) scope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceWithHarnessCDEntity() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, harnessCDEntity);

    Resource resource = event.getResource();

    assertThat(resource).isNotNull();
    assertThat(resource.getIdentifier())
        .isEqualTo(TEST_ACCOUNT_IDENTIFIER + "_" + harnessCDEntity.getParentType() + "_" + HARNESS_CD_IDENTIFIER);
    assertThat(resource.getType()).isEqualTo(IDP_CATALOG_INTEGRATIONS);
    assertThat(resource.getLabels()).isNotNull();
    assertThat(resource.getLabels()).containsKey(LABEL_KEY_RESOURCE_NAME);
    assertThat(resource.getLabels().get(LABEL_KEY_RESOURCE_NAME)).isEqualTo("Harness CD");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceWithRegularEntity() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, regularEntity);

    Resource resource = event.getResource();

    assertThat(resource).isNotNull();
    assertThat(resource.getIdentifier())
        .isEqualTo(TEST_ACCOUNT_IDENTIFIER + "_" + regularEntity.getParentType() + "_" + TEST_INTEGRATION_IDENTIFIER);
    assertThat(resource.getType()).isEqualTo(IDP_CATALOG_INTEGRATIONS);
    assertThat(resource.getLabels()).isNotNull();
    assertThat(resource.getLabels()).containsKey(LABEL_KEY_RESOURCE_NAME);
    assertThat(resource.getLabels().get(LABEL_KEY_RESOURCE_NAME)).isEqualTo(TEST_INTEGRATION_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEventType() {
    CatalogIntegrationDeleteEvent event = new CatalogIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, harnessCDEntity);

    assertThat(event.getEventType()).isEqualTo(CATALOG_INTEGRATION_DELETED);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEventConstant() {
    assertThat(CatalogIntegrationDeleteEvent.CATALOG_INTEGRATION_DELETED).isEqualTo("CATALOG_INTEGRATION_DELETED");
  }
}
