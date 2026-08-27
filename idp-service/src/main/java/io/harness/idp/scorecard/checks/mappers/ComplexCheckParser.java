/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.checks.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class ComplexCheckParser {
  private static final String OPERATORS = "==|!=|>=|<=|=~|!~|=\\^|!\\^|=\\$|!\\$|>|<";
  private static final String QUOTED_SEGMENT = "\"(?:[^\"\\\\]|\\\\.)*\"";
  final String RHS_VALUE = "(?:[0-9]+|true|false|" + QUOTED_SEGMENT + ")";
  final String ARRAY_OF_VALUES = "\\[\\s*" + RHS_VALUE + "(?:\\s*,\\s*" + RHS_VALUE + ")*\\s*\\]";
  final Pattern RULE_PATTERN =
      Pattern.compile("([a-zA-Z_-]+\\.[a-zA-Z_-]+(?:\\." + QUOTED_SEGMENT + "){0,3})" + // identifiers + optional quoted
          "\\s+(" + OPERATORS + ")\\s+" + // operator
          "(" + RHS_VALUE + "|" + ARRAY_OF_VALUES + ")" // RHS value (single value or array)
      );

  private static final Pattern TOKEN_PATTERN = Pattern.compile("\\s*(?:"
      + "(?<OR>\\|\\|)" + // ||
      "|(?<AND>&&)" + // &&
      "|(?<LPAREN>\\()" + // (
      "|(?<RPAREN>\\))" + // )
      "|(?<RULE>(?:\"(?:\\\\.|[^\"])*\"|[^()&|])+)" + // candidate rule
      ")");
  private static class Token {
    String type;
    String value;
    int startIndex; // position in original string
    Token(String type, String value, int startIndex) {
      this.type = type;
      this.value = value;
      this.startIndex = startIndex;
    }
    @Override
    public String toString() {
      return type + ":" + cleanValue(value) + " (at " + startIndex + ")";
    }
  }
  public static void validateExpression(String expression) {
    parseExpression(expression);
  }
  public static void parseExpression(String expression) {
    List<Token> tokens = tokenize(expression);
    int[] pos = {0};
    parseOrExpr(tokens, pos, expression);
    if (pos[0] < tokens.size()) {
      Token t = tokens.get(pos[0]);
      throw new RuntimeException("Unexpected token '" + cleanValue(t.value) + "' at index " + t.startIndex);
    }
  }
  private static List<Token> tokenize(String input) {
    List<Token> result = new ArrayList<>();
    Matcher m = TOKEN_PATTERN.matcher(input);
    int lastEnd = 0;
    while (m.find()) {
      if (m.start() > lastEnd) {
        throw new RuntimeException(
            "Invalid character(s) at index " + lastEnd + ": '" + input.substring(lastEnd, m.start()) + "'");
      }
      lastEnd = m.end();
      int start = m.start();
      if (m.group("OR") != null)
        result.add(new Token("OR", "||", start));
      else if (m.group("AND") != null)
        result.add(new Token("AND", "&&", start));
      else if (m.group("LPAREN") != null)
        result.add(new Token("LPAREN", "(", start));
      else if (m.group("RPAREN") != null)
        result.add(new Token("RPAREN", ")", start));
      else if (m.group("RULE") != null) {
        String rule = m.group("RULE").trim();
        if (!rule.isEmpty()) {
          result.add(new Token("RULE", rule, start));
        }
      }
    }
    if (lastEnd < input.length()) {
      throw new RuntimeException("Invalid character(s) at index " + lastEnd + ": '" + input.substring(lastEnd) + "'");
    }
    return result;
  }
  // Helpers
  private static Token peek(List<Token> tokens, int pos) {
    return pos < tokens.size() ? tokens.get(pos) : null;
  }
  private static Token consume(List<Token> tokens, int[] pos, String expectedType, String expr) {
    Token t = peek(tokens, pos[0]);
    if (t == null || !t.type.equals(expectedType)) {
      int errorIndex = (t != null) ? t.startIndex : expr.length();
      throw new RuntimeException(
          "Expected " + cleanValue(expectedType) + " at index " + errorIndex + " in expression: " + expr);
    }
    pos[0]++;
    return t;
  }
  // ----------------- Recursive descent parser -----------------
  private static void parseOrExpr(List<Token> tokens, int[] pos, String expr) {
    parseAndExpr(tokens, pos, expr);
    while (peek(tokens, pos[0]) != null && peek(tokens, pos[0]).type.equals("OR")) {
      consume(tokens, pos, "OR", expr);
      parseAndExpr(tokens, pos, expr);
    }
  }
  private static void parseAndExpr(List<Token> tokens, int[] pos, String expr) {
    parseFactor(tokens, pos, expr);
    while (peek(tokens, pos[0]) != null && peek(tokens, pos[0]).type.equals("AND")) {
      consume(tokens, pos, "AND", expr);
      parseFactor(tokens, pos, expr);
    }
  }
  private static void parseFactor(List<Token> tokens, int[] pos, String expr) {
    Token t = peek(tokens, pos[0]);
    if (t == null) {
      throw new RuntimeException("Unexpected end of input at index " + expr.length());
    }
    if (t.type.equals("LPAREN")) {
      consume(tokens, pos, "LPAREN", expr);
      parseOrExpr(tokens, pos, expr);
      consume(tokens, pos, "RPAREN", expr);
    } else if (t.type.equals("RULE")) {
      validateRule(t.value, t.startIndex);
      consume(tokens, pos, "RULE", expr);
    } else {
      throw new RuntimeException("Unexpected token: '" + cleanValue(t.value) + "' at index " + t.startIndex);
    }
  }
  private static void validateRule(String rule, int startIndex) {
    Matcher m = RULE_PATTERN.matcher(rule);
    if (!m.matches()) {
      throw new RuntimeException("Invalid rule starting at index " + startIndex + ": " + rule);
    }
  }
  private static String cleanValue(String value) {
    switch (value) {
      case "RPAREN":
        return "Right Parenthesis";
      case "LPAREN":
        return "Left Parenthesis";
      case "AND":
        return "AND Operator";
      case "OR":
        return "OR Operator";
      case "RULE":
        return "Rule Expression";
      default:
        return value;
    }
  }
}