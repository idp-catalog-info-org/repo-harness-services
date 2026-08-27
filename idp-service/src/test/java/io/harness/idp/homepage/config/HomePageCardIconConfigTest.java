/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.homepage.config;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(IDP)
public class HomePageCardIconConfigTest extends CategoryTest {
  private static final String TEST_BUCKET_NAME = "test-bucket-name";
  private static final String TEST_CDN_DNS = "https://cdn.example.com";
  private static final Boolean TEST_CDN_ENABLED = true;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageCardIconConfigBuilder() {
    HomePageCardIconConfig config = HomePageCardIconConfig.builder()
                                        .bucketName(TEST_BUCKET_NAME)
                                        .cdnDNS(TEST_CDN_DNS)
                                        .cdnEnabled(TEST_CDN_ENABLED)
                                        .build();

    assertThat(config).isNotNull();
    assertThat(config.getBucketName()).isEqualTo(TEST_BUCKET_NAME);
    assertThat(config.getCdnDNS()).isEqualTo(TEST_CDN_DNS);
    assertThat(config.getCdnEnabled()).isEqualTo(TEST_CDN_ENABLED);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageCardIconConfigWithCdnDisabled() {
    HomePageCardIconConfig config =
        HomePageCardIconConfig.builder().bucketName(TEST_BUCKET_NAME).cdnDNS(TEST_CDN_DNS).cdnEnabled(false).build();

    assertThat(config.getCdnEnabled()).isFalse();
    assertThat(config.getBucketName()).isEqualTo(TEST_BUCKET_NAME);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageCardIconConfigWithNullValues() {
    HomePageCardIconConfig config =
        HomePageCardIconConfig.builder().bucketName(null).cdnDNS(null).cdnEnabled(null).build();

    assertThat(config).isNotNull();
    assertThat(config.getBucketName()).isNull();
    assertThat(config.getCdnDNS()).isNull();
    assertThat(config.getCdnEnabled()).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageCardIconConfigEquality() {
    HomePageCardIconConfig config1 = HomePageCardIconConfig.builder()
                                         .bucketName(TEST_BUCKET_NAME)
                                         .cdnDNS(TEST_CDN_DNS)
                                         .cdnEnabled(TEST_CDN_ENABLED)
                                         .build();

    HomePageCardIconConfig config2 = HomePageCardIconConfig.builder()
                                         .bucketName(TEST_BUCKET_NAME)
                                         .cdnDNS(TEST_CDN_DNS)
                                         .cdnEnabled(TEST_CDN_ENABLED)
                                         .build();

    assertThat(config1).isEqualTo(config2);
    assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageCardIconConfigToString() {
    HomePageCardIconConfig config = HomePageCardIconConfig.builder()
                                        .bucketName(TEST_BUCKET_NAME)
                                        .cdnDNS(TEST_CDN_DNS)
                                        .cdnEnabled(TEST_CDN_ENABLED)
                                        .build();

    String configString = config.toString();
    assertThat(configString).contains(TEST_BUCKET_NAME);
    assertThat(configString).contains(TEST_CDN_DNS);
    assertThat(configString).contains(TEST_CDN_ENABLED.toString());
  }
}
