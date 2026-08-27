/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.idp.common.Constants.IMAGE_PATH_PREFIX;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.normalizeIdentifier;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.FileType;
import io.harness.idp.common.FileUtils;
import io.harness.idp.common.IconUtils;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierGroupConfig;
import io.harness.idp.scorecard.tiergroups.config.TierIconConfig;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.mappers.TierGroupDetailsMapper;
import io.harness.idp.scorecard.tiergroups.repositories.TierGroupRepository;
import io.harness.spec.server.idp.v1.model.ScoreTier;
import io.harness.spec.server.idp.v1.model.Tier;
import io.harness.spec.server.idp.v1.model.TierGroupDetails;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.UpdateResult;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class TierGroupServiceImpl implements TierGroupService {
  private final TierGroupRepository tierGroupRepository;
  private final TierGroupScorecardUsageChecker tierGroupScorecardUsageChecker;
  private final TierGroupLockHelper tierGroupLockHelper;
  private final DefaultTierGroupConfig defaultTierGroupConfig;
  private final CloudStorageUtil cloudStorageUtil;
  private final TierIconConfig tierIconConfig;
  private final String env;

  private static final String TIER_ICON_PATH = "tiers";
  private static final String FILE_NAME_SEPARATOR = "_";
  private static final int RANDOM_STRING_LENGTH = 6;
  private static final String SUPPORTED_TIER_ICON_FORMATS = "jpeg, jpg, png, svg";

  @Inject
  public TierGroupServiceImpl(TierGroupRepository tierGroupRepository,
      TierGroupScorecardUsageChecker tierGroupScorecardUsageChecker, TierGroupLockHelper tierGroupLockHelper,
      @Named("defaultTierGroupConfig") DefaultTierGroupConfig defaultTierGroupConfig, CloudStorageUtil cloudStorageUtil,
      @Named("tierIconConfig") TierIconConfig tierIconConfig, @Named("env") String env) {
    this.tierGroupRepository = tierGroupRepository;
    this.tierGroupScorecardUsageChecker = tierGroupScorecardUsageChecker;
    this.tierGroupLockHelper = tierGroupLockHelper;
    this.defaultTierGroupConfig = defaultTierGroupConfig;
    this.cloudStorageUtil = cloudStorageUtil;
    this.tierIconConfig = tierIconConfig;
    this.env = env;
  }

  @Override
  public List<TierGroupEntity> getAllTierGroups(String accountIdentifier) {
    List<TierGroupEntity> tierGroups =
        tierGroupRepository.findByAccountIdentifierAndIsDeleted(accountIdentifier, false);
    if (tierGroups == null || tierGroups.isEmpty()) {
      createDefaultTierGroupIfAbsent(accountIdentifier);
      tierGroups = tierGroupRepository.findByAccountIdentifierAndIsDeleted(accountIdentifier, false);
    }
    return tierGroups;
  }

  @Override
  public void createDefaultTierGroupIfAbsent(String accountIdentifier) {
    tierGroupLockHelper.executeWithTierGroupLock(accountIdentifier, DEFAULT_TIER_GROUP_IDENTIFIER, () -> {
      TierGroupEntity existing = tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
          accountIdentifier, DEFAULT_TIER_GROUP_IDENTIFIER, false);
      if (existing != null) {
        return;
      }
      try {
        tierGroupRepository.save(toDefaultTierGroupEntity(accountIdentifier));
      } catch (DuplicateKeyException e) {
        TierGroupEntity concurrentlyCreated = tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
            accountIdentifier, DEFAULT_TIER_GROUP_IDENTIFIER, false);
        if (concurrentlyCreated == null) {
          throw new IllegalStateException(
              format("Default tier group could not be created for account [%s] because of a conflicting record",
                  accountIdentifier),
              e);
        }
        log.info("Default tier group was concurrently created for account {}", accountIdentifier);
      }
    });
  }

  @Override
  public TierGroupDetailsResponse getTierGroupDetails(String accountIdentifier, String identifier) {
    TierGroupEntity entity = getActiveTierGroup(accountIdentifier, identifier);
    if (entity == null) {
      throw new InvalidRequestException(format("Tier group not found for identifier [%s]", identifier));
    }
    return TierGroupDetailsMapper.toDTO(entity);
  }

  @Override
  public TierGroupDetailsResponse saveTierGroup(TierGroupDetailsRequest request, String accountIdentifier) {
    validateSaveRequest(request);
    String tierGroupIdentifier = normalizeIdentifier(request.getTierGroup().getIdentifier());
    if (DEFAULT_TIER_GROUP_IDENTIFIER.equals(tierGroupIdentifier)) {
      throw new InvalidRequestException(
          format("Tier group identifier '%s' is reserved for system use", DEFAULT_TIER_GROUP_IDENTIFIER));
    }
    return tierGroupLockHelper.executeWithTierGroupLock(accountIdentifier, tierGroupIdentifier, () -> {
      TierGroupEntity existing =
          tierGroupRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, tierGroupIdentifier);
      if (existing != null) {
        throw new InvalidRequestException(existing.isDeleted()
                ? format(
                      "Tier group identifier '%s' was previously used and cannot be reused. Choose a new identifier.",
                      tierGroupIdentifier)
                : format("A tier group with identifier '%s' already exists", tierGroupIdentifier));
      }
      TierGroupEntity saved = tierGroupRepository.save(TierGroupDetailsMapper.fromDTO(request, accountIdentifier));
      return TierGroupDetailsMapper.toDTO(saved);
    });
  }

  @Override
  public TierGroupDetailsResponse updateTierGroup(TierGroupDetailsRequest request, String accountIdentifier) {
    validateSaveRequest(request);
    String tierGroupIdentifier = normalizeIdentifier(request.getTierGroup().getIdentifier());
    return tierGroupLockHelper.executeWithTierGroupLock(accountIdentifier, tierGroupIdentifier, () -> {
      TierGroupEntity existing = tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
          accountIdentifier, tierGroupIdentifier, false);
      if (existing == null) {
        throw new InvalidRequestException(format("Tier group not found for identifier [%s]", tierGroupIdentifier));
      }
      TierGroupEntity updated = tierGroupRepository.update(TierGroupDetailsMapper.fromDTO(request, accountIdentifier));
      return TierGroupDetailsMapper.toDTO(updated);
    });
  }

  @Override
  public void deleteTierGroup(String accountIdentifier, String identifier) {
    String normalizedIdentifier = normalizeIdentifier(identifier);
    if (DEFAULT_TIER_GROUP_IDENTIFIER.equals(normalizedIdentifier)) {
      throw new InvalidRequestException("The default tier group cannot be deleted");
    }
    tierGroupLockHelper.executeWithTierGroupLock(accountIdentifier, normalizedIdentifier, () -> {
      TierGroupEntity existing = tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
          accountIdentifier, normalizedIdentifier, false);
      if (existing == null) {
        throw new InvalidRequestException(format("Tier group not found for identifier [%s]", normalizedIdentifier));
      }
      if (tierGroupScorecardUsageChecker.isReferencedByScorecard(accountIdentifier, normalizedIdentifier)) {
        throw new InvalidRequestException(format(
            "Cannot delete tier group '%s' because it is referenced by one or more scorecards", normalizedIdentifier));
      }
      UpdateResult deleteResult = tierGroupRepository.softDelete(accountIdentifier, normalizedIdentifier);
      if (deleteResult.getModifiedCount() == 0) {
        throw new InvalidRequestException(format("Could not delete tier group [%s]", normalizedIdentifier));
      }
    });
  }

  @Override
  public TierGroupEntity getActiveTierGroup(String accountIdentifier, String identifier) {
    String normalizedIdentifier = normalizeIdentifier(identifier);
    if (StringUtils.isBlank(normalizedIdentifier)) {
      return null;
    }
    return tierGroupRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(
        accountIdentifier, normalizedIdentifier, false);
  }

  @Override
  public void validateTierGroupReference(String accountIdentifier, String tierGroupIdentifier) {
    String normalizedIdentifier = normalizeIdentifier(tierGroupIdentifier);
    if (StringUtils.isBlank(normalizedIdentifier)) {
      throw new InvalidRequestException("Tier group identifier is required");
    }
    TierGroupEntity tierGroup = getActiveTierGroup(accountIdentifier, normalizedIdentifier);
    if (tierGroup == null) {
      throw new InvalidRequestException(
          format("Error while saving scorecard. Could not find tier group %s", normalizedIdentifier));
    }
  }

  @Override
  public Optional<ScoreTier> resolveScoreTier(String accountIdentifier, String tierGroupIdentifier, int score) {
    return resolveScoreTier(getActiveTierGroup(accountIdentifier, tierGroupIdentifier), tierGroupIdentifier, score);
  }

  @Override
  public Optional<ScoreTier> resolveScoreTier(TierGroupEntity tierGroup, String tierGroupIdentifier, int score) {
    if (tierGroup == null) {
      return Optional.empty();
    }
    String normalizedIdentifier = normalizeIdentifier(tierGroupIdentifier);
    return TierResolutionHelper.resolveTier(tierGroup, score)
        .map(tier
            -> new ScoreTier()
                   .tierName(tier.getName())
                   .tierGroupIdentifier(normalizedIdentifier)
                   .tierDescription(tier.getDescription())
                   .tierIcon(TierResolutionHelper.resolveTierIconForDisplay(tier.getIcon()))
                   .tierColour(tier.getColour()));
  }

  @Override
  public String uploadTierIcon(
      String fileType, InputStream fileInputStream, FormDataContentDisposition fileDetail, String harnessAccount) {
    String fileName = validateTierIconUploadRequest(fileType, fileInputStream, fileDetail);
    // No entity id scopes the object, so randomise the name to avoid collisions.
    String iconName = RandomStringUtils.randomAlphanumeric(RANDOM_STRING_LENGTH) + FILE_NAME_SEPARATOR + fileName;
    String iconPath = IMAGE_PATH_PREFIX + FileUtils.PATH_SEPARATOR + env + FileUtils.PATH_SEPARATOR + harnessAccount
        + FileUtils.PATH_SEPARATOR + TIER_ICON_PATH;
    return IconUtils.getPublicUrl(
        cloudStorageUtil.uploadFile(tierIconConfig.getBucketName(), iconPath, iconName, fileInputStream),
        tierIconConfig.getCdnEnabled(), tierIconConfig.getBucketName(), tierIconConfig.getCdnDNS(),
        tierIconConfig.getStorageType(), cloudStorageUtil);
  }

  private String validateTierIconUploadRequest(
      String fileType, InputStream fileInputStream, FormDataContentDisposition fileDetail) {
    if (fileInputStream == null) {
      throw new InvalidRequestException("Tier icon file is required");
    }
    if (fileDetail == null || StringUtils.isBlank(fileDetail.getFileName())) {
      throw new InvalidRequestException("Tier icon file name is required");
    }
    if (StringUtils.isBlank(fileType)) {
      throw new InvalidRequestException("File type is required");
    }
    if (!FileType.ICON.name().equals(StringUtils.upperCase(fileType.trim()))) {
      throw new InvalidRequestException("File type must be ICON for tier icon upload");
    }
    String fileName = fileDetail.getFileName().trim();
    if (fileName.contains(FileUtils.PATH_SEPARATOR) || fileName.contains("\\")) {
      throw new InvalidRequestException("Tier icon file name must not contain path separators");
    }
    String iconExtension = StringUtils.lowerCase(FilenameUtils.getExtension(fileName));
    if (StringUtils.isBlank(iconExtension)) {
      throw new InvalidRequestException(
          format("Tier icon file must have one of the supported formats: %s", SUPPORTED_TIER_ICON_FORMATS));
    }
    if (!FileUtils.isFileFormatSupported(FileType.ICON.name(), iconExtension)) {
      throw new InvalidRequestException(format(
          "Tier icon format '%s' is not supported. Supported formats: %s", iconExtension, SUPPORTED_TIER_ICON_FORMATS));
    }
    // Sanitize filename so the GCS/S3 object key and its public URL contain no unsafe characters.
    return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void validateSaveRequest(TierGroupDetailsRequest request) {
    if (request == null || request.getTierGroup() == null) {
      throw new InvalidRequestException("Tier group details are required");
    }
    TierGroupDetails details = request.getTierGroup();
    if (StringUtils.isBlank(details.getIdentifier())) {
      throw new InvalidRequestException("Tier group identifier cannot be empty");
    }
    if (StringUtils.isBlank(details.getName())) {
      throw new InvalidRequestException("Tier group name cannot be empty");
    }
    TierResolutionHelper.validateDescriptionLength(details.getDescription(), "Tier group description");
    TierResolutionHelper.validateTiers(details.getTiers(), details.getIdentifier().trim());
  }

  private TierGroupEntity toDefaultTierGroupEntity(String accountIdentifier) {
    validateDefaultTierGroupConfig();
    List<TierGroupEntity.Tier> tiers = defaultTierGroupConfig.getTiers()
                                           .stream()
                                           .map(tier
                                               -> TierGroupEntity.Tier.builder()
                                                      .name(tier.getName().trim())
                                                      .description(StringUtils.trimToNull(tier.getDescription()))
                                                      .icon(tier.getIcon().name())
                                                      .colour(tier.getColour().trim())
                                                      .minScore(tier.getMinScore())
                                                      .maxScore(tier.getMaxScore())
                                                      .build())
                                           .toList();
    return TierGroupEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(defaultTierGroupConfig.getIdentifier().trim())
        .name(defaultTierGroupConfig.getName().trim())
        .description(StringUtils.trimToNull(defaultTierGroupConfig.getDescription()))
        .tiers(tiers)
        .build();
  }

  private void validateDefaultTierGroupConfig() {
    if (defaultTierGroupConfig == null) {
      throw new IllegalStateException("Default tier group configuration is required");
    }
    if (!DEFAULT_TIER_GROUP_IDENTIFIER.equals(StringUtils.trim(defaultTierGroupConfig.getIdentifier()))) {
      throw new IllegalStateException(
          format("Default tier group identifier must be '%s'", DEFAULT_TIER_GROUP_IDENTIFIER));
    }
    if (StringUtils.isBlank(defaultTierGroupConfig.getName())) {
      throw new IllegalStateException("Default tier group name is required");
    }
    if (defaultTierGroupConfig.getTiers() == null
        || defaultTierGroupConfig.getTiers().stream().anyMatch(tier -> tier == null)) {
      throw new IllegalStateException("Default tier group tiers are required");
    }

    for (DefaultTierGroupConfig.TierConfig tierConfig : defaultTierGroupConfig.getTiers()) {
      if (tierConfig.getIcon() == null) {
        throw new IllegalStateException("Default tier group tier icon is required");
      }
    }

    List<Tier> tiers = defaultTierGroupConfig.getTiers()
                           .stream()
                           .map(tier
                               -> new Tier()
                                      .name(tier.getName())
                                      .description(tier.getDescription())
                                      .icon(tier.getIcon().name())
                                      .colour(tier.getColour())
                                      .minScore(tier.getMinScore())
                                      .maxScore(tier.getMaxScore()))
                           .toList();
    try {
      TierResolutionHelper.validateDescriptionLength(
          defaultTierGroupConfig.getDescription(), "Default tier group description");
      TierResolutionHelper.validateTiers(tiers, DEFAULT_TIER_GROUP_IDENTIFIER);
    } catch (InvalidRequestException e) {
      throw new IllegalStateException(format("Invalid default tier group configuration: %s", e.getMessage()), e);
    }
  }
}
