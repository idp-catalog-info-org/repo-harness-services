/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.favorites.ResourceType.IDPENTITY;
import static io.harness.favorites.ResourceType.PROJECT;
import static io.harness.idp.catalog.utils.CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRefForFavorites;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_BLUEPRINT_KIND;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.catalog.utils.Constants.IS_CUSTOM_USER_GROUP;
import static io.harness.idp.catalog.utils.Constants.MEMBERS;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.NON_INHERITABLE_KINDS;
import static io.harness.idp.catalog.utils.Constants.RESERVED_KIND_IDENTIFIERS;
import static io.harness.idp.catalog.utils.Constants.SUPPORTED_MIGRATE_KINDS;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.VERSION_SUPPORTED_KINDS;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;
import static io.harness.idp.common.YamlUtils.yamlObject;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.favorites.FavoritesClient;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.backstage.expression.IdpVariableExpressionEvaluator;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.RbacUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.RecentSelectedScopesDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.GitMetadataUpdateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;
import io.harness.variable.remote.VariableClient;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceHelper {
  public LoadingCache<String, String> entitySchemaCache =
      CacheBuilder.newBuilder().maximumSize(12).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String kindVersion) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/" + kindVersion + ".schema.json");
        }
      });

  public LoadingCache<String, String> backstageEntitySchemaCache =
      CacheBuilder.newBuilder().maximumSize(9).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String kind) {
          return CommonUtils.readFileFromClassPath("backstage/entity-schema/" + kind + ".schema.json");
        }
      });

  private final static Pattern VARIABLE_EXPRESSION_PATTERN = Pattern.compile("<\\+variable\\.account\\.(.*?)>");

  private static final Pattern VERSION_LABEL_PATTERN = Pattern.compile("^[A-Za-z0-9][^\\s/&]*$");
  private static final int ORG_BATCH_SIZE = 30;
  private static final int PROJECT_BATCH_SIZE = 100;

  @Inject ScopeInfoClient scopeInfoClient;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject AccessControlClient accessControlClient;
  @Inject YamlSchemaValidator yamlSchemaValidator;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject @Named("PRIVILEGED") FavoritesClient favoritesClient;
  @Inject AccountClient accountClient;
  @Inject @Named("PRIVILEGED") VariableClient variableClient;
  @Inject @Named("PRIVILEGED") NGSettingsClient settingsClient;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject GitAwareEntityHelper gitAwareEntityHelper;
  @Inject IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  @Inject KindServiceHelper kindServiceHelper;
  @Inject @Named("RbacBatchExecutor") ExecutorService rbacBatchExecutor;

  public String validateAndSanitizeKind(String kind) {
    if (isEmpty(kind) || kind.trim().isEmpty()) {
      throw new InvalidRequestException("Kind cannot be null or empty");
    }
    return kind.toLowerCase();
  }

  public String validateAndSanitizeIdentifier(String identifier) {
    if (isEmpty(identifier) || identifier.trim().isEmpty()) {
      throw new InvalidRequestException("Identifier cannot be null or empty");
    }
    return identifier;
  }

  public void validateIdentifier(String identifier) {
    if (isEmpty(identifier) || identifier.trim().isEmpty()) {
      throw new InvalidRequestException("Identifier cannot be null or empty");
    }
  }

  public void validateIdentifierPattern(String identifier, String kind, String type) {
    if (!isEmpty(kind) && HIERARCHY_KIND.equals(kind) && !isEmpty(type) && type.equalsIgnoreCase("account")) {
      return;
    }
    final Pattern identifierPattern = EntityIdentifierValidator.IDENTIFIER_PATTERN;
    if (!identifierPattern.matcher(identifier).matches()) {
      throw new InvalidRequestException("Entity identifier = " + identifier + " is invalid");
    }
  }

  public void validateKindForCreateUpdateDelete(String kind) {
    if (USER_KIND.equals(kind)) {
      throw new InvalidRequestException(
          "Entity create / update / delete / conversion with kind = " + kind + " not supported.");
    }
  }

  public void validateKindForVersioning(String kind) {
    if (!VERSION_SUPPORTED_KINDS.contains(kind)) {
      throw new InvalidRequestException(
          "Versioning is only supported for " + String.join(", ", VERSION_SUPPORTED_KINDS) + " kind");
    }
  }

  public void validateVersionLabel(String versionLabel) {
    if (!VERSION_LABEL_PATTERN.matcher(versionLabel).matches()) {
      throw new InvalidRequestException("Version Label = " + versionLabel
          + (" is invalid. It must start with an alphanumeric character and should not contain whitespaces, '/' or "
              + "'&'."));
    }
  }

  public String getBlueprintVersionIdentifier(String blueprintIdentifier, String version) {
    // Using "&" as delimiter instead of "/" to avoid conflict with FQN parsing which splits by "/"
    // This ensures the FQN format "blueprintId&version" parses correctl
    return blueprintIdentifier + "&" + version;
  }

  public void validateMigrateRequest(EntitiesMigrateRequest body, String harnessAccount) {
    List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    List<String> entityRefs = body.getEntityRefs();
    ScorecardFilter filter = body.getFilter();
    if (isEmpty(body.getEntityRefs()) && body.getFilter() == null) {
      throw new InvalidRequestException("Request should contain either entity_refs or filter");
    }

    if (!isEmpty(entityRefs)) {
      entityRefs.forEach(entityRef -> {
        Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
        String kind = kindScopeIdentifier.getLeft();
        String scope = kindScopeIdentifier.getMiddle();
        if (!SUPPORTED_MIGRATE_KINDS.contains(kind)) {
          throw new InvalidRequestException(String.format(
              "Unsupported kind for entityRef %s. Allowed kinds are %s", entityRef, SUPPORTED_MIGRATE_KINDS));
        }
        if (!scope.equals("account")) {
          throw new InvalidRequestException(
              String.format("Unsupported scope for entityRef %s. Only account scope is allowed", entityRef));
        }
      });

      Set<String> permittedEntityRefs = checkEntityRefsPermission(harnessAccount, new HashSet<>(entityRefs), "edit");
      if (permittedEntityRefs.size() != entityRefs.size()) {
        throw new InvalidRequestException("Missing permission for updating one or more entities");
      }

      Pair<String, String> orgProjectIdentifier = getOrgProjectFromScope(body.getDestinationScope());
      if (orgProjectIdentifier.getLeft() == null && orgProjectIdentifier.getRight() == null) {
        throw new InvalidRequestException("Destination scope must be at Org/Project scope");
      }
      if (permittedEntityRefs.stream().noneMatch(permittedEntityRef -> permittedEntityRef.startsWith("workflow:"))) {
        permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                       .resourceScope(ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(),
                                           orgProjectIdentifier.getRight()))
                                       .resourceType("IDP_CATALOG")
                                       .permission("idp_catalog_edit")
                                       .build());
      } else if (permittedEntityRefs.stream().allMatch(
                     permittedEntityRef -> permittedEntityRef.startsWith("workflow:"))) {
        permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                       .resourceScope(ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(),
                                           orgProjectIdentifier.getRight()))
                                       .resourceType("IDP_WORKFLOW")
                                       .permission("idp_workflow_edit")
                                       .build());
      } else {
        permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                       .resourceScope(ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(),
                                           orgProjectIdentifier.getRight()))
                                       .resourceType("IDP_CATALOG")
                                       .permission("idp_catalog_edit")
                                       .build());
        permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                       .resourceScope(ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(),
                                           orgProjectIdentifier.getRight()))
                                       .resourceType("IDP_WORKFLOW")
                                       .permission("idp_workflow_edit")
                                       .build());
      }
    }

    if (filter != null) {
      List<String> scopes = filter.getScopes();
      if (!scopes.isEmpty() && !scopes.get(0).equalsIgnoreCase("account")) {
        throw new InvalidRequestException("Unsupported scope in filter %s. Only account scope is allowed");
      }
      String kind = filter.getKind();
      if (!SUPPORTED_MIGRATE_KINDS.contains(kind)) {
        throw new InvalidRequestException("Unsupported kind provided. Allowed kinds are " + SUPPORTED_MIGRATE_KINDS);
      }
      String resourceType = WORKFLOW_KIND.equals(kind) ? "IDP_WORKFLOW" : "IDP_CATALOG";
      String resourcePermission = (WORKFLOW_KIND.equals(kind) ? "idp_workflow_" : "idp_catalog_") + "edit";
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .resourceScope(ResourceScope.of(harnessAccount, null, null))
                                     .resourceType(resourceType)
                                     .permission(resourcePermission)
                                     .build());
      Pair<String, String> orgProjectIdentifier = getOrgProjectFromScope(body.getDestinationScope());
      if (orgProjectIdentifier.getLeft() == null && orgProjectIdentifier.getRight() == null) {
        throw new InvalidRequestException("Destination scope must be at Org/Project scope");
      }
      scopeInfoClient.getScopeInfo(harnessAccount, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight());
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .resourceScope(ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(),
                                         orgProjectIdentifier.getRight()))
                                     .resourceType(resourceType)
                                     .permission(resourcePermission)
                                     .build());
    }

    AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(
        RbacUtils.fromSecurityPrincipalTypeWithoutModifying(SecurityContextBuilder.getPrincipal().getType()),
        permissionCheckDTOList);
    accessCheckResponseDTO.getAccessControlList()
        .stream()
        .filter(accessControlDTO -> !accessControlDTO.isPermitted())
        .findAny()
        .ifPresent(accessControlDTO -> {
          throw new InvalidRequestException("Missing permission " + accessControlDTO.getPermission());
        });
  }

  public void checkCrudRbac(String harnessAccount, String orgIdentifier, String projectIdentifier, String kind,
      String resourceIdentifier, String permission) {
    // Skip RBAC for pure service-to-service calls (no user context)
    if (RbacUtils.isPureServiceToServiceCall()) {
      return;
    }

    if (!isEmpty(resourceIdentifier)) {
      Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(resourceIdentifier);
      resourceIdentifier = kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getRight();
    }
    if (WORKFLOW_KIND.equals(kind)) {
      accessControlClient.checkForAccessOrThrow(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
          Resource.of("IDP_WORKFLOW", resourceIdentifier), "idp_workflow_" + permission,
          String.format("Missing Workflow %s Permission", permission));
    } else if (ENVIRONMENT_KIND.equals(kind)) {
      accessControlClient.checkForAccessOrThrow(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
          Resource.of("IDP_ENVIRONMENT", resourceIdentifier), "idp_idpenvironment_" + permission,
          String.format("Missing Environment %s Permission", permission));

    } else if (ENVIRONMENT_BLUEPRINT_KIND.equals(kind)) {
      accessControlClient.checkForAccessOrThrow(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
          Resource.of("IDP_ENVIRONMENT_BLUEPRINT", resourceIdentifier), "idp_environmentblueprint_" + permission,
          String.format("Missing Environment Blueprint %s Permission", permission));
    } else if (GROUP_KIND.equals(kind)) {
      accessControlClient.checkForAccessOrThrow(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
          Resource.of("IDP_TEAM", resourceIdentifier), "idp_team_" + permission,
          String.format("Missing Team %s Permission", permission));
    } else {
      accessControlClient.checkForAccessOrThrow(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
          Resource.of("IDP_CATALOG", resourceIdentifier), "idp_catalog_" + permission,
          String.format("Missing Catalog %s Permission", permission));
    }
  }

  public void checkCrudRbac(String harnessAccount, String entityRef, String permission) {
    Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
    String kind = kindScopeIdentifier.getLeft();
    String scope = kindScopeIdentifier.getMiddle();
    Pair<String, String> orgProject = getOrgProjectFromScope(scope);
    checkCrudRbac(harnessAccount, orgProject.getLeft(), orgProject.getRight(), kind, entityRef, permission);
  }

  public void checkCreateRbac(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String kind, String resourceIdentifier) {
    if (ENVIRONMENT_KIND.equals(kind) || ENVIRONMENT_BLUEPRINT_KIND.equals(kind)) {
      checkCrudRbac(harnessAccount, orgIdentifier, projectIdentifier, kind, resourceIdentifier, "create");
    } else {
      checkCrudRbac(harnessAccount, orgIdentifier, projectIdentifier, kind, resourceIdentifier, "edit");
    }
  }

  public void checkRbacWithOwnerFallback(String harnessAccount, String entityRef, String owner, String permission) {
    Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
    String kind = validateAndSanitizeKind(kindScopeIdentifier.getLeft());
    if (isInheritableKind(kind) && !isEmpty(owner) && owner.startsWith("group:account")) {
      checkRbacForRUDWithFallback(harnessAccount, Set.of(entityRef, owner), kind, permission);
    } else {
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      String orgIdentifier = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      String projectIdentifier = scopeSplit.length == 3 ? scopeSplit[2] : null;
      checkCrudRbac(harnessAccount, orgIdentifier, projectIdentifier, kind, entityRef, permission);
    }
  }

  private void checkRbacForRUDWithFallback(
      String harnessAccount, Set<String> entityRefs, String kind, String permission) {
    if (isEmpty(checkEntityRefsPermission(harnessAccount, entityRefs, permission))) {
      throw new NGAccessDeniedException(
          String.format("Missing %s and Team %s Permission", kind, permission), USER, Collections.emptyList());
    }
  }

  public Set<String> checkEntityRefsPermissionWithOwnerFallback(
      String harnessAccount, Map<String, String> entityRefToOwner, String permission) {
    Set<String> allowedEntityRefs = new HashSet<>();
    if (isEmpty(entityRefToOwner)) {
      return allowedEntityRefs;
    }
    Map<String, String> entityRefToOwnerGroup = new HashMap<>();
    entityRefToOwner.forEach((entityRef, owner) -> {
      String kind = validateAndSanitizeKind(getKindScopeIdentifier(entityRef).getLeft());
      if (isInheritableKind(kind) && !isEmpty(owner) && owner.startsWith("group:account")) {
        entityRefToOwnerGroup.put(entityRef, owner);
      }
    });
    Set<String> refsToCheck = new HashSet<>(entityRefToOwner.keySet());
    refsToCheck.addAll(entityRefToOwnerGroup.values());
    Set<String> permittedRefs = checkEntityRefsPermission(harnessAccount, refsToCheck, permission);
    entityRefToOwner.keySet().forEach(entityRef -> {
      if (permittedRefs.contains(entityRef)
          || (entityRefToOwnerGroup.containsKey(entityRef)
              && permittedRefs.contains(entityRefToOwnerGroup.get(entityRef)))) {
        allowedEntityRefs.add(entityRef);
      }
    });
    return allowedEntityRefs;
  }

  public boolean isInheritableKind(String kind) {
    return !NON_INHERITABLE_KINDS.contains(kind);
  }

  public Set<String> uniqueParentScopesForGroups(List<ScopeInfo> requestedScopeInfos) {
    Set<String> uniqueScopes = new HashSet<>();
    for (ScopeInfo requestedScopeInfo : requestedScopeInfos) {
      if (ScopeLevel.PROJECT.equals(requestedScopeInfo.getScopeType())) {
        uniqueScopes.add(
            "account." + requestedScopeInfo.getOrgIdentifier() + "." + requestedScopeInfo.getProjectIdentifier());
        uniqueScopes.add("account." + requestedScopeInfo.getOrgIdentifier());
      } else if (ScopeLevel.ORGANIZATION.equals(requestedScopeInfo.getScopeType())) {
        uniqueScopes.add("account." + requestedScopeInfo.getOrgIdentifier());
      }
      uniqueScopes.add("account");
    }
    return uniqueScopes;
  }

  public Set<String> checkEntityRefsPermission(String harnessAccount, Set<String> entityRefs, String permission) {
    // Skip RBAC for pure service-to-service calls (no user context)
    if (RbacUtils.isPureServiceToServiceCall()) {
      return entityRefs;
    }

    List<PermissionCheckDTO> permissionCheckDTOList =
        entityRefs.stream()
            .map(entityRef -> {
              Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
              String kind = kindScopeIdentifier.getLeft();
              String scope = kindScopeIdentifier.getMiddle();
              String identifier = kindScopeIdentifier.getRight();
              Pair<String, String> orgProjectIdentifier = getOrgProjectFromScope(scope);
              Pair<String, String> resourceInfo = getResourceTypeAndPermission(kind, permission);
              String resourceType = resourceInfo.getLeft();
              String resourcePermission = resourceInfo.getRight();
              return PermissionCheckDTO.builder()
                  .resourceScope(
                      ResourceScope.of(harnessAccount, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight()))
                  .resourceType(resourceType)
                  .resourceIdentifier(kind + ":" + identifier)
                  .permission(resourcePermission)
                  .build();
            })
            .toList();

    List<List<PermissionCheckDTO>> permissionCheckDTOSList = Lists.partition(permissionCheckDTOList, 9999);

    Set<String> allowedEntityRefs = new HashSet<>();
    permissionCheckDTOSList.forEach(permissionCheckDTOSBatch -> {
      List<AccessControlDTO> accessCheckResponseDTO =
          accessControlClient
              .checkForAccess(RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
                  permissionCheckDTOSBatch)
              .getAccessControlList();

      if (accessCheckResponseDTO != null) {
        allowedEntityRefs.addAll(accessCheckResponseDTO.stream()
                                     .filter(AccessControlDTO::isPermitted)
                                     .map(accessControlDTO
                                         -> CatalogUtils.entityRef(accessControlDTO.getResourceScope(),
                                             accessControlDTO.getResourceIdentifier()))
                                     .collect(Collectors.toSet()));
      }
    });

    return allowedEntityRefs;
  }

  public void validateAgainstJsonSchema(String kind, String yaml, String schema) {
    try {
      if (RESERVED_KIND_IDENTIFIERS.contains(kind)) {
        yamlSchemaValidator.validate(yaml, entitySchemaCache.get(kind + ".v1"));
      } else {
        yamlSchemaValidator.validate(yaml, schema);
      }
    } catch (IOException | ExecutionException e) {
      log.error("Unexpected error in entity yaml validate. Exception  = {}", e.getMessage(), e);
      throw new InvalidRequestException("Unable to process request for entity create / update");
    } catch (InvalidYamlException e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public void validateAgainstBackstageJsonSchema(String kind, String yaml) {
    try {
      yamlSchemaValidator.validate(yaml, backstageEntitySchemaCache.get(kind));
    } catch (IOException | ExecutionException e) {
      log.error("Unexpected error in entity yaml validate. Exception  = {}", e.getMessage(), e);
      throw new InvalidRequestException("Unexpected error in entity yaml validate");
    } catch (InvalidYamlException e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public CatalogEntity catalogEntity(String parentUniqueId, String kind, String identifier) {
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    if (optionalCatalogEntity.isEmpty()) {
      throw new EntityNotFoundException("Entity with entityRef = " + kind + ":" + identifier + " not found");
    }
    return optionalCatalogEntity.get();
  }

  public ScopeInfo getScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  public CatalogEntity catalogEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String kind, String identifier) {
    ScopeInfo scopeInfo = getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);
    return optionalCatalogEntity.orElse(null);
  }

  public CatalogEntity catalogEntityFromGit(
      String parentUniqueId, String kind, String identifier, boolean loadFromCache, boolean loadFromFallbackBranch) {
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    if (optionalCatalogEntity.isEmpty()) {
      throw new EntityNotFoundException("Entity with entityRef = " + kind + ":" + identifier + " not found");
    }
    return optionalCatalogEntity.get() instanceof InlineCatalogEntity
        ? optionalCatalogEntity.get()
        : catalogEntityRepository.getRemoteServiceWithYaml(
              (GitReferencedCatalogEntity) optionalCatalogEntity.get(), loadFromCache, loadFromFallbackBranch);
  }

  public String getRepoUrlAndCheckForFileUniqueness(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);
    if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath(), gitEntityInfo.getConnectorRef(),
            gitEntityInfo.getRepoName())) {
      String error = "The Requested YAML with RepoURl: " + repoURL + ", FilePath: " + gitEntityInfo.getFilePath()
          + ", Repo: " + gitEntityInfo.getRepoName() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  private boolean isAlreadyImported(
      String accountIdentifier, String repoURL, String filePath, String connectorRef, String repoName) {
    Long totalInstancesOfYAML =
        catalogEntityRepository.countFileInstances(accountIdentifier, repoURL, filePath, connectorRef, repoName);
    return totalInstancesOfYAML > 0;
  }

  public String getRepoUrlAndCheckForFileUniqueness(CatalogEntity catalogEntity, GitMetadataUpdateRequest body) {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(((GitReferencedCatalogEntity) catalogEntity).getRepo())
                                      .connectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef())
                                      .build();
    GitAwareContextHelper.populateGitDetails(gitEntityInfo);
    String repoURL = gitAwareEntityHelper.getRepoUrl(
        catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
    if (!((GitReferencedCatalogEntity) catalogEntity).getRepoURL().equals(repoURL)
        || !((GitReferencedCatalogEntity) catalogEntity).getFilePath().equals(body.getFilePath())) {
      if (isAlreadyImported(catalogEntity.getAccountIdentifier(), repoURL, body.getFilePath(),
              gitEntityInfo.getConnectorRef(), gitEntityInfo.getRepoName())) {
        String error = "The Requested YAML with RepoURl: " + repoURL + ", FilePath: " + body.getFilePath()
            + " is already present.";
        throw new DuplicateEntityException(error);
      }
    }
    return repoURL;
  }

  public String importCatalogFromRemote(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public String fetchYAMLFromRemote(CatalogEntity catalogEntity) {
    Scope scope = Scope.of(
        catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
    GitContextRequestParams gitContextRequestParams =
        GitContextRequestParams.builder()
            .branchName(((GitReferencedCatalogEntity) catalogEntity).getFallBackBranch())
            .connectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef())
            .filePath(((GitReferencedCatalogEntity) catalogEntity).getFilePath())
            .repoName(((GitReferencedCatalogEntity) catalogEntity).getRepo())
            .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public String fetchYAMLFromRemote(CatalogEntity catalogEntity, String branchName) {
    Scope scope = Scope.of(
        catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
    GitContextRequestParams gitContextRequestParams =
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef())
            .filePath(((GitReferencedCatalogEntity) catalogEntity).getFilePath())
            .repoName(((GitReferencedCatalogEntity) catalogEntity).getRepo())
            .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public boolean yamlNotExistsInDefaultBranch(CatalogEntity catalogEntity, String defaultBranch) {
    try {
      return isEmpty(fetchYAMLFromRemote(catalogEntity, defaultBranch));
    } catch (Exception e) {
      return true;
    }
  }

  public Triple<String, String, String> getKindScopeIdentifier(String entityRef) {
    String[] entityRefSplit = entityRef.split(":");
    String kind;
    String scope = "account";
    String identifier;
    if (entityRefSplit.length == 1) {
      kind = COMPONENT_KIND;
      identifier = entityRefSplit[0];
    } else if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      String scopeAndIdentifier = entityRefSplit[1];
      int slashIndex = scopeAndIdentifier.indexOf("/");
      scope = slashIndex != -1 ? scopeAndIdentifier.substring(0, slashIndex) : scope;
      identifier = slashIndex != -1 ? scopeAndIdentifier.substring(slashIndex + 1) : scopeAndIdentifier;
    } else {
      throw new InvalidRequestException("Invalid entityRef " + entityRef + " provided");
    }
    if (Objects.equals(kind.toLowerCase(), "user") || Objects.equals(kind.toLowerCase(), "group")) {
      return Triple.of(kind.toLowerCase(), scope, identifier);
    }
    return Triple.of(kind.toLowerCase(), scope, identifier);
  }

  public Pair<String, String> getOrgProjectFromScope(String scope) {
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (isEmpty(scope)) {
      return Pair.of(orgIdentifier, projectIdentifier);
    }

    String[] hierarchyScope = scope.split("\\.");
    if (hierarchyScope.length == 3) {
      orgIdentifier = hierarchyScope[1];
      projectIdentifier = hierarchyScope[2];
    } else if (hierarchyScope.length == 2) {
      orgIdentifier = hierarchyScope[1];
    }

    return Pair.of(orgIdentifier, projectIdentifier);
  }

  public Pair<String, String> getOrgProjectFromEntityRef(String entityRef) {
    Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
    return getOrgProjectFromScope(kindScopeIdentifier.getMiddle());
  }

  public void validateForModifiableAction(String existingKind, String incomingKind, String existingIdentifier,
      String identifier, String existingOrgIdentifier, String orgIdentifier, String existingProjectIdentifier,
      String projectIdentifier) {
    if (!existingKind.equals(incomingKind)) {
      throw new InvalidRequestException("Kind cannot be modified for entity modification");
    }
    if (!existingIdentifier.equals(identifier)) {
      throw new InvalidRequestException("Identifier cannot be modified for entity modification");
    }
    if (!isEmpty(existingOrgIdentifier) && !existingOrgIdentifier.equals(orgIdentifier)) {
      throw new InvalidRequestException("OrgIdentifier cannot be modified for entity modification");
    }
    if (!isEmpty(existingProjectIdentifier) && !existingProjectIdentifier.equals(projectIdentifier)) {
      throw new InvalidRequestException("ProjectIdentifier cannot be modified for entity modification");
    }
  }

  public void validateForModifiableActionIfLinkedToCD(String existingName, String incomingName,
      String existingDescription, String incomingDescription, List<String> existingTags, List<String> incomingTags) {
    if (!existingName.equals(incomingName)) {
      throw new InvalidRequestException(existingName
          + (" is managed using Harness CD. You can update Name, Description, and Tags from Harness CD. Learn more "
              + "about Catalog and Harness CD integration. "
              + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/harness-cd"));
    }
    if (!Objects.equals(existingDescription, incomingDescription)) {
      throw new InvalidRequestException(existingName
          + (" is managed using Harness CD. You can update Name, Description, and Tags from Harness CD. Learn more "
              + "about Catalog and Harness CD integration. "
              + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/harness-cd"));
    }
    if (!Objects.equals(existingTags, incomingTags)) {
      throw new InvalidRequestException(existingName
          + (" is managed using Harness CD. You can update Name, Description, and Tags from Harness CD. Learn more "
              + "about Catalog and Harness CD integration. "
              + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/harness-cd"));
    }
  }

  public List<ScopeInfo> scopeInfosRbac(String harnessAccount, List<ScopeInfo> scopeInfos, String kind) {
    // Skip RBAC for pure service-to-service calls (no user context)
    if (RbacUtils.isPureServiceToServiceCall()) {
      return scopeInfos;
    }

    String resourceType = "IDP_CATALOG";
    String resourcePermission = "idp_catalog_view";
    List<String> kinds = !isEmpty(kind) ? List.of(kind.split(",")) : new ArrayList<>();
    if (!isEmpty(kinds) && kinds.size() == 1) {
      if (kinds.get(0).equalsIgnoreCase(WORKFLOW_KIND)) {
        resourceType = "IDP_WORKFLOW";
        resourcePermission = "idp_workflow_view";
      } else if (kinds.get(0).equalsIgnoreCase(ENVIRONMENT_KIND)) {
        resourceType = "IDP_ENVIRONMENT";
        resourcePermission = "idp_idpenvironment_view";
      } else if (kinds.get(0).equalsIgnoreCase(ENVIRONMENT_BLUEPRINT_KIND)) {
        resourceType = "IDP_ENVIRONMENT_BLUEPRINT";
        resourcePermission = "idp_environmentblueprint_view";
      } else if (kinds.get(0).equalsIgnoreCase(GROUP_KIND)) {
        resourceType = "IDP_TEAM";
        resourcePermission = "idp_team_view";
      }
    }
    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>();
    for (ScopeInfo scopeInfo : scopeInfos) {
      PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder()
                                                  .resourceScope(ResourceScope.of(harnessAccount,
                                                      scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()))
                                                  .resourceType(resourceType)
                                                  .permission(resourcePermission)
                                                  .build();

      permissionCheckDTOS.add(permissionCheckDTO);
    }

    List<List<PermissionCheckDTO>> permissionCheckDTOSList = Lists.partition(permissionCheckDTOS, 9999);

    List<ScopeInfo> scopeInfoList = new ArrayList<>();
    permissionCheckDTOSList.forEach(permissionCheckDTOSBatch -> {
      AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(
          RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
          permissionCheckDTOSBatch);

      scopeInfoList.addAll(
          scopeInfos.stream()
              .filter(scopeInfo
                  -> accessCheckResponseDTO.getAccessControlList().stream().anyMatch(dto
                      -> dto.isPermitted()
                          && Objects.equals(
                              dto.getResourceScope().getAccountIdentifier(), scopeInfo.getAccountIdentifier())
                          && Objects.equals(dto.getResourceScope().getOrgIdentifier(), scopeInfo.getOrgIdentifier())
                          && Objects.equals(
                              dto.getResourceScope().getProjectIdentifier(), scopeInfo.getProjectIdentifier())))
              .collect(Collectors.toList()));
    });

    return scopeInfoList;
  }

  private static final int RBAC_BATCH_SIZE = 1000;

  public List<ScopeInfo> scopeInfosRbacForKind(String harnessAccount, List<ScopeInfo> scopeInfos, String kind) {
    if (RbacUtils.isPureServiceToServiceCall()) {
      return scopeInfos;
    }

    List<String> kinds = !isEmpty(kind) ? List.of(kind.split(",")) : new ArrayList<>();
    String resourceType;
    String resourcePermission;
    if (!isEmpty(kinds) && kinds.size() == 1) {
      Pair<String, String> typeAndPerm = getResourceTypeAndPermission(kinds.get(0), "view");
      resourceType = typeAndPerm.getLeft();
      resourcePermission = typeAndPerm.getRight();
    } else {
      resourceType = "IDP_CATALOG";
      resourcePermission = "idp_catalog_view";
    }

    io.harness.security.dto.PrincipalType callerPrincipalType = SecurityContextBuilder.getPrincipal().getType();

    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>();
    for (ScopeInfo scopeInfo : scopeInfos) {
      permissionCheckDTOS.add(PermissionCheckDTO.builder()
                                  .resourceScope(ResourceScope.of(
                                      harnessAccount, scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()))
                                  .resourceType(resourceType)
                                  .permission(resourcePermission)
                                  .build());
    }

    List<List<PermissionCheckDTO>> batches = Lists.partition(permissionCheckDTOS, RBAC_BATCH_SIZE);
    List<List<ScopeInfo>> scopeBatches = Lists.partition(scopeInfos, RBAC_BATCH_SIZE);

    List<CompletableFuture<List<ScopeInfo>>> futures = new ArrayList<>();
    for (int i = 0; i < batches.size(); i++) {
      List<PermissionCheckDTO> batch = batches.get(i);
      List<ScopeInfo> scopeSlice = scopeBatches.get(i);
      futures.add(CompletableFuture.supplyAsync(() -> {
        AccessCheckResponseDTO accessCheckResponseDTO =
            accessControlClient.checkForAccess(RbacUtils.fromSecurityPrincipalType(callerPrincipalType), batch);

        List<AccessControlDTO> permitted = accessCheckResponseDTO.getAccessControlList()
                                               .stream()
                                               .filter(AccessControlDTO::isPermitted)
                                               .collect(Collectors.toList());

        return scopeSlice.stream()
            .filter(scopeInfo
                -> permitted.stream().anyMatch(dto
                    -> Objects.equals(dto.getResourceScope().getAccountIdentifier(), scopeInfo.getAccountIdentifier())
                        && Objects.equals(dto.getResourceScope().getOrgIdentifier(), scopeInfo.getOrgIdentifier())
                        && Objects.equals(
                            dto.getResourceScope().getProjectIdentifier(), scopeInfo.getProjectIdentifier())))
            .collect(Collectors.toList());
      }, rbacBatchExecutor));
    }

    return awaitRbacFutures(futures);
  }

  public List<ScopeInfo> scopeInfosRbacByResourceType(
      String harnessAccount, List<ScopeInfo> scopeInfos, String resourceType, String permission) {
    if (RbacUtils.isPureServiceToServiceCall()) {
      return scopeInfos;
    }

    io.harness.security.dto.PrincipalType callerPrincipalType = SecurityContextBuilder.getPrincipal().getType();
    Principal callerPrincipal = RbacUtils.fromSecurityPrincipalType(callerPrincipalType);
    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>();
    for (ScopeInfo scopeInfo : scopeInfos) {
      permissionCheckDTOS.add(PermissionCheckDTO.builder()
                                  .resourceScope(ResourceScope.of(
                                      harnessAccount, scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()))
                                  .resourceType(resourceType)
                                  .permission(permission)
                                  .build());
    }

    List<List<PermissionCheckDTO>> batches = Lists.partition(permissionCheckDTOS, RBAC_BATCH_SIZE);
    List<List<ScopeInfo>> scopeBatches = Lists.partition(scopeInfos, RBAC_BATCH_SIZE);

    List<CompletableFuture<List<ScopeInfo>>> futures = new ArrayList<>();
    for (int i = 0; i < batches.size(); i++) {
      List<PermissionCheckDTO> batch = batches.get(i);
      List<ScopeInfo> scopeSlice = scopeBatches.get(i);
      futures.add(CompletableFuture.supplyAsync(() -> {
        AccessCheckResponseDTO resp = accessControlClient.checkForAccess(callerPrincipal, batch);
        List<AccessControlDTO> permitted =
            resp.getAccessControlList().stream().filter(AccessControlDTO::isPermitted).collect(Collectors.toList());
        return scopeSlice.stream()
            .filter(si
                -> permitted.stream().anyMatch(dto
                    -> Objects.equals(dto.getResourceScope().getAccountIdentifier(), si.getAccountIdentifier())
                        && Objects.equals(dto.getResourceScope().getOrgIdentifier(), si.getOrgIdentifier())
                        && Objects.equals(dto.getResourceScope().getProjectIdentifier(), si.getProjectIdentifier())))
            .collect(Collectors.toList());
      }, rbacBatchExecutor));
    }

    return awaitRbacFutures(futures);
  }

  private static final long RBAC_TIMEOUT_SECONDS = 30;

  private List<ScopeInfo> awaitRbacFutures(List<CompletableFuture<List<ScopeInfo>>> futures) {
    CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    try {
      allOf.get(RBAC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      futures.forEach(f -> f.cancel(true));
      throw new RuntimeException("RBAC scope resolution timed out after " + RBAC_TIMEOUT_SECONDS + "s", e);
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      throw new RuntimeException("RBAC scope resolution failed", e.getCause());
    } catch (InterruptedException e) {
      futures.forEach(f -> f.cancel(true));
      Thread.currentThread().interrupt();
      throw new RuntimeException("RBAC scope resolution interrupted", e);
    }
    return futures.stream().map(CompletableFuture::join).flatMap(List::stream).collect(Collectors.toList());
  }

  public List<String> checkEntitiesRbac(String harnessAccount, List<String> parentUniqueIds) {
    Iterable<CatalogEntity> catalogEntities =
        catalogEntityRepository.findKindIdentifierScopeByParentUniqueIdIn(parentUniqueIds);
    return performRbacCheck(harnessAccount, catalogEntities);
  }

  public List<String> checkEntitiesRbacByKind(String harnessAccount, List<String> parentUniqueIds, String kind) {
    Iterable<CatalogEntity> catalogEntities =
        catalogEntityRepository.findByParentUniqueIdInAndKind(parentUniqueIds, kind);
    return performRbacCheck(harnessAccount, catalogEntities);
  }

  public List<String> performRbacCheck(String harnessAccount, Iterable<CatalogEntity> catalogEntities) {
    List<String> allEntityRefs = new ArrayList<>();
    catalogEntities.forEach(catalogEntity -> allEntityRefs.add(CatalogUtils.entityRef(catalogEntity)));
    return filterPermittedEntityRefs(harnessAccount, allEntityRefs);
  }

  public List<String> filterPermittedEntityRefs(String harnessAccount, Collection<String> entityRefs) {
    List<String> allEntityRefs = new ArrayList<>(entityRefs);

    // Skip RBAC for pure service-to-service calls (no user context)
    if (RbacUtils.isPureServiceToServiceCall()) {
      return allEntityRefs;
    }

    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>();

    allEntityRefs.forEach(entityRef -> {
      Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
      String kind = kindScopeIdentifier.getLeft();
      String scope = kindScopeIdentifier.getMiddle();
      String identifier = kindScopeIdentifier.getRight();
      Pair<String, String> orgProjectIdentifier = getOrgProjectFromScope(scope);
      Pair<String, String> resourceInfo = getResourceTypeAndPermission(kind, "view");
      String resourceType = resourceInfo.getLeft();
      String resourcePermission = resourceInfo.getRight();
      PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder()
                                                  .resourceScope(ResourceScope.of(harnessAccount,
                                                      orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight()))
                                                  .resourceType(resourceType)
                                                  .resourceIdentifier(kind + ":" + identifier)
                                                  .permission(resourcePermission)
                                                  .build();
      permissionCheckDTOS.add(permissionCheckDTO);
    });

    List<List<PermissionCheckDTO>> permissionCheckDTOSList = Lists.partition(permissionCheckDTOS, 9999);

    List<String> permittedEntityRefs = new ArrayList<>();
    permissionCheckDTOSList.forEach(permissionCheckDTOSBatch -> {
      AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(
          RbacUtils.fromSecurityPrincipalTypeWithoutModifying(SecurityContextBuilder.getPrincipal().getType()),
          permissionCheckDTOSBatch);

      permittedEntityRefs.addAll(accessCheckResponseDTO.getAccessControlList()
                                     .stream()
                                     .filter(AccessControlDTO::isPermitted)
                                     .map(accessControlDTO
                                         -> CatalogUtils.entityRef(accessControlDTO.getResourceScope(),
                                             accessControlDTO.getResourceIdentifier()))
                                     .collect(Collectors.toList()));
    });

    return permittedEntityRefs;
  }

  public String getUserFavoriteEntityRefs(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String resourceType) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      return null;
    }

    List<FavoriteResponse> favoriteResponses;
    if (!isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
      favoriteResponses = NGRestUtils.getGeneralResponse(favoritesClient.getProjectFavorites(
          harnessAccount, orgIdentifier, projectIdentifier, userPrincipal.getUniqueId(), resourceType));
    } else if (!isEmpty(orgIdentifier)) {
      favoriteResponses = NGRestUtils.getGeneralResponse(
          favoritesClient.getOrgFavorites(harnessAccount, orgIdentifier, userPrincipal.getUniqueId(), resourceType));
    } else {
      favoriteResponses = NGRestUtils.getGeneralResponse(
          favoritesClient.getAccountFavorites(harnessAccount, userPrincipal.getUniqueId(), resourceType));
    }
    StringBuilder userFavoriteEntityRefs = new StringBuilder();
    for (FavoriteResponse favorite : favoriteResponses) {
      byte[] entityRefEncoded = Base64.getDecoder().decode(favorite.getFavorite().getResourceId());
      String entityRef = new String(entityRefEncoded, StandardCharsets.UTF_8);
      userFavoriteEntityRefs.append(parseBackstageEntityReferenceToCatalogRelationRefForFavorites(entityRef, null))
          .append(",");
    }
    if (!isEmpty(userFavoriteEntityRefs.toString())) {
      return userFavoriteEntityRefs.substring(0, userFavoriteEntityRefs.length() - 1);
    }
    return null;
  }

  public String getUserFavoriteEntityRefsForOrgs(
      String harnessAccount, List<String> orgIdentifiers, String resourceType) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      return null;
    }

    List<FavoriteResponse> favoriteResponses = new ArrayList<>();
    if (!isEmpty(orgIdentifiers)) {
      favoriteResponses = NGRestUtils.getGeneralResponse(favoritesClient.getOrgsFavorites(
          harnessAccount, userPrincipal.getUniqueId(), String.join(",", orgIdentifiers), resourceType));
    }
    StringBuilder userFavoriteEntityRefs = new StringBuilder();
    for (FavoriteResponse favorite : favoriteResponses) {
      byte[] entityRefEncoded = Base64.getDecoder().decode(favorite.getFavorite().getResourceId());
      String entityRef = new String(entityRefEncoded, StandardCharsets.UTF_8);
      userFavoriteEntityRefs.append(parseBackstageEntityReferenceToCatalogRelationRefForFavorites(entityRef, null))
          .append(",");
    }
    if (!isEmpty(userFavoriteEntityRefs.toString())) {
      return userFavoriteEntityRefs.substring(0, userFavoriteEntityRefs.length() - 1);
    }
    return null;
  }

  public String getUserFavoriteEntityRefsForProjects(String harnessAccount, String orgProject, String resourceType) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      return null;
    }

    List<FavoriteResponse> favoriteResponses = new ArrayList<>();
    if (!isEmpty(orgProject)) {
      favoriteResponses = NGRestUtils.getGeneralResponse(
          favoritesClient.getProjectsFavorites(harnessAccount, userPrincipal.getUniqueId(), orgProject, resourceType));
    }
    StringBuilder userFavoriteEntityRefs = new StringBuilder();
    for (FavoriteResponse favorite : favoriteResponses) {
      byte[] entityRefEncoded = Base64.getDecoder().decode(favorite.getFavorite().getResourceId());
      String entityRef = new String(entityRefEncoded, StandardCharsets.UTF_8);
      userFavoriteEntityRefs.append(parseBackstageEntityReferenceToCatalogRelationRefForFavorites(entityRef, null))
          .append(",");
    }
    if (!isEmpty(userFavoriteEntityRefs.toString())) {
      return userFavoriteEntityRefs.substring(0, userFavoriteEntityRefs.length() - 1);
    }
    return null;
  }

  public String getOwnedByMe(String parentUniqueId, String owner) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      return owner;
    }

    CatalogEntity catalogEntity;
    try {
      catalogEntity = catalogEntity(parentUniqueId, USER_KIND, userPrincipal.getEmail());
    } catch (EntityNotFoundException ex) {
      boolean isHarnessSupportUserId =
          CGRestUtils.getResponse(accountClient.isHarnessSupportUserId(userPrincipal.getName()));
      if (isHarnessSupportUserId) {
        return null;
      }
      throw new InvalidRequestException(
          "Entity with entityRef = " + USER_KIND + ":" + userPrincipal.getEmail() + " not found");
    }

    Map<String, Set<String>> relations = catalogEntity.getRelations();
    Set<String> memberOf = new HashSet<>();
    if (!isEmpty(relations) && !isEmpty(relations.get(MEMBER_OF))) {
      memberOf = relations.get(MEMBER_OF);
    }

    if (!isEmpty(owner)) {
      StringBuilder ownerBuilder = new StringBuilder(owner + String.join(",", memberOf));
      for (String member : memberOf) {
        String[] memberSplit = member.split(":");
        if (memberSplit.length == 2 && memberSplit[0].equalsIgnoreCase("group")) {
          if (!memberSplit[1].startsWith("account/")) {
            memberSplit[1] = "account/" + memberSplit[1];
          }
          ownerBuilder.append(",").append("group:").append(memberSplit[1]);
        } else if (memberSplit.length == 1 && !isEmpty(memberSplit[0])) {
          ownerBuilder.append(",").append("group:account/").append(memberSplit[0]);
        }
      }
      owner = ownerBuilder.toString();
      owner = owner + ",user:account/" + catalogEntity.getIdentifier();
    } else {
      StringBuilder ownerBuilder = new StringBuilder(String.join(",", memberOf));
      for (String member : memberOf) {
        String[] memberSplit = member.split(":");
        if (memberSplit.length == 2 && memberSplit[0].equalsIgnoreCase("group")) {
          if (!memberSplit[1].startsWith("account/")) {
            memberSplit[1] = "account/" + memberSplit[1];
          }
          ownerBuilder.append(",").append("group:").append(memberSplit[1]);
        } else if (memberSplit.length == 1 && !isEmpty(memberSplit[0])) {
          ownerBuilder.append(",").append("group:account/").append(memberSplit[0]);
        }
      }
      owner = ownerBuilder.toString();
      owner = owner + ",user:account/" + catalogEntity.getIdentifier();
    }

    return owner;
  }

  public String resolveOwner(String accountIdentifier, String owner) {
    if (isEmpty(owner)) {
      return owner;
    }
    if (owner.startsWith("user:default/")) {
      return "user:account/" + owner.substring(13);
    }
    if (owner.startsWith("user:") && !owner.startsWith("user:account/") && owner.split(":").length == 2
        && !owner.contains("/")) {
      return "user:account/" + owner.substring(5);
    }
    if (owner.startsWith("group:default/")) {
      return "group:account/" + owner.substring(14);
    }
    if (owner.startsWith("group:") && !owner.startsWith("group:account/") && !owner.startsWith("group:account.")
        && owner.split(":").length == 2 && !owner.contains("/")) {
      return "group:account/" + owner.substring(6);
    }
    if (owner.startsWith("group:") && owner.startsWith("group:account") && owner.split(":").length == 2
        && owner.contains("/")) {
      return owner;
    }
    Optional<CatalogEntity> optionalUserEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(accountIdentifier, USER_KIND, owner);
    if (optionalUserEntity.isPresent()) {
      return "user:account/" + owner;
    }
    Optional<CatalogEntity> optionalGroupEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(accountIdentifier, GROUP_KIND, owner);
    if (optionalGroupEntity.isPresent()) {
      return "group:account/" + owner;
    }
    return owner;
  }

  public List<CatalogEntity> resolveOwner(String parentUniqueId, List<CatalogEntity> catalogEntities) {
    List<String> identifiers = new ArrayList<>();
    catalogEntities.forEach(catalogEntity -> {
      String owner = catalogEntity.getOwner();
      if (!isEmpty(owner)) {
        String identifier = owner.replaceFirst("(?i)^(user:|group:|user:account/|group:account/)", "");
        identifiers.add(identifier);
      }
    });

    List<CatalogEntity> userAndGroupEntities =
        catalogEntityRepository.findKindAndIdentifierByParentUniqueIdAndKindInAndIdentifierIn(
            parentUniqueId, List.of(USER_KIND, GROUP_KIND), identifiers);

    Set<String> userOwners = userAndGroupEntities.stream()
                                 .filter(entity -> USER_KIND.equals(entity.getKind()))
                                 .map(CatalogEntity::getIdentifier)
                                 .collect(Collectors.toSet());

    Set<String> groupOwners = userAndGroupEntities.stream()
                                  .filter(entity -> GROUP_KIND.equals(entity.getKind()))
                                  .map(CatalogEntity::getIdentifier)
                                  .collect(Collectors.toSet());

    catalogEntities.forEach(catalogEntity -> {
      String owner = catalogEntity.getOwner();
      if (!isEmpty(owner) && !owner.toLowerCase().startsWith("user:") && !owner.toLowerCase().startsWith("group:")) {
        if (userOwners.contains(owner)) {
          catalogEntity.setOwner("user:account/" + owner);
        } else if (groupOwners.contains(owner)) {
          catalogEntity.setOwner("group:account/" + owner);
        }
      }
    });

    return catalogEntities;
  }

  public void validateOwnerScope(String entityScope, String owner) {
    if (!isEmpty(owner) && owner.startsWith("group:account") && owner.contains("/")) {
      String ownerScopeIdentifier = owner.split(":")[1];
      String ownerScope = ownerScopeIdentifier.split("/")[0];
      String[] ownerScopeToken = ownerScope.split("\\.");
      String[] entityScopeToken = entityScope.split("\\.");
      boolean isValid = isValidOwnerOrParentScope(entityScopeToken, ownerScopeToken);
      if (!isValid) {
        throw new InvalidRequestException(
            String.format("Invalid owner scope '%s' for entity at '%s' scope", ownerScope, entityScope));
      }
    }
  }

  public void validateParentTeam(String entityRef, String parentRef) {
    Triple<String, String, String> entity = getKindScopeIdentifier(entityRef);
    if (!GROUP_KIND.equals(entity.getLeft()) || isEmpty(parentRef) || !isValidParentRef(parentRef)) {
      return;
    }

    Triple<String, String, String> parent = getKindScopeIdentifier(parentRef);
    validateParentScope(entity, parent);
    validateParentIsNotSameTeam(entity, parent);
  }

  private boolean isValidParentRef(String parentRef) {
    return parentRef.startsWith("group:account") && parentRef.contains("/");
  }

  private void validateParentScope(Triple<String, String, String> entity, Triple<String, String, String> parent) {
    String entityScope = entity.getMiddle();
    String parentScope = parent.getMiddle();

    String[] entityScopeTokens = entityScope.split("\\.");
    String[] parentScopeTokens = parentScope.split("\\.");

    if (!isValidOwnerOrParentScope(entityScopeTokens, parentScopeTokens)) {
      throw new InvalidRequestException(
          String.format("Invalid parentRef scope '%s' for entity at '%s' scope", parentScope, entityScope));
    }
  }

  private void validateParentIsNotSameTeam(
      Triple<String, String, String> entity, Triple<String, String, String> parent) {
    boolean isSameTeam = entity.getLeft().equals(parent.getLeft()) && entity.getMiddle().equals(parent.getMiddle())
        && entity.getRight().equals(parent.getRight());

    if (isSameTeam) {
      throw new InvalidRequestException("Parent team cannot be the same as current team");
    }
  }

  private boolean isValidOwnerOrParentScope(String[] entityScopeToken, String[] ownerScopeToken) {
    boolean isValid = true;
    if (entityScopeToken.length == 1) {
      if (ownerScopeToken.length != 1) {
        isValid = false;
      }
    } else if (entityScopeToken.length == 2) {
      if (ownerScopeToken.length == 3) {
        isValid = false;
      } else if (ownerScopeToken.length == 2 && !entityScopeToken[1].equals(ownerScopeToken[1])) {
        isValid = false;
      }
    } else {
      if (ownerScopeToken.length == 3
          && (!entityScopeToken[1].equals(ownerScopeToken[1]) || !entityScopeToken[2].equals(ownerScopeToken[2]))) {
        isValid = false;
      } else if (ownerScopeToken.length == 2 && !entityScopeToken[1].equals(ownerScopeToken[1])) {
        isValid = false;
      }
    }
    return isValid;
  }

  public String resolveExpressionsInEntityYaml(String accountIdentifier, String entityYaml) {
    Matcher matcher = VARIABLE_EXPRESSION_PATTERN.matcher(entityYaml);
    Set<String> identifiers = new HashSet<>();
    while (matcher.find()) {
      String identifier = matcher.group(1);
      identifiers.add(identifier);
    }
    Object systemVariables = getResponse(accountClient.getAccountDTO(accountIdentifier));
    Map<String, String> accountVariables = fetchAccountVariables(accountIdentifier, identifiers);
    IdpVariableExpressionEvaluator evaluator = new IdpVariableExpressionEvaluator(systemVariables, accountVariables);
    Object loadedYaml = new Yaml().load(entityYaml);
    Object resolvedYaml = evaluator.resolve(loadedYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return yamlObject().dump(resolvedYaml);
  }

  public void validateWorkflowNoCaseCollidingKeys(String kind, Map<String, Object> entityMap) {
    if (!WORKFLOW_KIND.equals(kind)) {
      return;
    }
    Set<String> keys = new HashSet<>();
    collectTraversedKeys(entityMap, "", keys);
    Map<String, String> lowerToOriginal = new HashMap<>();
    List<String> collisions = new ArrayList<>();
    for (String key : keys) {
      String lower = key.toLowerCase();
      String existing = lowerToOriginal.putIfAbsent(lower, key);
      if (existing != null) {
        collisions.add("'" + existing + "' vs '" + key + "'");
      }
    }
    if (!collisions.isEmpty()) {
      throw new InvalidRequestException("Workflow contains keys that differ only in casing which is not supported. "
          + "Conflicting keys: " + String.join(", ", collisions));
    }
  }

  @SuppressWarnings("unchecked")
  private void collectTraversedKeys(Object current, String path, Set<String> keys) {
    if (current == null || current instanceof String || current instanceof Number || current instanceof Boolean) {
      if (!path.isEmpty()) {
        keys.add(path);
      }
      return;
    }
    if (current instanceof List) {
      for (Object item : (List<?>) current) {
        collectTraversedKeys(item, path, keys);
        if (item instanceof String && !path.isEmpty()) {
          keys.add(path + "." + item);
        }
      }
      return;
    }
    if (current instanceof Map) {
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) current).entrySet()) {
        String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
        collectTraversedKeys(entry.getValue(), childPath, keys);
      }
    }
  }

  public void deleteFavorite(String harnessAccount, String orgIdentifier, String projectIdentifier, String resourceId) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      return;
    }

    resourceId = Base64.getEncoder().encodeToString(resourceId.getBytes());

    if (!isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
      NGRestUtils.getGeneralResponse(favoritesClient.deleteProjectFavorite(
          harnessAccount, orgIdentifier, projectIdentifier, userPrincipal.getUniqueId(), IDPENTITY.name(), resourceId));
    } else if (!isEmpty(orgIdentifier)) {
      NGRestUtils.getGeneralResponse(favoritesClient.deleteOrgFavorite(
          harnessAccount, orgIdentifier, userPrincipal.getUniqueId(), IDPENTITY.name(), resourceId));
    } else {
      NGRestUtils.getGeneralResponse(favoritesClient.deleteAccountFavorite(
          harnessAccount, userPrincipal.getUniqueId(), IDPENTITY.name(), resourceId));
    }
  }

  private Map<String, String> fetchAccountVariables(String accountIdentifier, Set<String> identifiers) {
    Map<String, String> accountVariables = new HashMap<>();
    if (isEmpty(identifiers)) {
      return accountVariables;
    }
    try {
      VariableListRequestDTO variableListRequestDTO =
          VariableListRequestDTO.builder().identifiers(identifiers.stream().toList()).build();
      PageResponse<VariableResponseDTO> variableResponseDTOPageResponse =
          NGRestUtils.getResponse(variableClient.getVariablesListV2(
              accountIdentifier, null, null, 0, 100, null, false, variableListRequestDTO));
      List<VariableResponseDTO> variableResponseDTOList = variableResponseDTOPageResponse.getContent();
      variableResponseDTOList.forEach(variableResponseDTO
          -> accountVariables.put(variableResponseDTO.getVariable().getIdentifier(),
              (String) variableResponseDTO.getVariable().getVariableConfig().getValue()));
    } catch (Exception e) {
      log.error("Error occurred while fetching variables: " + identifiers, e);
    }
    return accountVariables;
  }

  public String getAllScopes() {
    return "account.*";
  }

  public Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> getScopeInfosBasedOnScopesAndEntityRefs(
      String accountIdentifier, String scopes, String entityRefs) {
    Map<String, List<ScopeInfo>> scopeInfosForScopes = new HashMap<>();

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .uniqueId(accountIdentifier)
                                     .build();

    Set<String> orgs = new HashSet<>();
    Map<String, Set<String>> projectsByOrg = new HashMap<>();

    List<ScopeInfo> allScopeInfos = new ArrayList<>();
    if (!isEmpty(scopes)) {
      String[] scopesList = scopes.split(",");
      Arrays.stream(scopesList).forEach(scope -> {
        if (!isEmpty(scope)) {
          ScopeInfo scopeInfo;

          if (scope.equalsIgnoreCase("account")) {
            scopeInfo = accountScopeInfo;
            allScopeInfos.add(scopeInfo);
          } else if (scope.equalsIgnoreCase("account.org")) {
            List<CatalogEntity> catalogEntities =
                catalogEntityRepository.findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNull(
                    accountIdentifier);

            orgs.addAll(catalogEntities.stream()
                            .map(CatalogEntity::getOrgIdentifier)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
          } else if (scope.equalsIgnoreCase("account.org.project")) {
            List<CatalogEntity> catalogEntities =
                catalogEntityRepository
                    .findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNotNull(
                        accountIdentifier);

            catalogEntities.stream()
                .filter(ce -> ce.getOrgIdentifier() != null)
                .collect(Collectors.groupingBy(CatalogEntity::getOrgIdentifier,
                    Collectors.mapping(CatalogEntity::getProjectIdentifier, Collectors.toSet())))
                .forEach((org, projects) -> projectsByOrg.merge(org, projects, (existingSet, newSet) -> {
                  existingSet.addAll(newSet);
                  return existingSet;
                }));
          } else if (scope.equalsIgnoreCase("account.*")) {
            scopeInfo = accountScopeInfo;
            allScopeInfos.add(scopeInfo);
            // TODO change this logic
            List<CatalogEntity> catalogEntities =
                catalogEntityRepository.findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNull(
                    accountIdentifier);

            orgs.addAll(catalogEntities.stream()
                            .map(CatalogEntity::getOrgIdentifier)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
            catalogEntities = catalogEntityRepository
                                  .findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNotNull(
                                      accountIdentifier);

            catalogEntities.stream()
                .filter(ce -> ce.getOrgIdentifier() != null)
                .collect(Collectors.groupingBy(CatalogEntity::getOrgIdentifier,
                    Collectors.mapping(CatalogEntity::getProjectIdentifier, Collectors.toSet())))
                .forEach((org, projects) -> projectsByOrg.merge(org, projects, (existingSet, newSet) -> {
                  existingSet.addAll(newSet);
                  return existingSet;
                }));
          } else if (scope.startsWith("account.") && scope.endsWith(".*")) {
            String[] hierarchyScope = scope.split("\\.");
            String orgIdentifier = hierarchyScope[1];
            List<CatalogEntity> catalogEntities =
                catalogEntityRepository.findAllByAccountIdentifierAndOrgIdentifierIs(accountIdentifier, orgIdentifier);

            catalogEntities.stream()
                .filter(ce -> ce.getOrgIdentifier() != null)
                .collect(Collectors.groupingBy(CatalogEntity::getOrgIdentifier,
                    Collectors.mapping(CatalogEntity::getProjectIdentifier, Collectors.toSet())))
                .forEach((org, projects) -> projectsByOrg.merge(org, projects, (existingSet, newSet) -> {
                  existingSet.addAll(newSet);
                  return existingSet;
                }));
          } else {
            String[] hierarchyScope = scope.split("\\.");
            if (hierarchyScope.length == 3) {
              projectsByOrg.computeIfAbsent(hierarchyScope[1], k -> new HashSet<>()).add(hierarchyScope[2]);
            } else if (hierarchyScope.length == 2) {
              orgs.add(hierarchyScope[1]);
            }
          }
        }
      });
    }

    if (!isEmpty(entityRefs)) {
      String[] entityRefsList = entityRefs.split(",");
      Arrays.stream(entityRefsList).forEach(entityRef -> {
        if (!isEmpty(entityRef)) {
          Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifier(entityRef);
          String[] hierarchyScope = kindScopeIdentifier.getMiddle().split("\\.");
          ScopeInfo scopeInfo;
          if (hierarchyScope.length == 3) {
            projectsByOrg.computeIfAbsent(hierarchyScope[1], k -> new HashSet<>()).add(hierarchyScope[2]);
          } else if (hierarchyScope.length == 2) {
            orgs.add(hierarchyScope[1]);
          } else if (hierarchyScope.length == 1 && hierarchyScope[0].equals("account")) {
            scopeInfo = accountScopeInfo;
            allScopeInfos.add(scopeInfo);
          }
        }
      });
    }

    if (!isEmpty(orgs)) {
      List<OrganizationResponse> organizationResponses =
          NGRestUtils
              .getResponse(organizationClient.listAllOrganizations(accountIdentifier, orgs.stream().toList(), null))
              .getContent();
      Set<String> orgIdentifiersInAccount = organizationResponses.stream()
                                                .map(response -> response.getOrganization().getIdentifier())
                                                .collect(Collectors.toSet());
      if (!isEmpty(orgIdentifiersInAccount)) {
        List<ScopeInfo> scopeInfoList = new ArrayList<>();
        try {
          scopeInfoList =
              NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgIdentifiersInAccount));
        } catch (Exception ex) {
          log.warn("Error in getScopeInfoList for list of organizations = {} Error = {}", orgIdentifiersInAccount,
              ex.getMessage(), ex);
        }
        if (!isEmpty(scopeInfoList)) {
          allScopeInfos.addAll(scopeInfoList.stream().filter(Objects::nonNull).toList());
        }
      }
    }

    Set<String> orgIdentifiers =
        projectsByOrg.keySet().stream().filter(key -> !isEmpty(key)).collect(Collectors.toSet());
    if (!isEmpty(orgIdentifiers)) {
      List<OrganizationResponse> organizationResponses =
          NGRestUtils
              .getResponse(
                  organizationClient.listAllOrganizations(accountIdentifier, orgIdentifiers.stream().toList(), null))
              .getContent();
      orgIdentifiers = organizationResponses.stream()
                           .map(response -> response.getOrganization().getIdentifier())
                           .collect(Collectors.toSet());
      final Set<String> validOrgIdentifiers = orgIdentifiers;
      projectsByOrg.entrySet().removeIf(entry -> !validOrgIdentifiers.contains(entry.getKey()));
    }
    List<String> projectIdentifiers =
        projectsByOrg.values().stream().filter(Objects::nonNull).flatMap(Set::stream).filter(v -> !isEmpty(v)).toList();

    List<ProjectDTO> projectDTOS = new ArrayList<>();
    if (!isEmpty(orgIdentifiers) && !isEmpty(projectIdentifiers)) {
      List<List<String>> orgBatches = Lists.partition(new ArrayList<>(orgIdentifiers), ORG_BATCH_SIZE);
      for (List<String> orgBatch : orgBatches) {
        Set<String> orgBatchSet = new HashSet<>(orgBatch);
        List<String> projectsForBatch = orgBatchSet.stream()
                                            .filter(projectsByOrg::containsKey)
                                            .flatMap(org
                                                -> projectsByOrg.get(org) != null ? projectsByOrg.get(org).stream()
                                                                                  : java.util.stream.Stream.empty())
                                            .filter(v -> !isEmpty(v))
                                            .distinct()
                                            .toList();
        if (isEmpty(projectsForBatch)) {
          continue;
        }

        // TODO : add caching and then listen to harness-redis-streams
        List<List<String>> projectBatches = Lists.partition(projectsForBatch, PROJECT_BATCH_SIZE);
        for (List<String> projectBatch : projectBatches) {
          int page = 0;
          final int pageSize = 100;
          while (true) {
            PageResponse<ProjectResponse> projects = NGRestUtils.getResponse(projectClient.listWithMultiOrg(
                accountIdentifier, orgBatchSet, false, projectBatch, null, null, page, pageSize, null, false));
            if (projects == null || isEmpty(projects.getContent())) {
              break;
            }
            projectDTOS.addAll(projects.getContent().stream().map(ProjectResponse::getProject).toList());
            if (projects.getContent().size() < pageSize) {
              break;
            }
            page++;
          }
        }
      }
    }
    Map<String, Set<String>> projectsByOrganization = projectDTOS.stream().collect(Collectors.groupingBy(
        ProjectDTO::getOrgIdentifier, Collectors.mapping(ProjectDTO::getIdentifier, Collectors.toSet())));

    projectsByOrganization.forEach((k, v) -> {
      List<ScopeInfo> projectsScopeInfoList = new ArrayList<>();
      try {
        projectsScopeInfoList = NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, k, v));
      } catch (Exception ex) {
        log.warn("Error in getScopeInfoList for list of projects = {} in organization = {} Error = {}", v, k,
            ex.getMessage(), ex);
      }
      if (!isEmpty(projectsScopeInfoList)) {
        allScopeInfos.addAll(projectsScopeInfoList.stream().filter(Objects::nonNull).toList());
      }
    });

    if (isEmpty(allScopeInfos)) {
      allScopeInfos.add(accountScopeInfo);
    }

    scopeInfosForScopes.put(scopes, allScopeInfos);

    return Pair.of(allScopeInfos, scopeInfosForScopes);
  }

  public void validateSystemScope(CatalogEntity catalogEntity) {
    Map<String, Object> spec = catalogEntity.getSpec();

    if (spec == null || !spec.containsKey("system")) {
      return;
    }

    Object systemField = spec.get("system");

    List<String> systemValues = new ArrayList<>();

    if (systemField instanceof String) {
      systemValues.add((String) systemField);
    } else if (systemField instanceof List<?>) {
      for (Object obj : (List<?>) systemField) {
        if (obj instanceof String) {
          systemValues.add((String) obj);
        } else {
          throw new InvalidRequestException(
              String.format("Invalid value in 'spec.system' for entity [%s]: expected list of strings but found %s",
                  catalogEntity.getIdentifier(), obj == null ? "null" : obj.getClass().getSimpleName()));
        }
      }
    } else {
      throw new InvalidRequestException(String.format(
          "'spec.system' must be either a string or list of strings for entity [%s]", catalogEntity.getIdentifier()));
    }

    String entityScope = catalogEntity.getScope();
    String orgId = catalogEntity.getOrgIdentifier();
    String projectId = catalogEntity.getProjectIdentifier();

    List<String> invalidSystems = new ArrayList<>();

    for (String rawSystem : systemValues) {
      if (!rawSystem.startsWith("system:")) {
        invalidSystems.add(rawSystem + " (missing 'system:' prefix)");
        continue;
      }

      String systemRef = rawSystem.substring("system:".length());
      String[] parts = systemRef.split("/");

      if (parts.length < 1) {
        invalidSystems.add(rawSystem + " (missing scope or identifier)");
        continue;
      }

      String scopePart = parts[0];
      String[] scopeTokens = scopePart.split("\\.");

      boolean isValid = switch (entityScope) {
        case "ACCOUNT" -> scopeTokens.length == 1 && "account".equals(scopeTokens[0]);

        case "ORGANIZATION" -> scopeTokens.length == 2
                && "account".equals(scopeTokens[0])
                && orgId != null
                && orgId.equals(scopeTokens[1]);

        case "PROJECT" -> scopeTokens.length == 3
                && "account".equals(scopeTokens[0])
                && orgId != null
                && orgId.equals(scopeTokens[1])
                && projectId != null
                && projectId.equals(scopeTokens[2]);

        default -> false;
      };

      if (!isValid) {
        invalidSystems.add(rawSystem + " (does not match " + entityScope + " scope)");
      }
    }

    if (!invalidSystems.isEmpty()) {
      throw new InvalidRequestException(String.format(
              "Invalid 'spec.system' values(system should be in same scope) for catalog entity [identifier: %s, scope: %s]: %s",
              catalogEntity.getIdentifier(), entityScope, String.join("; ", invalidSystems)));
    }
  }

  public Map<String, Object> getScopeFilter(String accountIdentifier, List<String> kinds, String filter) {
    Map<String, Object> scopeFilter = new HashMap<>();

    List<RecentSelectedScopesDTO> recentSelectedScopesDTOList = getRecentSelectedScopes(accountIdentifier);
    Set<RecentSelectedScopesDTO> recentOrganizations =
        recentSelectedScopesDTOList.stream()
            .filter(recentSelectedScopesDTO
                -> !isEmpty(recentSelectedScopesDTO.getScope())
                    && recentSelectedScopesDTO.getScope().equals("Organization"))
            .collect(Collectors.toSet());
    Set<RecentSelectedScopesDTO> recentProjects =
        recentSelectedScopesDTOList.stream()
            .filter(recentSelectedScopesDTO
                -> !isEmpty(recentSelectedScopesDTO.getScope()) && recentSelectedScopesDTO.getScope().equals("Project"))
            .collect(Collectors.toSet());

    ScopeInfo accountScopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));
    long accountCatalogs =
        catalogEntityRepository.countByParentUniqueIdAndKindInAndFilter(accountScopeInfo.getUniqueId(), kinds, filter);
    scopeFilter.put("account_level_entities", accountCatalogs);

    List<Map<String, Object>> recentOrganizationsData = new ArrayList<>();
    recentOrganizations.forEach(recentOrganization -> {
      if (!isEmpty(recentOrganization.getOrgIdentifier())) {
        ScopeInfo orgScopeInfo = failSafeGetScopeInfo(accountIdentifier, recentOrganization.getOrgIdentifier(), null);
        if (orgScopeInfo != null) {
          long orgCatalogs = catalogEntityRepository.countByParentUniqueIdAndKindInAndFilter(orgScopeInfo.getUniqueId(), kinds, filter);
          recentOrganizationsData.add(Map.of("org_identifier", recentOrganization.getOrgIdentifier(), "org_name",
              recentOrganization.getName(), "count", orgCatalogs));
        }
      }
    });
    scopeFilter.put("recent_organizations", recentOrganizationsData);

    List<Map<String, Object>> recentProjectsData = new ArrayList<>();
    recentProjects.forEach(recentProject -> {
      if (!isEmpty(recentProject.getOrgIdentifier()) && !isEmpty(recentProject.getProjIdentifier())) {
        ScopeInfo projectScopeInfo = failSafeGetScopeInfo(
            accountIdentifier, recentProject.getOrgIdentifier(), recentProject.getProjIdentifier());
        if (projectScopeInfo != null) {
          long projectCatalogs =
              catalogEntityRepository.countByParentUniqueIdAndKindInAndFilter(projectScopeInfo.getUniqueId(), kinds, filter);
          Optional<OrganizationResponse> optionalOrganizationResponse = NGRestUtils.getResponse(
              organizationClient.getOrganization(recentProject.getOrgIdentifier(), accountIdentifier));
          recentProjectsData.add(Map.of("org_identifier", recentProject.getOrgIdentifier(), "org_name",
              optionalOrganizationResponse.get().getOrganization().getName(), "project_identifier",
              recentProject.getProjIdentifier(), "project_name", recentProject.getName(), "count", projectCatalogs));
        }
      }
    });
    scopeFilter.put("recent_projects", recentProjectsData);

    List<Map<String, Object>> favoriteProjectsData = new ArrayList<>();
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null) {
      scopeFilter.put("favorite_projects", favoriteProjectsData);
      return scopeFilter;
    }

    List<FavoriteResponse> favoriteResponses = new ArrayList<>();
    List<OrganizationResponse> organizationResponseList =
        NGRestUtils
            .getResponse(organizationClient.listAllOrganizations(accountIdentifier, Collections.emptyList(), null))
            .getContent();
    organizationResponseList.forEach(organizationResponse
        -> favoriteResponses.addAll(NGRestUtils.getGeneralResponse(favoritesClient.getOrgFavorites(accountIdentifier,
            organizationResponse.getOrganization().getIdentifier(), userPrincipal.getUniqueId(), PROJECT.name()))));
    favoriteResponses.forEach(favoriteResponse -> {
      FavoriteDTO favoriteDTO = favoriteResponse.getFavorite();
      String projectIdentifier = favoriteDTO.getResourceId();
      ScopeInfo projectScopeInfo = failSafeGetScopeInfo(accountIdentifier, favoriteDTO.getOrg(), projectIdentifier);
      if (projectScopeInfo != null) {
        long projectCatalogs =
            catalogEntityRepository.countByParentUniqueIdAndKindInAndFilter(projectScopeInfo.getUniqueId(), kinds, filter);
        Optional<OrganizationResponse> optionalOrganizationResponse =
            NGRestUtils.getResponse(organizationClient.getOrganization(favoriteDTO.getOrg(), accountIdentifier));
        Optional<ProjectResponse> optionalProjectResponse = NGRestUtils.getResponse(
            projectClient.getProject(projectIdentifier, accountIdentifier, favoriteDTO.getOrg()));
        favoriteProjectsData.add(Map.of("org_identifier", favoriteDTO.getOrg(), "org_name",
            optionalOrganizationResponse.get().getOrganization().getName(), "project_identifier", projectIdentifier,
            "project_name", optionalProjectResponse.get().getProject().getName(), "count", projectCatalogs));
      }
    });
    scopeFilter.put("favorite_projects", favoriteProjectsData);

    return scopeFilter;
  }

  public List<RecentSelectedScopesDTO> getRecentSelectedScopes(String harnessAccount) {
    Map<String, String> userPreferences = getUserPreferences(harnessAccount);
    String recentSelectedScopes = userPreferences.getOrDefault("recent_selected_scopes", "");
    if (!isEmpty(recentSelectedScopes)) {
      return Arrays.stream(GsonUtils.convertJsonStringToObject(recentSelectedScopes, RecentSelectedScopesDTO[].class))
          .toList();
    }
    return new ArrayList<>();
  }

  public Map<String, String> getUserPreferences(String harnessAccount) {
    try {
      return NGRestUtils.getResponse(settingsClient.getUserPreferences(harnessAccount));
    } catch (Exception ex) {
      log.error("Error in getUserPreferences for account = {} Error = {}", harnessAccount, ex.getMessage(), ex);
      return new HashMap<>();
    }
  }

  public String getScope(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("AccountIdentifier cannot be empty for scope derivation");
    }
    if (!isEmpty(projectIdentifier) && !isEmpty(orgIdentifier)) {
      return ScopeLevel.PROJECT.name();
    }
    if (!isEmpty(orgIdentifier)) {
      return ScopeLevel.ORGANIZATION.name();
    }
    return ScopeLevel.ACCOUNT.name();
  }

  public GitReferencedCatalogEntity convertInlineToGitEntity(CatalogEntity catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      return (GitReferencedCatalogEntity) catalogEntity;
    }
    GitReferencedCatalogEntity gitReferencedCatalogEntity = new GitReferencedCatalogEntity();
    gitReferencedCatalogEntity.setId(catalogEntity.getId());
    gitReferencedCatalogEntity.setReferenceType(ReferenceType.GIT);
    gitReferencedCatalogEntity.setAccountIdentifier(catalogEntity.getAccountIdentifier());
    gitReferencedCatalogEntity.setOrgIdentifier(catalogEntity.getOrgIdentifier());
    gitReferencedCatalogEntity.setProjectIdentifier(catalogEntity.getProjectIdentifier());
    gitReferencedCatalogEntity.setIdentifier(catalogEntity.getIdentifier());
    gitReferencedCatalogEntity.setApiVersion(catalogEntity.getApiVersion());
    gitReferencedCatalogEntity.setKind(catalogEntity.getKind());
    gitReferencedCatalogEntity.setType(catalogEntity.getType());
    gitReferencedCatalogEntity.setName(catalogEntity.getName());
    gitReferencedCatalogEntity.setDescription(catalogEntity.getDescription());
    gitReferencedCatalogEntity.setOwner(catalogEntity.getOwner());
    gitReferencedCatalogEntity.setTags(catalogEntity.getTags());
    gitReferencedCatalogEntity.setSourceLocation(catalogEntity.getSourceLocation());
    gitReferencedCatalogEntity.setSpec(catalogEntity.getSpec());
    gitReferencedCatalogEntity.setMetadata(catalogEntity.getMetadata());
    gitReferencedCatalogEntity.setRelations(catalogEntity.getRelations());
    if (!isEmpty(catalogEntity.getDecorator())) {
      gitReferencedCatalogEntity.setDecorator(catalogEntity.getDecorator());
    }
    gitReferencedCatalogEntity.setYaml(catalogEntity.getYaml());
    gitReferencedCatalogEntity.setUniqueId(catalogEntity.getUniqueId());
    gitReferencedCatalogEntity.setParentUniqueId(catalogEntity.getParentUniqueId());
    gitReferencedCatalogEntity.setCreatedAt(catalogEntity.getCreatedAt());
    gitReferencedCatalogEntity.setCreatedBy(catalogEntity.getCreatedBy());

    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    gitReferencedCatalogEntity.setStoreType(gitEntityInfo.getStoreType());
    gitReferencedCatalogEntity.setRepo(gitEntityInfo.getRepoName());
    gitReferencedCatalogEntity.setFilePath(gitEntityInfo.getFilePath());
    gitReferencedCatalogEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    gitReferencedCatalogEntity.setFallBackBranch(gitEntityInfo.getBranch());
    gitReferencedCatalogEntity.setRepoURL(gitAwareEntityHelper.getRepoUrl(catalogEntity.getAccountIdentifier(),
            catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()));
    return gitReferencedCatalogEntity;
  }

  private ScopeInfo failSafeGetScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception ex) {
      log.error("Error in getScopeInfo for account = {} org = {} project = {} Error = {}", accountIdentifier,
          orgIdentifier, projectIdentifier, ex.getMessage(), ex);
      return null;
    }
  }

  public void validateAndPopulateIsCustomForCustomUserGroup(CatalogEntity catalogEntity) {
    if (GROUP_KIND.equals(catalogEntity.getKind())) {
      Map<String, Object> spec = isEmpty(catalogEntity.getSpec()) ? new HashMap<>() :
          catalogEntity.getSpec();
          spec.put(IS_CUSTOM_USER_GROUP, true);
          catalogEntity.setSpec(spec);
      }
    }

    public void validateUpdateDeleteForCustomUserGroup(CatalogEntity catalogEntity) {
      if (GROUP_KIND.equals(catalogEntity.getKind())) {
        Map<String, Object> spec = catalogEntity.getSpec();
        if (!isEmpty(spec) && !spec.containsKey(IS_CUSTOM_USER_GROUP)) {
          throw new InvalidRequestException("Cannot update or delete platform user group");
        }
      }
    }

    public List<String> filterValidWorkflows(List<String> workflows, Set<String> validWorkflowIdentifiers) {
      if (isEmpty(workflows) || isEmpty(validWorkflowIdentifiers)) {
        return workflows != null ? workflows : Collections.emptyList();
      }
      return workflows.stream().filter(validWorkflowIdentifiers::contains).collect(Collectors.toList());
    }

    public boolean checkIfCustomUserGroup(CatalogEntity catalogEntity) {
      return GROUP_KIND.equals(catalogEntity.getKind()) && !isEmpty(catalogEntity.getSpec())
          && catalogEntity.getSpec().containsKey(IS_CUSTOM_USER_GROUP);
    }

    public String resolveMembersForCustomUserGroup(String kind, String yaml) {
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(yaml);
      Map<String, Object> spec = from(entityYamlMap, "spec", Map.class);
      if (!isEmpty(spec) && kind.equals(GROUP_KIND)) {
        Set<String> members = from(spec, MEMBERS, Set.class);
        if (!isEmpty(members)) {
          List<String> resolvedMembers = new ArrayList<>();
          members.forEach(member -> resolvedMembers.add(resolveUser(member)));
          spec.put(MEMBERS, resolvedMembers);
        }
      }
      return YamlUtils.writeObjectAsYaml(entityYamlMap);
    }

    public void publishAsyncComputationEvent(
        String accountIdentifier, String scorecardIdentifier, String entityIdentifier) {
      idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
          accountIdentifier, scorecardIdentifier, entityIdentifier);
    }

    private String resolveUser(String user) {
      if (user.startsWith("user:default/")) {
        return "user:account/" + user.substring(13);
      }
      if (user.startsWith("user:") && !user.startsWith("user:account/") && user.split(":").length == 2
          && !user.contains("/")) {
        return "user:account/" + user.substring(5);
      }
      if (!user.startsWith("user:") && !user.startsWith("user:account/")) {
        return "user:account/" + user;
      }
      return user;
    }

    private Pair<String, String> getResourceTypeAndPermission(String kind, String permission) {
      if (WORKFLOW_KIND.equals(kind)) {
        return Pair.of("IDP_WORKFLOW", "idp_workflow_" + permission);
      } else if (ENVIRONMENT_KIND.equals(kind)) {
        return Pair.of("IDP_ENVIRONMENT", "idp_idpenvironment_" + permission);
      } else if (ENVIRONMENT_BLUEPRINT_KIND.equals(kind)) {
        return Pair.of("IDP_ENVIRONMENT_BLUEPRINT", "idp_environmentblueprint_" + permission);
      } else if (GROUP_KIND.equals(kind)) {
        return Pair.of("IDP_TEAM", "idp_team_" + permission);
      } else {
        return Pair.of("IDP_CATALOG", "idp_catalog_" + permission);
      }
    }

    public void validateMultipleDefinitionInYaml(String entityYaml) {
      List<Object> yamlDefinitions = new ArrayList<>();
      yamlObject().loadAll(entityYaml).forEach(yamlDefinitions::add);
      if (yamlDefinitions.size() > 1) {
        throw new InvalidRequestException("Multiple entity definition in single yaml is not supported yet");
      }
    }

    public String queryableEntityRef(CatalogEntity catalogEntity) {
      return catalogEntity.getParentUniqueId() + "/" + catalogEntity.getKind() + "/" + catalogEntity.getIdentifier();
    }

    public String includeChildScopesIfApplicable(String scopes, Boolean includeChildScopes) {
      if (Boolean.TRUE.equals(includeChildScopes)) {
        StringBuilder descendantScopes = new StringBuilder();
        String[] scopesArray = scopes.split(",");
        for (int i = 0; i < scopesArray.length; i++) {
          if (scopesArray[i].endsWith(".*")) {
            descendantScopes.append(scopesArray[i]);
          } else {
            String[] split = scopesArray[i].split("\\.");
            if (split.length == 1) {
              descendantScopes.append("account.*");
            } else if (split.length == 2) {
              descendantScopes.append(scopesArray[i]).append(".*");
            } else {
              descendantScopes.append(scopesArray[i]);
            }
          }
          if (i < scopesArray.length - 1) {
            descendantScopes.append(",");
          }
        }
        return descendantScopes.toString();
      }
      return scopes;
    }

    @SuppressWarnings("unchecked")
    public CatalogEntity getCatalogEntityWithRbac(String harnessAccount, String kind, String scope, String identifier,
        boolean loadFromCache, boolean loadFromFallbackBranch) {
      kind = validateAndSanitizeKind(kind);
      Pair<String, String> orgProject = getOrgProjectFromScope(scope);
      ScopeInfo scopeInfo = getScopeInfo(harnessAccount, orgProject.getLeft(), orgProject.getRight());
      CatalogEntity catalogEntity =
          catalogEntityFromGit(scopeInfo.getUniqueId(), kind, identifier, loadFromCache, loadFromFallbackBranch);
      String entityRef = kind + ":" + scope + "/" + identifier;
      String owner = resolveOwner(harnessAccount, catalogEntity.getOwner());
      checkRbacWithOwnerFallback(harnessAccount, entityRef, owner, "view");
      return catalogEntity;
    }
  }
