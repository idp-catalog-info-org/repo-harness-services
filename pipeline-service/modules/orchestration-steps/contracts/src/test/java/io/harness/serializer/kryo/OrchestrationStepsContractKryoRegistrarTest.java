/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.rule.OwnerRule.GONZALO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.fme.FmeMetricCheckResponseData;

import com.esotericsoftware.kryo.Kryo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class OrchestrationStepsContractKryoRegistrarTest extends CategoryTest {
  private Kryo kryo;

  @Before
  public void setup() {
    kryo = new Kryo();
    new OrchestrationStepsContractKryoRegistrar().register(kryo);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testFmeMetricCheckClassesRegistered() {
    assertThat(kryo.getRegistration(FmeMetricCheckResponseData.class)).isNotNull();
    assertThat(kryo.getRegistration(FmeMetricCheckResponseData.class).getId()).isEqualTo(390133);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testNoDuplicateRegistrationIds() {
    Kryo freshKryo = new Kryo();
    new OrchestrationStepsContractKryoRegistrar().register(freshKryo);
    assertThat(freshKryo.getRegistration(FmeMetricCheckResponseData.class)).isNotNull();
  }
}
