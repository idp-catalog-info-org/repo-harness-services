/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.MAX_SCORE;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.MAX_TIERS;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.MIN_SCORE;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.MIN_TIERS;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.TIER_GROUP_DESCRIPTION_MAX_LENGTH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierIcon;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.spec.server.idp.v1.model.Tier;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class TierResolutionHelper {
  public void validateDescriptionLength(String description, String fieldLabel) {
    if (description != null && description.length() > TIER_GROUP_DESCRIPTION_MAX_LENGTH) {
      throw new InvalidRequestException(
          String.format("%s cannot exceed %d characters", fieldLabel, TIER_GROUP_DESCRIPTION_MAX_LENGTH));
    }
  }

  public void validateTiers(List<Tier> tiers) {
    validateTiers(tiers, null);
  }

  public void validateTiers(List<Tier> tiers, String tierGroupIdentifier) {
    boolean allowDefaultTierIcons = DEFAULT_TIER_GROUP_IDENTIFIER.equals(StringUtils.trimToEmpty(tierGroupIdentifier));

    if (tiers == null || tiers.size() < MIN_TIERS) {
      throw new InvalidRequestException(String.format("At least %d tiers must be defined in a tier group", MIN_TIERS));
    }
    if (tiers.size() > MAX_TIERS) {
      throw new InvalidRequestException(String.format("A tier group cannot have more than %d tiers", MAX_TIERS));
    }

    Set<String> names = new HashSet<>();
    for (Tier tier : tiers) {
      String tierName = tier.getName() == null ? "" : tier.getName().trim();
      if (tierName.isEmpty()) {
        throw new InvalidRequestException("Tier name cannot be empty");
      }
      tier.setName(tierName);

      if (!names.add(tierName)) {
        throw new InvalidRequestException(String.format("Duplicate tier name '%s' in tier group", tierName));
      }

      validateDescriptionLength(tier.getDescription(), String.format("Tier '%s' description", tierName));

      if (StringUtils.isBlank(tier.getIcon())) {
        throw new InvalidRequestException(String.format("Tier '%s' icon cannot be empty", tierName));
      }
      validateAndNormalizeTierIcon(tier, tierName, allowDefaultTierIcons);

      if (StringUtils.isBlank(tier.getColour())) {
        throw new InvalidRequestException(String.format("Tier '%s' colour cannot be empty", tierName));
      }

      if (tier.getMinScore() == null || tier.getMaxScore() == null) {
        throw new InvalidRequestException(String.format("Tier '%s' must define min_score and max_score", tierName));
      }
      if (tier.getMinScore() < MIN_SCORE || tier.getMinScore() > MAX_SCORE) {
        throw new InvalidRequestException(
            String.format("Tier '%s' min_score must be between %d and %d", tierName, MIN_SCORE, MAX_SCORE));
      }
      if (tier.getMaxScore() < MIN_SCORE || tier.getMaxScore() > MAX_SCORE) {
        throw new InvalidRequestException(
            String.format("Tier '%s' max_score must be between %d and %d", tierName, MIN_SCORE, MAX_SCORE));
      }
      if (tier.getMinScore() > tier.getMaxScore()) {
        throw new InvalidRequestException(
            String.format("Tier '%s' min_score cannot be greater than max_score", tierName));
      }
    }

    List<Tier> sortedTiers = tiers.stream().sorted(Comparator.comparingInt(Tier::getMinScore)).toList();
    if (sortedTiers.get(0).getMinScore() != MIN_SCORE) {
      throw new InvalidRequestException(String.format("Tier ranges must start at %d", MIN_SCORE));
    }
    if (sortedTiers.get(sortedTiers.size() - 1).getMaxScore() != MAX_SCORE) {
      throw new InvalidRequestException(String.format("Tier ranges must end at %d", MAX_SCORE));
    }
    for (int i = 0; i < sortedTiers.size() - 1; i++) {
      Tier current = sortedTiers.get(i);
      Tier next = sortedTiers.get(i + 1);
      if (current.getMaxScore() + 1 != next.getMinScore()) {
        throw new InvalidRequestException(
            String.format("Tier ranges must be contiguous with no gaps or overlaps. Gap between '%s' and '%s'",
                current.getName(), next.getName()));
      }
    }
  }

  public String resolveTierIconForDisplay(String storedIcon) {
    return DefaultTierIcon.resolveForDisplay(storedIcon);
  }

  public Optional<TierGroupEntity.Tier> resolveTier(TierGroupEntity tierGroup, int score) {
    if (tierGroup == null || tierGroup.getTiers() == null) {
      return Optional.empty();
    }
    return tierGroup.getTiers()
        .stream()
        .filter(tier -> score >= tier.getMinScore() && score <= tier.getMaxScore())
        .findFirst();
  }

  private void validateAndNormalizeTierIcon(Tier tier, String tierName, boolean allowDefaultTierIcons) {
    String trimmedIcon = tier.getIcon().trim();
    Optional<DefaultTierIcon> defaultIcon = DefaultTierIcon.fromStoredValue(trimmedIcon);
    if (allowDefaultTierIcons && defaultIcon.isPresent()) {
      tier.setIcon(defaultIcon.get().name());
      return;
    }
    if (!isValidIconUrl(trimmedIcon)) {
      if (allowDefaultTierIcons) {
        throw new InvalidRequestException(
            String.format("Tier '%s' icon must be a built-in default tier icon (%s) or a valid HTTPS URL", tierName,
                DefaultTierIcon.validIconNamesForMessage()));
      }
      throw new InvalidRequestException(String.format("Tier '%s' icon must be a valid HTTPS URL", tierName));
    }
    tier.setIcon(trimmedIcon);
  }

  private boolean isValidIconUrl(String url) {
    try {
      URI uri = new URI(url);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
