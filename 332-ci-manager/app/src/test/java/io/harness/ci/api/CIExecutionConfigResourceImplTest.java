/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;
import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.CIExecutionConfigResourceImpl;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.Operation;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CIExecutionConfigResourceImplTest {
  @Mock private CIExecutionConfigService configService;
  @InjectMocks private CIExecutionConfigResourceImpl ciExecutionConfigResource;

  private static final String ACCOUNT_ID = "testAccount";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUpdateExecutionConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for updateExecutionConfig method
    Method updateExecutionConfigMethod = CIExecutionConfigResourceImpl.class.getDeclaredMethod(
        "updateExecutionConfig", Type.class, String.class, List.class);
    assertTrue(updateExecutionConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, updateExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        EDIT_ACCOUNT_PERMISSION, updateExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(updateExecutionConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResetExecutionConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for resetExecutionConfig method
    Method resetExecutionConfigMethod = CIExecutionConfigResourceImpl.class.getDeclaredMethod(
        "resetExecutionConfig", Type.class, String.class, List.class);
    assertTrue(resetExecutionConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, resetExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        EDIT_ACCOUNT_PERMISSION, resetExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(resetExecutionConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDeleteExecutionConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for deleteExecutionConfig method
    Method deleteExecutionConfigMethod =
        CIExecutionConfigResourceImpl.class.getDeclaredMethod("deleteExecutionConfig", String.class);
    assertTrue(deleteExecutionConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, deleteExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        EDIT_ACCOUNT_PERMISSION, deleteExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(deleteExecutionConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetExecutionConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getExecutionConfig method
    Method getExecutionConfigMethod =
        CIExecutionConfigResourceImpl.class.getDeclaredMethod("getExecutionConfig", String.class);
    assertTrue(getExecutionConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_ACCOUNT_PERMISSION, getExecutionConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getExecutionConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetDeprecatedConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getDeprecatedConfig method
    Method getDeprecatedConfigMethod =
        CIExecutionConfigResourceImpl.class.getDeclaredMethod("getDeprecatedConfig", String.class);
    assertTrue(getDeprecatedConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getDeprecatedConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_ACCOUNT_PERMISSION, getDeprecatedConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getDeprecatedConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCustomerConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getCustomerConfig method
    Method getCustomerConfigMethod = CIExecutionConfigResourceImpl.class.getDeclaredMethod(
        "getCustomerConfig", Type.class, boolean.class, String.class);
    assertTrue(getCustomerConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getCustomerConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_ACCOUNT_PERMISSION, getCustomerConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getCustomerConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetDefaultConfigAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getDefaultConfig method
    Method getDefaultConfigMethod =
        CIExecutionConfigResourceImpl.class.getDeclaredMethod("getDefaultConfig", String.class, Type.class);
    assertTrue(getDefaultConfigMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getDefaultConfigMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_ACCOUNT_PERMISSION, getDefaultConfigMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getDefaultConfigMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateExecutionConfig() {
    Type infra = Type.K8;
    List<Operation> operations = Collections.emptyList();

    when(configService.updateCIContainerTags(ACCOUNT_ID, operations, infra)).thenReturn(true);

    ResponseDTO<Boolean> response = ciExecutionConfigResource.updateExecutionConfig(infra, ACCOUNT_ID, operations);

    assertEquals(Boolean.TRUE, response.getData());
    verify(configService, times(1)).updateCIContainerTags(ACCOUNT_ID, operations, infra);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResetExecutionConfig() {
    Type infra = Type.K8;
    List<Operation> operations = Collections.emptyList();

    when(configService.resetCIContainerTags(ACCOUNT_ID, operations, infra)).thenReturn(true);

    ResponseDTO<Boolean> response = ciExecutionConfigResource.resetExecutionConfig(infra, ACCOUNT_ID, operations);

    assertEquals(Boolean.TRUE, response.getData());
    verify(configService, times(1)).resetCIContainerTags(ACCOUNT_ID, operations, infra);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeleteExecutionConfig() {
    when(configService.deleteCIExecutionConfig(ACCOUNT_ID)).thenReturn(true);

    ResponseDTO<Boolean> response = ciExecutionConfigResource.deleteExecutionConfig(ACCOUNT_ID);

    assertEquals(Boolean.TRUE, response.getData());
    verify(configService, times(1)).deleteCIExecutionConfig(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetExecutionConfig() {
    List<DeprecatedImageInfo> deprecatedTags = Collections.emptyList();
    when(configService.getDeprecatedTags(ACCOUNT_ID)).thenReturn(deprecatedTags);

    ResponseDTO<List<DeprecatedImageInfo>> response = ciExecutionConfigResource.getExecutionConfig(ACCOUNT_ID);

    assertEquals(deprecatedTags, response.getData());
    verify(configService, times(1)).getDeprecatedTags(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDeprecatedConfig() {
    CIExecutionImages images = CIExecutionImages.builder().build();
    when(configService.getDeprecatedImages(ACCOUNT_ID)).thenReturn(images);

    ResponseDTO<CIExecutionImages> response = ciExecutionConfigResource.getDeprecatedConfig(ACCOUNT_ID);

    assertEquals(images, response.getData());
    verify(configService, times(1)).getDeprecatedImages(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCustomerConfig() {
    Type infra = Type.K8;
    boolean overridesOnly = true;
    CIExecutionImages images = CIExecutionImages.builder().build();
    when(configService.getCustomerConfig(ACCOUNT_ID, infra, overridesOnly)).thenReturn(images);

    ResponseDTO<CIExecutionImages> response =
        ciExecutionConfigResource.getCustomerConfig(infra, overridesOnly, ACCOUNT_ID);

    assertEquals(images, response.getData());
    verify(configService, times(1)).getCustomerConfig(ACCOUNT_ID, infra, overridesOnly);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDefaultConfig() {
    Type infra = Type.K8;
    CIExecutionImages images = CIExecutionImages.builder().build();
    when(configService.getDefaultConfig(infra)).thenReturn(images);

    ResponseDTO<CIExecutionImages> response = ciExecutionConfigResource.getDefaultConfig(ACCOUNT_ID, infra);

    assertEquals(images, response.getData());
    verify(configService, times(1)).getDefaultConfig(infra);
  }
}
