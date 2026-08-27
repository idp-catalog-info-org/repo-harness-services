/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BackstageUtils {
  public static final String DEFAULT_NAMESPACE = "default";
  public static final String DEFAULT_KIND = "component";
  public static final String ENTITY_REF_PATTERN = "%s:%s/%s";

  public static String getEntityUniqueId(BackstageCatalogEntity backstageCatalogEntity) {
    return getEntityUniqueId(BackstageCatalogEntity.getValue(
                                 backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAMESPACE, String.class),
        backstageCatalogEntity.getKind(),
        BackstageCatalogEntity.getValue(
            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAME, String.class));
  }

  public static String getEntityUniqueId(String namespace, String kind, String name) {
    namespace = isEmpty(namespace) ? "default" : namespace;
    return namespace + "/" + kind + "/" + name;
  }

  public static String getEntityRef(BackstageCatalogEntity backstageCatalogEntity) {
    return getEntityRef(BackstageCatalogEntity.getValue(
                            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAMESPACE, String.class),
        backstageCatalogEntity.getKind(),
        BackstageCatalogEntity.getValue(
            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAME, String.class));
  }

  public static String getEntityRef(String namespace, String kind, String name) {
    namespace = isEmpty(namespace) ? "default" : namespace;
    return String.format(ENTITY_REF_PATTERN, kind, namespace, name).toLowerCase();
  }

  public static String getEntityUniqueIdForByNameAPI(String entityUid) {
    String[] namespaceKindAndName = entityUid.split("/");
    return namespaceKindAndName[1] + "/" + namespaceKindAndName[0] + "/" + namespaceKindAndName[2];
  }

  public static String getEntityRefFromUid(String entityUid) {
    if (entityUid.contains(":") && entityUid.contains("/")) {
      return entityUid.toLowerCase();
    }
    String[] kindNamespaceAndName = entityUid.split("/");
    String kind = Character.toLowerCase(kindNamespaceAndName[1].charAt(0)) + kindNamespaceAndName[1].substring(1);
    return String.format(ENTITY_REF_PATTERN, kind, kindNamespaceAndName[0], kindNamespaceAndName[2]).toLowerCase();
  }

  public static String getEntityUidFromEntityRef(String entityRef) {
    String[] colonSplit = entityRef.split(":");
    String[] namespaceAndName = colonSplit[1].split("/");
    String kind = colonSplit[0].toLowerCase();
    if (!BackstageCatalogEntityTypes.API.kind.equalsIgnoreCase(kind)) {
      kind = Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    } else {
      kind = kind.toUpperCase();
    }
    return namespaceAndName[0] + "/" + kind + "/" + namespaceAndName[1];
  }

  public static String getFullyQualifiedEntityRef(String entityRef) {
    if (entityRef.length() == 0) {
      throw new InvalidRequestException("entity_ref cannot be empty");
    }
    String[] entityRefParts = entityRef.split("[:/]", 3);
    String kind = DEFAULT_KIND;
    String namespace = DEFAULT_NAMESPACE;
    if (entityRefParts.length == 3) {
      kind = entityRefParts[0];
      validateKind(kind);
      namespace = entityRefParts[1];
    } else if (entityRefParts.length == 2) {
      if (entityRef.contains(":")) {
        kind = entityRefParts[0];
        validateKind(kind);
      } else if (entityRef.contains("/")) {
        namespace = entityRefParts[0];
      }
    }
    String name = entityRefParts[entityRefParts.length - 1];
    return String.format(ENTITY_REF_PATTERN, kind, namespace, name).toLowerCase();
  }

  private void validateKind(String kind) {
    try {
      BackstageCatalogEntityTypes.fromString(kind);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(String.format("Kind %s is not supported", kind));
    }
  }
}
