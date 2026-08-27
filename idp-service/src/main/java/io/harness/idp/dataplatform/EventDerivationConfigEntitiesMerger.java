/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config_models.derivation_config.v1.EntityDerivationMapping;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.shared_models.transformation.v1.AttributeDerivationMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class EventDerivationConfigEntitiesMerger {
  public static EventDerivationConfig mergeForPublish(
      @Nullable EventDerivationConfig existing, EventDerivationConfig computedEntitiesConfig) {
    EntityDerivationMapping ownedEntityMapping = getOwnedEntity(computedEntitiesConfig);
    log.info("{} merger start targetEntityType={} hasExisting={} existingEntities={} computedEntities={}",
        UdpEventDerivationConstants.LOG_PREFIX, ownedEntityMapping.getEntityType(), existing != null,
        existing == null ? 0 : existing.getEntitiesCount(), computedEntitiesConfig.getEntitiesCount());
    if (existing == null) {
      return computedEntitiesConfig;
    }
    EventDerivationConfig.Builder out = existing.toBuilder();
    String targetType = ownedEntityMapping.getEntityType();
    int idx = indexOfEntityType(existing, targetType);
    if (idx < 0) {
      out.addEntities(ownedEntityMapping);
      log.info("{} merger append path targetEntityType={} mergedEntities={}", UdpEventDerivationConstants.LOG_PREFIX,
          targetType, out.getEntitiesCount());
    } else {
      EntityDerivationMapping prior = existing.getEntities(idx);
      out.setEntities(
          idx, prior.toBuilder().clearAttributes().addAllAttributes(ownedEntityMapping.getAttributesList()).build());
      log.info("{} merger patch path targetEntityType={} targetIndex={} patchedAttributes={}",
          UdpEventDerivationConstants.LOG_PREFIX, targetType, idx, ownedEntityMapping.getAttributesCount());
    }
    out.clearAttributes().addAllAttributes(mergeAttributes(existing, computedEntitiesConfig).values());
    EventDerivationConfig merged = out.build();
    log.info("{} merger complete targetEntityType={} mergedEntities={}", UdpEventDerivationConstants.LOG_PREFIX,
        targetType, merged.getEntitiesCount());
    return merged;
  }

  private static EntityDerivationMapping getOwnedEntity(EventDerivationConfig computedEntitiesConfig) {
    if (computedEntitiesConfig.getEntitiesCount() != 1) {
      throw new IllegalArgumentException(
          "Computed event derivation config must include exactly one owned entity mapping");
    }
    return computedEntitiesConfig.getEntities(0);
  }

  static int indexOfEntityType(EventDerivationConfig cfg, String entityType) {
    for (int i = 0; i < cfg.getEntitiesCount(); i++) {
      if (entityType.equals(cfg.getEntities(i).getEntityType())) {
        return i;
      }
    }
    return -1;
  }

  private static Map<String, AttributeDerivationMapping> mergeAttributes(
      EventDerivationConfig baseConfig, EventDerivationConfig computedEntitiesConfig) {
    Map<String, AttributeDerivationMapping> mergedAttributes = new LinkedHashMap<>();
    for (AttributeDerivationMapping attribute : baseConfig.getAttributesList()) {
      mergedAttributes.put(attribute.getName(), attribute);
    }
    for (AttributeDerivationMapping attribute : computedEntitiesConfig.getAttributesList()) {
      mergedAttributes.put(attribute.getName(), attribute);
    }
    return mergedAttributes;
  }
}
