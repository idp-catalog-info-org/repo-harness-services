/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;
import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.cache.api.CacheMetadataInfo;
import io.harness.beans.cache.api.DeleteCacheResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.CICacheManagementResourceImpl;
import io.harness.ci.cache.CICacheManagementService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CICacheManagementResourceImplTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private CICacheManagementService ciCacheManagementService;
  @InjectMocks private CICacheManagementResourceImpl ciCacheManagementResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheInfoAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getCacheInfo method
    Method getCacheInfoMethod = CICacheManagementResourceImpl.class.getDeclaredMethod("getCacheInfo", String.class);
    assertTrue(getCacheInfoMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getCacheInfoMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(EDIT_ACCOUNT_PERMISSION, getCacheInfoMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getCacheInfoMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDeleteCacheAnnotations() throws NoSuchMethodException {
    // Check method level annotations for deleteCache method
    Method deleteCacheMethod =
        CICacheManagementResourceImpl.class.getDeclaredMethod("deleteCache", String.class, String.class, String.class);
    assertTrue(deleteCacheMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, deleteCacheMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(EDIT_ACCOUNT_PERMISSION, deleteCacheMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(deleteCacheMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheInfo() {
    CacheMetadataInfo expectedInfo = CacheMetadataInfo.builder()
                                         .used(100L)
                                         .available(900L)
                                         .total(1000L)
                                         .unit("MB")
                                         .details(Collections.emptyList())
                                         .build();
    when(ciCacheManagementService.getCacheMetadata(ACCOUNT_IDENTIFIER)).thenReturn(expectedInfo);

    ResponseDTO<CacheMetadataInfo> response = ciCacheManagementResource.getCacheInfo(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(expectedInfo);
    verify(ciCacheManagementService).getCacheMetadata(ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDeleteCache() {
    String path = "/test/path";
    String cacheType = "s3";
    DeleteCacheResponse expectedResponse = DeleteCacheResponse.builder().deleted(Collections.emptyList()).build();
    when(ciCacheManagementService.deleteCache(ACCOUNT_IDENTIFIER, path, cacheType)).thenReturn(expectedResponse);

    ResponseDTO<DeleteCacheResponse> response =
        ciCacheManagementResource.deleteCache(ACCOUNT_IDENTIFIER, path, cacheType);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(expectedResponse);
    verify(ciCacheManagementService).deleteCache(ACCOUNT_IDENTIFIER, path, cacheType);
  }
}
