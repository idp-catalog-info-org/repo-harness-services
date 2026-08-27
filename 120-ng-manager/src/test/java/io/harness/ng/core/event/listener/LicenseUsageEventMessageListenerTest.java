/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.rule.OwnerRule.PRATHMESH;

import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.platform.ArchitectureType;
import io.harness.eventsframework.schemas.platform.BuildInfraType;
import io.harness.eventsframework.schemas.platform.CILicenseUsageData;
import io.harness.ng.core.licenseusage.dto.CILicenseUsageDataDTO;
import io.harness.ng.core.licenseusage.dto.LicenseUsageDTO;
import io.harness.ng.core.licenseusage.event.LicenseUsageEventMessageListener;
import io.harness.ng.core.licenseusage.mapper.LicenseUsageProtoToRestDTOMapper;
import io.harness.ng.core.licenseusage.services.LicenseUsageService;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;
import io.harness.repositories.licenseusage.LicenseUsageRepository;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LicenseUsageEventMessageListenerTest extends CategoryTest {
  private LicenseUsageRepository licenseUsageRepository;

  @InjectMocks LicenseUsageEventMessageListener licenseUsageEventMessageListener;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock LicenseUsageProtoToRestDTOMapper licenseUsageProtoToRestDTOMapper;
  @Mock LicenseUsageService licenseUsageService;
  @Mock LicenseUsageMetricHelper metricHelper;
  private static final String ACCOUNT_ID = "test_account";
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    licenseUsageRepository = mock(LicenseUsageRepository.class);
  }

  @Test
  @Owner(developers = PRATHMESH)
  @Category(UnitTests.class)
  // PL should ignore any self hosted CI events and only consider hosted events for license usage calc.
  public void testHandleSelfHostedCIEvent() {
    String accountIdentifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY))
                                          .setData(getCiLicenseUsageData())
                                          .build())
                          .build();
    Set<String> ffEnabledAccounts = new HashSet<>(Arrays.asList(ACCOUNT_ID));

    doReturn(ffEnabledAccounts)
        .when(featureFlagHelperService)
        .getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_LICENSE_USAGE_COMPUTE.name());
    doReturn(getCiLicenseUsageDTO()).when(licenseUsageProtoToRestDTOMapper).toRestDTO(any());
    boolean result = licenseUsageEventMessageListener.handleMessage(message);
    verify(licenseUsageRepository, times(0)).save(any()); // The entity is not saved
    assertTrue(result);
  }
  private ByteString getCiLicenseUsageData() {
    return CILicenseUsageData.newBuilder()
        .setArchType(ArchitectureType.ARCHITECTURE_TYPE_AMD64)
        .setBuildInfraType(BuildInfraType.BUILD_INFRA_TYPE_DOCKER)
        .build()
        .toByteString();
  }
  private LicenseUsageDTO getCiLicenseUsageDTO() {
    return LicenseUsageDTO.builder()
        .accountIdentifier(ACCOUNT_ID)
        .moduleUsageData(CILicenseUsageDataDTO.builder()
                             .architectureType(io.harness.ArchitectureType.AMD64)
                             .buildInfraType(io.harness.BuildInfraType.DOCKER)
                             .build())
        .build();
  }
}
