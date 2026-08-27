/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mappers;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.EntityLinks.FieldMapping;
import io.harness.idp.catalog.entities.EntityLinks.LinkTarget;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityLink;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class EntityLinkMapperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ENTITY_REF = "workflow:account/my-workflow";

  // ── toEntity ────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToEntity_mapsAllFields() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);
    link.setScopes(List.of("account.default.project1"));

    io.harness.spec.server.idp.v1.model.LinkTarget apiTarget = new io.harness.spec.server.idp.v1.model.LinkTarget();
    apiTarget.setEntityKind("component");
    apiTarget.setEntityType("service");
    link.setTargets(List.of(apiTarget));

    io.harness.spec.server.idp.v1.model.FieldMapping apiMapping =
        new io.harness.spec.server.idp.v1.model.FieldMapping();
    apiMapping.setInput("myInput");
    apiMapping.setEntityFieldSource("metadata.name");
    link.setFieldMappings(List.of(apiMapping));

    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);

    EntityLinks entity = EntityLinkMapper.toEntity(ACCOUNT_ID, request);

    assertThat(entity.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(entity.getEntityRef()).isEqualTo(ENTITY_REF);
    assertThat(entity.getScopes()).containsExactly("account.default.project1");

    assertThat(entity.getTargets()).hasSize(1);
    assertThat(entity.getTargets().get(0).getEntityKind()).isEqualTo("component");
    assertThat(entity.getTargets().get(0).getEntityType()).isEqualTo("service");

    assertThat(entity.getFieldMappings()).hasSize(1);
    assertThat(entity.getFieldMappings().get(0).getInput()).isEqualTo("myInput");
    assertThat(entity.getFieldMappings().get(0).getEntityFieldSource()).isEqualTo("metadata.name");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToEntity_nullTargets_mapsToEmptyList() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);
    link.setTargets(null);
    link.setFieldMappings(null);
    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);

    EntityLinks entity = EntityLinkMapper.toEntity(ACCOUNT_ID, request);

    assertThat(entity.getTargets()).isEmpty();
    assertThat(entity.getFieldMappings()).isEmpty();
  }

  // ── toDTO ───────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_mapsAllFields() {
    EntityLinks entity =
        EntityLinks.builder()
            .accountIdentifier(ACCOUNT_ID)
            .entityRef(ENTITY_REF)
            .scopes(List.of("account.default.project1"))
            .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
            .fieldMappings(List.of(FieldMapping.builder().input("myInput").entityFieldSource("metadata.name").build()))
            .build();

    EntityLinkResponse response = EntityLinkMapper.toDTO(entity);

    assertThat(response.getEntityLink().getEntityRef()).isEqualTo(ENTITY_REF);
    assertThat(response.getEntityLink().getScopes()).containsExactly("account.default.project1");

    assertThat(response.getEntityLink().getTargets()).hasSize(1);
    assertThat(response.getEntityLink().getTargets().get(0).getEntityKind()).isEqualTo("component");
    assertThat(response.getEntityLink().getTargets().get(0).getEntityType()).isEqualTo("service");

    assertThat(response.getEntityLink().getFieldMappings()).hasSize(1);
    assertThat(response.getEntityLink().getFieldMappings().get(0).getInput()).isEqualTo("myInput");
    assertThat(response.getEntityLink().getFieldMappings().get(0).getEntityFieldSource()).isEqualTo("metadata.name");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_nullTargetsAndFieldMappings_omitsLists() {
    EntityLinks entity = EntityLinks.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .entityRef(ENTITY_REF)
                             .targets(null)
                             .fieldMappings(null)
                             .build();

    EntityLinkResponse response = EntityLinkMapper.toDTO(entity);

    assertThat(response.getEntityLink().getEntityRef()).isEqualTo(ENTITY_REF);
    // generated model initializes list fields to empty list by default when not set
    assertThat(response.getEntityLink().getTargets()).isEmpty();
    assertThat(response.getEntityLink().getFieldMappings()).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToEntity_emptyTargetsList_mapsToEmptyList() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);
    link.setTargets(List.of());
    link.setFieldMappings(List.of());
    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);

    EntityLinks entity = EntityLinkMapper.toEntity(ACCOUNT_ID, request);

    assertThat(entity.getTargets()).isEmpty();
    assertThat(entity.getFieldMappings()).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_multipleFieldMappings_allMapped() {
    EntityLinks entity =
        EntityLinks.builder()
            .accountIdentifier(ACCOUNT_ID)
            .entityRef(ENTITY_REF)
            .fieldMappings(List.of(FieldMapping.builder().input("input1").entityFieldSource("metadata.name").build(),
                FieldMapping.builder().input("input2").entityFieldSource("spec.type").build()))
            .build();

    EntityLinkResponse response = EntityLinkMapper.toDTO(entity);

    assertThat(response.getEntityLink().getFieldMappings()).hasSize(2);
    assertThat(response.getEntityLink().getFieldMappings().get(0).getInput()).isEqualTo("input1");
    assertThat(response.getEntityLink().getFieldMappings().get(1).getInput()).isEqualTo("input2");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_nullScopes_preserved() {
    EntityLinks entity =
        EntityLinks.builder()
            .accountIdentifier(ACCOUNT_ID)
            .entityRef(ENTITY_REF)
            .scopes(null)
            .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
            .build();

    EntityLinkResponse response = EntityLinkMapper.toDTO(entity);

    assertThat(response.getEntityLink().getScopes()).isNull();
    assertThat(response.getEntityLink().getTargets()).hasSize(1);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToEntity_multipleTargets_allMapped() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);

    io.harness.spec.server.idp.v1.model.LinkTarget t1 = new io.harness.spec.server.idp.v1.model.LinkTarget();
    t1.setEntityKind("component");
    t1.setEntityType("service");
    io.harness.spec.server.idp.v1.model.LinkTarget t2 = new io.harness.spec.server.idp.v1.model.LinkTarget();
    t2.setEntityKind("resource");
    t2.setEntityType("database");
    link.setTargets(List.of(t1, t2));

    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);

    EntityLinks entity = EntityLinkMapper.toEntity(ACCOUNT_ID, request);

    assertThat(entity.getTargets()).hasSize(2);
    assertThat(entity.getTargets().get(1).getEntityKind()).isEqualTo("resource");
    assertThat(entity.getTargets().get(1).getEntityType()).isEqualTo("database");
  }
}
