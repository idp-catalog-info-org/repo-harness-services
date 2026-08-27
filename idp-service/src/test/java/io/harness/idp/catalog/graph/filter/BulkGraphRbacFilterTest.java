/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.graph.filter;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BulkGraphRbacFilterTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-id";

  AutoCloseable openMocks;

  @Mock CatalogServiceHelper catalogServiceHelper;
  @InjectMocks BulkGraphRbacFilter bulkGraphRbacFilter;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    SecurityContextBuilder.unsetCompleteContext();
  }

  @After
  public void tearDown() throws Exception {
    SecurityContextBuilder.unsetCompleteContext();
    openMocks.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFilterPermittedReturnsEmptyForNullOrEmptyEntities() {
    assertThat(bulkGraphRbacFilter.filterPermitted(TEST_ACCOUNT_ID, null)).isEmpty();
    assertThat(bulkGraphRbacFilter.filterPermitted(TEST_ACCOUNT_ID, List.of())).isEmpty();

    verifyNoInteractions(catalogServiceHelper);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFilterPermittedSkipsRbacWhenPrincipalMissing() {
    List<CatalogEntity> entities = List.of(
        createEntity("Component", "payment-service", null, null), createEntity("API", "payment-api", "org1", "proj1"));

    List<CatalogEntity> result = bulkGraphRbacFilter.filterPermitted(TEST_ACCOUNT_ID, entities);

    assertThat(result).containsExactlyElementsOf(entities);
    verifyNoInteractions(catalogServiceHelper);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFilterPermittedUsesCatalogPermissionsAndEntityRefMapping() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", TEST_ACCOUNT_ID));
    CatalogEntity component = createEntity("Component", "payment-service", null, null);
    CatalogEntity api = createEntity("API", "payment-api", "org1", "proj1");
    List<CatalogEntity> entities = List.of(component, api);
    Map<String, String> expectedEntityRefToOwner = new HashMap<>();
    expectedEntityRefToOwner.put("component:account/payment-service", null);
    expectedEntityRefToOwner.put("api:account.org1.proj1/payment-api", null);

    when(catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
             TEST_ACCOUNT_ID, expectedEntityRefToOwner, "view"))
        .thenReturn(Set.of("api:account.org1.proj1/payment-api"));

    List<CatalogEntity> result = bulkGraphRbacFilter.filterPermitted(TEST_ACCOUNT_ID, entities);

    verify(catalogServiceHelper)
        .checkEntityRefsPermissionWithOwnerFallback(TEST_ACCOUNT_ID, expectedEntityRefToOwner, "view");
    assertThat(result).containsExactly(api);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFilterPermittedIgnoresUnknownAndMalformedPermittedRefs() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", TEST_ACCOUNT_ID));
    CatalogEntity entity = createEntity("Component", "payment-service", null, null);
    Map<String, String> expectedEntityRefToOwner = new HashMap<>();
    expectedEntityRefToOwner.put("component:account/payment-service", null);

    when(catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
             TEST_ACCOUNT_ID, expectedEntityRefToOwner, "view"))
        .thenReturn(Set.of("invalid-ref", "component:account/unknown-service", "component:account/payment-service"));

    List<CatalogEntity> result = bulkGraphRbacFilter.filterPermitted(TEST_ACCOUNT_ID, List.of(entity));

    assertThat(result).containsExactly(entity);
  }

  private InlineCatalogEntity createEntity(
      String kind, String identifier, String orgIdentifier, String projectIdentifier) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_ID)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .referenceType(ReferenceType.INLINE)
        .apiVersion("harness.io/v1")
        .kind(kind)
        .yaml("yaml")
        .build();
  }
}
