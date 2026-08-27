/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class InfraDataTest extends CategoryTest {
  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFromJsonNode() {
    // Test case 1: Simple string infraData
    String infraDataYaml = "infra_name";
    InfraData expectedInfraData = InfraData.builder().id("infra_name").build();
    JsonNode infraNode = YamlUtils.readAsJsonNode(infraDataYaml);

    InfraData actualInfraData = InfraData.fromJsonNode(infraNode);
    assertThat(actualInfraData).isNotNull();
    assertThat(actualInfraData.getId()).isEqualTo(expectedInfraData.getId());
    assertThat(actualInfraData.getInputs()).isNull();

    // Test case 2: Object with id and with fields
    String infraDataYaml2 = "id: infra_test2 \nwith:  \n  region: test";
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("region", "test");
    InfraData expectedInfraData2 = InfraData.builder().id("infra_test2").inputs(inputs).build();
    JsonNode infraNode2 = YamlUtils.readAsJsonNode(infraDataYaml2);

    InfraData actualInfraData2 = InfraData.fromJsonNode(infraNode2);
    assertThat(actualInfraData2).isNotNull();
    assertThat(actualInfraData2.getId()).isEqualTo(expectedInfraData2.getId());
    assertThat(actualInfraData2.getInputs()).isNotNull();
    assertThat(actualInfraData2.getInputs()).containsKey("region");
    assertThat(actualInfraData2.getInputs().get("region").toString()).contains("test");

    // Test case 3: Null input
    InfraData nullInfraData = InfraData.fromJsonNode(null);
    assertThat(nullInfraData).isNull();

    // Test case 4: Object without id field
    String invalidYaml = "name: infra_test3";
    JsonNode invalidNode = YamlUtils.readAsJsonNode(invalidYaml);
    InfraData invalidInfraData = InfraData.fromJsonNode(invalidNode);
    assertThat(invalidInfraData).isNull();
  }
}
