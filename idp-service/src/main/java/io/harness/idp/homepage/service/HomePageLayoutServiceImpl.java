/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.Constants;
import io.harness.idp.common.FileUtils;
import io.harness.idp.common.IconUploadType;
import io.harness.idp.common.IconUtils;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.idp.homepage.events.HomePageLayoutCreateEvent;
import io.harness.idp.homepage.events.HomePageLayoutUpdateEvent;
import io.harness.idp.homepage.mappers.HomePageLayoutMapper;
import io.harness.idp.homepage.repositories.HomePageLayoutRepository;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;
import io.harness.spec.server.idp.v1.model.CustomLinkCard;
import io.harness.spec.server.idp.v1.model.HeaderInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.HomePageLayoutYamlResponse;
import io.harness.spec.server.idp.v1.model.LinksInfo;
import io.harness.spec.server.idp.v1.model.UploadInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class HomePageLayoutServiceImpl implements HomePageLayoutService {
  HomePageLayoutRepository homePageLayoutRepository;
  CardService cardService;
  private TransactionTemplate transactionTemplate;
  private CloudStorageUtil cloudStorageUtil;
  private OutboxService outboxService;
  String env;
  HomePageCardIconConfig homePageCardIconConfig;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private static final String HOME_PAGE_LAYOUT_NOT_FOUND_ERROR = "Home Page Layout not found for account - %s";

  @Inject
  public HomePageLayoutServiceImpl(HomePageLayoutRepository homePageLayoutRepository, CardService cardService,
      TransactionTemplate transactionTemplate, CloudStorageUtil cloudStorageUtil, @Named("env") String env,
      @Named("homePageCardIconConfig") HomePageCardIconConfig homePageCardIconConfig, OutboxService outboxService) {
    this.homePageLayoutRepository = homePageLayoutRepository;
    this.cardService = cardService;
    this.transactionTemplate = transactionTemplate;
    this.cloudStorageUtil = cloudStorageUtil;
    this.env = env;
    this.homePageCardIconConfig = homePageCardIconConfig;
    this.outboxService = outboxService;
  }

  @Override
  public HomePageLayoutResponse getHomePageLayout(String accountIdentifier) {
    Optional<HomePageLayoutEntity> homePageLayoutEntity =
        homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);

    if (!homePageLayoutEntity.isPresent()) {
      throw new NotFoundException(String.format(HOME_PAGE_LAYOUT_NOT_FOUND_ERROR, accountIdentifier));
    }

    HomePageLayoutResponse response = HomePageLayoutMapper.toResponse(homePageLayoutEntity.get(),
        cardService.getAllCardsForIdentifiers(accountIdentifier, homePageLayoutEntity.get().getCards()));
    presignResponseUrls(response);
    return response;
  }

  @Override
  public HomePageLayoutResponse saveHomePageLayout(

      HomePageLayoutRequest homePageLayoutRequest, String accountIdentifier) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      // Read the prior layout inside the transaction so the delete-diff operates on the transaction's snapshot
      // (avoids racing with a concurrent save of the same account's homepage).
      Optional<HomePageLayoutEntity> storedLayoutEntityOptional =
          homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);
      List<CardResponse> oldSavedResponseCards = cardService.getAllActiveCardsForAccount(accountIdentifier);
      List<Card> oldSavedCards = oldSavedResponseCards.stream().map(CardResponse::getCard).collect(Collectors.toList());
      oldSavedCards.sort(Comparator.comparing(Card::getType));

      List<Card> requestCards = homePageLayoutRequest.getHomePageLayout().getCards();
      requestCards = requestCards == null ? List.of() : requestCards;

      // Split the incoming cards: untouched OOTB cards stay lightweight ootb:* references (resolved globally at
      // read time), while customized OOTB cards and user-owned cards become account-scoped rows. The returned
      // identifier list is ordered (drag-and-drop) and de-duplicated, and is what we persist on the layout.
      CardReferenceResolution resolution = cardService.resolveCardReferences(requestCards);
      List<String> cardsIdentifiers = resolution.getOrderedIdentifiers();
      List<Card> accountOwnedCards = resolution.getAccountOwnedCards();

      // Delete only the cards THIS layout previously owned but are no longer present (diff against its own prior
      // card list, not the whole account — otherwise we would clobber cards owned by other persona views sharing
      // the account-scoped cards collection). OOTB cards (ootb:*) are global refs, never account-owned, so they are
      // excluded from deletion. An empty request therefore still clears all of this layout's own cards.
      List<String> priorIdentifiers = storedLayoutEntityOptional.map(HomePageLayoutEntity::getCards).orElse(List.of());
      Set<String> cardsIdentifierSet = new HashSet<>(cardsIdentifiers);
      List<String> toDeleteIdentifiers =
          priorIdentifiers.stream()
              .filter(id -> !cardsIdentifierSet.contains(id) && !Constants.isOotbCardIdentifier(id))
              .collect(Collectors.toList());
      cardService.deleteCardsByIdentifiers(accountIdentifier, toDeleteIdentifiers);

      // Only account-owned cards (user cards + materialized customized OOTB cards) are persisted as account rows;
      // pure ootb refs resolve against the global __GLOBAL_ACCOUNT_ID__ account at read time and are never written
      // under the customer's account.
      List<Card> savedCards = cardService.saveAllCards(accountOwnedCards, accountIdentifier);
      List<Card> sortedCards =
          savedCards.stream().sorted(Comparator.comparing(Card::getType)).collect(Collectors.toList());

      HomePageLayoutEntity toSaveLayoutEntity =
          HomePageLayoutMapper.fromDTO(homePageLayoutRequest.getHomePageLayout(), accountIdentifier, cardsIdentifiers);

      // update case
      storedLayoutEntityOptional.ifPresent(
          homePageLayoutEntity -> toSaveLayoutEntity.setId(homePageLayoutEntity.getId()));

      HomePageLayoutEntity savedHomePageLayoutEntity = homePageLayoutRepository.save(toSaveLayoutEntity);

      if (storedLayoutEntityOptional.isEmpty()) {
        outboxService.save(new HomePageLayoutCreateEvent(
            HomePageLayoutMapper.toResponse(savedHomePageLayoutEntity, sortedCards), accountIdentifier));
      } else {
        outboxService.save(
            new HomePageLayoutUpdateEvent(HomePageLayoutMapper.toResponse(savedHomePageLayoutEntity, sortedCards),
                HomePageLayoutMapper.toResponse(storedLayoutEntityOptional.get(), oldSavedCards), accountIdentifier));
      }

      return HomePageLayoutMapper.toResponse(savedHomePageLayoutEntity, savedCards);
    }));
  }

  @Override
  public HomePageLayoutYamlResponse getHomePageLayoutYaml(String accountIdentifier) {
    Optional<HomePageLayoutEntity> homePageLayoutEntity =
        homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);
    if (!homePageLayoutEntity.isPresent()) {
      throw new NotFoundException(String.format(HOME_PAGE_LAYOUT_NOT_FOUND_ERROR, accountIdentifier));
    }
    List<Card> cards = cardService.getAllCardsForIdentifiers(accountIdentifier, homePageLayoutEntity.get().getCards());

    HomePageLayoutYamlDTO homePageLayoutYamlDTO = HomePageLayoutYamlDTO.builder()
                                                      .headerInfo(homePageLayoutEntity.get().getHeader())
                                                      .bannerInfo(homePageLayoutEntity.get().getBanner())
                                                      .cards(cards)
                                                      .build();
    String yaml = NGYamlUtils.getYamlString(homePageLayoutYamlDTO, objectMapper);
    HomePageLayoutYamlResponse homePageLayoutYamlResponse = new HomePageLayoutYamlResponse();
    homePageLayoutYamlResponse.setYaml(yaml);
    return homePageLayoutYamlResponse;
  }

  @Override
  public String uploadIcon(IconUploadType type, String identifier, String fileType, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, String harnessAccount) {
    String iconExtension = FilenameUtils.getExtension(fileDetail.getFileName());
    if (!iconExtension.isBlank() && !FileUtils.isFileFormatSupported(fileType, iconExtension)) {
      throw new UnsupportedOperationException(
          "Icon format " + iconExtension + " is not supported. . Account " + harnessAccount);
    }

    String iconName = identifier + fileDetail.getFileName();

    return IconUtils.getPublicUrl(cloudStorageUtil.uploadFile(homePageCardIconConfig.getBucketName(),
                                      IconUtils.getIconPath(harnessAccount, type, env), iconName, fileInputStream),
        homePageCardIconConfig.getCdnEnabled(), homePageCardIconConfig.getBucketName(),
        homePageCardIconConfig.getCdnDNS(), homePageCardIconConfig.getStorageType(), cloudStorageUtil);
  }

  @Override
  public void deleteHeaderQuickLinksIcon(String accountIdentifier, String quickLinksIdentifier) {
    Optional<HomePageLayoutEntity> homePageLayoutEntity =
        homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);

    if (!homePageLayoutEntity.isPresent()) {
      throw new NotFoundException(String.format(HOME_PAGE_LAYOUT_NOT_FOUND_ERROR, accountIdentifier));
    }

    HeaderInfo headerInfo = homePageLayoutEntity.get().getHeader();
    String urlToDelete;
    if (!isEmpty(headerInfo.getQuickLinks())) {
      Optional<LinksInfo> linksInfo = headerInfo.getQuickLinks()
                                          .stream()
                                          .filter(quickLink -> quickLink.getIdentifier().equals(quickLinksIdentifier))
                                          .findFirst();
      if (linksInfo.isPresent()) {
        urlToDelete = linksInfo.get().getIcon();
        cloudStorageUtil.deleteFile(IconUtils.getStorageUrl(urlToDelete, homePageCardIconConfig.getCdnEnabled(),
            homePageCardIconConfig.getBucketName(), homePageCardIconConfig.getCdnDNS(),
            homePageCardIconConfig.getStorageType(), homePageCardIconConfig.getS3Region()));
      }
    }
  }

  @Override
  public void deleteCustomCardQuickLinksIcon(
      String accountIdentifier, String cardIdentifier, String quickLinksIdentifier) {
    String urlToDelete = cardService.getCustomCardQuickLinkUrl(accountIdentifier, cardIdentifier, quickLinksIdentifier);
    if (!isEmpty(urlToDelete)) {
      cloudStorageUtil.deleteFile(IconUtils.getStorageUrl(urlToDelete, homePageCardIconConfig.getCdnEnabled(),
          homePageCardIconConfig.getBucketName(), homePageCardIconConfig.getCdnDNS(),
          homePageCardIconConfig.getStorageType(), homePageCardIconConfig.getS3Region()));
    }
  }

  @Override
  public void deleteCardIcon(String accountId, String cardIdentifier) {
    String urlToDelete = cardService.getCardIconUrl(accountId, cardIdentifier);
    if (!isEmpty(urlToDelete)) {
      cloudStorageUtil.deleteFile(IconUtils.getStorageUrl(urlToDelete, homePageCardIconConfig.getCdnEnabled(),
          homePageCardIconConfig.getBucketName(), homePageCardIconConfig.getCdnDNS(),
          homePageCardIconConfig.getStorageType(), homePageCardIconConfig.getS3Region()));
    }
  }

  @Override
  public void deleteHomePageLayoutIcon(String accountIdentifier, String iconUrl) {
    if (!isEmpty(iconUrl) && iconUrl.contains(accountIdentifier)) {
      cloudStorageUtil.deleteFile(IconUtils.getStorageUrl(iconUrl, homePageCardIconConfig.getCdnEnabled(),
          homePageCardIconConfig.getBucketName(), homePageCardIconConfig.getCdnDNS(),
          homePageCardIconConfig.getStorageType(), homePageCardIconConfig.getS3Region()));
    }
  }

  /**
   * Generates fresh presigned URLs for all icon/image fields in the response
   * so that stored S3 URLs (which may have expired presigned tokens) are
   * replaced with valid ones before being returned to the client.
   * No-op when storage type is not S3.
   */
  private void presignResponseUrls(HomePageLayoutResponse response) {
    if (!"S3".equalsIgnoreCase(homePageCardIconConfig.getStorageType())) {
      return;
    }
    HomePageLayoutInfo layout = response.getHomePageLayout();
    if (layout == null) {
      return;
    }

    if (layout.getHeader() != null) {
      presignLinksIcons(layout.getHeader().getQuickLinks());
    }

    BannerInfo banner = layout.getBanner();
    if (banner != null) {
      presignUploadInfo(banner.getImage());
      presignUploadInfo(banner.getVideo());
    }

    if (layout.getCards() != null) {
      for (Card card : layout.getCards()) {
        if (!isEmpty(card.getIconUrl())) {
          card.setIconUrl(cloudStorageUtil.getReadableUrl(card.getIconUrl()));
        }
        if (card instanceof CustomLinkCard) {
          presignLinksIcons(((CustomLinkCard) card).getLinks());
        }
      }
    }
  }

  private void presignLinksIcons(List<LinksInfo> links) {
    if (links == null) {
      return;
    }
    for (LinksInfo link : links) {
      if (!isEmpty(link.getIcon())) {
        link.setIcon(cloudStorageUtil.getReadableUrl(link.getIcon()));
      }
    }
  }

  private void presignUploadInfo(UploadInfo uploadInfo) {
    if (uploadInfo != null && !isEmpty(uploadInfo.getUrl())) {
      uploadInfo.setUrl(cloudStorageUtil.getReadableUrl(uploadInfo.getUrl()));
    }
  }
}
