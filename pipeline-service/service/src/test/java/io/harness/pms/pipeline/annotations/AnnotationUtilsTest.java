/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AnnotationUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc123";
  private static final String PLAN_EXECUTION_ID = "plan456";

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGetAnnotationFilePath_WithSpecialCharacters() {
    String contextId = "test.context/with-special:chars";
    String result = AnnotationUtils.getAnnotationFilePath(ACCOUNT_ID, PLAN_EXECUTION_ID, contextId);

    // URL encoded version prevents path traversal
    assertThat(result).contains("acc123/pipelineAnnotations/plan456/");
    assertThat(result).contains("annotation-summary.txt");
    // Verify encoding happened (/ becomes %2F, : becomes %3A)
    assertThat(result).contains("%2F");
    assertThat(result).contains("%3A");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGetAnnotationFilePath_PreventPathTraversal() {
    String contextId = "../../../malicious";
    String result = AnnotationUtils.getAnnotationFilePath(ACCOUNT_ID, PLAN_EXECUTION_ID, contextId);

    // Verify path traversal is encoded (/ becomes %2F, . and .. are encoded in URL encoding)
    assertThat(result).doesNotContain("../");
    assertThat(result).contains("%2F"); // Forward slash is encoded
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testExtractPreviewLines_MoreThan5Lines() {
    String content = "Line1\nLine2\nLine3\nLine4\nLine5\nLine6\nLine7\nLine8";
    String result = AnnotationUtils.extractPreviewLines(content);

    assertThat(result).isEqualTo("Line1\nLine2\nLine3\nLine4\nLine5");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testExtractPreviewLines_EmptyContent() {
    String result = AnnotationUtils.extractPreviewLines("");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testAppendContent_BothNonEmpty() {
    String existing = "Existing1\nExisting2";
    String newContent = "New1\nNew2";
    String result = AnnotationUtils.appendContent(existing, newContent);
    assertThat(result).isEqualTo("Existing1\nExisting2\nNew1\nNew2");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testAppendContent_NullHandling() {
    // Test existing null
    assertThat(AnnotationUtils.appendContent(null, "New")).isEqualTo("New");
    // Test new null
    assertThat(AnnotationUtils.appendContent("Existing", null)).isEqualTo("Existing");
  }
}
