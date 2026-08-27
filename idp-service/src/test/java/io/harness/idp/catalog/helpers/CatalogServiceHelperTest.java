/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.favorites.ResourceType.IDPENTITY;
import static io.harness.ng.core.variable.VariableValueType.FIXED;
import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.FavoritesClient;
import io.harness.idp.catalog.beans.Kind;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.CommonUtils;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;
import io.harness.variable.remote.VariableClient;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceHelperTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks CatalogServiceHelper catalogServiceHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock YamlSchemaValidator yamlSchemaValidator;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock FavoritesClient favoritesClient;
  @Mock AccountClient accountClient;
  @Mock VariableClient variableClient;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock KindServiceHelper kindServiceHelper;
  @Captor private ArgumentCaptor<List<PermissionCheckDTO>> permissionCheckDTOListArgumentCaptor;

  final LoadingCache<String, String> entitySchemaCache =
      CacheBuilder.newBuilder().maximumSize(4).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String kindVersion) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/" + kindVersion + ".schema.json");
        }
      });

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    FieldUtils.writeField(catalogServiceHelper, "entitySchemaCache", entitySchemaCache, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndSanitizeKindInvalidKind() {
    catalogServiceHelper.validateAndSanitizeKind("");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndSanitizeKind() {
    String kind = catalogServiceHelper.validateAndSanitizeKind("Component");
    assertThat(kind).isEqualTo("component");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndSanitizeIdentifierInvalidIdentifier() {
    catalogServiceHelper.validateAndSanitizeIdentifier("");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndSanitizeIdentifier() {
    String kind = catalogServiceHelper.validateAndSanitizeIdentifier("artist");
    assertThat(kind).isEqualTo("artist");

    kind = catalogServiceHelper.validateAndSanitizeIdentifier("artisT");
    assertThat(kind).isEqualTo("artisT");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithNullOwner() {
    catalogServiceHelper.validateOwnerScope("account", null);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithEmptyOwner() {
    catalogServiceHelper.validateOwnerScope("account", "");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithValidOwnerScope() {
    catalogServiceHelper.validateOwnerScope("account.org1", "group:account.org1/team1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithInvalidOwnerScope() {
    catalogServiceHelper.validateOwnerScope("account", "group:account.org1/team1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithCrossProjectOwnerScope() {
    catalogServiceHelper.validateOwnerScope("account.org1.proj1", "group:account.org1.proj2/team1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithCrossOrgOwnerScope() {
    catalogServiceHelper.validateOwnerScope("account.org1.proj1", "group:account.org2.proj1/team1");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateOwnerScopeWithParentOrgOwnerScope() {
    catalogServiceHelper.validateOwnerScope("account.org1.proj1", "group:account.org1/team1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateIdentifierPatternInvalidIdentifier() {
    catalogServiceHelper.validateIdentifierPattern("a-b", null, null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateIdentifierPattern() {
    catalogServiceHelper.validateIdentifierPattern("artist", null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateKindForCreateUpdateDeleteInvalidKind() {
    catalogServiceHelper.validateKindForCreateUpdateDelete("user");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateKindForCreateUpdateDelete() {
    catalogServiceHelper.validateKindForCreateUpdateDelete("component");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCheckCrudRbacWorkflow() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkCrudRbac(TEST_ACCOUNT_IDENTIFIER, null, null, "workflow", "resourceIdentifier", "edit");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCheckCrudRbac() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkCrudRbac(TEST_ACCOUNT_IDENTIFIER, null, null, "resource", "resourceIdentifier", "delete");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCheckCreateRbacEnvironment() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkCreateRbac(TEST_ACCOUNT_IDENTIFIER, null, null, "environment", "resourceIdentifier");
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any(), eq("idp_idpenvironment_create"), any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCheckCreateRbacEnvironmentBlueprint() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkCreateRbac(
        TEST_ACCOUNT_IDENTIFIER, null, null, "environmentblueprint", "resourceIdentifier");
    verify(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any(), eq("idp_environmentblueprint_create"), any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCheckCreateRbacFallbackToEditForOtherKinds() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkCreateRbac(TEST_ACCOUNT_IDENTIFIER, null, null, "component", "resourceIdentifier");
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any(), eq("idp_catalog_edit"), any());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCheckEntityRefsPermission() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(true)
                                .build()))
                        .build());
    Set<String> result = catalogServiceHelper.checkEntityRefsPermission(
        TEST_ACCOUNT_IDENTIFIER, Set.of("component:test1", "workflow:test2"), "delete");
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckRbacWithOwnerFallbackDirectPathWhenOwnerIsNull() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkRbacWithOwnerFallback(
        TEST_ACCOUNT_IDENTIFIER, "component:account.org1/comp1", null, "view");
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any(), eq("idp_catalog_view"), any());
    verify(accessControlClient, never()).checkForAccess(any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckRbacWithOwnerFallbackDirectPathWhenOwnerNotGroupAccount() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkRbacWithOwnerFallback(
        TEST_ACCOUNT_IDENTIFIER, "component:account.org1/comp1", "user:account/john", "view");
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any(), eq("idp_catalog_view"), any());
    verify(accessControlClient, never()).checkForAccess(any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckRbacWithOwnerFallbackDirectPathForNonInheritableKind() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any(), any());
    catalogServiceHelper.checkRbacWithOwnerFallback(
        TEST_ACCOUNT_IDENTIFIER, "group:account.org1/team1", "group:account/parentteam", "view");
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), any(), eq("idp_team_view"), any());
    verify(accessControlClient, never()).checkForAccess(any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckRbacWithOwnerFallbackUsesFallbackWhenPermitted() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    ResourceScope resourceScope = ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, "org1", null);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(resourceScope)
                                                       .resourceIdentifier("component:comp1")
                                                       .permitted(true)
                                                       .build()))
                        .build());
    catalogServiceHelper.checkRbacWithOwnerFallback(
        TEST_ACCOUNT_IDENTIFIER, "component:account.org1/comp1", "group:account/team1", "edit");
    verify(accessControlClient).checkForAccess(any(), any());
    verify(accessControlClient, never()).checkForAccessOrThrow(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckRbacWithOwnerFallbackThrowsWhenFallbackDenied() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder().accessControlList(List.of()).build());
    assertThatThrownBy(()
                           -> catalogServiceHelper.checkRbacWithOwnerFallback(
                               TEST_ACCOUNT_IDENTIFIER, "component:account.org1/comp1", "group:account/team1", "edit"))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testIsInheritableKindForInheritableKind() {
    assertThat(catalogServiceHelper.isInheritableKind("component")).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testIsInheritableKindForNonInheritableKind() {
    assertThat(catalogServiceHelper.isInheritableKind("group")).isFalse();
    assertThat(catalogServiceHelper.isInheritableKind("user")).isFalse();
    assertThat(catalogServiceHelper.isInheritableKind("workflow")).isFalse();
    assertThat(catalogServiceHelper.isInheritableKind("environment")).isFalse();
    assertThat(catalogServiceHelper.isInheritableKind("environmentblueprint")).isFalse();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUniqueParentScopesForGroupsAtProjectScope() {
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .orgIdentifier("org1")
                                 .projectIdentifier("proj1")
                                 .uniqueId("uid1")
                                 .build();
    Set<String> result = catalogServiceHelper.uniqueParentScopesForGroups(List.of(projectScope));
    assertThat(result).containsExactlyInAnyOrder("account.org1.proj1", "account.org1", "account");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUniqueParentScopesForGroupsAtOrganizationScope() {
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .orgIdentifier("org1")
                             .uniqueId("uid2")
                             .build();
    Set<String> result = catalogServiceHelper.uniqueParentScopesForGroups(List.of(orgScope));
    assertThat(result).containsExactlyInAnyOrder("account.org1", "account");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUniqueParentScopesForGroupsAtAccountScope() {
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    Set<String> result = catalogServiceHelper.uniqueParentScopesForGroups(List.of(accountScope));
    assertThat(result).containsExactlyInAnyOrder("account");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUniqueParentScopesForGroupsDeduplicatesAcrossScopes() {
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .orgIdentifier("org1")
                                 .projectIdentifier("proj1")
                                 .uniqueId("uid1")
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .orgIdentifier("org1")
                             .uniqueId("uid2")
                             .build();
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    Set<String> result =
        catalogServiceHelper.uniqueParentScopesForGroups(List.of(projectScope, orgScope, accountScope));
    assertThat(result).hasSize(3);
    assertThat(result).containsExactlyInAnyOrder("account.org1.proj1", "account.org1", "account");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAgainstJsonSchema() {
    when(kindServiceHelper.kindEntity(TEST_ACCOUNT_IDENTIFIER, "resource")).thenReturn(KindEntity.builder().build());
    catalogServiceHelper.validateAgainstJsonSchema("resource",
        "apiVersion: harness.io/v1\n"
            + "kind: Resource\n"
            + "type: database\n"
            + "identifier: artists-db\n"
            + "name: artists-db\n"
            + "owner: team-a\n"
            + "spec: {}\n"
            + "metadata:\n"
            + "  description: Stores artist details",
        null);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCatalogEntityInvalid() {
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_ACCOUNT_IDENTIFIER, "kind", "invalid_identifier"))
        .thenReturn(Optional.empty());
    catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "kind", "invalid_identifier");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCatalogEntity() {
    when(
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "kind", "identifier"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().identifier("identifier").build()));
    CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "kind", "identifier");
    assertThat(catalogEntity).isNotNull();
    assertThat(catalogEntity.getIdentifier()).isEqualTo("identifier");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateForModifiableActionInvalidKind() {
    catalogServiceHelper.validateForModifiableAction("component", "api", null, null, null, null, null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateForModifiableActionInvalidIdentifier() {
    catalogServiceHelper.validateForModifiableAction(
        "component", "component", "test1", "test2", null, null, null, null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCheckEntitiesRbac() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(catalogEntityRepository.findKindIdentifierScopeByParentUniqueIdIn(List.of(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(InlineCatalogEntity.builder().identifier("test1").kind(Kind.component.name()).build(),
            InlineCatalogEntity.builder().identifier("test2").kind(Kind.workflow.name()).build()));
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(true)
                                .build()))
                        .build());
    List<String> result =
        catalogServiceHelper.checkEntitiesRbac(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_ACCOUNT_IDENTIFIER));
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetUserFavoriteEntityRefs() throws IOException {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    Response<List<FavoriteResponse>> favoritesResponse = Response.success(List.of(
        new FavoriteResponse().favorite(new FavoriteDTO().resourceId("Y29tcG9uZW50Om15c2FtcGxlc2VydmljZWdpdGh1Yg"))));
    Call<List<FavoriteResponse>> getFavoritesCall = mock(Call.class);
    when(getFavoritesCall.execute()).thenReturn(favoritesResponse);
    when(favoritesClient.getAccountFavorites(TEST_ACCOUNT_IDENTIFIER, "name", IDPENTITY.name()))
        .thenReturn(getFavoritesCall);

    String userFavoriteEntityRefs =
        catalogServiceHelper.getUserFavoriteEntityRefs(TEST_ACCOUNT_IDENTIFIER, null, null, IDPENTITY.name());
    assertThat(userFavoriteEntityRefs).isEqualTo("component:account/mysampleservicegithub");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedByMe() {
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "email"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder()
                                    .identifier("email")
                                    .kind(Kind.user.name())
                                    .relations(Map.of("memberOf", Set.of("email1, email2")))
                                    .build()));
    String ownedByMe = catalogServiceHelper.getOwnedByMe(TEST_ACCOUNT_IDENTIFIER, "test");
    assertThat(ownedByMe).isEqualTo("testemail1, email2,group:account/email1, email2,user:account/email");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testResolveOwnerUser() {
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "user", "test"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().identifier("test").kind(Kind.user.name()).build()));
    String owner = catalogServiceHelper.resolveOwner(TEST_ACCOUNT_IDENTIFIER, "test");
    assertThat(owner).isEqualTo("user:account/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testResolveOwnerGroup() {
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", "test"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().identifier("test").kind(Kind.group.name()).build()));
    String owner = catalogServiceHelper.resolveOwner(TEST_ACCOUNT_IDENTIFIER, "test");
    assertThat(owner).isEqualTo("group:account/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testResolveOwnerMultipleEntities() {
    when(catalogEntityRepository.findKindAndIdentifierByParentUniqueIdAndKindInAndIdentifierIn(
             TEST_ACCOUNT_IDENTIFIER, List.of(Kind.user.name(), Kind.group.name()), List.of("test1, test2")))
        .thenReturn(List.of(InlineCatalogEntity.builder().kind(Kind.user.name()).identifier("test1").build(),
            InlineCatalogEntity.builder().kind(Kind.group.name()).identifier("test2").build()));

    List<CatalogEntity> catalogEntities = catalogServiceHelper.resolveOwner(TEST_ACCOUNT_IDENTIFIER,
        List.of(InlineCatalogEntity.builder().owner("test1").kind(Kind.component.name()).build(),
            InlineCatalogEntity.builder().owner("test2").kind(Kind.resource.name()).build()));
    assertThat(catalogEntities).isNotEmpty();
    assertThat(catalogEntities.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testResolveExpressionsInEntityYaml() throws IOException {
    Response<RestResponse<AccountDTO>> accountResponse =
        Response.success(new RestResponse<>(AccountDTO.builder().identifier(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<RestResponse<AccountDTO>> accountCall = mock(Call.class);
    when(accountCall.execute()).thenReturn(accountResponse);
    when(accountClient.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(accountCall);

    Response<ResponseDTO<PageResponse<VariableResponseDTO>>> variablesResponse =
        Response.success(ResponseDTO.newResponse(
            PageResponse.<VariableResponseDTO>builder()
                .content(List.of(
                    VariableResponseDTO.builder()
                        .variable(VariableDTO.builder()
                                      .identifier("var1")
                                      .variableConfig(
                                          StringVariableConfigDTO.builder().fixedValue("id").valueType(FIXED).build())
                                      .build())
                        .build()))
                .build()));
    Call<ResponseDTO<PageResponse<VariableResponseDTO>>> variablesCall = mock(Call.class);
    when(variablesCall.execute()).thenReturn(variablesResponse);
    when(variableClient.getVariablesListV2(
             eq(TEST_ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(0), eq(100), eq(null), eq(false), any()))
        .thenReturn(variablesCall);

    String yaml = catalogServiceHelper.resolveExpressionsInEntityYaml(
        TEST_ACCOUNT_IDENTIFIER, "identifier: <+variable.account.var1>");
    assertThat(yaml).isEqualTo("identifier: id\n");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForInvalidRequest() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForFilter() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account"));
    request.setFilter(filter);
    request.setDestinationScope("account.default.SampleTest");
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(true)
                                .build()))
                        .build());
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
    verify(accessControlClient, times(1)).checkForAccess(any(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForFilterWithInvalidScope() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.default"));
    request.setFilter(filter);
    request.setDestinationScope("account.default.SampleTest");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForFilterWithUnsupportedKind() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("environment");
    filter.setScopes(List.of("account"));
    request.setFilter(filter);
    request.setDestinationScope("account.default.SampleTest");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForFilterWithInvalidDestinationScope() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("environment");
    filter.setScopes(List.of("account"));
    request.setFilter(filter);
    request.setDestinationScope("account");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForFilterWithoutPermission() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account"));
    request.setFilter(filter);
    request.setDestinationScope("account.default.SampleTest");
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(false)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(false)
                                .build()))
                        .build());
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForEntityRefsWithInvalidScope() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("component:account.default.SampleTest/idp-service"));
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForEntityRefsWithUnsupportedKind() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("environment:idp-service"));
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForEntityRefsWithoutPermission() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("component:test1", "workflow:test2"));
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(false)
                                .build()))
                        .build());
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForEntityRefsWithInvalidDestinationScope() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("component:test1", "workflow:test2"));
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(true)
                                .build()))
                        .build());
    request.setDestinationScope("account");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForComponentAndWorkflowEntityRefs() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("component:test1", "workflow:test2"));
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build(),
                            AccessControlDTO.builder()
                                .resourceScope(ResourceScope.builder().build())
                                .resourceIdentifier("workflow:test2")
                                .permitted(true)
                                .build()))
                        .build());
    request.setDestinationScope("account.default.SampleTest");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
    verify(accessControlClient, times(2)).checkForAccess(any(), permissionCheckDTOListArgumentCaptor.capture());
    List<PermissionCheckDTO> permissionCheckDTOList = permissionCheckDTOListArgumentCaptor.getValue();
    assertThat(permissionCheckDTOList).hasSize(2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForComponentEntityRefs() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("component:test1"));
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("component:test1")
                                                       .permitted(true)
                                                       .build()))
                        .build());
    request.setDestinationScope("account.default.SampleTest");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
    verify(accessControlClient, times(2)).checkForAccess(any(), permissionCheckDTOListArgumentCaptor.capture());
    List<PermissionCheckDTO> permissionCheckDTOList = permissionCheckDTOListArgumentCaptor.getValue();
    assertThat(permissionCheckDTOList).hasSize(1);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateMigrateRequestForWorkflowEntityRefs() {
    EntitiesMigrateRequest request = new EntitiesMigrateRequest();
    request.setEntityRefs(List.of("workflow:test2"));
    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder()
                                                       .resourceScope(ResourceScope.builder().build())
                                                       .resourceIdentifier("workflow:test2")
                                                       .permitted(true)
                                                       .build()))
                        .build());
    request.setDestinationScope("account.default.SampleTest");
    catalogServiceHelper.validateMigrateRequest(request, TEST_ACCOUNT_IDENTIFIER);
    verify(accessControlClient, times(2)).checkForAccess(any(), permissionCheckDTOListArgumentCaptor.capture());
    List<PermissionCheckDTO> permissionCheckDTOList = permissionCheckDTOListArgumentCaptor.getValue();
    assertThat(permissionCheckDTOList).hasSize(1);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_ServiceToService_ReturnsAll() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal servicePrincipal = new io.harness.security.dto.ServicePrincipal("idp-service");
    SecurityContextBuilder.setContext(servicePrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(servicePrincipal);

    List<ScopeInfo> scopeInfos = List.of(ScopeInfo.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .scopeType(ScopeLevel.ACCOUNT)
                                             .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                             .build(),
        ScopeInfo.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .scopeType(ScopeLevel.ORGANIZATION)
            .uniqueId(TEST_ACCOUNT_IDENTIFIER + "_org1")
            .orgIdentifier("org1")
            .build());

    List<ScopeInfo> result = catalogServiceHelper.scopeInfosRbacByResourceType(
        TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_CATALOG", "idp_catalog_view");

    assertThat(result).hasSize(2);
    assertThat(result).isEqualTo(scopeInfos);
    executor.shutdownNow();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_AllScopesPermitted() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal principal =
        new UserPrincipal("testUser", "test@example.com", "Test User", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(TEST_ACCOUNT_IDENTIFIER + "_org1")
                             .orgIdentifier("org1")
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(List.of(AccessControlDTO.builder()
                                               .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, null, null))
                                               .permitted(true)
                                               .build(),
                    AccessControlDTO.builder()
                        .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, "org1", null))
                        .permitted(true)
                        .build()))
                .build());

    List<ScopeInfo> result = catalogServiceHelper.scopeInfosRbacByResourceType(
        TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_WORKFLOW", "idp_workflow_view");

    assertThat(result).hasSize(2);
    assertThat(result).containsExactlyInAnyOrder(accountScope, orgScope);
    executor.shutdownNow();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_PartialScopesPermitted() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal principal =
        new UserPrincipal("testUser", "test@example.com", "Test User", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(TEST_ACCOUNT_IDENTIFIER + "_org1")
                             .orgIdentifier("org1")
                             .build();
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER + "_org1_proj1")
                                 .orgIdentifier("org1")
                                 .projectIdentifier("proj1")
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope, projectScope);

    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(List.of(AccessControlDTO.builder()
                                               .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, null, null))
                                               .permitted(true)
                                               .build(),
                    AccessControlDTO.builder()
                        .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, "org1", null))
                        .permitted(false)
                        .build(),
                    AccessControlDTO.builder()
                        .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, "org1", "proj1"))
                        .permitted(true)
                        .build()))
                .build());

    List<ScopeInfo> result = catalogServiceHelper.scopeInfosRbacByResourceType(
        TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_CATALOG", "idp_catalog_view");

    assertThat(result).hasSize(2);
    assertThat(result).containsExactlyInAnyOrder(accountScope, projectScope);
    executor.shutdownNow();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_NoScopesPermitted() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal principal =
        new UserPrincipal("testUser", "test@example.com", "Test User", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(accessControlClient.checkForAccess(any(), any()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(List.of(AccessControlDTO.builder()
                                               .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, null, null))
                                               .permitted(false)
                                               .build()))
                .build());

    List<ScopeInfo> result = catalogServiceHelper.scopeInfosRbacByResourceType(
        TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_WORKFLOW", "idp_workflow_view");

    assertThat(result).isEmpty();
    executor.shutdownNow();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_AccessControlFailure_ThrowsException() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal principal =
        new UserPrincipal("testUser", "test@example.com", "Test User", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(accessControlClient.checkForAccess(any(), any()))
        .thenThrow(new RuntimeException("access-control service unavailable"));

    assertThatThrownBy(()
                           -> catalogServiceHelper.scopeInfosRbacByResourceType(
                               TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_CATALOG", "idp_catalog_view"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("RBAC scope resolution failed");
    executor.shutdownNow();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopeInfosRbacByResourceType_CorrectPermissionCheckDTOsBuilt() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    FieldUtils.writeField(catalogServiceHelper, "rbacBatchExecutor", executor, true);

    io.harness.security.dto.Principal principal =
        new UserPrincipal("testUser", "test@example.com", "Test User", TEST_ACCOUNT_IDENTIFIER);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(TEST_ACCOUNT_IDENTIFIER + "_org1")
                             .orgIdentifier("org1")
                             .build();
    List<ScopeInfo> scopeInfos = List.of(orgScope);

    when(accessControlClient.checkForAccess(any(), permissionCheckDTOListArgumentCaptor.capture()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(List.of(AccessControlDTO.builder()
                                               .resourceScope(ResourceScope.of(TEST_ACCOUNT_IDENTIFIER, "org1", null))
                                               .permitted(true)
                                               .build()))
                .build());

    catalogServiceHelper.scopeInfosRbacByResourceType(
        TEST_ACCOUNT_IDENTIFIER, scopeInfos, "IDP_ENVIRONMENT", "idp_idpenvironment_view");

    List<PermissionCheckDTO> capturedChecks = permissionCheckDTOListArgumentCaptor.getValue();
    assertThat(capturedChecks).hasSize(1);
    assertThat(capturedChecks.get(0).getResourceType()).isEqualTo("IDP_ENVIRONMENT");
    assertThat(capturedChecks.get(0).getPermission()).isEqualTo("idp_idpenvironment_view");
    assertThat(capturedChecks.get(0).getResourceScope().getOrgIdentifier()).isEqualTo("org1");
    executor.shutdownNow();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
    SecurityContextBuilder.unsetCompleteContext();
  }
}
