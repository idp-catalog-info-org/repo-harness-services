/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionBuiltinConfig;
import io.harness.idp.catalog.entities.ActionHttpConfig;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ActionLifecycleValidatorTest extends CategoryTest {
  // --- validateStatusTransition ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void draftToPublished_succeeds() {
    assertThatCode(() -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.DRAFT, ActionStatus.PUBLISHED))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void draftToDeprecated_throws() {
    assertThatThrownBy(
        () -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.DRAFT, ActionStatus.DEPRECATED))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void publishedToDeprecated_succeeds() {
    assertThatCode(
        () -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.PUBLISHED, ActionStatus.DEPRECATED))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void publishedToDraft_throws() {
    assertThatThrownBy(
        () -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.PUBLISHED, ActionStatus.DRAFT))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void deprecatedToPublished_throws() {
    assertThatThrownBy(
        () -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.DEPRECATED, ActionStatus.PUBLISHED))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void deprecatedToDraft_throws() {
    assertThatThrownBy(
        () -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.DEPRECATED, ActionStatus.DRAFT))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void sameStatus_noop() {
    assertThatCode(() -> ActionLifecycleValidator.validateStatusTransition(ActionStatus.DRAFT, ActionStatus.DRAFT))
        .doesNotThrowAnyException();
  }

  // --- validateReadyToPublish ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void httpAction_withValidConfig_succeeds() {
    Action action = Action.builder()
                        .type(ActionType.HTTP)
                        .httpConfig(ActionHttpConfig.builder().method("GET").path("/api/resource").build())
                        .build();
    assertThatCode(() -> ActionLifecycleValidator.validateReadyToPublish(action)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void httpAction_missingHttpConfig_throws() {
    Action action = Action.builder().type(ActionType.HTTP).httpConfig(null).build();
    assertThatThrownBy(() -> ActionLifecycleValidator.validateReadyToPublish(action))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void builtinAction_missingHandler_throws() {
    Action action = Action.builder().type(ActionType.BUILTIN).builtinConfig(null).build();
    assertThatThrownBy(() -> ActionLifecycleValidator.validateReadyToPublish(action))
        .isInstanceOf(InvalidRequestException.class);
  }
}
