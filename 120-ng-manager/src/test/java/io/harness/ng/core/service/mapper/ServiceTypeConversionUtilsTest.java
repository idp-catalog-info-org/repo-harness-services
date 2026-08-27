/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.rule.OwnerRule.DANIEL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.spec.ServiceType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceTypeConversionUtilsTest {
  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testElastigroupMapsToSpot() {
    assertThat(
        ServiceTypeConversionUtils.SERVICE_TYPE_CONVERSION_MAP.get(ServiceDefinitionType.ELASTIGROUP.getYamlName()))
        .isEqualTo(ServiceType.SPOT);
  }
}
