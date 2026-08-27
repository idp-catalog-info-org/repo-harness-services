/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.entities;

import static io.harness.idp.backstage.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.backstage.Constants.PIPE_DELIMITER;
import static io.harness.idp.backstage.Constants.SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.spec.server.idp.v1.model.CDEntityAsIdpEntity;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogComponentEntity extends BackstageCatalogEntity {
  private Spec spec;

  public BackstageCatalogComponentEntity() {
    super.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
  }

  public BackstageCatalogComponentEntity(Spec spec) {
    super.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
    this.spec = spec;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Spec {
    private String type;
    private String lifecycle;
    private String owner;
    private String domain;
    private List<String> system;
    @JsonIgnore private String harnessSystem;
    private Object subcomponentOf;
    private Object providesApis;
    private Object consumesApis;
    private Object dependsOn;
  }

  public static List<HarnessBackstageEntities> map(
      List<BackstageCatalogComponentEntity> backstageCatalogComponentEntities) {
    return backstageCatalogComponentEntities.stream()
        .map(BackstageCatalogComponentEntity::convert)
        .collect(Collectors.toList());
  }

  public static List<CDEntityAsIdpEntity> mapV2(
      List<BackstageCatalogComponentEntity> backstageCatalogComponentEntities) {
    return backstageCatalogComponentEntities.stream().map(BackstageCatalogComponentEntity::convertV2).toList();
  }

  private static HarnessBackstageEntities convert(BackstageCatalogComponentEntity backstageCatalogComponentEntity) {
    HarnessBackstageEntities harnessIdpServiceEntity = new HarnessBackstageEntities();

    harnessIdpServiceEntity.setIdentifier(backstageCatalogComponentEntity.getSpec().getDomain() + PIPE_DELIMITER
        + backstageCatalogComponentEntity.getSpec().getHarnessSystem() + PIPE_DELIMITER
        + BackstageCatalogEntity.getValue(
            backstageCatalogComponentEntity.getMetadata(), MetadataFieldConstants.IDENTIFIER, String.class));
    harnessIdpServiceEntity.setEntityType(SERVICE);
    harnessIdpServiceEntity.setName(BackstageCatalogEntity.getValue(
        backstageCatalogComponentEntity.getMetadata(), MetadataFieldConstants.NAME, String.class));
    harnessIdpServiceEntity.setType(SERVICE);
    harnessIdpServiceEntity.setOwner(ENTITY_UNKNOWN_OWNER);
    harnessIdpServiceEntity.setSystem(backstageCatalogComponentEntity.getSpec().getHarnessSystem());

    return harnessIdpServiceEntity;
  }

  private static CDEntityAsIdpEntity convertV2(BackstageCatalogComponentEntity backstageCatalogComponentEntity) {
    CDEntityAsIdpEntity cdEntityAsIdpEntity = new CDEntityAsIdpEntity();

    cdEntityAsIdpEntity.setHarnessAbsoluteIdentifier(backstageCatalogComponentEntity.getSpec().getDomain()
        + PIPE_DELIMITER + backstageCatalogComponentEntity.getSpec().getHarnessSystem() + PIPE_DELIMITER
        + BackstageCatalogEntity.getValue(
            backstageCatalogComponentEntity.getMetadata(), MetadataFieldConstants.IDENTIFIER, String.class));
    cdEntityAsIdpEntity.setHarnessType(SERVICE);
    cdEntityAsIdpEntity.setName(BackstageCatalogEntity.getValue(
        backstageCatalogComponentEntity.getMetadata(), MetadataFieldConstants.NAME, String.class));
    cdEntityAsIdpEntity.setDomain(backstageCatalogComponentEntity.getSpec().getDomain());
    cdEntityAsIdpEntity.setSystem(backstageCatalogComponentEntity.getSpec().getHarnessSystem());
    cdEntityAsIdpEntity.setOwner(ENTITY_UNKNOWN_OWNER);
    cdEntityAsIdpEntity.setType(SERVICE);

    return cdEntityAsIdpEntity;
  }
}
