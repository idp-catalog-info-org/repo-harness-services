/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogApiEntity;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogDomainEntity;
import io.harness.idp.backstage.entities.BackstageCatalogGroupEntity;
import io.harness.idp.backstage.entities.BackstageCatalogLocationEntity;
import io.harness.idp.backstage.entities.BackstageCatalogResourceEntity;
import io.harness.idp.backstage.entities.BackstageCatalogSystemEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.entities.BackstageCatalogUserEntity;
import io.harness.rule.Owner;

import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntityTypesTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityType() {
    BackstageCatalogApiEntity apiEntity = new BackstageCatalogApiEntity();
    apiEntity.setSpec(BackstageCatalogApiEntity.Spec.builder().type("API").build());
    assertEquals("API", BackstageCatalogEntityTypes.getEntityType(apiEntity));

    BackstageCatalogComponentEntity catalogComponentEntity = new BackstageCatalogComponentEntity();
    catalogComponentEntity.setSpec(BackstageCatalogComponentEntity.Spec.builder().type("Component").build());
    assertEquals("Component", BackstageCatalogEntityTypes.getEntityType(catalogComponentEntity));

    BackstageCatalogLocationEntity locationEntity = new BackstageCatalogLocationEntity();
    locationEntity.setSpec(BackstageCatalogLocationEntity.Spec.builder().type("Location").build());
    assertEquals("Location", BackstageCatalogEntityTypes.getEntityType(locationEntity));

    locationEntity =
        new BackstageCatalogLocationEntity(BackstageCatalogLocationEntity.Spec.builder().type("Location").build());
    assertEquals("Location", BackstageCatalogEntityTypes.getEntityType(locationEntity));

    BackstageCatalogTemplateEntity templateEntity = new BackstageCatalogTemplateEntity();
    templateEntity.setSpec(BackstageCatalogTemplateEntity.Spec.builder().type("Template").build());
    assertEquals("Template", BackstageCatalogEntityTypes.getEntityType(templateEntity));

    BackstageCatalogResourceEntity resourceEntity = new BackstageCatalogResourceEntity();
    resourceEntity.setSpec(BackstageCatalogResourceEntity.Spec.builder().type("Resource").build());
    assertEquals("Resource", BackstageCatalogEntityTypes.getEntityType(resourceEntity));

    resourceEntity =
        new BackstageCatalogResourceEntity(BackstageCatalogResourceEntity.Spec.builder().type("Resource").build());
    assertEquals("Resource", BackstageCatalogEntityTypes.getEntityType(resourceEntity));

    BackstageCatalogUserEntity userEntity = new BackstageCatalogUserEntity();
    userEntity.setSpec(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntityType(userEntity));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityOwner() {
    BackstageCatalogApiEntity apiEntity =
        new BackstageCatalogApiEntity(BackstageCatalogApiEntity.Spec.builder().owner("API Owner").build());
    assertEquals("API Owner", BackstageCatalogEntityTypes.getEntityOwner(apiEntity));

    BackstageCatalogComponentEntity catalogComponentEntity = new BackstageCatalogComponentEntity(
        BackstageCatalogComponentEntity.Spec.builder().owner("Component Owner").build());
    assertEquals("Component Owner", BackstageCatalogEntityTypes.getEntityOwner(catalogComponentEntity));

    BackstageCatalogResourceEntity resourceEntity = new BackstageCatalogResourceEntity();
    resourceEntity.setSpec(BackstageCatalogResourceEntity.Spec.builder().owner("Resource Owner").build());
    assertEquals("Resource Owner", BackstageCatalogEntityTypes.getEntityOwner(resourceEntity));

    BackstageCatalogDomainEntity domainEntity = new BackstageCatalogDomainEntity();
    domainEntity.setSpec(BackstageCatalogDomainEntity.Spec.builder().owner("Domain Owner").build());
    assertEquals("Domain Owner", BackstageCatalogEntityTypes.getEntityOwner(domainEntity));

    domainEntity =
        new BackstageCatalogDomainEntity(BackstageCatalogDomainEntity.Spec.builder().owner("Domain Owner").build());
    assertEquals("Domain Owner", BackstageCatalogEntityTypes.getEntityOwner(domainEntity));

    BackstageCatalogSystemEntity systemEntity = new BackstageCatalogSystemEntity();
    systemEntity.setSpec(BackstageCatalogSystemEntity.Spec.builder().owner("System Owner").build());
    assertEquals("System Owner", BackstageCatalogEntityTypes.getEntityOwner(systemEntity));

    systemEntity =
        new BackstageCatalogSystemEntity(BackstageCatalogSystemEntity.Spec.builder().owner("System Owner").build());
    assertEquals("System Owner", BackstageCatalogEntityTypes.getEntityOwner(systemEntity));

    BackstageCatalogGroupEntity groupEntity = new BackstageCatalogGroupEntity();
    groupEntity.setSpec(BackstageCatalogGroupEntity.Spec.builder().owner("Group Owner").build());
    assertEquals("Group Owner", BackstageCatalogEntityTypes.getEntityOwner(groupEntity));

    groupEntity =
        new BackstageCatalogGroupEntity(BackstageCatalogGroupEntity.Spec.builder().owner("Group Owner").build());
    groupEntity.setSpec(BackstageCatalogGroupEntity.Spec.builder().owner("Group Owner").build());

    BackstageCatalogTemplateEntity templateEntity = new BackstageCatalogTemplateEntity();
    templateEntity.setSpec(BackstageCatalogTemplateEntity.Spec.builder().owner("Template Owner").build());
    assertEquals("Template Owner", BackstageCatalogEntityTypes.getEntityOwner(templateEntity));

    templateEntity = new BackstageCatalogTemplateEntity(
        BackstageCatalogTemplateEntity.Spec.builder().owner("Template Owner").build());
    assertEquals("Template Owner", BackstageCatalogEntityTypes.getEntityOwner(templateEntity));

    BackstageCatalogUserEntity userEntity = new BackstageCatalogUserEntity();
    userEntity.setSpec(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntityOwner(userEntity));

    userEntity = new BackstageCatalogUserEntity(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntityOwner(userEntity));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityDomain() {
    BackstageCatalogComponentEntity catalogComponentEntity = new BackstageCatalogComponentEntity();
    catalogComponentEntity.setSpec(BackstageCatalogComponentEntity.Spec.builder().domain("Component domain").build());
    assertEquals("Component domain", BackstageCatalogEntityTypes.getEntityDomain(catalogComponentEntity));

    BackstageCatalogSystemEntity systemEntity = new BackstageCatalogSystemEntity();
    systemEntity.setSpec(BackstageCatalogSystemEntity.Spec.builder().domain("System domain").build());
    assertEquals("System domain", BackstageCatalogEntityTypes.getEntityDomain(systemEntity));

    BackstageCatalogUserEntity userEntity = new BackstageCatalogUserEntity();
    userEntity.setSpec(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntityDomain(userEntity));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitySystem() {
    BackstageCatalogApiEntity apiEntity = new BackstageCatalogApiEntity();
    apiEntity.setSpec(BackstageCatalogApiEntity.Spec.builder().system(Collections.singletonList("API System")).build());
    assertEquals("API System", BackstageCatalogEntityTypes.getEntitySystem(apiEntity));

    BackstageCatalogComponentEntity catalogComponentEntity = new BackstageCatalogComponentEntity();
    catalogComponentEntity.setSpec(
        BackstageCatalogComponentEntity.Spec.builder().system(Collections.singletonList("Component System")).build());
    assertEquals("Component System", BackstageCatalogEntityTypes.getEntitySystem(catalogComponentEntity));

    BackstageCatalogResourceEntity resourceEntity = new BackstageCatalogResourceEntity();
    resourceEntity.setSpec(
        BackstageCatalogResourceEntity.Spec.builder().system(Collections.singletonList("Resource System")).build());
    assertEquals("Resource System", BackstageCatalogEntityTypes.getEntitySystem(resourceEntity));

    BackstageCatalogUserEntity userEntity = new BackstageCatalogUserEntity();
    userEntity.setSpec(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntitySystem(userEntity));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityLifecycle() {
    BackstageCatalogApiEntity apiEntity = new BackstageCatalogApiEntity();
    apiEntity.setSpec(BackstageCatalogApiEntity.Spec.builder().lifecycle("API Lifecycle").build());
    assertEquals("API Lifecycle", BackstageCatalogEntityTypes.getEntityLifecycle(apiEntity));

    BackstageCatalogComponentEntity catalogComponentEntity = new BackstageCatalogComponentEntity();
    catalogComponentEntity.setSpec(
        BackstageCatalogComponentEntity.Spec.builder().lifecycle("Component Lifecycle").build());
    assertEquals("Component Lifecycle", BackstageCatalogEntityTypes.getEntityLifecycle(catalogComponentEntity));

    BackstageCatalogUserEntity userEntity = new BackstageCatalogUserEntity();
    userEntity.setSpec(BackstageCatalogUserEntity.Spec.builder().build());
    assertNull(BackstageCatalogEntityTypes.getEntityLifecycle(userEntity));
  }
}
