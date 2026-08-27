/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class SupportedProvidersInSourceLocationTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEnumValues() {
    assertThat(SupportedProvidersInSourceLocation.values()).hasSize(5);
    assertThat(SupportedProvidersInSourceLocation.values())
        .containsExactly(SupportedProvidersInSourceLocation.GITHUB, SupportedProvidersInSourceLocation.GITLAB,
            SupportedProvidersInSourceLocation.BITBUCKET, SupportedProvidersInSourceLocation.HARNESS,
            SupportedProvidersInSourceLocation.AZURE_REPO);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetName() {
    assertThat(SupportedProvidersInSourceLocation.GITHUB.getName()).isEqualTo("github");
    assertThat(SupportedProvidersInSourceLocation.GITLAB.getName()).isEqualTo("gitlab");
    assertThat(SupportedProvidersInSourceLocation.BITBUCKET.getName()).isEqualTo("bitbucket");
    assertThat(SupportedProvidersInSourceLocation.HARNESS.getName()).isEqualTo("harness");
    assertThat(SupportedProvidersInSourceLocation.AZURE_REPO.getName()).isEqualTo("azurerepo");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedWithValidProviders() {
    // Test with exact case
    assertThat(SupportedProvidersInSourceLocation.isSupported("github")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("gitlab")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("bitbucket")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("harness")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("azurerepo")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedWithUpperCase() {
    // Test with uppercase
    assertThat(SupportedProvidersInSourceLocation.isSupported("GITHUB")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("GITLAB")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("BITBUCKET")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("HARNESS")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("AZUREREPO")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedWithMixedCase() {
    // Test with mixed case
    assertThat(SupportedProvidersInSourceLocation.isSupported("GitHub")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("GitLab")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("BitBucket")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("Harness")).isTrue();
    assertThat(SupportedProvidersInSourceLocation.isSupported("AzureRepo")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedWithInvalidProviders() {
    assertThat(SupportedProvidersInSourceLocation.isSupported("invalid")).isFalse();
    assertThat(SupportedProvidersInSourceLocation.isSupported("svn")).isFalse();
    assertThat(SupportedProvidersInSourceLocation.isSupported("mercurial")).isFalse();
    assertThat(SupportedProvidersInSourceLocation.isSupported("")).isFalse();
    assertThat(SupportedProvidersInSourceLocation.isSupported("  ")).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedWithNull() {
    assertThat(SupportedProvidersInSourceLocation.isSupported(null)).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllProviderNames() {
    List<String> providerNames = SupportedProvidersInSourceLocation.getAllProviderNames();

    assertThat(providerNames).hasSize(5);
    assertThat(providerNames).containsExactly("github", "gitlab", "bitbucket", "harness", "azurerepo");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAllProviderNamesAreLowercase() {
    List<String> providerNames = SupportedProvidersInSourceLocation.getAllProviderNames();

    for (String provider : providerNames) {
      assertThat(provider).isEqualTo(provider.toLowerCase());
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEnumValueOf() {
    assertThat(SupportedProvidersInSourceLocation.valueOf("GITHUB"))
        .isEqualTo(SupportedProvidersInSourceLocation.GITHUB);
    assertThat(SupportedProvidersInSourceLocation.valueOf("GITLAB"))
        .isEqualTo(SupportedProvidersInSourceLocation.GITLAB);
    assertThat(SupportedProvidersInSourceLocation.valueOf("BITBUCKET"))
        .isEqualTo(SupportedProvidersInSourceLocation.BITBUCKET);
    assertThat(SupportedProvidersInSourceLocation.valueOf("HARNESS"))
        .isEqualTo(SupportedProvidersInSourceLocation.HARNESS);
    assertThat(SupportedProvidersInSourceLocation.valueOf("AZURE_REPO"))
        .isEqualTo(SupportedProvidersInSourceLocation.AZURE_REPO);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEnumOrdinal() {
    assertThat(SupportedProvidersInSourceLocation.GITHUB.ordinal()).isEqualTo(0);
    assertThat(SupportedProvidersInSourceLocation.GITLAB.ordinal()).isEqualTo(1);
    assertThat(SupportedProvidersInSourceLocation.BITBUCKET.ordinal()).isEqualTo(2);
    assertThat(SupportedProvidersInSourceLocation.HARNESS.ordinal()).isEqualTo(3);
    assertThat(SupportedProvidersInSourceLocation.AZURE_REPO.ordinal()).isEqualTo(4);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEnumComparison() {
    assertThat(SupportedProvidersInSourceLocation.GITHUB).isLessThan(SupportedProvidersInSourceLocation.GITLAB);
    assertThat(SupportedProvidersInSourceLocation.GITLAB).isLessThan(SupportedProvidersInSourceLocation.BITBUCKET);
    assertThat(SupportedProvidersInSourceLocation.BITBUCKET).isLessThan(SupportedProvidersInSourceLocation.HARNESS);
    assertThat(SupportedProvidersInSourceLocation.HARNESS).isLessThan(SupportedProvidersInSourceLocation.AZURE_REPO);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAllProviderNamesMatchEnumNames() {
    // Verify that all enum values have corresponding names in getAllProviderNames
    List<String> allProviderNames = SupportedProvidersInSourceLocation.getAllProviderNames();

    for (SupportedProvidersInSourceLocation provider : SupportedProvidersInSourceLocation.values()) {
      assertThat(allProviderNames).contains(provider.getName());
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsSupportedCoversAllEnumValues() {
    // Verify that isSupported returns true for all enum values
    for (SupportedProvidersInSourceLocation provider : SupportedProvidersInSourceLocation.values()) {
      assertThat(SupportedProvidersInSourceLocation.isSupported(provider.getName())).isTrue();
      assertThat(SupportedProvidersInSourceLocation.isSupported(provider.getName().toUpperCase())).isTrue();
    }
  }
}
