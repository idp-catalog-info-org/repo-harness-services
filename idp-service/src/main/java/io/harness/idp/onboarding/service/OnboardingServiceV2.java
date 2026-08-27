/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchResponse;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefRequest;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefResponse;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesResponse;
import io.harness.spec.server.idp.v1.model.OnboardingSkipRequest;
import io.harness.spec.server.idp.v1.model.OnboardingSkipResponse;
import io.harness.spec.server.idp.v1.model.OnboardingStatusResponse;

import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface OnboardingServiceV2 {
  OnboardingCdEntitiesCountResponse cdEntitiesCount(String harnessAccount);
  OnboardingCdEntitiesFetchResponse cdEntitiesFetch(String harnessAccount,
      OnboardingCdEntitiesFetchRequest onboardingCdEntitiesFetchRequest, Pageable pageable, String searchTerm);
  OnboardingGenerateYamlDefResponse generateYamlDef(
      String harnessAccount, OnboardingGenerateYamlDefRequest onboardingGenerateYamlDefRequest);
  OnboardingStatusResponse getOnboardingStatus(String harnessAccount);
  OnboardingImportCdEntitiesResponse importCdEntities(
      String harnessAccount, OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest);
  OnboardingSkipResponse postOnboardingSkip(String harnessAccount, OnboardingSkipRequest onboardingSkipRequest);
}
