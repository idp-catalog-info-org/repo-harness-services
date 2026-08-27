/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.kryo;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.aitestautomation.models.AiTestExecutionData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.esotericsoftware.kryo.Kryo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class CIExecutionKryoRegistrarTest extends CategoryTest {
  private Kryo kryo;

  @Before
  public void setup() {
    kryo = new Kryo();
    new CIExecutionKryoRegistrar().register(kryo);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAiTestExecutionDataRegistered() {
    assertThat(kryo.getRegistration(AiTestExecutionData.class)).isNotNull();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAiTestAutomationPlaywrightExecutionDataRegistered() {
    assertThat(kryo.getRegistration(AiTestAutomationPlaywrightExecutionData.class)).isNotNull();
  }
}
