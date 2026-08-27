/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.homepage.service;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.IconUploadType;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.idp.homepage.repositories.HomePageLayoutRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CustomLinkCard;
import io.harness.spec.server.idp.v1.model.HeaderInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.HomePageLayoutYamlResponse;
import io.harness.spec.server.idp.v1.model.LinksInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(IDP)
public class HomePageLayoutServiceImplTest extends CategoryTest {
  @Mock private CardService cardService;
  @Mock private HomePageLayoutRepository homePageLayoutRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private CloudStorageUtil cloudStorageUtil;
  @Mock private HomePageCardIconConfig homePageCardIconConfig;
  @Mock private OutboxService outboxService;
  String env = "qa";
  HomePageLayoutServiceImpl homePageLayoutServiceImpl;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_HOME_PAGE_LAYOUT_ID = "test-home-page-layout-id";

  private static final String TEST_LINKS_ICON_VALUE = "test-icon-value";
  private static final String TEST_LINKS_ICON_URL = "test-icon-url";
  private static final String TEST_LINKS_IDENTIFIER = "test-name";
  private static final String TEST_LINKS_TITLE = "test-title";

  private static final String TEST_CARD_IDENTIFIER = "test-card-id";
  private static final boolean TEST_DEFAULT_CARD_VALUE = false;
  private static final boolean TEST_DRAFT_VALUE = false;
  private static final String TEST_CARD_TITLE = "card-title";
  private static final String TEST_CARD_IMAGE_URL = "card-image-url";
  private static final Card.TypeEnum TEST_CARD_TYPE = Card.TypeEnum.CUSTOM_LINK;

  private static final String BANNER_TYPE = "image";
  private static final Boolean BANNER_ENABLED = false;

  private static final String TEST_HEADER_TEXT = "test-header-text";
  private static final String HOME_PAGE_LAYOUT_NOT_FOUND_ERROR =
      "Home Page Layout not found for account - test-account-id";
  private static final String TEST_BUCKET_NAME = "test-bucket-name";
  private static final String TEST_CDN_DNS_VALUE = "test-dns-value";

