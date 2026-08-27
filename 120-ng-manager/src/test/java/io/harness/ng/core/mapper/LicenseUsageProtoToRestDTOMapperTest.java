/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.eventsframework.schemas.platform.ModuleName.MODULE_NAME_CI;
import static io.harness.eventsframework.schemas.platform.ModuleName.MODULE_NAME_IACM;
import static io.harness.eventsframework.schemas.platform.ModuleName.MODULE_NAME_IDP;
import static io.harness.eventsframework.schemas.platform.ModuleName.MODULE_NAME_STO;
import static io.harness.rule.OwnerRule.EBTASAM;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.platform.Developer;
import io.harness.eventsframework.schemas.platform.LicenseUsageEvent;
import io.harness.ng.core.licenseusage.dto.LicenseUsageDTO;
import io.harness.ng.core.licenseusage.mapper.LicenseUsageProtoToRestDTOMapper;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LicenseUsageProtoToRestDTOMapperTest extends CategoryTest {
  @InjectMocks LicenseUsageProtoToRestDTOMapper mapper;
  @Before
  public void setup() {}

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testToRestDTO_withMODULE_NAME_CI() {
    LicenseUsageEvent licenseUsageEventProtoDTO = LicenseUsageEvent.newBuilder()
                                                      .setAccountIdentifier("account123")
                                                      .setOrgIdentifier("org123")
                                                      .setProjectIdentifier("project123")
                                                      .setDeveloper(Developer.newBuilder().build())
                                                      .setPipelineIdentifier("pipeline123")
                                                      .setStepIdentifier("step123")
                                                      .setStageIdentifier("stage123")
                                                      .setModuleName(MODULE_NAME_CI)
                                                      .build();
    LicenseUsageDTO result = mapper.toRestDTO(licenseUsageEventProtoDTO);

    assertEquals(ModuleType.CI, result.getModuleType());
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testToRestDTO_withMODULE_NAME_STO() {
    LicenseUsageEvent licenseUsageEventProtoDTO = LicenseUsageEvent.newBuilder()
                                                      .setAccountIdentifier("account123")
                                                      .setOrgIdentifier("org123")
                                                      .setProjectIdentifier("project123")
                                                      .setDeveloper(Developer.newBuilder().build())
                                                      .setPipelineIdentifier("pipeline123")
                                                      .setStepIdentifier("step123")
                                                      .setStageIdentifier("stage123")
                                                      .setModuleName(MODULE_NAME_STO)
                                                      .build();
    LicenseUsageDTO result = mapper.toRestDTO(licenseUsageEventProtoDTO);

    assertEquals(ModuleType.STO, result.getModuleType());
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testToRestDTO_withMODULE_NAME_IDP() {
    LicenseUsageEvent licenseUsageEventProtoDTO = LicenseUsageEvent.newBuilder()
                                                      .setAccountIdentifier("account123")
                                                      .setOrgIdentifier("org123")
                                                      .setProjectIdentifier("project123")
                                                      .setDeveloper(Developer.newBuilder().build())
                                                      .setPipelineIdentifier("pipeline123")
                                                      .setStepIdentifier("step123")
                                                      .setStageIdentifier("stage123")
                                                      .setModuleName(MODULE_NAME_IDP)
                                                      .build();
    LicenseUsageDTO result = mapper.toRestDTO(licenseUsageEventProtoDTO);

    assertEquals(ModuleType.IDP, result.getModuleType());
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testToRestDTO_withMODULE_NAME_IACM() {
    LicenseUsageEvent licenseUsageEventProtoDTO = LicenseUsageEvent.newBuilder()
                                                      .setAccountIdentifier("account123")
                                                      .setOrgIdentifier("org123")
                                                      .setProjectIdentifier("project123")
                                                      .setDeveloper(Developer.newBuilder().build())
                                                      .setPipelineIdentifier("pipeline123")
                                                      .setStepIdentifier("step123")
                                                      .setStageIdentifier("stage123")
                                                      .setModuleName(MODULE_NAME_IACM)
                                                      .build();
    LicenseUsageDTO result = mapper.toRestDTO(licenseUsageEventProtoDTO);

    assertEquals(ModuleType.IACM, result.getModuleType());
  }
}
