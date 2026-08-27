/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierGroupConfig;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierIcon;
import io.harness.idp.scorecard.tiergroups.config.TierIconConfig;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.repositories.TierGroupRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Tier;
import io.harness.spec.server.idp.v1.model.TierGroupDetails;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;

import com.mongodb.client.result.UpdateResult;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";

  @Mock private TierGroupRepository tierGroupRepository;
  @Mock private TierGroupScorecardUsageChecker tierGroupScorecardUsageChecker;
  @Mock private TierGroupLockHelper tierGroupLockHelper;
  @Mock private CloudStorageUtil cloudStorageUtil;
  private static final String ENV = "qa";

  private TierGroupServiceImpl tierGroupService;
  private TierIconConfig tierIconConfig;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    tierIconConfig = TierIconConfig.builder()
                         .bucketName("idp-tiers-qa")
                         .cdnDNS("idp-tiers-qa.qa.harness.io")
                         .cdnEnabled(false)
                         .storageType("GCS")
                         .s3Region("us-west-2")
                         .build();
    tierGroupService = new TierGroupServiceImpl(tierGroupRepository, tierGroupScorecardUsageChecker,
        tierGroupLockHelper, buildDefaultTierGroupConfig(), cloudStorageUtil, tierIconConfig, ENV);
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(2);
      runnable.run();
      return null;
    })
        .when(tierGroupLockHelper)
        .executeWithTierGroupLock(anyString(), anyString(), any(Runnable.class));
    doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
        .when(tierGroupLockHelper)
        .executeWithTierGroupLock(anyString(), anyString(), any(Supplier.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getAllTierGroupsCreatesAndRefetchesDefaultWhenRepositoryIsEmpty() {
    TierGroupEntity defaultTierGroup = TierGroupEntity.builder().identifier(DEFAULT_TIER_GROUP_IDENTIFIER).build();
    when(tierGroupRepository.findByAccountIdentifierAndIsDeleted(ACCOUNT_ID, false))
        .thenReturn(List.of())
        .thenReturn(List.of(defaultTierGroup));
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null);

    List<TierGroupEntity> result = tierGroupService.getAllTierGroups(ACCOUNT_ID);

    assertThat(result).containsExactly(defaultTierGroup);
    verify(tierGroupRepository, times(2)).findByAccountIdentifierAndIsDeleted(ACCOUNT_ID, false);
    verify(tierGroupRepository).save(any(TierGroupEntity.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getAllTierGroupsCreatesAndRefetchesDefaultWhenRepositoryReturnsNull() {
    TierGroupEntity defaultTierGroup = TierGroupEntity.builder().identifier(DEFAULT_TIER_GROUP_IDENTIFIER).build();
    when(tierGroupRepository.findByAccountIdentifierAndIsDeleted(ACCOUNT_ID, false))
        .thenReturn(null)
        .thenReturn(List.of(defaultTierGroup));
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null);

    List<TierGroupEntity> result = tierGroupService.getAllTierGroups(ACCOUNT_ID);

    assertThat(result).containsExactly(defaultTierGroup);
    verify(tierGroupRepository, times(2)).findByAccountIdentifierAndIsDeleted(ACCOUNT_ID, false);
    verify(tierGroupRepository).save(any(TierGroupEntity.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getAllTierGroupsDoesNotCreateDefaultWhenCustomGroupsExist() {
    TierGroupEntity customTierGroup = TierGroupEntity.builder().identifier("custom_tiers").build();
    when(tierGroupRepository.findByAccountIdentifierAndIsDeleted(ACCOUNT_ID, false))
        .thenReturn(List.of(customTierGroup));

    List<TierGroupEntity> result = tierGroupService.getAllTierGroups(ACCOUNT_ID);

    assertThat(result).containsExactly(customTierGroup);
    verify(tierGroupRepository, never())
        .findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false);
    verify(tierGroupRepository, never()).save(any(TierGroupEntity.class));
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createDefaultTierGroupIfAbsentCreatesConfiguredGroupUnderLock() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null);
    ArgumentCaptor<TierGroupEntity> entityCaptor = ArgumentCaptor.forClass(TierGroupEntity.class);

    tierGroupService.createDefaultTierGroupIfAbsent(ACCOUNT_ID);

    verify(tierGroupLockHelper)
        .executeWithTierGroupLock(eq(ACCOUNT_ID), eq(DEFAULT_TIER_GROUP_IDENTIFIER), any(Runnable.class));
    verify(tierGroupRepository).save(entityCaptor.capture());
    TierGroupEntity saved = entityCaptor.getValue();
    assertThat(saved.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(saved.getIdentifier()).isEqualTo(DEFAULT_TIER_GROUP_IDENTIFIER);
    assertThat(saved.getName()).isEqualTo("Default Tier Group");
    assertThat(saved.getDescription()).isEqualTo("Default score tiers for measuring software delivery performance.");
    assertThat(saved.getTiers())
        .extracting(TierGroupEntity.Tier::getName)
        .containsExactly("Critical", "Warning", "Healthy");
    assertThat(saved.getTiers())
        .extracting(TierGroupEntity.Tier::getDescription)
        .containsExactly("Score is below recommended standards and needs immediate attention.",
            "Score partially meets recommended standards and needs improvement.",
            "Score meets or exceeds recommended standards.");
    assertThat(saved.getTiers())
        .extracting(TierGroupEntity.Tier::getMinScore, TierGroupEntity.Tier::getMaxScore)
        .containsExactly(tuple(0, 49), tuple(50, 74), tuple(75, 100));
    assertThat(saved.getTiers())
        .extracting(TierGroupEntity.Tier::getColour)
        .containsExactly("#e43326", "#ffaf00", "#1b841d");
    assertThat(saved.getTiers()).extracting(TierGroupEntity.Tier::getIcon).containsExactly("BRONZE", "SILVER", "GOLD");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createDefaultTierGroupIfAbsentDoesNotOverwriteExistingActiveDefault() {
    TierGroupEntity existing = TierGroupEntity.builder().identifier(DEFAULT_TIER_GROUP_IDENTIFIER).build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(existing);

    tierGroupService.createDefaultTierGroupIfAbsent(ACCOUNT_ID);

    verify(tierGroupRepository, never()).save(any(TierGroupEntity.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createDefaultTierGroupIfAbsentAcceptsVerifiedDuplicateKeyRace() {
    TierGroupEntity concurrentlyCreated = TierGroupEntity.builder().identifier(DEFAULT_TIER_GROUP_IDENTIFIER).build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null)
        .thenReturn(concurrentlyCreated);
    doThrow(new DuplicateKeyException("concurrent default creation"))
        .when(tierGroupRepository)
        .save(any(TierGroupEntity.class));

    tierGroupService.createDefaultTierGroupIfAbsent(ACCOUNT_ID);

    verify(tierGroupRepository).save(any(TierGroupEntity.class));
    verify(tierGroupRepository, times(2))
        .findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createDefaultTierGroupIfAbsentRejectsDuplicateWithoutActiveDefault() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null);
    doThrow(new DuplicateKeyException("conflicting deleted record"))
        .when(tierGroupRepository)
        .save(any(TierGroupEntity.class));

    assertThatThrownBy(() -> tierGroupService.createDefaultTierGroupIfAbsent(ACCOUNT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("conflicting record");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createDefaultTierGroupIfAbsentRejectsMissingConfiguredScoreBound() {
    DefaultTierGroupConfig validConfig = buildDefaultTierGroupConfig();
    DefaultTierGroupConfig invalidConfig = DefaultTierGroupConfig.builder()
                                               .identifier(DEFAULT_TIER_GROUP_IDENTIFIER)
                                               .name("Default Tier Group")
                                               .tiers(List.of(DefaultTierGroupConfig.TierConfig.builder()
                                                                  .name("Bronze")
                                                                  .icon(DefaultTierIcon.BRONZE)
                                                                  .colour("#CD7F32")
                                                                  .maxScore(49)
                                                                  .build(),
                                                   validConfig.getTiers().get(1), validConfig.getTiers().get(2)))
                                               .build();
    TierGroupServiceImpl serviceWithInvalidConfig = new TierGroupServiceImpl(tierGroupRepository,
        tierGroupScorecardUsageChecker, tierGroupLockHelper, invalidConfig, cloudStorageUtil, tierIconConfig, ENV);
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(null);

    assertThatThrownBy(() -> serviceWithInvalidConfig.createDefaultTierGroupIfAbsent(ACCOUNT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must define min_score and max_score");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupPersistsValidGroup() {
    TierGroupDetailsRequest request = buildRequest("gold_silver_bronze");
    when(tierGroupRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, "gold_silver_bronze")).thenReturn(null);
    when(tierGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TierGroupDetailsResponse response = tierGroupService.saveTierGroup(request, ACCOUNT_ID);

    assertThat(response.getTierGroup().getIdentifier()).isEqualTo("gold_silver_bronze");
    assertThat(response.getTierGroup().getTiers()).hasSize(3);
    verify(tierGroupLockHelper).executeWithTierGroupLock(eq(ACCOUNT_ID), eq("gold_silver_bronze"), any(Supplier.class));
    verify(tierGroupRepository).save(any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupNormalizesWhitespacePaddedIdentifierForLockAndLookup() {
    TierGroupDetailsRequest request = buildRequest("  gold_silver_bronze  ");
    when(tierGroupRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, "gold_silver_bronze")).thenReturn(null);
    when(tierGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TierGroupDetailsResponse response = tierGroupService.saveTierGroup(request, ACCOUNT_ID);

    assertThat(response.getTierGroup().getIdentifier()).isEqualTo("gold_silver_bronze");
    verify(tierGroupLockHelper).executeWithTierGroupLock(eq(ACCOUNT_ID), eq("gold_silver_bronze"), any(Supplier.class));
    verify(tierGroupRepository).findByAccountIdentifierAndIdentifier(ACCOUNT_ID, "gold_silver_bronze");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsReservedDefaultIdentifierWithWhitespace() {
    TierGroupDetailsRequest request = buildRequest("  " + DEFAULT_TIER_GROUP_IDENTIFIER + "  ");

    assertThatThrownBy(() -> tierGroupService.saveTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("reserved for system use");

    verifyNoInteractions(tierGroupRepository, tierGroupScorecardUsageChecker, tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteTierGroupRejectsDefaultIdentifierBeforeRepositoryLookup() {
    assertThatThrownBy(() -> tierGroupService.deleteTierGroup(ACCOUNT_ID, "  " + DEFAULT_TIER_GROUP_IDENTIFIER + "  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("cannot be deleted");

    verifyNoInteractions(tierGroupRepository, tierGroupScorecardUsageChecker, tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconReturnsPublicUrl() {
    String storageUrl = "https://storage.cloud.google.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID + "/tiers/gold.png";
    when(cloudStorageUtil.uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any())).thenReturn(storageUrl);
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();
    String url =
        tierGroupService.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);
    // Non-CDN GCS urls are rewritten to the public storage host.
    assertThat(url).isEqualTo(
        "https://storage.googleapis.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID + "/tiers/gold.png");
    verify(cloudStorageUtil).uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any());
  }
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsUnsupportedFormat() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.exe").size(1024).build();
    assertThatThrownBy(()
                           -> tierGroupService.uploadTierIcon(
                               "ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("format 'exe' is not supported")
        .hasMessageContaining("jpeg, jpg, png, svg");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsMissingFile() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();

    assertThatThrownBy(() -> tierGroupService.uploadTierIcon("ICON", null, fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier icon file is required");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsMissingFileName() {
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").build();

    assertThatThrownBy(()
                           -> tierGroupService.uploadTierIcon(
                               "ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier icon file name is required");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsMissingExtension() {
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("gold").size(1024).build();

    assertThatThrownBy(()
                           -> tierGroupService.uploadTierIcon(
                               "ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("supported formats")
        .hasMessageContaining("jpeg, jpg, png, svg");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsPathLikeFileName() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("../gold.png").size(1024).build();

    assertThatThrownBy(()
                           -> tierGroupService.uploadTierIcon(
                               "ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("must not contain path separators");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconRejectsNonIconFileType() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();

    assertThatThrownBy(()
                           -> tierGroupService.uploadTierIcon(
                               "SCREENSHOT", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("File type must be ICON");
    verify(cloudStorageUtil, never()).uploadFile(anyString(), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconAcceptsUppercaseExtension() {
    String storageUrl = "https://storage.cloud.google.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID + "/tiers/gold.PNG";
    when(cloudStorageUtil.uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any())).thenReturn(storageUrl);
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.PNG").size(1024).build();

    String url =
        tierGroupService.uploadTierIcon("icon", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(url).isEqualTo(
        "https://storage.googleapis.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID + "/tiers/gold.PNG");
    verify(cloudStorageUtil).uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void uploadTierIconSanitizesSpecialCharsInFileName() {
    String storageUrl = "https://storage.cloud.google.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID
        + "/tiers/Screenshot_2026-07-27_at_5.41.54_PM.png";
    when(cloudStorageUtil.uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any())).thenReturn(storageUrl);
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("Screenshot 2026-07-27 at 5.41.54 PM.png").size(1024).build();

    tierGroupService.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    // Spaces and other unsafe characters must be replaced with '_' before the object key is created.
    verify(cloudStorageUtil)
        .uploadFile(eq("idp-tiers-qa"), anyString(),
            org.mockito.ArgumentMatchers.argThat(name -> name.contains("Screenshot_2026-07-27_at_5.41.54_PM.png")),
            any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupPermitsDefaultIdentifier() {
    TierGroupDetailsRequest request = buildRequest(DEFAULT_TIER_GROUP_IDENTIFIER);
    TierGroupEntity existing = TierGroupEntity.builder().identifier(DEFAULT_TIER_GROUP_IDENTIFIER).build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(existing);
    when(tierGroupRepository.update(any(TierGroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TierGroupDetailsResponse response = tierGroupService.updateTierGroup(request, ACCOUNT_ID);

    assertThat(response.getTierGroup().getIdentifier()).isEqualTo(DEFAULT_TIER_GROUP_IDENTIFIER);
    verify(tierGroupLockHelper)
        .executeWithTierGroupLock(eq(ACCOUNT_ID), eq(DEFAULT_TIER_GROUP_IDENTIFIER), any(Supplier.class));
    verify(tierGroupRepository).update(any(TierGroupEntity.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void defaultTierGroupSupportsLookupReferenceValidationAndResolution() {
    TierGroupEntity defaultTierGroup = TierGroupEntity.builder()
                                           .identifier(DEFAULT_TIER_GROUP_IDENTIFIER)
                                           .name("Edited Default")
                                           .tiers(List.of(TierGroupEntity.Tier.builder()
                                                              .name("Bronze")
                                                              .icon("https://example.com/bronze.png")
                                                              .colour("#CD7F32")
                                                              .minScore(0)
                                                              .maxScore(49)
                                                              .build(),
                                               TierGroupEntity.Tier.builder()
                                                   .name("Gold")
                                                   .icon("https://example.com/gold.png")
                                                   .colour("#FFD700")
                                                   .minScore(50)
                                                   .maxScore(100)
                                                   .build()))
                                           .build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(defaultTierGroup);

    TierGroupDetailsResponse details = tierGroupService.getTierGroupDetails(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER);
    tierGroupService.validateTierGroupReference(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER);
    Optional<io.harness.spec.server.idp.v1.model.ScoreTier> resolved =
        tierGroupService.resolveScoreTier(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, 75);

    assertThat(details.getTierGroup().getName()).isEqualTo("Edited Default");
    assertThat(resolved).isPresent();
    assertThat(resolved.get().getTierName()).isEqualTo("Gold");
    assertThat(resolved.get().getTierGroupIdentifier()).isEqualTo(DEFAULT_TIER_GROUP_IDENTIFIER);
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteTierGroupFailsWhenReferencedByScorecard() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "levels", false))
        .thenReturn(TierGroupEntity.builder().identifier("levels").build());
    when(tierGroupScorecardUsageChecker.isReferencedByScorecard(ACCOUNT_ID, "levels")).thenReturn(true);

    assertThatThrownBy(() -> tierGroupService.deleteTierGroup(ACCOUNT_ID, "levels"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("referenced");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupDetailsReturnsDefaultTiersWithRanges() {
    TierGroupEntity defaultTierGroup = TierGroupEntity.builder()
                                           .identifier(DEFAULT_TIER_GROUP_IDENTIFIER)
                                           .name("Default Tier Group")
                                           .tiers(List.of(TierGroupEntity.Tier.builder()
                                                              .name("Bronze")
                                                              .icon("https://example.com/bronze.png")
                                                              .colour("#CD7F32")
                                                              .minScore(0)
                                                              .maxScore(49)
                                                              .build(),
                                               TierGroupEntity.Tier.builder()
                                                   .name("Silver")
                                                   .icon("https://example.com/silver.png")
                                                   .colour("#C0C0C0")
                                                   .minScore(50)
                                                   .maxScore(74)
                                                   .build(),
                                               TierGroupEntity.Tier.builder()
                                                   .name("Gold")
                                                   .icon("https://example.com/gold.png")
                                                   .colour("#FFD700")
                                                   .minScore(75)
                                                   .maxScore(100)
                                                   .build()))
                                           .build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(defaultTierGroup);

    TierGroupDetailsResponse response = tierGroupService.getTierGroupDetails(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER);

    assertThat(response.getTierGroup().getIdentifier()).isEqualTo(DEFAULT_TIER_GROUP_IDENTIFIER);
    assertThat(response.getTierGroup().getTiers()).hasSize(3);
    assertThat(response.getTierGroup().getTiers().get(0).getMinScore()).isEqualTo(0);
    assertThat(response.getTierGroup().getTiers().get(2).getMaxScore()).isEqualTo(100);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupPersistsNameAndColours() {
    TierGroupDetailsRequest request = buildRequest("compliance_tiers");
    request.getTierGroup().setName("Updated Compliance");
    request.getTierGroup().getTiers().get(0).setColour("#AAAAAA");
    TierGroupEntity existing = TierGroupEntity.builder().identifier("compliance_tiers").build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "compliance_tiers", false))
        .thenReturn(existing);
    when(tierGroupRepository.update(any(TierGroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TierGroupDetailsResponse response = tierGroupService.updateTierGroup(request, ACCOUNT_ID);

    assertThat(response.getTierGroup().getName()).isEqualTo("Updated Compliance");
    assertThat(response.getTierGroup().getTiers().get(0).getColour()).isEqualTo("#AAAAAA");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteTierGroupSucceedsWhenUnused() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "custom_tiers", false))
        .thenReturn(TierGroupEntity.builder().identifier("custom_tiers").build());
    when(tierGroupScorecardUsageChecker.isReferencedByScorecard(ACCOUNT_ID, "custom_tiers")).thenReturn(false);
    when(tierGroupRepository.softDelete(ACCOUNT_ID, "custom_tiers")).thenReturn(UpdateResult.acknowledged(1, 1L, null));

    tierGroupService.deleteTierGroup(ACCOUNT_ID, "custom_tiers");

    verify(tierGroupRepository).softDelete(ACCOUNT_ID, "custom_tiers");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsDuplicateIdentifier() {
    TierGroupDetailsRequest request = buildRequest("compliance_tiers");
    when(tierGroupRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, "compliance_tiers"))
        .thenReturn(TierGroupEntity.builder().identifier("compliance_tiers").isDeleted(false).build());

    assertThatThrownBy(() -> tierGroupService.saveTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsPreviouslyUsedSoftDeletedIdentifier() {
    TierGroupDetailsRequest request = buildRequest("retired_tiers");
    when(tierGroupRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, "retired_tiers"))
        .thenReturn(TierGroupEntity.builder().identifier("retired_tiers").isDeleted(true).build());

    assertThatThrownBy(() -> tierGroupService.saveTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("cannot be reused");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupThrowsWhenNotFound() {
    TierGroupDetailsRequest request = buildRequest("missing_tiers");
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "missing_tiers", false))
        .thenReturn(null);

    assertThatThrownBy(() -> tierGroupService.updateTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier group not found");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupDetailsThrowsWhenNotFound() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "missing_tiers", false))
        .thenReturn(null);

    assertThatThrownBy(() -> tierGroupService.getTierGroupDetails(ACCOUNT_ID, "missing_tiers"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier group not found");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsNullRequest() {
    assertThatThrownBy(() -> tierGroupService.saveTierGroup(null, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier group details are required");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsEmptyIdentifier() {
    TierGroupDetailsRequest request = buildRequest("compliance_tiers");
    request.getTierGroup().setIdentifier("  ");

    assertThatThrownBy(() -> tierGroupService.saveTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("identifier cannot be empty");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void saveTierGroupRejectsEmptyName() {
    TierGroupDetailsRequest request = buildRequest("compliance_tiers");
    request.getTierGroup().setName("  ");

    assertThatThrownBy(() -> tierGroupService.saveTierGroup(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("name cannot be empty");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTierGroupReferenceRejectsBlankIdentifier() {
    assertThatThrownBy(() -> tierGroupService.validateTierGroupReference(ACCOUNT_ID, "  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier group identifier is required");
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTierGroupReferenceThrowsWhenTierGroupMissing() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "unknown_group", false))
        .thenReturn(null);

    assertThatThrownBy(() -> tierGroupService.validateTierGroupReference(ACCOUNT_ID, "unknown_group"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not find tier group");
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveScoreTierReturnsEmptyWhenTierGroupMissing() {
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "missing_tiers", false))
        .thenReturn(null);

    Optional<io.harness.spec.server.idp.v1.model.ScoreTier> result =
        tierGroupService.resolveScoreTier(ACCOUNT_ID, "missing_tiers", 50);

    assertThat(result).isEmpty();
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconWithCdnEnabledReturnsCdnUrl() {
    TierIconConfig cdnConfig = TierIconConfig.builder()
                                   .bucketName("idp-tiers-qa")
                                   .cdnDNS("idp-tiers-qa.qa.harness.io")
                                   .cdnEnabled(true)
                                   .storageType("GCS")
                                   .s3Region("us-west-2")
                                   .build();
    TierGroupServiceImpl cdnService = new TierGroupServiceImpl(tierGroupRepository, tierGroupScorecardUsageChecker,
        tierGroupLockHelper, buildDefaultTierGroupConfig(), cloudStorageUtil, cdnConfig, ENV);
    String storageUrl = "https://storage.cloud.google.com/idp-tiers-qa/static/qa/" + ACCOUNT_ID + "/tiers/gold.png";
    when(cloudStorageUtil.uploadFile(eq("idp-tiers-qa"), anyString(), anyString(), any())).thenReturn(storageUrl);
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();

    String url = cdnService.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(url).isEqualTo("https://idp-tiers-qa.qa.harness.io/static/qa/" + ACCOUNT_ID + "/tiers/gold.png");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveScoreTierReturnsResolvedTier() {
    TierGroupEntity entity = TierGroupEntity.builder()
                                 .identifier("levels")
                                 .tiers(List.of(TierGroupEntity.Tier.builder()
                                                    .name("Level 1")
                                                    .description("Level 1")
                                                    .icon("https://example.com/level1.png")
                                                    .colour("#111111")
                                                    .minScore(0)
                                                    .maxScore(19)
                                                    .build(),
                                     TierGroupEntity.Tier.builder()
                                         .name("Level 2")
                                         .description("Level 2")
                                         .icon("https://example.com/level2.png")
                                         .colour("#222222")
                                         .minScore(20)
                                         .maxScore(100)
                                         .build()))
                                 .build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(ACCOUNT_ID, "levels", false))
        .thenReturn(entity);

    Optional<io.harness.spec.server.idp.v1.model.ScoreTier> scoreTier =
        tierGroupService.resolveScoreTier(ACCOUNT_ID, "levels", 25);

    assertThat(scoreTier).isPresent();
    assertThat(scoreTier.get().getTierName()).isEqualTo("Level 2");
    assertThat(scoreTier.get().getTierGroupIdentifier()).isEqualTo("levels");
    assertThat(scoreTier.get().getTierColour()).isEqualTo("#222222");
    verifyNoInteractions(tierGroupLockHelper);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveScoreTierResolvesBuiltInDefaultTierIconToDisplayUrl() {
    TierGroupEntity entity = TierGroupEntity.builder()
                                 .identifier(DEFAULT_TIER_GROUP_IDENTIFIER)
                                 .tiers(List.of(TierGroupEntity.Tier.builder()
                                                    .name("Bronze")
                                                    .icon("BRONZE")
                                                    .colour("#CD7F32")
                                                    .minScore(0)
                                                    .maxScore(49)
                                                    .build(),
                                     TierGroupEntity.Tier.builder()
                                         .name("Gold")
                                         .icon("GOLD")
                                         .colour("#FFD700")
                                         .minScore(50)
                                         .maxScore(100)
                                         .build()))
                                 .build();
    when(tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
             ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, false))
        .thenReturn(entity);

    Optional<io.harness.spec.server.idp.v1.model.ScoreTier> scoreTier =
        tierGroupService.resolveScoreTier(ACCOUNT_ID, DEFAULT_TIER_GROUP_IDENTIFIER, 75);

    assertThat(scoreTier).isPresent();
    assertThat(scoreTier.get().getTierIcon()).isEqualTo(DefaultTierIcon.GOLD.getDisplayUrl());
    verifyNoInteractions(tierGroupLockHelper);
  }

  private TierGroupDetailsRequest buildRequest(String identifier) {
    TierGroupDetails details =
        new TierGroupDetails()
            .identifier(identifier)
            .name("Compliance Tiers")
            .tiers(List.of(buildTier("Bronze", 0, 49), buildTier("Silver", 50, 74), buildTier("Gold", 75, 100)));
    return new TierGroupDetailsRequest().tierGroup(details);
  }

  private Tier buildTier(String name, int minScore, int maxScore) {
    return new Tier()
        .name(name)
        .description(name + " description")
        .icon("https://example.com/" + name.toLowerCase() + ".png")
        .colour("#000000")
        .minScore(minScore)
        .maxScore(maxScore);
  }

  private DefaultTierGroupConfig buildDefaultTierGroupConfig() {
    return DefaultTierGroupConfig.builder()
        .identifier(DEFAULT_TIER_GROUP_IDENTIFIER)
        .name("Default Tier Group")
        .description("Default score tiers for measuring software delivery performance.")
        .tiers(List.of(DefaultTierGroupConfig.TierConfig.builder()
                           .name("Critical")
                           .description("Score is below recommended standards and needs immediate attention.")
                           .icon(DefaultTierIcon.BRONZE)
                           .colour("#e43326")
                           .minScore(0)
                           .maxScore(49)
                           .build(),
            DefaultTierGroupConfig.TierConfig.builder()
                .name("Warning")
                .description("Score partially meets recommended standards and needs improvement.")
                .icon(DefaultTierIcon.SILVER)
                .colour("#ffaf00")
                .minScore(50)
                .maxScore(74)
                .build(),
            DefaultTierGroupConfig.TierConfig.builder()
                .name("Healthy")
                .description("Score meets or exceeds recommended standards.")
                .icon(DefaultTierIcon.GOLD)
                .colour("#1b841d")
                .minScore(75)
                .maxScore(100)
                .build()))
        .build();
  }
}