  private static final String TEST_GCS_URL = "https://storage.cloud.google.com/test-bucket-name/static/qa/"
      + "test-account-id/cards/0c1c2ed2-0a39-4e77-8922-8fa74663cfaeharness.png";
  private static final String TEST_CDN_URL =
      "https://test-dns-value/static/qa/test-account-id/cards/0c1c2ed2-0a39-4e77-8922-8fa74663cfaeharness.png";
  private static final String TEST_ICON_PATH = "static/qa/test-account-id/cards";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(homePageCardIconConfig.getCdnEnabled()).thenReturn(true);
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);
    when(homePageCardIconConfig.getCdnDNS()).thenReturn(TEST_CDN_DNS_VALUE);
    when(homePageCardIconConfig.getStorageType()).thenReturn("GCS");
    homePageLayoutServiceImpl = new HomePageLayoutServiceImpl(homePageLayoutRepository, cardService,
        transactionTemplate, cloudStorageUtil, env, homePageCardIconConfig, outboxService);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetHomePageLayout() {
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(getHomePageLayoutEntity()));
    when(cardService.getAllCardsForIdentifiers(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_CARD_IDENTIFIER)))
        .thenReturn(List.of(getCard()));
    HomePageLayoutResponse homePageLayoutResponse =
        homePageLayoutServiceImpl.getHomePageLayout(TEST_ACCOUNT_IDENTIFIER);
    assertNotNull(homePageLayoutResponse);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getCards().get(0).getTitle(), TEST_CARD_TITLE);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getBanner().getType(), BANNER_TYPE);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getHeader().getHeaderText(), TEST_HEADER_TEXT);

    // not found case
    Exception exception = null;
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    try {
      homePageLayoutServiceImpl.getHomePageLayout(TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(HOME_PAGE_LAYOUT_NOT_FOUND_ERROR, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveHomePageLayout() {
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(getHomePageLayoutEntity()));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    when(cardService.resolveCardReferences(List.of(getCard())))
        .thenReturn(new CardReferenceResolution(
            new ArrayList<>(List.of(TEST_CARD_IDENTIFIER)), new ArrayList<>(List.of(getCard()))));
    when(cardService.saveAllCards(List.of(getCard()), TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new ArrayList<>(Arrays.asList(getCard())));
    when(homePageLayoutRepository.save(getHomePageLayoutEntity())).thenReturn(getHomePageLayoutEntity());
    HomePageLayoutRequest homePageLayoutRequest = new HomePageLayoutRequest();
    HomePageLayoutInfo homePageLayoutInfo = new HomePageLayoutInfo();
    homePageLayoutInfo.setHeader(getHeaderInfo());
    homePageLayoutInfo.setBanner(getBannerInfo());
    homePageLayoutInfo.setCards(List.of(getCard()));
    homePageLayoutRequest.setHomePageLayout(homePageLayoutInfo);

    HomePageLayoutResponse homePageLayoutResponse =
        homePageLayoutServiceImpl.saveHomePageLayout(homePageLayoutRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getCards().get(0).getTitle(), TEST_CARD_TITLE);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getBanner().getType(), BANNER_TYPE);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getHeader().getHeaderText(), TEST_HEADER_TEXT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHomePageLayoutYaml() {
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(getHomePageLayoutEntity()));
    when(cardService.getAllCardsForIdentifiers(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_CARD_IDENTIFIER)))
        .thenReturn(List.of(getCard()));
    HomePageLayoutYamlResponse homePageLayoutYamlResponse =
        homePageLayoutServiceImpl.getHomePageLayoutYaml(TEST_ACCOUNT_IDENTIFIER);
    assertNotNull(homePageLayoutYamlResponse);
    assertTrue(homePageLayoutYamlResponse.getYaml().contains(TEST_CARD_IDENTIFIER));
    assertTrue(homePageLayoutYamlResponse.getYaml().contains(TEST_HEADER_TEXT));
    assertTrue(homePageLayoutYamlResponse.getYaml().contains(TEST_CARD_TITLE));

    // not found case
    Exception exception = null;
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    try {
      homePageLayoutServiceImpl.getHomePageLayoutYaml(TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(HOME_PAGE_LAYOUT_NOT_FOUND_ERROR, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUploadIcon() throws FileNotFoundException {
    File file = new File("idp-service/src/test/resources/images/harness.png");
    FileInputStream fileStream = new FileInputStream(file);
    FormDataContentDisposition disposition = FormDataContentDisposition.name("file")
                                                 .fileName(URLEncoder.encode("harness.png", StandardCharsets.UTF_8))
                                                 .build();
    when(cloudStorageUtil.uploadFile(any(), any(), any(), any())).thenReturn(TEST_GCS_URL);
    String cdnUrl = homePageLayoutServiceImpl.uploadIcon(
        IconUploadType.cards, "test", "ICON", fileStream, disposition, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_CDN_URL, cdnUrl);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteHeaderQuickLinksIcon() {
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(getHomePageLayoutEntity()));
    doNothing().when(cloudStorageUtil).deleteFile(any());
    homePageLayoutServiceImpl.deleteHeaderQuickLinksIcon(TEST_ACCOUNT_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    verify(cloudStorageUtil, times(1)).deleteFile(TEST_LINKS_ICON_VALUE);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteCustomCardQuickLinksIcon() {
    when(cardService.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER))
        .thenReturn(TEST_GCS_URL);
    doNothing().when(cloudStorageUtil).deleteFile(any());
    homePageLayoutServiceImpl.deleteCustomCardQuickLinksIcon(
        TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    verify(cloudStorageUtil, times(1)).deleteFile(TEST_GCS_URL);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteCardIcon() {
    when(cardService.getCardIconUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER)).thenReturn(TEST_GCS_URL);
    doNothing().when(cloudStorageUtil).deleteFile(any());
    homePageLayoutServiceImpl.deleteCardIcon(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER);
    verify(cloudStorageUtil, times(1)).deleteFile(TEST_GCS_URL);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteHomePageLayoutIcon() {
    doNothing().when(cloudStorageUtil).deleteFile(any());
    homePageLayoutServiceImpl.deleteHomePageLayoutIcon(TEST_ACCOUNT_IDENTIFIER, TEST_GCS_URL);
    verify(cloudStorageUtil, times(1)).deleteFile(TEST_GCS_URL);
  }

  private CustomLinkCard getCard() {
    CustomLinkCard card = new CustomLinkCard();
    card.setDefaultCard(TEST_DEFAULT_CARD_VALUE);
    card.setDraft(TEST_DRAFT_VALUE);
    card.setIdentifier(TEST_CARD_IDENTIFIER);
    card.setIdentifier(TEST_CARD_IDENTIFIER);
    card.setType(TEST_CARD_TYPE);
    card.setTitle(TEST_CARD_TITLE);
    card.setIconUrl(TEST_CARD_IMAGE_URL);
    card.setLinks(List.of(getLinksInfo()));
    return card;
  }

  private HomePageLayoutEntity getHomePageLayoutEntity() {
    return HomePageLayoutEntity.builder()
        .header(getHeaderInfo())
        .banner(getBannerInfo())
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .cards(new ArrayList<>(List.of(TEST_CARD_IDENTIFIER)))
        .id(TEST_HOME_PAGE_LAYOUT_ID)
        .build();
  }

  private HeaderInfo getHeaderInfo() {
    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(TEST_HEADER_TEXT);
    headerInfo.setQuickLinks(new ArrayList<>(List.of(getLinksInfo())));
    headerInfo.setQuickLinks(new ArrayList<>(List.of(getLinksInfo())));
    return headerInfo;
  }

  private BannerInfo getBannerInfo() {
    BannerInfo bannerInfo = new BannerInfo();
    bannerInfo.setType(BANNER_TYPE);
    bannerInfo.setBannerEnabled(BANNER_ENABLED);
    return bannerInfo;
  }

  private LinksInfo getLinksInfo() {
    LinksInfo linksInfo = new LinksInfo();
    linksInfo.setTitle(TEST_LINKS_TITLE);
    linksInfo.setUrl(TEST_LINKS_ICON_URL);
    linksInfo.setIcon(TEST_LINKS_ICON_VALUE);
    linksInfo.setIdentifier(TEST_LINKS_IDENTIFIER);
    return linksInfo;
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveHomePageLayoutForCreateScenario() {
    // Test when no existing layout is found (create scenario)
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    when(cardService.resolveCardReferences(List.of(getCard())))
        .thenReturn(new CardReferenceResolution(
            new ArrayList<>(List.of(TEST_CARD_IDENTIFIER)), new ArrayList<>(List.of(getCard()))));
    when(cardService.saveAllCards(List.of(getCard()), TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new ArrayList<>(Arrays.asList(getCard())));
    when(homePageLayoutRepository.save(any())).thenReturn(getHomePageLayoutEntity());

    HomePageLayoutRequest homePageLayoutRequest = new HomePageLayoutRequest();
    HomePageLayoutInfo homePageLayoutInfo = new HomePageLayoutInfo();
    homePageLayoutInfo.setHeader(getHeaderInfo());
    homePageLayoutInfo.setBanner(getBannerInfo());
    homePageLayoutInfo.setCards(List.of(getCard()));
    homePageLayoutRequest.setHomePageLayout(homePageLayoutInfo);

    HomePageLayoutResponse homePageLayoutResponse =
        homePageLayoutServiceImpl.saveHomePageLayout(homePageLayoutRequest, TEST_ACCOUNT_IDENTIFIER);
    assertNotNull(homePageLayoutResponse);
    assertEquals(homePageLayoutResponse.getHomePageLayout().getCards().get(0).getTitle(), TEST_CARD_TITLE);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteHeaderQuickLinksIconWithNullQuickLinks() {
    HomePageLayoutEntity entity = getHomePageLayoutEntity();
    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(TEST_HEADER_TEXT);
    headerInfo.setQuickLinks(null);
    entity.setHeader(headerInfo);

    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(entity));
    homePageLayoutServiceImpl.deleteHeaderQuickLinksIcon(TEST_ACCOUNT_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    // Should not throw exception and should not call deleteFile
    verify(cloudStorageUtil, times(0)).deleteFile(any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteHeaderQuickLinksIconWithNoMatchingIdentifier() {
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(getHomePageLayoutEntity()));
    homePageLayoutServiceImpl.deleteHeaderQuickLinksIcon(TEST_ACCOUNT_IDENTIFIER, "non-matching-id");
    // Should not call deleteFile when no matching quick link is found
    verify(cloudStorageUtil, times(0)).deleteFile(any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteCustomCardQuickLinksIconWithNullUrl() {
    when(cardService.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER))
        .thenReturn(null);
    homePageLayoutServiceImpl.deleteCustomCardQuickLinksIcon(
        TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    // Should not call deleteFile when URL is null
    verify(cloudStorageUtil, times(0)).deleteFile(any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteCardIconWithNullUrl() {
    when(cardService.getCardIconUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER)).thenReturn(null);
    homePageLayoutServiceImpl.deleteCardIcon(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER);
    // Should not call deleteFile when URL is null
    verify(cloudStorageUtil, times(0)).deleteFile(any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteHomePageLayoutIconWithNullUrl() {
    homePageLayoutServiceImpl.deleteHomePageLayoutIcon(TEST_ACCOUNT_IDENTIFIER, null);
    // Should not call deleteFile when URL is null
    verify(cloudStorageUtil, times(0)).deleteFile(any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetHomePageLayoutWithEmptyCards() {
    HomePageLayoutEntity entity = getHomePageLayoutEntity();
    entity.setCards(new ArrayList<>());
    when(homePageLayoutRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(entity));
    when(cardService.getAllCardsForIdentifiers(TEST_ACCOUNT_IDENTIFIER, List.of())).thenReturn(List.of());
    HomePageLayoutResponse homePageLayoutResponse =
        homePageLayoutServiceImpl.getHomePageLayout(TEST_ACCOUNT_IDENTIFIER);
    assertNotNull(homePageLayoutResponse);
    assertEquals(0, homePageLayoutResponse.getHomePageLayout().getCards().size());
  }
}
