/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.events;

import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_INTEGRATIONS;
import static io.harness.idp.integrations.events.CatalogIntegrationUpdateEvent.CATALOG_INTEGRATION_UPDATED;
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
public class CatalogIntegrationUpdateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_INTEGRATION_IDENTIFIER = "test_integration";
  private static final String HARNESS_CD_IDENTIFIER = "_harness_cd";

  private HarnessCDIntegrationEntity oldHarnessCDEntity;
  private HarnessCDIntegrationEntity newHarnessCDEntity;
  private HarnessCDIntegrationEntity oldRegularEntity;
  private HarnessCDIntegrationEntity newRegularEntity;

  @Before
  public void setup() {
    List<String> oldScopes = Arrays.asList("account", "org1");
    List<String> newScopes = Arrays.asList("account", "org1", "project1");

    oldHarnessCDEntity = HarnessCDIntegrationEntity.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .identifier(HARNESS_CD_IDENTIFIER)
                             .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                             .integration(IntegrationEntity.Integration.CATALOG)
                             .scopesToSync(String.join(",", oldScopes))
                             .enabled(false)
                             .autoDeletion(false)
                             .build();

    newHarnessCDEntity = HarnessCDIntegrationEntity.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .identifier(HARNESS_CD_IDENTIFIER)
                             .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                             .integration(IntegrationEntity.Integration.CATALOG)
                             .scopesToSync(String.join(",", newScopes))
                             .enabled(true)
                             .autoDeletion(true)
                             .build();

    oldRegularEntity = HarnessCDIntegrationEntity.builder()
                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                           .identifier(TEST_INTEGRATION_IDENTIFIER)
                           .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                           .integration(IntegrationEntity.Integration.CATALOG)
                           .scopesToSync(String.join(",", oldScopes))
                           .enabled(false)
                           .autoDeletion(false)
                           .build();

    newRegularEntity = HarnessCDIntegrationEntity.builder()
                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                           .identifier(TEST_INTEGRATION_IDENTIFIER)
                           .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                           .integration(IntegrationEntity.Integration.CATALOG)
                           .scopesToSync(String.join(",", newScopes))
                           .enabled(true)
                           .autoDeletion(true)
                           .build();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstructorWithHarnessCDEntity() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldHarnessCDEntity, newHarnessCDEntity);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(event.getOldEntity()).isEqualTo(oldHarnessCDEntity);
    assertThat(event.getNewEntity()).isEqualTo(newHarnessCDEntity);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    CatalogIntegrationUpdateEvent event = new CatalogIntegrationUpdateEvent();
    assertThat(event).isNotNull();
    assertThat(event.getAccountIdentifier()).isNull();
    assertThat(event.getOldEntity()).isNull();
    assertThat(event.getNewEntity()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceScopeWithHarnessCDEntity() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldHarnessCDEntity, newHarnessCDEntity);

    ResourceScope scope = event.getResourceScope();

    assertThat(scope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) scope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceWithHarnessCDEntity() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldHarnessCDEntity, newHarnessCDEntity);

    Resource resource = event.getResource();

    assertThat(resource).isNotNull();
    assertThat(resource.getIdentifier())
        .isEqualTo(TEST_ACCOUNT_IDENTIFIER + "_" + newHarnessCDEntity.getParentType() + "_" + HARNESS_CD_IDENTIFIER);
    assertThat(resource.getType()).isEqualTo(IDP_CATALOG_INTEGRATIONS);
    assertThat(resource.getLabels()).isNotNull();
    assertThat(resource.getLabels()).containsKey(LABEL_KEY_RESOURCE_NAME);
    assertThat(resource.getLabels().get(LABEL_KEY_RESOURCE_NAME)).isEqualTo("Harness CD");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceWithRegularEntity() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldRegularEntity, newRegularEntity);

    Resource resource = event.getResource();

    assertThat(resource).isNotNull();
    assertThat(resource.getIdentifier())
        .isEqualTo(
            TEST_ACCOUNT_IDENTIFIER + "_" + newRegularEntity.getParentType() + "_" + TEST_INTEGRATION_IDENTIFIER);
    assertThat(resource.getType()).isEqualTo(IDP_CATALOG_INTEGRATIONS);
    assertThat(resource.getLabels()).isNotNull();
    assertThat(resource.getLabels()).containsKey(LABEL_KEY_RESOURCE_NAME);
    assertThat(resource.getLabels().get(LABEL_KEY_RESOURCE_NAME)).isEqualTo(TEST_INTEGRATION_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEventType() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldHarnessCDEntity, newHarnessCDEntity);

    assertThat(event.getEventType()).isEqualTo(CATALOG_INTEGRATION_UPDATED);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEventConstant() {
    assertThat(CatalogIntegrationUpdateEvent.CATALOG_INTEGRATION_UPDATED).isEqualTo("CATALOG_INTEGRATION_UPDATED");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResourceUsesNewEntityValues() {
    CatalogIntegrationUpdateEvent event =
        new CatalogIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldHarnessCDEntity, newHarnessCDEntity);

    Resource resource = event.getResource();

    assertThat(resource.getIdentifier()).contains(newHarnessCDEntity.getIdentifier());
    assertThat(resource.getIdentifier()).contains(newHarnessCDEntity.getParentType().toString());
    assertThat(resource.getIdentifier()).contains(newHarnessCDEntity.getAccountIdentifier());
  }
}
