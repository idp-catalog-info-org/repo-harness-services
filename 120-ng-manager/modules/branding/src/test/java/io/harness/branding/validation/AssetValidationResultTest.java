/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.validation;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class AssetValidationResultTest {
  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testSuccessFactory() {
    AssetValidationResult result = AssetValidationResult.success();

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testFailureFactory() {
    String errorMessage = "Test error message";
    AssetValidationResult result = AssetValidationResult.failure(errorMessage);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testFailureFactoryWithNullMessage() {
    AssetValidationResult result = AssetValidationResult.failure(null);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testBuilderWithValidResult() {
    AssetValidationResult result = AssetValidationResult.builder().valid(true).build();

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testBuilderWithInvalidResult() {
    String errorMessage = "Builder error message";
    AssetValidationResult result = AssetValidationResult.builder().valid(false).errorMessage(errorMessage).build();

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
  }
}