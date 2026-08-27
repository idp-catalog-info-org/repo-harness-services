/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.config;

import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.tiergroups.service.TierResolutionHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Tier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class DefaultTierGroupConfigTest extends CategoryTest {
  private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
  private static final String SHIPPED_CONFIG_PATH = "idp-service/config/config.yml";

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void builderRetainsConfiguredValuesAndNestedTiers() {
    DefaultTierGroupConfig.TierConfig bronze = DefaultTierGroupConfig.TierConfig.builder()
                                                   .name("Bronze")
                                                   .description("Bronze description")
                                                   .icon(DefaultTierIcon.BRONZE)
                                                   .colour("#CD7F32")
                                                   .minScore(0)
                                                   .maxScore(49)
                                                   .build();
    DefaultTierGroupConfig.TierConfig silver = DefaultTierGroupConfig.TierConfig.builder()
                                                   .name("Silver")
                                                   .description("Silver description")
                                                   .icon(DefaultTierIcon.SILVER)
                                                   .colour("#C0C0C0")
                                                   .minScore(50)
                                                   .maxScore(100)
                                                   .build();

    DefaultTierGroupConfig config = DefaultTierGroupConfig.builder()
                                        .identifier("default_tiers")
                                        .name("Default Tier Group")
                                        .description("Default description")
                                        .tiers(List.of(bronze, silver))
                                        .build();

    assertThat(config.getIdentifier()).isEqualTo("default_tiers");
    assertThat(config.getName()).isEqualTo("Default Tier Group");
    assertThat(config.getDescription()).isEqualTo("Default description");
    assertThat(config.getTiers()).containsExactly(bronze, silver);
    assertThat(config.getTiers().get(0))
        .extracting(DefaultTierGroupConfig.TierConfig::getName, DefaultTierGroupConfig.TierConfig::getDescription,
            DefaultTierGroupConfig.TierConfig::getIcon, DefaultTierGroupConfig.TierConfig::getColour,
            DefaultTierGroupConfig.TierConfig::getMinScore, DefaultTierGroupConfig.TierConfig::getMaxScore)
        .containsExactly("Bronze", "Bronze description", DefaultTierIcon.BRONZE, "#CD7F32", 0, 49);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void yamlDeserializationPreservesMissingScoreBoundsAsNull() throws Exception {
    String yaml = "identifier: default_tiers\n"
        + "name: Default Tier Group\n"
        + "tiers:\n"
        + "  - name: Bronze\n"
        + "    icon: BRONZE\n"
        + "    colour: '#CD7F32'\n"
        + "    maxScore: 49\n";

    DefaultTierGroupConfig config = new ObjectMapper(new YAMLFactory()).readValue(yaml, DefaultTierGroupConfig.class);

    assertThat(config.getTiers()).hasSize(1);
    assertThat(config.getTiers().get(0).getMinScore()).isNull();
    assertThat(config.getTiers().get(0).getMaxScore()).isEqualTo(49);
    assertThat(config.getTiers().get(0).getIcon()).isEqualTo(DefaultTierIcon.BRONZE);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shippedDefaultTierGroupConfigIsValid() throws Exception {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    String resolvedYaml = resolveEnvPlaceholders(Files.readString(resolveShippedConfigPath()));
    JsonNode defaultTierGroupNode = yamlMapper.readTree(resolvedYaml).get("defaultTierGroupConfig");
    assertThat(defaultTierGroupNode).isNotNull();

    DefaultTierGroupConfig config = yamlMapper.treeToValue(defaultTierGroupNode, DefaultTierGroupConfig.class);
    assertThat(config.getIdentifier()).isEqualTo(DEFAULT_TIER_GROUP_IDENTIFIER);
    assertThat(config.getTiers()).isNotEmpty();
    assertThat(config.getTiers())
        .extracting(DefaultTierGroupConfig.TierConfig::getIcon)
        .containsExactly(DefaultTierIcon.BRONZE, DefaultTierIcon.SILVER, DefaultTierIcon.GOLD);

    List<Tier> tiers = config.getTiers()
                           .stream()
                           .map(tier
                               -> new Tier()
                                      .name(tier.getName())
                                      .description(tier.getDescription())
                                      .icon(tier.getIcon().name())
                                      .colour(tier.getColour())
                                      .minScore(tier.getMinScore())
                                      .maxScore(tier.getMaxScore()))
                           .collect(Collectors.toList());

    assertThatCode(() -> TierResolutionHelper.validateTiers(tiers, DEFAULT_TIER_GROUP_IDENTIFIER))
        .doesNotThrowAnyException();
    assertThat(tiers).extracting(Tier::getName).containsExactly("Critical", "Warning", "Healthy");
  }

  private static Path resolveShippedConfigPath() {
    Path workspaceRelative = Path.of(SHIPPED_CONFIG_PATH);
    if (Files.exists(workspaceRelative)) {
      return workspaceRelative;
    }
    String testSrcDir = System.getenv("TEST_SRCDIR");
    String testWorkspace = System.getenv("TEST_WORKSPACE");
    if (testSrcDir != null && testWorkspace != null) {
      Path runfilesPath = Path.of(testSrcDir, testWorkspace, SHIPPED_CONFIG_PATH);
      if (Files.exists(runfilesPath)) {
        return runfilesPath;
      }
    }
    throw new IllegalStateException("Could not locate shipped config at " + SHIPPED_CONFIG_PATH);
  }

  /**
   * Resolves Dropwizard-style {@code ${ENV:-default}} placeholders to their default values so the
   * shipped config can be validated without starting the application.
   */
  private static String resolveEnvPlaceholders(String yaml) {
    Matcher matcher = ENV_PLACEHOLDER.matcher(yaml);
    StringBuffer resolved = new StringBuffer();
    while (matcher.find()) {
      String expression = matcher.group(1);
      int defaultSeparator = expression.indexOf(":-");
      String replacement = defaultSeparator >= 0 ? expression.substring(defaultSeparator + 2) : "";
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }
}
