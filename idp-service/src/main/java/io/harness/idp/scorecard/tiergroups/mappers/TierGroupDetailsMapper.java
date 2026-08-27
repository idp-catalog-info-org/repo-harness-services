/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.spec.server.idp.v1.model.Tier;
import io.harness.spec.server.idp.v1.model.TierGroup;
import io.harness.spec.server.idp.v1.model.TierGroupDetails;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class TierGroupDetailsMapper {
  public TierGroupDetailsResponse toDTO(TierGroupEntity entity) {
    TierGroupDetailsResponse response = new TierGroupDetailsResponse();
    response.setTierGroup(toDetails(entity));
    return response;
  }

  public TierGroup toListItem(TierGroupEntity entity) {
    TierGroup tierGroup = new TierGroup();
    tierGroup.setIdentifier(entity.getIdentifier());
    tierGroup.setName(entity.getName());
    tierGroup.setDescription(entity.getDescription());
    tierGroup.setTiers(toTierModels(entity.getTiers()));
    return tierGroup;
  }

  public TierGroupEntity fromDTO(TierGroupDetailsRequest request, String accountIdentifier) {
    TierGroupDetails details = request.getTierGroup();
    return TierGroupEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(details.getIdentifier().trim())
        .name(details.getName().trim())
        .description(StringUtils.trimToNull(details.getDescription()))
        .tiers(toTierEntities(details.getTiers()))
        .build();
  }

  private TierGroupDetails toDetails(TierGroupEntity entity) {
    TierGroupDetails details = new TierGroupDetails();
    details.setIdentifier(entity.getIdentifier());
    details.setName(entity.getName());
    details.setDescription(entity.getDescription());
    details.setTiers(toTierModels(entity.getTiers()));
    return details;
  }

  private List<Tier> toTierModels(List<TierGroupEntity.Tier> tiers) {
    if (tiers == null) {
      return List.of();
    }
    return tiers.stream()
        .sorted(Comparator.comparingInt(TierGroupEntity.Tier::getMinScore))
        .map(tier
            -> new Tier()
                   .name(tier.getName())
                   .description(tier.getDescription())
                   .icon(tier.getIcon())
                   .colour(tier.getColour())
                   .minScore(tier.getMinScore())
                   .maxScore(tier.getMaxScore()))
        .collect(Collectors.toList());
  }

  private List<TierGroupEntity.Tier> toTierEntities(List<Tier> tiers) {
    if (tiers == null) {
      return List.of();
    }
    return tiers.stream()
        .sorted(Comparator.comparingInt(Tier::getMinScore))
        .map(tier
            -> TierGroupEntity.Tier.builder()
                   .name(tier.getName() == null ? null : tier.getName().trim())
                   .description(tier.getDescription())
                   .icon(tier.getIcon() == null ? null : tier.getIcon().trim())
                   .colour(tier.getColour())
                   .minScore(tier.getMinScore())
                   .maxScore(tier.getMaxScore())
                   .build())
        .collect(Collectors.toList());
  }
}
