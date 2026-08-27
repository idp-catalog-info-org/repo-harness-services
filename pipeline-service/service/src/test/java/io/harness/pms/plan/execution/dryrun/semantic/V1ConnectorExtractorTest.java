/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class V1ConnectorExtractorTest extends CategoryTest {
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private List<String> refs(String yaml) throws Exception {
    JsonNode root = YAML.readTree(yaml);
    return V1ConnectorExtractor.extractReferredConnectors(root, "acct", null, null)
        .stream()
        .map(e -> e.getIdentifierRef().getIdentifier().getValue())
        .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void collectsAllInlineConnectorSlots() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s\n"
        + "      clone:\n"
        + "        connector: account.gitConn\n"
        + "      runtime:\n"
        + "        kubernetes:\n"
        + "          connector: account.k8sConn\n"
        + "          harness-image-connector: account.imgConn\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            container:\n"
        + "              connector: account.ctrConn\n"
        + "              registryRef: account.regRef\n"
        + "      options:\n"
        + "        registry:\n"
        + "          credentials:\n"
        + "            - name: account.credConn\n"
        + "              match: docker.io/*\n";
    assertThat(refs(yaml)).containsExactlyInAnyOrder("gitConn", "k8sConn", "imgConn", "ctrConn", "regRef", "credConn");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void skipsExpressionsAndBlanks() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s\n"
        + "      clone:\n"
        + "        connector: <+input>\n"
        + "      run:\n"
        + "        container:\n"
        + "          connector: \"\"\n";
    assertThat(refs(yaml)).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void dedupesByScopedRef() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: a\n"
        + "      run:\n"
        + "        container:\n"
        + "          connector: account.dup\n"
        + "    - id: b\n"
        + "      run:\n"
        + "        container:\n"
        + "          connector: account.dup\n";
    assertThat(refs(yaml)).containsExactly("dup");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void ignoresRegistryCredentialMatchGlob() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s\n"
        + "      options:\n"
        + "        registry:\n"
        + "          credentials:\n"
        + "            - name: account.credConn\n"
        + "              match: docker.io/myorg/*\n";
    assertThat(refs(yaml)).containsExactly("credConn");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void skipsInvalidConnectorReferences() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s\n"
        + "      clone:\n"
        + "        connector: invalid.connector\n"
        + "      run:\n"
        + "        container:\n"
        + "          connector: account.validConn\n";

    assertThatCode(() -> refs(yaml)).doesNotThrowAnyException();
    assertThat(refs(yaml)).containsExactly("validConn");
  }
}
