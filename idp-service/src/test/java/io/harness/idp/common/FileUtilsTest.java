/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class FileUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadFileWithNonExistentFile() {
    String content = FileUtils.readFile("test/", "nonexistent.txt");
    assertThat(content).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsFileFormatSupportedForZip() {
    assertThat(FileUtils.isFileFormatSupported("ZIP", "zip")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ZIP", "tar.gz")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ZIP", "tgz")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ZIP", "tar.bz2")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ZIP", "txt")).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsFileFormatSupportedForIcon() {
    assertThat(FileUtils.isFileFormatSupported("ICON", "jpeg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ICON", "jpg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ICON", "png")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ICON", "svg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("ICON", "txt")).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsFileFormatSupportedForScreenshot() {
    assertThat(FileUtils.isFileFormatSupported("SCREENSHOT", "jpeg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("SCREENSHOT", "jpg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("SCREENSHOT", "png")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("SCREENSHOT", "svg")).isTrue();
    assertThat(FileUtils.isFileFormatSupported("SCREENSHOT", "gif")).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsFileFormatSupportedWithUnsupportedType() {
    assertThatThrownBy(() -> FileUtils.isFileFormatSupported("INVALID", "txt"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadDirectory() {
    // Test reading a directory - will return empty set if not found
    assertThat(FileUtils.readDirectory(FileUtilsTest.class, "nonexistent/")).isEmpty();
  }
}
