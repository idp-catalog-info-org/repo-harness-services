/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.rule.OwnerRule.GOKUL;
import static io.harness.rule.OwnerRule.SWAROOP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.config.AutoProvisionLicenseConfig;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class FlexLicenseResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acct-1";
  private static final long START_TIME = 1736899200000L;

  @Mock private LicenseService licenseService;
  @Mock private AutoProvisionLicenseConfig autoProvisionLicenseConfig;

  private FlexLicenseResource underTest;

  @Before
  public void setUp() {
    underTest = new FlexLicenseResource(licenseService, autoProvisionLicenseConfig);
  }

  @Test
  @Owner(developers = GOKUL)
  @Category(UnitTests.class)
  public void applyFlexLicense_previewTrue_callsPreviewAndDoesNotPersist() {
    List<ModuleType> modules = Arrays.asList(ModuleType.CD);
    List<ModuleLicenseDTO> previewLicenses =
        Collections.singletonList(CDModuleLicenseDTO.builder().moduleType(ModuleType.CD).build());
    when(autoProvisionLicenseConfig.getModulesForEdition(Edition.ENTERPRISE)).thenReturn(modules);
    when(licenseService.previewFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, modules)).thenReturn(previewLicenses);

    Response response = underTest.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, null, true);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    @SuppressWarnings("unchecked")
    ResponseDTO<List<ModuleLicenseDTO>> body = (ResponseDTO<List<ModuleLicenseDTO>>) response.getEntity();
    assertThat(body.getData()).isSameAs(previewLicenses);
    verify(licenseService, never()).startFlexLicense(eq(ACCOUNT_ID), eq(Edition.ENTERPRISE), eq(modules));
  }

  @Test
  @Owner(developers = GOKUL)
  @Category(UnitTests.class)
  public void applyFlexLicense_previewFalse_callsStartAndPersists() {
    List<ModuleType> modules = Arrays.asList(ModuleType.CD);
    List<ModuleLicenseDTO> appliedLicenses =
        Collections.singletonList(CDModuleLicenseDTO.builder().moduleType(ModuleType.CD).build());
    when(autoProvisionLicenseConfig.getModulesForEdition(Edition.ENTERPRISE)).thenReturn(modules);
    when(licenseService.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, modules)).thenReturn(appliedLicenses);

    Response response = underTest.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, null, false);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    @SuppressWarnings("unchecked")
    ResponseDTO<List<ModuleLicenseDTO>> body = (ResponseDTO<List<ModuleLicenseDTO>>) response.getEntity();
    assertThat(body.getData()).isSameAs(appliedLicenses);
    verify(licenseService, never()).previewFlexLicense(eq(ACCOUNT_ID), eq(Edition.ENTERPRISE), eq(modules));
  }

  @Test
  @Owner(developers = SWAROOP)
  @Category(UnitTests.class)
  public void applyFlexLicense_withStartTime_forwardsToService() {
    List<ModuleType> modules = Arrays.asList(ModuleType.CD);
    List<ModuleLicenseDTO> appliedLicenses =
        Collections.singletonList(CDModuleLicenseDTO.builder().moduleType(ModuleType.CD).startTime(START_TIME).build());
    when(autoProvisionLicenseConfig.getModulesForEdition(Edition.ENTERPRISE)).thenReturn(modules);
    when(licenseService.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, modules, START_TIME))
        .thenReturn(appliedLicenses);

    Response response = underTest.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, START_TIME, false);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    verify(licenseService).startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, modules, START_TIME);
  }

  // The resource forwards whatever AutoProvisionLicenseConfig resolves — including an empty list.
  // The service is responsible for the empty-list short-circuit; the resource just plumbs through.
  @Test
  @Owner(developers = GOKUL)
  @Category(UnitTests.class)
  public void applyFlexLicense_emptyModulesFromConfig_passesThroughToService() {
    when(autoProvisionLicenseConfig.getModulesForEdition(Edition.ENTERPRISE)).thenReturn(Collections.emptyList());
    when(licenseService.previewFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, Collections.emptyList()))
        .thenReturn(Collections.emptyList());

    Response response = underTest.startFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, null, true);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    @SuppressWarnings("unchecked")
    ResponseDTO<List<ModuleLicenseDTO>> body = (ResponseDTO<List<ModuleLicenseDTO>>) response.getEntity();
    assertThat(body.getData()).isEmpty();
    verify(licenseService).previewFlexLicense(ACCOUNT_ID, Edition.ENTERPRISE, Collections.emptyList());
  }
}
