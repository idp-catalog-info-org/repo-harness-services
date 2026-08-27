/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UdpEventDerivationConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testDerivationConfigUuidIsValidUuidAndDeterministic() {
    String uuid1 = UdpEventDerivationConstants.derivationConfigUuid("acc1", "Component");
    String uuid2 = UdpEventDerivationConstants.derivationConfigUuid("acc1", "Component");
    assertThat(uuid1).isEqualTo(uuid2);
    assertThat(UUID.fromString(uuid1)).isNotNull();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testDerivationConfigUuidDiffersAcrossAccounts() {
    String uuid1 = UdpEventDerivationConstants.derivationConfigUuid("acc1", "Component");
    String uuid2 = UdpEventDerivationConstants.derivationConfigUuid("acc2", "Component");
    assertThat(uuid1).isNotEqualTo(uuid2);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testConnectorMappingConfigUuidIsValidAndDeterministic() {
    String uuid1 = UdpEventDerivationConstants.connectorMappingConfigUuid("acc1", "service");
    String uuid2 = UdpEventDerivationConstants.connectorMappingConfigUuid("acc1", "service");
    assertThat(uuid1).isEqualTo(uuid2);
    assertThat(UUID.fromString(uuid1)).isNotNull();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testDerivationConfigNameIncludesAccountAndKind() {
    String name = UdpEventDerivationConstants.derivationConfigName("acc1", "Component");
    assertThat(name).isEqualTo("acc1:idp:component_entity_event_derivation_config");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testConnectorMappingConfigNameIncludesAccountAndKind() {
    String name = UdpEventDerivationConstants.connectorMappingConfigName("acc1", "My Custom-Kind");
    assertThat(name).isEqualTo("acc1:idp:my_custom_kind_connector_mapping_config");
  }
}
