/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class RelationsProcessorTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock KindServiceHelper kindServiceHelper;
  @Mock KindEntityRepository kindEntityRepository;
  @InjectMocks RelationsProcessor relationsProcessor;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testEstablishRelations() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(Map.of("providesApis", Set.of("component:test1"), "apiProvidedBy",
                Set.of("component:default/test2"), "consumesApis", Set.of("test3"), "apiConsumedBy", Set.of("test4"),
                "dependsOn", Set.of("test5"), "dependencyOf", Set.of("test6"), "partOf", Set.of("test7"), "hasPart",
                Set.of("test8"), "ownedBy", Set.of("test9"), "ownerOf", Set.of("test10")))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test1"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(COMPONENT_KIND).identifier("test1").build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test2"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(COMPONENT_KIND).identifier("test2").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test3"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(API_KIND).identifier("test3").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test4"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(API_KIND).identifier("test4").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "resource", "test5"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(RESOURCE_KIND)
                                    .identifier("test5")
                                    .relations(new HashMap<>(Map.of("test", new HashSet<>(Set.of("test")))))
                                    .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test6"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(COMPONENT_KIND)
                                    .identifier("test6")
                                    .spec(new HashMap<>(Map.of("lifecycle", "production")))
                                    .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test7"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(API_KIND)
                                    .identifier("test7")
                                    .spec(new HashMap<>(Map.of("lifecycle", "relation")))
                                    .relations(new HashMap<>(Map.of("test", new HashSet<>(Set.of("test")))))
                                    .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test8"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test9"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "test9"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(USER_KIND).identifier("test9").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test10"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("test10").build()));

    List<CatalogEntity> catalogEntities = relationsProcessor.establishRelations(inlineCatalogEntity);
    assertThat(catalogEntities.size()).isEqualTo(9);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateRelationsAdded() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(Map.of("providesApis", Set.of("component:test1"), "apiProvidedBy",
                Set.of("component:default/test2"), "consumesApis", Set.of("test3"), "apiConsumedBy", Set.of("test4"),
                "dependsOn", Set.of("test5"), "dependencyOf", Set.of("test6"), "partOf", Set.of("test7"), "hasPart",
                Set.of("test8"), "ownedBy", Set.of("test9"), "ownerOf", Set.of("test10")))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(Map.of("providesApis", Set.of("component:test1", "component:test2"), "apiProvidedBy",
                Set.of("component:default/test2", "component:default/test1"), "consumesApis", Set.of("test3", "test4"),
                "apiConsumedBy", Set.of("test4", "test3"), "dependsOn", Set.of("test5", "test6"), "dependencyOf",
                Set.of("test6", "test5"), "partOf", Set.of("test7", "test8"), "hasPart", Set.of("test8", "test7"),
                "ownedBy", Set.of("test9", "test10"), "ownerOf", Set.of("test10", "test9")))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test1"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(COMPONENT_KIND).identifier("test1").build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test2"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(COMPONENT_KIND).identifier("test2").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test3"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(API_KIND).identifier("test3").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test4"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(API_KIND).identifier("test4").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "resource", "test5"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(RESOURCE_KIND)
                                    .identifier("test5")
                                    .relations(new HashMap<>(Map.of("test", new HashSet<>(Set.of("test")))))
                                    .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test6"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(COMPONENT_KIND)
                                    .identifier("test6")
                                    .spec(new HashMap<>(Map.of("lifecycle", "production")))
                                    .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test7"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .kind(API_KIND)
                                    .identifier("test7")
                                    .spec(new HashMap<>(Map.of("lifecycle", "relation")))
                                    .relations(new HashMap<>(Map.of("test", new HashSet<>(Set.of("test")))))
                                    .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test8"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test9"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "test9"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(USER_KIND).identifier("test9").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test10"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("test10").build()));

    List<CatalogEntity> catalogEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);
    assertThat(catalogEntities.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateRelationsRemoved() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(Map.of("providesApis", Set.of("component:test1", "component:test2"), "apiProvidedBy",
                Set.of("component:default/test2", "component:default/test1"), "consumesApis", Set.of("test3", "test4"),
                "apiConsumedBy", Set.of("test4", "test3"), "dependsOn", Set.of("test5", "test6"), "dependencyOf",
                Set.of("test6", "test5"), "partOf", Set.of("test7", "test8"), "hasPart", Set.of("test8", "test7"),
                "ownedBy", Set.of("test9", "test10"), "ownerOf", Set.of("test10", "test9")))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(Map.of("providesApis", Set.of("component:test1"), "apiProvidedBy",
                Set.of("component:default/test2"), "consumesApis", Set.of("test3"), "apiConsumedBy", Set.of("test4"),
                "dependsOn", Set.of("test5"), "dependencyOf", Set.of("test6"), "partOf", Set.of("test7"), "hasPart",
                Set.of("test8"), "ownedBy", Set.of("test9"), "ownerOf", Set.of("test10")))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test1"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test1")
                .spec(new HashMap<>(Map.of("providesApis", new ArrayList<>(List.of("component:identifier")),
                    "apiProvidedBy", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("component:identifier")),
                    "apiProvidedBy", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test2"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test2")
                .spec(new HashMap<>(Map.of("providesApis", new ArrayList<>(List.of("component:identifier")),
                    "apiProvidedBy", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("component:identifier")),
                    "apiProvidedBy", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test3"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(API_KIND)
                .identifier("test3")
                .spec(new HashMap<>(Map.of("apiConsumedBy", new ArrayList<>(List.of("component:identifier")),
                    "consumesApis", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("apiConsumedBy", new HashSet<>(Set.of("component:identifier")),
                    "consumesApis", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test4"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(API_KIND)
                .identifier("test4")
                .spec(new HashMap<>(Map.of("consumesApis", new ArrayList<>(List.of("component:identifier")),
                    "apiConsumedBy", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("consumesApis", new HashSet<>(Set.of("component:identifier")),
                    "apiConsumedBy", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "resource", "test5"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(RESOURCE_KIND)
                .identifier("test5")
                .spec(new HashMap<>(Map.of("dependencyOf", new ArrayList<>(List.of("component:identifier")),
                    "dependsOn", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("dependencyOf", new HashSet<>(Set.of("component:identifier")),
                    "dependsOn", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test6"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(COMPONENT_KIND)
                            .identifier("test6")
                            .spec(new HashMap<>(Map.of("dependsOn", new ArrayList<>(List.of("component:identifier")),
                                "dependencyOf", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("dependsOn", new HashSet<>(Set.of("component:identifier")),
                                "dependencyOf", new HashSet<>(Set.of("component:identifier")))))
                            .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test7"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(API_KIND)
                            .identifier("test7")
                            .spec(new HashMap<>(Map.of("hasPart", new ArrayList<>(List.of("component:identifier")),
                                "partOf", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("hasPart", new HashSet<>(Set.of("component:identifier")),
                                "partOf", new HashSet<>(Set.of("component:identifier")))))
                            .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test8"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test9"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "test9"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(USER_KIND)
                            .identifier("test9")
                            .spec(new HashMap<>(Map.of("ownerOf", new ArrayList<>(List.of("component:identifier")),
                                "ownedBy", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("ownerOf", new HashSet<>(Set.of("component:identifier")),
                                "ownedBy", new HashSet<>(Set.of("component:identifier")))))
                            .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test10"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(GROUP_KIND)
                            .identifier("test10")
                            .spec(new HashMap<>(Map.of("ownedBy", new ArrayList<>(List.of("component:identifier")),
                                "ownerOf", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("component:identifier")),
                                "ownerOf", new HashSet<>(Set.of("component:identifier")))))
                            .build()));

    List<CatalogEntity> catalogEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);
    assertThat(catalogEntities.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDisbandRelations() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .relations(
                Map.of("providesApis", Set.of("component:test1"), "apiProvidedBy", Set.of("component:default/test2"),
                    "consumesApis", Set.of("api:test3"), "apiConsumedBy", Set.of("api:test4"), "dependsOn",
                    Set.of("resource:test5"), "dependencyOf", Set.of("component:test6"), "partOf", Set.of("api:test7"),
                    "hasPart", Set.of("test8"), "ownedBy", Set.of("user:test9"), "ownerOf", Set.of("group:test10")))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test1"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test1")
                .spec(new HashMap<>(Map.of("apiProvidedBy", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test2"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test2")
                .spec(new HashMap<>(Map.of("providesApis", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test3"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(API_KIND)
                .identifier("test3")
                .spec(new HashMap<>(Map.of("apiConsumedBy", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("apiConsumedBy", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test4"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(API_KIND)
                .identifier("test4")
                .spec(new HashMap<>(Map.of("consumesApis", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("consumesApis", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "resource", "test5"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(RESOURCE_KIND)
                .identifier("test5")
                .spec(new HashMap<>(Map.of("dependencyOf", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("dependencyOf", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test6"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test6")
                .spec(new HashMap<>(Map.of("dependsOn", new ArrayList<>(List.of("component:identifier")))))
                .relations(new HashMap<>(Map.of("dependsOn", new HashSet<>(Set.of("component:identifier")))))
                .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "api", "test7"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(API_KIND)
                            .identifier("test7")
                            .spec(new HashMap<>(Map.of("hasPart", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("hasPart", new HashSet<>(Set.of("component:identifier")))))
                            .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test8"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test9"))
        .thenReturn(Optional.empty());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "test9"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(USER_KIND)
                            .identifier("test9")
                            .spec(new HashMap<>(Map.of("ownerOf", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("ownerOf", new HashSet<>(Set.of("component:identifier")))))
                            .build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test10"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(GROUP_KIND)
                            .identifier("test10")
                            .spec(new HashMap<>(Map.of("ownedBy", new ArrayList<>(List.of("component:identifier")))))
                            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("component:identifier")))))
                            .build()));

    List<CatalogEntity> catalogEntities = relationsProcessor.disbandRelations(inlineCatalogEntity);
    assertThat(catalogEntities.size()).isEqualTo(9);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateRelationsEntireRelationKeyRemoved() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental", "providesApis",
                new ArrayList<>(List.of("component:default/test1", "component:default/test2")))))
            .relations(Map.of("providesApis", Set.of("component:default/test1", "component:default/test2"), "ownedBy",
                Set.of("owner")))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental")))
            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test1"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test1")
                .spec(new HashMap<>(Map.of("apiProvidedBy", new ArrayList<>(List.of("component:account/identifier")))))
                .relations(
                    new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:account/identifier")))))
                .build()));
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "test2"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("test2")
                .spec(new HashMap<>(Map.of("apiProvidedBy", new ArrayList<>(List.of("component:account/identifier")))))
                .relations(
                    new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:account/identifier")))))
                .build()));

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);

    assertThat(referencedEntities).hasSize(2);
    for (CatalogEntity entity : referencedEntities) {
      Set<String> apiProvidedBy = entity.getRelations().get("apiProvidedBy");
      assertThat(apiProvidedBy).doesNotContain("component:account/identifier");
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateRelationsReferencedTypeExplicitlyRemovedFromSpec() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of(
                "lifecycle", "experimental", "apiProvidedBy", new ArrayList<>(List.of("component:default/comp1")))))
            .relations(Map.of("apiProvidedBy", Set.of("component:default/comp1"), "ownedBy", Set.of("owner")))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental")))
            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "component", "comp1"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("comp1")
                .spec(new HashMap<>(Map.of("providesApis", new ArrayList<>(List.of("api:account/identifier")))))
                .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("api:account/identifier")))))
                .build()));

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);

    assertThat(referencedEntities).hasSize(1);
    CatalogEntity comp = referencedEntities.get(0);
    Set<String> providesApis = comp.getRelations().get("providesApis");
    assertThat(providesApis).doesNotContain("api:account/identifier");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateRelationsSystemPopulatedReferencedTypeNotProcessed() {
    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental")))
            .relations(new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:default/comp1")),
                "ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("identifier")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental")))
            .relations(new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:default/comp1")),
                "ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);

    assertThat(referencedEntities).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateRelationsReferencedTypeAddedForFirstTime() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingInlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("test_api_1")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "experimental")))
            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("test_api_1")
            .kind(API_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of(
                "lifecycle", "experimental", "apiProvidedBy", new ArrayList<>(List.of("component:account/comp1")))))
            .relations(new HashMap<>(Map.of("apiProvidedBy", new HashSet<>(Set.of("component:account/comp1")),
                "ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    InlineCatalogEntity referencedComponent =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("comp1")
            .kind(COMPONENT_KIND)
            .owner("owner")
            .spec(new HashMap<>(Map.of("lifecycle", "production")))
            .relations(new HashMap<>(Map.of("ownedBy", new HashSet<>(Set.of("owner")))))
            .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_ACCOUNT_IDENTIFIER, COMPONENT_KIND, "comp1"))
        .thenReturn(Optional.of(referencedComponent));

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingInlineCatalogEntity, inlineCatalogEntity);

    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getRelations().get("providesApis")).contains("api:account/test_api_1");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testEstablishRelationsForLeaderOfAndHasLeader() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity userCatalogEntity = InlineCatalogEntity.builder()
                                                .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                .identifier("user1")
                                                .kind(USER_KIND)
                                                .relations(Map.of("leaderOf", Set.of("group1")))
                                                .build();

    InlineCatalogEntity groupCatalogEntity = InlineCatalogEntity.builder()
                                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                 .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                 .identifier("group2")
                                                 .kind(GROUP_KIND)
                                                 .relations(Map.of("hasLeader", Set.of("user2")))
                                                 .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "group1"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("group1").build()));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "user2"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(USER_KIND).identifier("user2").build()));

    List<CatalogEntity> catalogEntitiesForUser = relationsProcessor.establishRelations(userCatalogEntity);
    assertThat(catalogEntitiesForUser).hasSize(1);
    assertThat(catalogEntitiesForUser.get(0).getRelations().get("hasLeader")).contains("user:account/user1");

    List<CatalogEntity> catalogEntitiesForGroup = relationsProcessor.establishRelations(groupCatalogEntity);
    assertThat(catalogEntitiesForGroup).hasSize(1);
    assertThat(catalogEntitiesForGroup.get(0).getRelations().get("leaderOf")).contains("group:account/group2");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateRelationsAddedForLeaderOfAndHasLeader() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingUserCatalogEntity = InlineCatalogEntity.builder()
                                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                        .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                        .identifier("user1")
                                                        .kind(USER_KIND)
                                                        .spec(new HashMap<>())
                                                        .relations(new HashMap<>())
                                                        .build();

    InlineCatalogEntity updatedUserCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("user1")
            .kind(USER_KIND)
            .spec(new HashMap<>(Map.of("leaderOf", new ArrayList<>(List.of("group:account/group1")))))
            .relations(new HashMap<>(Map.of("leaderOf", new HashSet<>(Set.of("group1")))))
            .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "group1"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("group1").build()));

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingUserCatalogEntity, updatedUserCatalogEntity);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getRelations().get("hasLeader")).contains("user:account/user1");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateRelationsRemovedForLeaderOfAndHasLeader() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity existingUserCatalogEntity =
        InlineCatalogEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
            .identifier("user1")
            .kind(USER_KIND)
            .spec(new HashMap<>(Map.of("leaderOf", new ArrayList<>(List.of("group:account/group1")))))
            .relations(new HashMap<>(Map.of("leaderOf", new HashSet<>(Set.of("group1")))))
            .build();

    InlineCatalogEntity updatedUserCatalogEntity = InlineCatalogEntity.builder()
                                                       .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                       .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                       .identifier("user1")
                                                       .kind(USER_KIND)
                                                       .spec(new HashMap<>())
                                                       .relations(new HashMap<>())
                                                       .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "group1"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(GROUP_KIND)
                            .identifier("group1")
                            .spec(new HashMap<>(Map.of("hasLeader", new ArrayList<>(List.of("user:account/user1")))))
                            .relations(new HashMap<>(Map.of("hasLeader", new HashSet<>(Set.of("user:account/user1")))))
                            .build()));

    List<CatalogEntity> referencedEntities =
        relationsProcessor.updateRelations(existingUserCatalogEntity, updatedUserCatalogEntity);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getRelations().get("hasLeader")).doesNotContain("user:account/user1");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testChangeScopeForLeaderOfAndHasLeader() {
    InlineCatalogEntity userCatalogEntity = InlineCatalogEntity.builder()
                                                .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                .identifier("user1")
                                                .kind(USER_KIND)
                                                .relations(Map.of("leaderOf", Set.of("group1")))
                                                .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "group1"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(GROUP_KIND)
                            .identifier("group1")
                            .spec(new HashMap<>(Map.of("hasLeader", new ArrayList<>(List.of("user:account/user1")))))
                            .relations(new HashMap<>(Map.of("hasLeader", new HashSet<>(Set.of("user:account/user1")))))
                            .build()));

    ScopeInfo destinationScopeInfo = ScopeInfo.builder()
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                         .orgIdentifier("org2")
                                         .build();

    List<CatalogEntity> referencedEntities = relationsProcessor.changeScope(userCatalogEntity, destinationScopeInfo);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getRelations().get("hasLeader")).contains("user:account.org2/user1");
    assertThat(referencedEntities.get(0).getRelations().get("hasLeader")).doesNotContain("user:account/user1");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDisbandRelationsForLeaderOfAndHasLeader() {
    InlineCatalogEntity userCatalogEntity = InlineCatalogEntity.builder()
                                                .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                .identifier("user1")
                                                .kind(USER_KIND)
                                                .relations(Map.of("leaderOf", Set.of("group1")))
                                                .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "group1"))
        .thenReturn(
            Optional.of(InlineCatalogEntity.builder()
                            .kind(GROUP_KIND)
                            .identifier("group1")
                            .spec(new HashMap<>(Map.of("hasLeader", new ArrayList<>(List.of("user:account/user1")))))
                            .relations(new HashMap<>(Map.of("hasLeader", new HashSet<>(Set.of("user:account/user1")))))
                            .build()));

    List<CatalogEntity> referencedEntities = relationsProcessor.disbandRelations(userCatalogEntity);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getRelations().get("hasLeader")).doesNotContain("user:account/user1");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDisbandRelationsRemovesSubcomponentOfFromChildSpec() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity parentEntity = InlineCatalogEntity.builder()
                                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                           .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                           .identifier("parent-comp")
                                           .kind(COMPONENT_KIND)
                                           .relations(Map.of("hasPart", Set.of("component:account/child-comp")))
                                           .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_ACCOUNT_IDENTIFIER, COMPONENT_KIND, "child-comp"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("child-comp")
                .spec(new HashMap<>(Map.of("subcomponentOf", "parent-comp")))
                .relations(new HashMap<>(Map.of("partOf", new HashSet<>(Set.of("component:account/parent-comp")))))
                .build()));

    List<CatalogEntity> referencedEntities = relationsProcessor.disbandRelations(parentEntity);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getSpec()).doesNotContainKey("subcomponentOf");
    assertThat(referencedEntities.get(0).getRelations().get("partOf")).doesNotContain("component:account/parent-comp");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDisbandRelationsKeepsSubcomponentOfWhenRefDoesNotMatch() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(anyString(), eq(null), eq(null))).thenReturn(scopeInfoCall);

    InlineCatalogEntity parentEntity = InlineCatalogEntity.builder()
                                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                           .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                           .identifier("parent-comp")
                                           .kind(COMPONENT_KIND)
                                           .relations(Map.of("hasPart", Set.of("component:account/child-comp")))
                                           .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_ACCOUNT_IDENTIFIER, COMPONENT_KIND, "child-comp"))
        .thenReturn(Optional.of(
            InlineCatalogEntity.builder()
                .kind(COMPONENT_KIND)
                .identifier("child-comp")
                .spec(new HashMap<>(Map.of("subcomponentOf", "other-parent")))
                .relations(new HashMap<>(Map.of("partOf", new HashSet<>(Set.of("component:account/parent-comp")))))
                .build()));

    List<CatalogEntity> referencedEntities = relationsProcessor.disbandRelations(parentEntity);
    assertThat(referencedEntities).hasSize(1);
    assertThat(referencedEntities.get(0).getSpec()).containsKey("subcomponentOf");
    assertThat(referencedEntities.get(0).getSpec().get("subcomponentOf")).isEqualTo("other-parent");
    assertThat(referencedEntities.get(0).getRelations().get("partOf")).doesNotContain("component:account/parent-comp");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
