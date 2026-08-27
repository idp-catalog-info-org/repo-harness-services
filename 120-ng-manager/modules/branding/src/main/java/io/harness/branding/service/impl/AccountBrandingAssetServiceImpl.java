/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.service.impl;

import static io.harness.delegate.beans.FileBucket.BRANDING_ASSETS;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;

import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.enums.BrandingAssetType;
import io.harness.branding.enums.MimeType;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetDeleteEvent;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetUploadEvent;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.branding.validation.AssetValidationResult;
import io.harness.branding.validation.AssetValidator;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.file.beans.NGBaseFile;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.BrandingAssetsRepository;

import software.wings.service.intfc.FileService;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class AccountBrandingAssetServiceImpl implements AccountBrandingAssetService {
  private final FileService fileService;
  private final AssetValidator assetValidator;
  private final BrandingAssetsRepository assetsRepository;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  @Inject
  public AccountBrandingAssetServiceImpl(FileService fileService, AssetValidator assetValidator,
      BrandingAssetsRepository assetsRepository,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.fileService = fileService;
    this.assetValidator = assetValidator;
    this.assetsRepository = assetsRepository;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public BrandingAsset getBrandingAsset(String accountIdentifier, String type) {
    Optional<BrandingAsset> brandingAssetOptional =
        assetsRepository.findByAccountIdentifierAndAssetType(accountIdentifier, type);
    if (brandingAssetOptional.isEmpty()) {
      log.error("Branding Asset with type [{}] could not be found", type);
      throw new EntityNotFoundException(
          format("Branding Asset with type [%s] does not exist for the account [%s]", type, accountIdentifier));
    }
    return brandingAssetOptional.get();
  }

  @Override
  public void deleteBrandingAsset(String accountIdentifier, String type) {
    Optional<BrandingAsset> brandingAssetOptional =
        assetsRepository.findByAccountIdentifierAndAssetType(accountIdentifier, type);
    if (brandingAssetOptional.isEmpty()) {
      log.error("Branding Asset with type [{}] could not be deleted as it does not exist", type);
      throw new EntityNotFoundException(
          format("Branding Asset with type [%s] does not exist for the account [%s]", type, accountIdentifier));
    }
    try {
      BrandingAsset brandingAsset = brandingAssetOptional.get();

      Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        fileService.deleteFile(brandingAsset.getAssetId(), BRANDING_ASSETS);
        assetsRepository.delete(brandingAsset);
        outboxService.save(new BrandingAssetDeleteEvent(accountIdentifier, brandingAsset));
        return null;
      }));
    } catch (Exception e) {
      String errorMessage =
          String.format("Error occurred while deleting asset [%s] for accountId [%s]", type, accountIdentifier);
      log.error(errorMessage, e);
      throw new InternalServerErrorException(errorMessage);
    }
  }

  @Override
  public Optional<BrandingAsset> prepareAndUploadBrandingAsset(String accountIdentifier, InputStream inputStream,
      String extension, BrandingAssetType assetType, Map<String, String> errorMap) {
    byte[] fileData = null;
    if (inputStream != null) {
      try {
        fileData = ByteStreams.toByteArray(inputStream);
      } catch (IOException e) {
        errorMap.put(assetType.getAssetName(), "Failed to read input stream: " + e.getMessage());
        return Optional.empty();
      }
    }
    AssetValidationResult result = assetValidator.validateAsset(fileData, extension, assetType);
    if (!result.isValid()) {
      errorMap.put(assetType.getAssetName(), result.getErrorMessage());
      return Optional.empty();
    }

    // If no file to process (both stream and mimeType are null), skip saving
    if (fileData == null) {
      return Optional.empty();
    }

    Optional<BrandingAsset> brandingAssetOptional =
        assetsRepository.findByAccountIdentifierAndAssetType(accountIdentifier, assetType.getAssetName());

    InputStream fileStream = new ByteArrayInputStream(fileData);
    String fileId = createFile(fileStream, extension, assetType, accountIdentifier);

    if (brandingAssetOptional.isPresent()) {
      BrandingAsset brandingAsset = brandingAssetOptional.get();
      fileService.deleteFile(brandingAsset.getAssetId(), BRANDING_ASSETS);
      brandingAsset.setAssetId(fileId);
      return Optional.of(brandingAsset);
    }

    return Optional.of(BrandingAsset.builder()
                           .accountIdentifier(accountIdentifier)
                           .assetType(assetType.getAssetName())
                           .assetId(fileId)
                           .mimeType(MimeType.fromExtension(extension).getType())
                           .build());
  }

  @Override
  public Iterable<BrandingAsset> saveAllAssets(String accountIdentifier, List<BrandingAsset> brandingAssetList) {
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Iterable<BrandingAsset> savedBrandingAssets = assetsRepository.saveAll(brandingAssetList);
      savedBrandingAssets.forEach(savedBrandingAsset -> {
        outboxService.save(new BrandingAssetUploadEvent(accountIdentifier, savedBrandingAsset));
      });
      return savedBrandingAssets;
    }));
  }

  private String createFile(InputStream inputStream, String extension, BrandingAssetType assetType, String accountId) {
    NGBaseFile baseFile = new NGBaseFile();
    String fileName = assetType.getAssetName() + "." + extension;
    baseFile.setFileName(fileName);
    baseFile.setAccountId(accountId);
    baseFile.setFileUuid(UUIDGenerator.generateUuid());

    return fileService.saveFile(baseFile, inputStream, BRANDING_ASSETS);
  }
}
