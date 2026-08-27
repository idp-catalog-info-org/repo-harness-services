/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StringFunctorTest extends CategoryTest {
  private final StringFunctor functor = new StringFunctor();

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_null() {
    assertThat(functor.escapeDoubleQuotes(null)).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_empty() {
    assertThat(functor.escapeDoubleQuotes("")).isEqualTo("");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_simpleText_noChange() {
    assertThat(functor.escapeDoubleQuotes("hello world")).isEqualTo("hello world");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_simpleText() {
    String input = "8E92hr20Sb{{{}}}}///a\"/asd/as6O3-bd0kCQsw\"";
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo("8E92hr20Sb{{{}}}}///a\\\"/asd/as6O3-bd0kCQsw\\\"");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_unescapedDoubleQuotesAreEscaped() {
    String input = "He said \\\"Hello\\\" and then said \\\"Bye\\\""; // already escaped quotes
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo(input); // no change

    String input2 = "He said \"Hello\" and then said \"Bye\" again \"oops\" and " + '"' + " more";
    // The last '"' + " more" introduces an unescaped quote then space then word:
    String expected2 = "He said \\\"Hello\\\" and then said \\\"Bye\\\" again \\\"oops\\\" and \\\" more";
    assertThat(functor.escapeDoubleQuotes(input2)).isEqualTo(expected2);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_alreadyEscapedQuotesPreserved() {
    String input = "exam\\\"ple"; // exam\"ple
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo(input);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_backslashesPreserved() {
    String input = "C:\\path\\to\\file"; // C:\\path\\to\\file literal -> actual C:\path\to\file at runtime
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo(input);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_controlCharsPreserved() {
    String input = "line1\nline2\tend"; // actual newline and tab characters remain
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo(input);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_jsonLikeString_onlyQuotesEscaped() {
    String input = "{\\\"key\\\":\\\"value\\\"}"; // already-escaped quotes inside JSON-like string
    assertThat(functor.escapeDoubleQuotes(input)).isEqualTo(input); // should remain unchanged

    String input2 =
        "{\"childNodeID\":\"8E92hr20Sb{{{}}}}///a\"/asd/as6O3-bd0kCQsw\"}"; // contains an unescaped quote before /asd
    String expected2 = "{\\\"childNodeID\\\":\\\"8E92hr20Sb{{{}}}}///a\\\"/asd/as6O3-bd0kCQsw\\\"}";
    assertThat(functor.escapeDoubleQuotes(input2)).isEqualTo(expected2);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeDoubleQuotes_evenVsOddBackslashesBeforeQuote() {
    // Even number of backslashes -> quote is unescaped -> should be escaped
    String inputEven = "foo\\\\\"bar"; // literal: foo\\"bar -> two backslashes then quote
    String expectedEven = "foo\\\\\\\"bar"; // becomes foo\\\"bar (i.e., add one backslash before the quote)
    assertThat(functor.escapeDoubleQuotes(inputEven)).isEqualTo(expectedEven);

    // Odd number of backslashes -> quote is escaped -> should remain unchanged
    String inputOdd = "foo\\\"bar"; // literal: foo\"bar -> one backslash then quote
    assertThat(functor.escapeDoubleQuotes(inputOdd)).isEqualTo(inputOdd);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_null() {
    assertThat(functor.escapeJson(null)).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_empty() {
    assertThat(functor.escapeJson("")).isEqualTo("");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_simpleText_noChange() {
    assertThat(functor.escapeJson("hello world")).isEqualTo("hello world");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_doubleQuotesEscaped() {
    String input = "He said \"Hello\"";
    String expected = "He said \\\"Hello\\\""; // He said \"Hello\"
    assertThat(functor.escapeJson(input)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_backslashesEscaped() {
    String input = "C:\\path\\to\\file"; // C:\path\to\file
    String expected = "C:\\\\path\\\\to\\\\file"; // C:\\path\\to\\file
    assertThat(functor.escapeJson(input)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_controlCharsEscaped() {
    String input = "line1\nline2\tend";
    String expected = "line1\\nline2\\tend";
    assertThat(functor.escapeJson(input)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_jsonLikeString() {
    String input = "{\"key\":\"value\"}";
    String expected = "{\\\"key\\\":\\\"value\\\"}"; // {"key":"value"} with JSON escaping
    assertThat(functor.escapeJson(input)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEscapeJson_unicodePreserved() {
    String input = "café — naïve";
    // Jackson by default preserves unicode characters in UTF-8 (no ASCII escaping), so expect same chars
    assertThat(functor.escapeJson(input)).isEqualTo("café — naïve");
  }
}
