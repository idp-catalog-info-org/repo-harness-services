/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.mapper.model.ParsedKubeConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for {@link KubeconfigParser}: YAML-to-model parsing, kind validation, and the invalid-input paths.
 */
public class KubeconfigParserTest extends CategoryTest {
  private static final String VALID_KUBECONFIG = "apiVersion: v1\n"
      + "kind: Config\n"
      + "current-context: ctx1\n"
      + "clusters:\n"
      + "  - name: c1\n"
      + "    cluster:\n"
      + "      server: https://k8s.example.com\n"
      + "      certificate-authority-data: Y2EtcGVt\n"
      + "users:\n"
      + "  - name: u1\n"
      + "    user:\n"
      + "      token: sa-token\n"
      + "contexts:\n"
      + "  - name: ctx1\n"
      + "    context:\n"
      + "      cluster: c1\n"
      + "      user: u1\n"
      + "      namespace: prod\n";

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseValidKubeconfig() {
    ParsedKubeConfig cfg = KubeconfigParser.parse(VALID_KUBECONFIG);

    assertThat(cfg).isNotNull();
    assertThat(cfg.getApiVersion()).isEqualTo("v1");
    assertThat(cfg.getKind()).isEqualTo("Config");
    assertThat(cfg.getCurrentContext()).isEqualTo("ctx1");
    assertThat(cfg.getClusters()).hasSize(1);
    assertThat(cfg.getClusters().get(0).getName()).isEqualTo("c1");
    assertThat(cfg.getClusters().get(0).getCluster().getServer()).isEqualTo("https://k8s.example.com");
    assertThat(cfg.getUsers()).hasSize(1);
    assertThat(cfg.getUsers().get(0).getUser().getToken()).isEqualTo("sa-token");
    assertThat(cfg.getContexts()).hasSize(1);
    assertThat(cfg.getContexts().get(0).getContext().getNamespace()).isEqualTo("prod");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseIsLenientWhenKindOmitted() {
    String noKind = "apiVersion: v1\n"
        + "clusters: []\n"
        + "users: []\n"
        + "contexts: []\n";

    ParsedKubeConfig cfg = KubeconfigParser.parse(noKind);

    assertThat(cfg).isNotNull();
    assertThat(cfg.getKind()).isNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseIgnoresUnknownFields() {
    String withExtras = "apiVersion: v1\n"
        + "kind: Config\n"
        + "preferences: {}\n"
        + "some-vendor-extra: value\n"
        + "contexts: []\n";

    ParsedKubeConfig cfg = KubeconfigParser.parse(withExtras);

    assertThat(cfg).isNotNull();
    assertThat(cfg.getKind()).isEqualTo("Config");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseBlankThrows() {
    assertThatThrownBy(() -> KubeconfigParser.parse("  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("empty");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseNullThrows() {
    assertThatThrownBy(() -> KubeconfigParser.parse(null)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseWrongKindThrows() {
    String wrongKind = "apiVersion: v1\n"
        + "kind: Secret\n"
        + "contexts: []\n";

    assertThatThrownBy(() -> KubeconfigParser.parse(wrongKind))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not a kubeConfig");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseKindIsCaseInsensitive() {
    String lowerKind = "apiVersion: v1\n"
        + "kind: config\n"
        + "contexts: []\n";

    ParsedKubeConfig cfg = KubeconfigParser.parse(lowerKind);

    assertThat(cfg).isNotNull();
    assertThat(cfg.getKind()).isEqualTo("config");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseInvalidYamlThrows() {
    // A YAML scalar deserializes to a String, not the ParsedKubeConfig object -> parse failure.
    assertThatThrownBy(() -> KubeconfigParser.parse("just-a-scalar-string"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not a valid kubeConfig");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testParseMalformedYamlThrows() {
    String malformed = "kind: Config\n"
        + "clusters:\n"
        + "  - name: c1\n"
        + "    cluster: {server: https://x, : broken}\n";

    assertThatThrownBy(() -> KubeconfigParser.parse(malformed)).isInstanceOf(InvalidRequestException.class);
  }
}
