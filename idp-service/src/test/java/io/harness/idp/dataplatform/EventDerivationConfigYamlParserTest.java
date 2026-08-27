/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EventDerivationConfigYamlParserTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testParseSubstitutesCdcKindInScopeExpression() {
    EventDerivationConfigYamlParser parser = new EventDerivationConfigYamlParser();
    EventDerivationConfig cfg = parser.parseForCdcKind("my_custom_kind");
    assertThat(cfg.hasScopeExpression()).isTrue();
    assertThat(cfg.getScopeExpression().getJexlExpression()).contains("my_custom_kind");
    assertThat(cfg.hasSource()).isTrue();
    assertThat(cfg.hasTenantId()).isTrue();
    assertThat(cfg.hasEventTimestamp()).isTrue();
    assertThat(cfg.getEventTimestamp().getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.lastUpdatedAt['$numberLong'].getAsLong()");
    assertThat(cfg.getVariablesList())
        .extracting(variable -> variable.getName())
        .containsExactly("cdc_event", "idp_entity_ref");
    assertThat(cfg.getEntitiesCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testParseRejectsUnsafeCharactersInCdcKind() {
    EventDerivationConfigYamlParser parser = new EventDerivationConfigYamlParser();
    assertThatThrownBy(() -> parser.parseForCdcKind("team's kind"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsafe");
    assertThatThrownBy(() -> parser.parseForCdcKind("bad\nkind"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsafe");
    assertThatThrownBy(() -> parser.parseForCdcKind("kind: [injected]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsafe");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testParseAcceptsDollarSignAllowedByIdentifierValidation() {
    EventDerivationConfigYamlParser parser = new EventDerivationConfigYamlParser();
    EventDerivationConfig cfg = parser.parseForCdcKind("my$kind");
    assertThat(cfg.getScopeExpression().getJexlExpression()).contains("my$kind");
  }
}
