/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.catalog.utils.Constants.DEFAULT_NAMESPACE;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.Constants.NAMESPACE_ACCOUNT_PREFIX;

import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.utils.BackstageUtils;
import io.harness.idp.catalog.beans.Scope;
import io.harness.idp.catalog.entities.CatalogEntity;

import java.util.Map;
import java.util.Optional;
import lombok.Value;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogUtils {
  public static final String DEFAULT_KIND = "component";
  public static final String ENTITY_REF_PATTERN = "%s:%s/%s";

  public static String parseBackstageEntityReferenceToCatalogRelationRef(
      String entityReference, Map<String, String> usernameAndEmailMapping) {
    if (isEmpty(entityReference)) {
      return null;
    }

    int colonIndex = entityReference.indexOf(':');
    int slashIndex = entityReference.indexOf('/');

    if (slashIndex != -1 && slashIndex < colonIndex) {
      colonIndex = -1;
    }

    String kind = colonIndex == -1 ? null : entityReference.substring(0, colonIndex);
    String namespace = slashIndex == -1 ? null : entityReference.substring(colonIndex + 1, slashIndex);
    String name = entityReference.substring(Math.max(colonIndex + 1, slashIndex + 1));

    if (isEmpty(kind) || isEmpty(namespace) || isEmpty(name)) {
      return entityReference;
    }

    if (kind.equals("group") && name.startsWith("harness_")) {
      name = name.substring(7);
    }

    if (kind.equals("user")) {
      if (!name.contains("@")) {
        name = name.replaceAll("plus", "+");
      }
      if (usernameAndEmailMapping != null) {
        String email = usernameAndEmailMapping.get(name);
        if (email != null) {
          return "user:" + email;
        }
      }
    }

    return (kind.equals("domain") || kind.equals("system") || kind.equals("location"))
        ? null
        : (kind.equals("template") ? "workflow" : kind) + ":" + name;
  }

  public static String parseBackstageEntityReferenceToCatalogRelationRefForFavorites(
      String entityReference, Map<String, String> usernameAndEmailMapping) {
    if (isEmpty(entityReference)) {
      return null;
    }

    int colonIndex = entityReference.indexOf(':');
    int slashIndex = entityReference.indexOf('/');

    if (slashIndex != -1 && slashIndex < colonIndex) {
      colonIndex = -1;
    }

    String kind = colonIndex == -1 ? null : entityReference.substring(0, colonIndex);
    String namespace = slashIndex == -1 ? null : entityReference.substring(colonIndex + 1, slashIndex);
    String name = entityReference.substring(Math.max(colonIndex + 1, slashIndex + 1));

    namespace = isEmpty(namespace) ? "account" : namespace;

    if (isEmpty(kind) || isEmpty(namespace) || isEmpty(name)) {
      return entityReference;
    }

    if (kind.equals("group") && name.startsWith("harness_")) {
      name = name.substring(7);
    }

    if (kind.equals("user")) {
      if (!name.contains("@")) {
        name = name.replaceAll("plus", "+");
      }
      if (usernameAndEmailMapping != null) {
        String email = usernameAndEmailMapping.get(name);
        if (email != null) {
          return "user:" + email;
        }
      }
    }

    return (kind.equals("domain") || kind.equals("location"))
        ? null
        : (kind.equals("template") ? "workflow" : kind) + ":" + namespace + "/" + name;
  }

  public static String parseBackstageEntityRefFromCatalogRef(String entityReference, boolean migration) {
    if (isEmpty(entityReference)) {
      return null;
    }

    int colonIndex = entityReference.indexOf(':');
    String kind = colonIndex == -1 ? null : entityReference.substring(0, colonIndex);
    String remaining = entityReference.substring(colonIndex + 1);

    int slashIndex = remaining.indexOf('/');
    String namespace = "account";
    String name;

    if (slashIndex != -1) {
      namespace = remaining.substring(0, slashIndex);
      name = remaining.substring(slashIndex + 1);
    } else {
      name = remaining;
    }

    if (isEmpty(kind)) {
      return name;
    }

    if (kind.equals("user")) {
      name = name.split("@")[0].replaceAll("\\+", "plus");
    }

    if (migration) {
      namespace = "default";
    }

    return (kind.equals("workflow") ? "template" : kind) + ":" + namespace + "/" + name;
  }

  public static String parseBackstageEntityRefFromCatalogRefWithoutUserManipulation(
      String entityReference, boolean migration) {
    if (isEmpty(entityReference)) {
      return null;
    }

    int colonIndex = entityReference.indexOf(':');
    String kind = colonIndex == -1 ? null : entityReference.substring(0, colonIndex);
    String remaining = entityReference.substring(colonIndex + 1);

    int slashIndex = remaining.indexOf('/');
    String namespace = "account";
    String name;

    if (slashIndex != -1) {
      namespace = remaining.substring(0, slashIndex);
      name = remaining.substring(slashIndex + 1);
    } else {
      name = remaining;
    }

    if (isEmpty(kind)) {
      return name;
    }

    if (migration) {
      namespace = "default";
    }

    return (kind.equals("workflow") ? "template" : kind) + ":" + namespace + "/" + name;
  }

  public String replaceEmailAddressInCatalogRef(Map<String, String> usernameAndEmailMapping, String entityRef) {
    if (isEmpty(entityRef)) {
      return null;
    }

    int colonIndex = entityRef.indexOf(':');
    int slashIndex = entityRef.indexOf('/');

    if (slashIndex != -1 && slashIndex < colonIndex) {
      colonIndex = -1;
    }

    String kind = colonIndex == -1 ? null : entityRef.substring(0, colonIndex);
    String namespace = slashIndex == -1 ? null : entityRef.substring(colonIndex + 1, slashIndex);
    String name = entityRef.substring(Math.max(colonIndex + 1, slashIndex + 1));

    if (isEmpty(kind)) {
      return entityRef;
    }

    if (kind.equals("user")) {
      name = usernameAndEmailMapping.get(name.split("@")[0]);
    }

    if (!isEmpty(namespace)) {
      return kind + ":" + namespace + "/" + name;
    }
    return kind + ":" + name;
  }

  public static String getBackstageCatalogKindFromEntityUid(String entityUid) {
    String[] kindNamespaceAndName = entityUid.split("/");
    if (kindNamespaceAndName.length == 3) {
      return getHarnessCatalogKind(kindNamespaceAndName[1].toLowerCase());
    }
    return "";
  }

  public static String getBackstageCatalogNameFromEntityUid(String entityUid) {
    String[] kindNamespaceAndName = entityUid.split("/");
    if (kindNamespaceAndName.length == 3) {
      return kindNamespaceAndName[2];
    }
    return "";
  }

  public static String getHarnessCatalogKind(String backstageKind) {
    backstageKind = backstageKind.toLowerCase();
    if (backstageKind.equals("template")) {
      return WORKFLOW_KIND;
    }
    return backstageKind;
  }

  public static String getEntityUniqueIdForByNameAPI(String entityUid) {
    String[] namespaceKindAndName = entityUid.split("/");
    return namespaceKindAndName[1] + "/" + namespaceKindAndName[0] + "/" + namespaceKindAndName[2];
  }

  public static String getIdentifierForWorkflowsInGroup(String scope, String kind, String identifier) {
    return scope + "/" + kind + "/" + identifier;
  }

  public static String getFullyQualifiedScopeRef(String scope, String orgIdentifier, String projectIdentifier) {
    if (scope.equals(Scope.PROJECT.name())) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + orgIdentifier + "." + projectIdentifier;
    } else if (scope.equals(Scope.ORGANIZATION.name())) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + orgIdentifier;
    } else {
      return NAMESPACE_ACCOUNT_PREFIX;
    }
  }

  public String getEntityUUId(Object entity) {
    if (entity instanceof CatalogEntity) {
      return getFullyQualifiedScopeRef(((CatalogEntity) entity).getScope(), ((CatalogEntity) entity).getOrgIdentifier(),
                 ((CatalogEntity) entity).getProjectIdentifier())
          + "/"
          + (WORKFLOW_KIND.equals(((CatalogEntity) entity).getKind()) ? TEMPLATE_KIND
                                                                      : ((CatalogEntity) entity).getKind())
          + "/" + ((CatalogEntity) entity).getIdentifier();
    } else {
      return getEntityUniqueId((BackstageCatalogEntity) entity);
    }
  }

  public String extractConnectorRefFromSpec(CatalogEntity entity) {
    Map<String, Object> spec = entity.getSpec();
    if (isEmpty(spec)) {
      return null;
    }

    Map<String, Object> sourceCode = from(spec, "sourceCode", Map.class);
    if (isEmpty(sourceCode)) {
      return null;
    }

    String connectorIdentifier = from(sourceCode, "connectorRef", String.class);
    if (isEmpty(connectorIdentifier)) {
      throw new InvalidRequestException(String.format(
          "Connector ref is not found in sourceCode for the entity - %s in account - %s org - %s project - %s",
          entity.getIdentifier(), entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier()));
    }

    return connectorIdentifier;
  }

  public String extractSourceLocationUrlFromSpec(CatalogEntity entity) {
    Map<String, Object> spec = entity.getSpec();
    if (isEmpty(spec)) {
      return null;
    }

    Map<String, Object> sourceCode = from(spec, "sourceCode", Map.class);
    if (isEmpty(sourceCode)) {
      return null;
    }

    String url = from(sourceCode, "url", String.class);
    if (isEmpty(url)) {
      return null;
    }

    return url;
  }

  public String getEntityRef(Object entity) {
    if (entity instanceof CatalogEntity) {
      return getEntityRefInBackstageNaming((CatalogEntity) entity);
    } else {
      return getEntityRef((BackstageCatalogEntity) entity);
    }
  }

  public String getEntityRefFromUid(Object entity) {
    if (entity instanceof CatalogEntity) {
      return getEntityRefInBackstageNaming((CatalogEntity) entity);
    } else {
      return BackstageUtils.getEntityRefFromUid(((BackstageCatalogEntity) entity).getEntityUid());
    }
  }

  public String getEntityRefInBackstageNaming(CatalogEntity catalogEntity) {
    return (WORKFLOW_KIND.equals(catalogEntity.getKind()) ? TEMPLATE_KIND : catalogEntity.getKind()) + ":"
        + getFullyQualifiedScopeRef(
            catalogEntity.getScope(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier())
        + "/" + catalogEntity.getIdentifier();
  }

  public String getEntityRef(BackstageCatalogEntity backstageCatalogEntity) {
    return BackstageUtils.getEntityRef(backstageCatalogEntity);
  }

  public String getEntityRefFromUid(String entityUid) {
    if (!entityUid.contains(":")) {
      String[] parts = entityUid.split("/");
      if (parts.length == 3) {
        return (parts[1].equalsIgnoreCase("template") ? "workflow" : parts[1]) + ":"
            + (parts[0].equalsIgnoreCase("default") ? "account" : parts[0]) + "/" + parts[2];
      } else if (parts.length == 2) {
        return parts[1];
      }
      return parts[0];
    }
    return entityUid;
  }

  public String entityRef(CatalogEntity catalogEntity, boolean isIdpV2Enabled) {
    return catalogEntity.getKind() + ":" + (isIdpV2Enabled ? "account" : "default")
        + (!isEmpty(catalogEntity.getOrgIdentifier()) ? "." + catalogEntity.getOrgIdentifier() : "")
        + (!isEmpty(catalogEntity.getProjectIdentifier()) ? "." + catalogEntity.getProjectIdentifier() : "") + "/"
        + catalogEntity.getIdentifier();
  }

  public String entityRef(CatalogEntity catalogEntity) {
    return catalogEntity.getKind() + ":"
        + "account" + (!isEmpty(catalogEntity.getOrgIdentifier()) ? "." + catalogEntity.getOrgIdentifier() : "")
        + (!isEmpty(catalogEntity.getProjectIdentifier()) ? "." + catalogEntity.getProjectIdentifier() : "") + "/"
        + catalogEntity.getIdentifier();
  }

  public String entityRef(ResourceScope resourceScope, String resourceIdentifier) {
    String[] kindAndIdentifier = resourceIdentifier.split(":");
    return entityRef(kindAndIdentifier[0], resourceScope.getOrgIdentifier(), resourceScope.getProjectIdentifier(),
        kindAndIdentifier[1]);
  }

  public String entityRef(String kind, String orgIdentifier, String projectIdentifier, String identifier) {
    return kind + ":"
        + "account" + (!isEmpty(orgIdentifier) ? "." + orgIdentifier : "")
        + (!isEmpty(projectIdentifier) ? "." + projectIdentifier : "") + "/" + identifier;
  }

  /**
   * Parses a catalog relation reference string of the form {@code "<kind>:<identifier>"} or
   * {@code "<kind>:<namespace>/<identifier>"} into its components.
   *
   * <p>Both shapes are accepted by IDP and are equivalent for the implicit {@code account} namespace,
   * so the bare identifier must be returned in either case — naive prefix-strip would leave
   * {@code "account/<identifier>"} in the second form and miss downstream lookups.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code "group:my_group"}              → kind=group, namespace=account, identifier=my_group</li>
   *   <li>{@code "group:account/my_group"}      → kind=group, namespace=account, identifier=my_group</li>
   *   <li>{@code "group:account.acme/my_group"} → kind=group, namespace=account.acme, identifier=my_group</li>
   *   <li>{@code "user:jane@acme.io"}           → kind=user,  namespace=account, identifier=jane@acme.io</li>
   * </ul>
   *
   * @param relationRef the relation reference string from {@code CatalogEntity#relations}
   * @return parsed components, or empty if the input is blank or malformed
   */
  public Optional<RelationRef> parseRelationRef(String relationRef) {
    if (isEmpty(relationRef)) {
      return Optional.empty();
    }
    int colonIdx = relationRef.indexOf(':');
    if (colonIdx < 0) {
      return Optional.empty();
    }
    String kind = relationRef.substring(0, colonIdx).toLowerCase();
    String rest = relationRef.substring(colonIdx + 1);
    int slashIdx = rest.indexOf('/');
    String namespace = slashIdx < 0 ? NAMESPACE_ACCOUNT_PREFIX : rest.substring(0, slashIdx);
    String identifier = slashIdx < 0 ? rest : rest.substring(slashIdx + 1);
    if (isEmpty(identifier)) {
      return Optional.empty();
    }
    return Optional.of(new RelationRef(kind, namespace, identifier));
  }

  /** Parsed components of a catalog relation reference. See {@link #parseRelationRef(String)}. */
  @Value
  public static class RelationRef {
    String kind;
    String namespace;
    String identifier;
  }

  public String entityRefV1(CatalogEntity catalogEntity) {
    return "account" + (!isEmpty(catalogEntity.getOrgIdentifier()) ? "." + catalogEntity.getOrgIdentifier() : "")
        + (!isEmpty(catalogEntity.getProjectIdentifier()) ? "." + catalogEntity.getProjectIdentifier() : "") + "/"
        + (WORKFLOW_KIND.equals(catalogEntity.getKind()) ? TEMPLATE_KIND : catalogEntity.getKind()) + "/"
        + catalogEntity.getIdentifier();
  }

  public static String getFullyQualifiedEntityRef(String entityRef) {
    if (entityRef.isEmpty()) {
      throw new InvalidRequestException("entity_ref cannot be empty");
    }
    String[] entityRefParts = entityRef.split("[:/]", 3);
    String kind = DEFAULT_KIND;
    String namespace = NAMESPACE_ACCOUNT_PREFIX;
    if (entityRefParts.length == 3) {
      kind = entityRefParts[0];
      namespace = entityRefParts[1];
    } else if (entityRefParts.length == 2) {
      if (entityRef.contains(":")) {
        kind = entityRefParts[0];
      } else if (entityRef.contains("/")) {
        namespace = entityRefParts[0];
      }
    }
    String name = entityRefParts[entityRefParts.length - 1];
    return String.format(ENTITY_REF_PATTERN, kind.toLowerCase(),
        namespace.equalsIgnoreCase(DEFAULT_NAMESPACE) ? NAMESPACE_ACCOUNT_PREFIX : namespace, name);
  }

  public String getNamespace(CatalogEntity catalogEntity) {
    if (catalogEntity.getScope().equals(Scope.PROJECT.name())) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + catalogEntity.getOrgIdentifier() + "."
          + catalogEntity.getProjectIdentifier();
    } else if (catalogEntity.getScope().equals(Scope.ORGANIZATION.name())) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + catalogEntity.getOrgIdentifier();
    } else {
      return NAMESPACE_ACCOUNT_PREFIX;
    }
  }

  public String getScope(String orgIdentifier, String projectIdentifier) {
    if (!isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + orgIdentifier + "." + projectIdentifier;
    } else if (!isEmpty(orgIdentifier)) {
      return NAMESPACE_ACCOUNT_PREFIX + "." + orgIdentifier;
    } else {
      return NAMESPACE_ACCOUNT_PREFIX;
    }
  }
}
