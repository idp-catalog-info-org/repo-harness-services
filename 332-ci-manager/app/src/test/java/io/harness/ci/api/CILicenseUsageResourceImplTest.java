/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.licensing.accesscontrol.LicenseAccessControlPermissions.VIEW_LICENSE_PERMISSION;
import static io.harness.licensing.accesscontrol.ResourceTypes.LICENSE;
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
import io.harness.beans.licensing.api.CIDevelopersFilterParams;
import io.harness.beans.licensing.api.CILicense;
import io.harness.beans.licensing.api.CILicenseHistoryDTO;
import io.harness.beans.licensing.api.CILicenseType;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.CILicenseUsageResourceImpl;
import io.harness.ci.licensing.CILicenseUsageImpl;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CILicenseUsageResourceImplTest {
  @Mock private CILicenseUsageImpl ciLicenseUsageService;
  @InjectMocks private CILicenseUsageResourceImpl ciLicenseUsageResource;

  private static final String ACCOUNT_ID = "testAccount";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testCiLicenseUsageAnnotations() throws NoSuchMethodException {
    // Check method level annotations for ciLicenseUsage method
    Method ciLicenseUsageMethod = CILicenseUsageResourceImpl.class.getDeclaredMethod(
        "ciLicenseUsage", String.class, int.class, int.class, List.class, long.class, CIDevelopersFilterParams.class);
    assertTrue(ciLicenseUsageMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(LICENSE, ciLicenseUsageMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(VIEW_LICENSE_PERMISSION, ciLicenseUsageMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(ciLicenseUsageMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDownloadActiveDevelopersCSVReportAnnotations() throws NoSuchMethodException {
    // Check method level annotations for downloadActiveDevelopersCSVReport method
    Method downloadActiveDevelopersCSVReportMethod = CILicenseUsageResourceImpl.class.getDeclaredMethod(
        "downloadActiveDevelopersCSVReport", String.class, long.class);
    assertTrue(downloadActiveDevelopersCSVReportMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(
        LICENSE, downloadActiveDevelopersCSVReportMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(VIEW_LICENSE_PERMISSION,
        downloadActiveDevelopersCSVReportMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(downloadActiveDevelopersCSVReportMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testListActiveDevelopersAnnotations() throws NoSuchMethodException {
    // Check method level annotations for listActiveDevelopers method
    Method listActiveDevelopersMethod =
        CILicenseUsageResourceImpl.class.getDeclaredMethod("listActiveDevelopers", String.class, long.class);
    assertTrue(listActiveDevelopersMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(LICENSE, listActiveDevelopersMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_LICENSE_PERMISSION, listActiveDevelopersMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(listActiveDevelopersMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetLicenseHistoryUsageAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getLicenseHistoryUsage method
    Method getLicenseHistoryUsageMethod = CILicenseUsageResourceImpl.class.getDeclaredMethod(
        "getLicenseHistoryUsage", String.class, CILicenseType.class, CIDevelopersFilterParams.class);
    assertTrue(getLicenseHistoryUsageMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(LICENSE, getLicenseHistoryUsageMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_LICENSE_PERMISSION, getLicenseHistoryUsageMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getLicenseHistoryUsageMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListActiveDevelopers() {
    long currentTsInMs = 1000L;
    Set<String> activeDevelopers = new HashSet<>();
    activeDevelopers.add("dev1");
    activeDevelopers.add("dev2");

    when(ciLicenseUsageService.listActiveDevelopers(ACCOUNT_ID, currentTsInMs)).thenReturn(activeDevelopers);

    ResponseDTO<Set<String>> response = ciLicenseUsageResource.listActiveDevelopers(ACCOUNT_ID, currentTsInMs);

    assertEquals(activeDevelopers, response.getData());
    verify(ciLicenseUsageService, times(1)).listActiveDevelopers(ACCOUNT_ID, currentTsInMs);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLicenseHistoryUsage() {
    CILicenseType licenseType = CILicenseType.DEVELOPERS;
    CIDevelopersFilterParams filterParams = CIDevelopersFilterParams.builder().build();
    CILicenseHistoryDTO historyDTO = CILicenseHistoryDTO.builder().licenseType(licenseType).build();

    when(ciLicenseUsageService.getLicenseHistoryUsage(ACCOUNT_ID, licenseType, filterParams)).thenReturn(historyDTO);

    ResponseDTO<CILicenseHistoryDTO> response =
        ciLicenseUsageResource.getLicenseHistoryUsage(ACCOUNT_ID, licenseType, filterParams);

    assertEquals(historyDTO, response.getData());
    verify(ciLicenseUsageService, times(1)).getLicenseHistoryUsage(ACCOUNT_ID, licenseType, filterParams);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCILicense() {
    CILicense ciLicense = CILicense.builder().account_id(ACCOUNT_ID).build();

    when(ciLicenseUsageService.getCILicense(ACCOUNT_ID)).thenReturn(ciLicense);

    ResponseDTO<CILicense> response = ciLicenseUsageResource.getCILicense(ACCOUNT_ID);

    assertEquals(ciLicense, response.getData());
    verify(ciLicenseUsageService, times(1)).getCILicense(ACCOUNT_ID);
  }
}
