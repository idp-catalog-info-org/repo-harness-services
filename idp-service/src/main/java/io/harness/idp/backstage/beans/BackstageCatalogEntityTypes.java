/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogApiEntity;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogDomainEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogGroupEntity;
import io.harness.idp.backstage.entities.BackstageCatalogLocationEntity;
import io.harness.idp.backstage.entities.BackstageCatalogResourceEntity;
import io.harness.idp.backstage.entities.BackstageCatalogSystemEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public enum BackstageCatalogEntityTypes {
  API("API"),
  COMPONENT("Component"),
  DOMAIN("Domain"),
  GROUP("Group"),
  LOCATION("Location"),
  RESOURCE("Resource"),
  SYSTEM("System"),
  TEMPLATE("Template"),
  USER("User");

  public final String kind;

  BackstageCatalogEntityTypes(String kind) {
    this.kind = kind;
  }

  public static BackstageCatalogEntityTypes fromString(String text) {
    for (BackstageCatalogEntityTypes type : BackstageCatalogEntityTypes.values()) {
      if (type.kind.equalsIgnoreCase(text)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Could not find type for %s", text));
  }

  public static String getEntityType(BackstageCatalogEntity entity) {
    return switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API -> ((BackstageCatalogApiEntity) entity).getSpec().getType();
      case COMPONENT -> ((BackstageCatalogComponentEntity) entity).getSpec().getType();
      case LOCATION -> ((BackstageCatalogLocationEntity) entity).getSpec().getType();
      case TEMPLATE -> ((BackstageCatalogTemplateEntity) entity).getSpec().getType();
      case RESOURCE -> ((BackstageCatalogResourceEntity) entity).getSpec().getType();
      default -> null;
    };
  }

  public static String getEntityOwner(BackstageCatalogEntity entity) {
    return switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API -> ((BackstageCatalogApiEntity) entity).getSpec().getOwner();
      case COMPONENT -> ((BackstageCatalogComponentEntity) entity).getSpec().getOwner();
      case RESOURCE -> ((BackstageCatalogResourceEntity) entity).getSpec().getOwner();
      case DOMAIN -> ((BackstageCatalogDomainEntity) entity).getSpec().getOwner();
      case SYSTEM -> ((BackstageCatalogSystemEntity) entity).getSpec().getOwner();
      case GROUP -> ((BackstageCatalogGroupEntity) entity).getSpec().getOwner();
      case TEMPLATE -> ((BackstageCatalogTemplateEntity) entity).getSpec().getOwner();
      default -> null;
    };
  }

  public static void setEntityOwner(BackstageCatalogEntity entity, String owner) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API -> ((BackstageCatalogApiEntity) entity).getSpec().setOwner(owner);
      case COMPONENT -> ((BackstageCatalogComponentEntity) entity).getSpec().setOwner(owner);
      case RESOURCE -> ((BackstageCatalogResourceEntity) entity).getSpec().setOwner(owner);
      case DOMAIN -> ((BackstageCatalogDomainEntity) entity).getSpec().setOwner(owner);
      case SYSTEM -> ((BackstageCatalogSystemEntity) entity).getSpec().setOwner(owner);
      case GROUP -> ((BackstageCatalogGroupEntity) entity).getSpec().setOwner(owner);
      case TEMPLATE -> ((BackstageCatalogTemplateEntity) entity).getSpec().setOwner(owner);
      default -> {
      }
    }
  }

  public static String getEntityDomain(BackstageCatalogEntity entity) {
    return switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case COMPONENT -> ((BackstageCatalogComponentEntity) entity).getSpec().getDomain();
      case SYSTEM -> ((BackstageCatalogSystemEntity) entity).getSpec().getDomain();
      default -> null;
    };
  }

  public static String getEntitySystem(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API: {
        List<String> system = ((BackstageCatalogApiEntity) entity).getSpec().getSystem();
        return (!isEmpty(system)) ? system.get(0) : null;
      }
      case COMPONENT: {
        List<String> system = ((BackstageCatalogComponentEntity) entity).getSpec().getSystem();
        return (!isEmpty(system)) ? system.get(0) : null;
      }
      case RESOURCE: {
        List<String> system = ((BackstageCatalogResourceEntity) entity).getSpec().getSystem();
        return (!isEmpty(system)) ? system.get(0) : null;
      }
      default:
        return null;
    }
  }

  public static String getEntityLifecycle(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API:
        return ((BackstageCatalogApiEntity) entity).getSpec().getLifecycle();
      case COMPONENT:
        return ((BackstageCatalogComponentEntity) entity).getSpec().getLifecycle();
      default:
        return null;
    }
  }
}
