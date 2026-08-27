/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.service.impl;

import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.enums.BrandingAssetType;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.outbox.branding.events.BrandingCreateEvent;
import io.harness.branding.outbox.branding.events.BrandingUpdateEvent;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.branding.service.AccountBrandingService;
import io.harness.exception.EntityNotFoundException;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.BrandingRepository;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;
import io.harness.spec.server.ng.v1.model.BrandingResponseDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class AccountBrandingServiceImpl implements AccountBrandingService {
  private final AccountBrandingAssetService accountBrandingAssetService;
  private final BrandingRepository brandingRepository;
  private final BrandingMapper brandingMapper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  @Inject
  public AccountBrandingServiceImpl(AccountBrandingAssetService accountBrandingAssetService,
      BrandingRepository brandingRepository, BrandingMapper brandingMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.accountBrandingAssetService = accountBrandingAssetService;
    this.brandingRepository = brandingRepository;
    this.brandingMapper = brandingMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public BrandingResponseDTO saveBrandingInfo(String accountId, InputStream largeLogoLightInputStream,
      String largeLogoLightExtension, InputStream smallLogoLightInputStream, String smallLogoLightExtension,
      InputStream faviconInputStream, String faviconExtension, InputStream largeLogoDarkInputStream,
      String largeLogoDarkExtension, Boolean brandingOnSignInPage) {
    Map<String, String> errorMap = new HashMap<>();

    Optional<BrandingAsset> largeLogoLightAsset = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        accountId, largeLogoLightInputStream, largeLogoLightExtension, BrandingAssetType.LARGE_LOGO_LIGHT, errorMap);
    Optional<BrandingAsset> smallLogoLightAsset = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        accountId, smallLogoLightInputStream, smallLogoLightExtension, BrandingAssetType.SMALL_LOGO_LIGHT, errorMap);
    Optional<BrandingAsset> faviconAsset = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        accountId, faviconInputStream, faviconExtension, BrandingAssetType.FAVICON, errorMap);
    Optional<BrandingAsset> largeLogoDarkAsset = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        accountId, largeLogoDarkInputStream, largeLogoDarkExtension, BrandingAssetType.LARGE_LOGO_DARK, errorMap);

    List<BrandingAsset> savedAssets =
        Stream.of(largeLogoLightAsset, smallLogoLightAsset, faviconAsset, largeLogoDarkAsset)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

    Branding settings = upsertBrandingSettings(accountId, brandingOnSignInPage);

    Iterable<BrandingAsset> assets = accountBrandingAssetService.saveAllAssets(accountId, savedAssets);

    List<BrandingAssetsDTO> savedBrandingAssetsDTOList = new ArrayList<>();
    for (BrandingAsset asset : assets) {
      savedBrandingAssetsDTOList.add(brandingMapper.toBrandingAssetsDTO(asset));
    }
    List<BrandingAssetsDTO> failedBrandingAssetsDTOList = new ArrayList<>();
    for (Map.Entry<String, String> entry : errorMap.entrySet()) {
      failedBrandingAssetsDTOList.add(new BrandingAssetsDTO().assetType(entry.getKey()).error(entry.getValue()));
    }

    return new BrandingResponseDTO()
        .settings(brandingMapper.toBrandingSettingsDTO(settings))
        .savedAssets(savedBrandingAssetsDTOList)
        .errors(failedBrandingAssetsDTOList);
  }

  @Override
  public BrandingSettingsDTO getBrandingSettings(String harnessAccount) {
    Optional<Branding> branding = brandingRepository.findByAccountIdentifier(harnessAccount);
    if (branding.isEmpty()) {
      log.error("Branding Settings could not be found");
      throw new EntityNotFoundException(
          format("Branding Settings does not exist for the account [%s]", harnessAccount));
    }
    return new BrandingSettingsDTO().brandingOnSignInPage(branding.get().isBrandingOnSignInPage());
  }

  private Branding upsertBrandingSettings(String accountId, boolean brandingOnSignInPage) {
    Optional<Branding> brandingOptional = brandingRepository.findByAccountIdentifier(accountId);
    if (brandingOptional.isEmpty()) {
      Branding newBranding =
          Branding.builder().accountIdentifier(accountId).brandingOnSignInPage(brandingOnSignInPage).build();
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Branding savedBranding = brandingRepository.save(newBranding);
        outboxService.save(new BrandingCreateEvent(accountId, savedBranding));
        return savedBranding;
      }));
    }
    Branding existingBranding = brandingOptional.get();
    Branding updatedBranding = existingBranding.toBuilder().brandingOnSignInPage(brandingOnSignInPage).build();

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Branding savedBranding = brandingRepository.save(updatedBranding);
      outboxService.save(new BrandingUpdateEvent(accountId, savedBranding, existingBranding));
      return savedBranding;
    }));
  }
}