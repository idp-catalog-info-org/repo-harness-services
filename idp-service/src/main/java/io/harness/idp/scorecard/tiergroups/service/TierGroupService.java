/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.spec.server.idp.v1.model.ScoreTier;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

@OwnedBy(HarnessTeam.IDP)
public interface TierGroupService {
  List<TierGroupEntity> getAllTierGroups(String accountIdentifier);

  void createDefaultTierGroupIfAbsent(String accountIdentifier);

  TierGroupDetailsResponse getTierGroupDetails(String accountIdentifier, String identifier);

  TierGroupDetailsResponse saveTierGroup(TierGroupDetailsRequest request, String accountIdentifier);

  TierGroupDetailsResponse updateTierGroup(TierGroupDetailsRequest request, String accountIdentifier);

  void deleteTierGroup(String accountIdentifier, String identifier);

  TierGroupEntity getActiveTierGroup(String accountIdentifier, String identifier);

  void validateTierGroupReference(String accountIdentifier, String tierGroupIdentifier);

  Optional<ScoreTier> resolveScoreTier(String accountIdentifier, String tierGroupIdentifier, int score);

  Optional<ScoreTier> resolveScoreTier(TierGroupEntity tierGroup, String tierGroupIdentifier, int score);

  String uploadTierIcon(
      String fileType, InputStream fileInputStream, FormDataContentDisposition fileDetail, String harnessAccount);
}
