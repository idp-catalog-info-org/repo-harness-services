/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.Operation;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPExecutionConfigResourceTest extends CategoryTest {
  @Mock private CIExecutionConfigService ciExecutionConfigService;

  private IDPExecutionConfigResource idpExecutionConfigResource;

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final StageInfraDetails.Type TEST_INFRA_TYPE = StageInfraDetails.Type.K8;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    idpExecutionConfigResource = new IDPExecutionConfigResource();

    Field field = IDPExecutionConfigResource.class.getDeclaredField("ciExecutionConfigService");
    field.setAccessible(true);
    field.set(idpExecutionConfigResource, ciExecutionConfigService);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateExecutionConfig_Success() {
    List<Operation> operations = Arrays.asList(new Operation());
    when(ciExecutionConfigService.updateCIContainerTags(eq(TEST_ACCOUNT_ID), eq(operations), eq(TEST_INFRA_TYPE)))
        .thenReturn(true);

    ResponseDTO<Boolean> response =
        idpExecutionConfigResource.updateExecutionConfig(TEST_INFRA_TYPE, TEST_ACCOUNT_ID, operations);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertTrue(response.getData());
    verify(ciExecutionConfigService).updateCIContainerTags(TEST_ACCOUNT_ID, operations, TEST_INFRA_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateExecutionConfig_EmptyOperations() {
    List<Operation> operations = Collections.emptyList();
    when(ciExecutionConfigService.updateCIContainerTags(eq(TEST_ACCOUNT_ID), eq(operations), eq(TEST_INFRA_TYPE)))
        .thenReturn(false);

    ResponseDTO<Boolean> response =
        idpExecutionConfigResource.updateExecutionConfig(TEST_INFRA_TYPE, TEST_ACCOUNT_ID, operations);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertFalse(response.getData());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResetExecutionConfig_Success() {
    List<Operation> operations = Arrays.asList(new Operation());
    when(ciExecutionConfigService.resetCIContainerTags(eq(TEST_ACCOUNT_ID), eq(operations), eq(TEST_INFRA_TYPE)))
        .thenReturn(true);

    ResponseDTO<Boolean> response =
        idpExecutionConfigResource.resetExecutionConfig(TEST_INFRA_TYPE, TEST_ACCOUNT_ID, operations);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertTrue(response.getData());
    verify(ciExecutionConfigService).resetCIContainerTags(TEST_ACCOUNT_ID, operations, TEST_INFRA_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteExecutionConfig_Success() {
    when(ciExecutionConfigService.deleteCIExecutionConfig(TEST_ACCOUNT_ID)).thenReturn(true);

    ResponseDTO<Boolean> response = idpExecutionConfigResource.deleteExecutionConfig(TEST_ACCOUNT_ID);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertTrue(response.getData());
    verify(ciExecutionConfigService).deleteCIExecutionConfig(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetExecutionConfig_Success() {
    List<DeprecatedImageInfo> deprecatedImages =
        Arrays.asList(DeprecatedImageInfo.builder().tag("test-tag").version("test-version").build());
    when(ciExecutionConfigService.getDeprecatedTags(TEST_ACCOUNT_ID)).thenReturn(deprecatedImages);

    ResponseDTO<List<DeprecatedImageInfo>> response = idpExecutionConfigResource.getExecutionConfig(TEST_ACCOUNT_ID);

    assertNotNull(response);
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    verify(ciExecutionConfigService).getDeprecatedTags(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDeprecatedConfig_Success() {
    CIExecutionImages executionImages = CIExecutionImages.builder().build();
    when(ciExecutionConfigService.getDeprecatedImages(TEST_ACCOUNT_ID)).thenReturn(executionImages);

    ResponseDTO<CIExecutionImages> response = idpExecutionConfigResource.getDeprecatedConfig(TEST_ACCOUNT_ID);

    assertNotNull(response);
    assertNotNull(response.getData());
    verify(ciExecutionConfigService).getDeprecatedImages(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCustomerConfig_Success() {
    CIExecutionImages executionImages = CIExecutionImages.builder().build();
    when(ciExecutionConfigService.getCustomerConfig(TEST_ACCOUNT_ID, TEST_INFRA_TYPE, true))
        .thenReturn(executionImages);

    ResponseDTO<CIExecutionImages> response =
        idpExecutionConfigResource.getCustomerConfig(TEST_INFRA_TYPE, true, TEST_ACCOUNT_ID);

    assertNotNull(response);
    assertNotNull(response.getData());
    verify(ciExecutionConfigService).getCustomerConfig(TEST_ACCOUNT_ID, TEST_INFRA_TYPE, true);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDefaultConfig_Success() {
    CIExecutionImages executionImages = CIExecutionImages.builder().build();
    when(ciExecutionConfigService.getDefaultConfig(TEST_INFRA_TYPE)).thenReturn(executionImages);

    ResponseDTO<CIExecutionImages> response =
        idpExecutionConfigResource.getDefaultConfig(TEST_ACCOUNT_ID, TEST_INFRA_TYPE);

    assertNotNull(response);
    assertNotNull(response.getData());
    verify(ciExecutionConfigService).getDefaultConfig(TEST_INFRA_TYPE);
  }
}
