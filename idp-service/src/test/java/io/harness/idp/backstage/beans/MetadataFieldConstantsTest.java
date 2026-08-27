/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class MetadataFieldConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdentifier() {
    assertThat(MetadataFieldConstants.IDENTIFIER).isEqualTo("identifier");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testName() {
    assertThat(MetadataFieldConstants.NAME).isEqualTo("name");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAbsoluteIdentifier() {
    assertThat(MetadataFieldConstants.ABSOLUTE_IDENTIFIER).isEqualTo("absoluteIdentifier");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testTitle() {
    assertThat(MetadataFieldConstants.TITLE).isEqualTo("title");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNamespace() {
    assertThat(MetadataFieldConstants.NAMESPACE).isEqualTo("namespace");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDescription() {
    assertThat(MetadataFieldConstants.DESCRIPTION).isEqualTo("description");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testTags() {
    assertThat(MetadataFieldConstants.TAGS).isEqualTo("tags");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUid() {
    assertThat(MetadataFieldConstants.UID).isEqualTo("uid");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEtag() {
    assertThat(MetadataFieldConstants.ETAG).isEqualTo("etag");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAnnotations() {
    assertThat(MetadataFieldConstants.ANNOTATIONS).isEqualTo("annotations");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLinks() {
    assertThat(MetadataFieldConstants.LINKS).isEqualTo("links");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLabels() {
    assertThat(MetadataFieldConstants.LABELS).isEqualTo("labels");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHarnessData() {
    assertThat(MetadataFieldConstants.HARNESS_DATA).isEqualTo("harnessData");
  }
}
