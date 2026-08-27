/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestRenderUtilsTest {
  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_trimsLeadingAndTrailingSpaces() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"   world    \"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"\"world\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_trimsTabsAndSpaces() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"\t  value \t\"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"\"value\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_preservesInteriorWhitespace() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"  hello   world  \"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    // Only the leading/trailing whitespace of the wrapped content is trimmed, not the interior.
    assertThat(result.get("file.yaml")).isEqualTo("\"\"hello   world\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_handlesMultiplePairsOnSameLine() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"  a  \"\" and \"\"  b  \"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"\"a\"\" and \"\"b\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_leavesStandaloneEmptyPairUnchanged() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_doesNotCrossNewlines() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "key1: \"\"value1\"\"\nkey2: \"\"value2\"\"");

    Map<String, String> result = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("key1: \"\"value1\"\"\nkey2: \"\"value2\"\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimWhitespaceInsideDoubleQuotes_emptyMapReturnsEmptyMap() {
    assertThat(ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(new HashMap<>())).isEmpty();
    assertThat(ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(null)).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_collapsesWhenAdjacentBeforeAndAfter() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"world\"\"");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"world\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_collapsesWhenOnlyCharBefore() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "a\"\"");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("a\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_collapsesWhenOnlyCharAfter() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"a");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("\"a");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_collapsesNonAlphabetAdjacentCharacter() {
    Map<String, String> content = new HashMap<>();
    // Non-alphabet characters (digits/symbols) directly adjacent to the pair should still trigger the collapse.
    content.put("file.yaml", "1\"\"2");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    assertThat(result.get("file.yaml")).isEqualTo("1\"2");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_collapsesWrappedValueInYaml() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "key: \"\"value\"\"");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    // The space after "key:" is ignored, but the pair collapses because it is adjacent to the value characters.
    assertThat(result.get("file.yaml")).isEqualTo("key: \"value\"");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_ignoresWhitespaceNeighbors() {
    Map<String, String> content = new HashMap<>();
    // Only whitespace on both sides of the pair -> not collapsed.
    content.put("bothSides", "a \"\" b");
    // Whitespace before and nothing after -> not collapsed (e.g. an empty YAML value).
    content.put("emptyValue", "key: \"\"");
    // Whitespace before and a newline after -> not collapsed.
    content.put("trailingNewline", "key: \"\"\nnext: v");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    assertThat(result.get("bothSides")).isEqualTo("a \"\" b");
    assertThat(result.get("emptyValue")).isEqualTo("key: \"\"");
    assertThat(result.get("trailingNewline")).isEqualTo("key: \"\"\nnext: v");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_leavesStandalonePairOnOwnLineUntouched() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "foo\n\"\"\nbar");

    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(content);

    // The "" is flanked only by newlines, so it is preserved.
    assertThat(result.get("file.yaml")).isEqualTo("foo\n\"\"\nbar");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReplaceDoubleQuotes_emptyMapReturnsEmptyMap() {
    assertThat(ManifestRenderUtils.replaceDoubleQuotesInMap(new HashMap<>())).isEmpty();
    assertThat(ManifestRenderUtils.replaceDoubleQuotesInMap(null)).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTrimThenReplaceDoubleQuotes_endToEnd() {
    Map<String, String> content = new HashMap<>();
    content.put("file.yaml", "\"\"   world    \"\"");

    Map<String, String> trimmed = ManifestRenderUtils.trimWhitespaceInsideDoubleQuotesInMap(content);
    Map<String, String> result = ManifestRenderUtils.replaceDoubleQuotesInMap(trimmed);

    assertThat(result.get("file.yaml")).isEqualTo("\"world\"");
  }
}
