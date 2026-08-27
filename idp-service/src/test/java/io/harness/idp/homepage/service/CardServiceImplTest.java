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
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.homepage.entities.CardEntity;
import io.harness.idp.homepage.entities.CustomLinkCardEntity;
import io.harness.idp.homepage.entities.IncidentsCardEntity;
import io.harness.idp.homepage.repositories.CardRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;
import io.harness.spec.server.idp.v1.model.CustomLinkCard;
import io.harness.spec.server.idp.v1.model.IncidentsCard;
import io.harness.spec.server.idp.v1.model.LinksInfo;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(IDP)
public class CardServiceImplTest extends CategoryTest {
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private CardRepository cardRepository;
  @Inject
  private Map<Card.TypeEnum, CardEntity.CardMapper> mapper =
      Map.of(Card.TypeEnum.CUSTOM_LINK, new CustomLinkCardEntity.CustomLinkCardMapper(), Card.TypeEnum.INCIDENTS,
          new IncidentsCardEntity.IncidentsCardMapper());

  CardServiceImpl cardServiceImpl;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";

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
  private static final String DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE =
      "Card with identifier - test-card-id already exists";
  private static final String CARD_NOT_FOUND_ERROR_MESSAGE = "Card with identifier - test-card-id not found";
  private static final String TEST_MONGO_ID_VALUE = "test-mongo-id";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cardServiceImpl = new CardServiceImpl(cardRepository, mapper, transactionTemplate);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllActiveCardsForAccount() {
    when(cardRepository.findAllByAccountIdentifierAndIsDraft(TEST_ACCOUNT_IDENTIFIER, false))
        .thenReturn(List.of(getCardEntity()));
    List<CardResponse> cardResponses = cardServiceImpl.getAllActiveCardsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, cardResponses.size());
    assertEquals(cardResponses.get(0).getCard().getIdentifier(), TEST_CARD_IDENTIFIER);
    assertEquals(cardResponses.get(0).getCard().getTitle(), TEST_CARD_TITLE);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllCardsForIdentifiers() {
    when(cardRepository.findByAccountIdentifierAndIdentifierIn(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_CARD_IDENTIFIER)))
        .thenReturn(List.of(getCardEntity()));
    List<Card> cards =
        cardServiceImpl.getAllCardsForIdentifiers(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_CARD_IDENTIFIER));
    assertEquals(1, cards.size());
    assertEquals(TEST_CARD_IDENTIFIER, cards.get(0).getIdentifier());
    assertEquals(TEST_CARD_TITLE, cards.get(0).getTitle());
    assertEquals(TEST_CARD_IMAGE_URL, cards.get(0).getIconUrl());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveAllCards() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    String updateCardIdentifier = "Update" + TEST_CARD_IDENTIFIER;
    CardEntity updateCardEntity = getCardEntity();
    updateCardEntity.setId(TEST_MONGO_ID_VALUE);
    updateCardEntity.setIdentifier(updateCardIdentifier);

    Card toUpdateCard = getCard();
    toUpdateCard.setIdentifier(updateCardIdentifier);

    // Existing entity is resolved for the incoming identifiers so its _id is reused on update.
    when(cardRepository.findByAccountIdentifierAndIdentifierIn(any(), any())).thenReturn(List.of(updateCardEntity));
    when(cardRepository.saveAll(any()))
        .thenReturn(new ArrayList<>(List.of(updateCardEntity)))
        .thenReturn(new ArrayList<>(List.of(getCardEntity())));

    List<Card> cards = cardServiceImpl.saveAllCards(List.of(toUpdateCard, getCard()), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(2, cards.size());
    assertEquals(updateCardIdentifier, cards.get(0).getIdentifier());
    assertEquals(TEST_CARD_IDENTIFIER, cards.get(1).getIdentifier());

    // saveAllCards is a pure upsert and must never delete.
    verify(cardRepository, never()).deleteByAccountIdentifierAndIdentifierIn(any(), any());

    // Duplicate Exception case
    when(cardRepository.saveAll(any()))
        .thenReturn(new ArrayList<>(List.of(updateCardEntity)))
        .thenThrow(new DuplicateKeyException("dup key : test-card-id"));
    Exception exception = null;
    try {
      cardServiceImpl.saveAllCards(List.of(toUpdateCard, getCard()), TEST_ACCOUNT_IDENTIFIER);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomCardQuickLinkUrl() {
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.of(getCardEntity()));
    String iconUrl =
        cardServiceImpl.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    assertEquals(TEST_LINKS_ICON_VALUE, iconUrl);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCardIconUrl() {
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.of(getCardEntity()));
    String iconUrl = cardServiceImpl.getCardIconUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER);
    assertEquals(TEST_CARD_IMAGE_URL, iconUrl);
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

  private CardEntity getCardEntity() {
    return mapper.get(Card.TypeEnum.CUSTOM_LINK).fromDto(getCard(), TEST_ACCOUNT_IDENTIFIER);
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
  public void testGetAllCardsForIdentifiersWithEmptyList() {
    when(cardRepository.findByAccountIdentifierAndIdentifierIn(TEST_ACCOUNT_IDENTIFIER, List.of()))
        .thenReturn(new ArrayList<>());
    List<Card> cards = cardServiceImpl.getAllCardsForIdentifiers(TEST_ACCOUNT_IDENTIFIER, List.of());
    assertEquals(0, cards.size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetCustomCardQuickLinkUrlWithNoCard() {
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.empty());
    String iconUrl =
        cardServiceImpl.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    assertEquals(null, iconUrl);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetCustomCardQuickLinkUrlWithEmptyLinks() {
    CustomLinkCardEntity cardEntity = (CustomLinkCardEntity) getCardEntity();
    cardEntity.setLinks(new ArrayList<>());
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.of(cardEntity));
    String iconUrl =
        cardServiceImpl.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, TEST_LINKS_IDENTIFIER);
    assertEquals(null, iconUrl);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetCustomCardQuickLinkUrlWithNonMatchingIdentifier() {
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.of(getCardEntity()));
    String iconUrl =
        cardServiceImpl.getCustomCardQuickLinkUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER, "non-matching-id");
    assertEquals(null, iconUrl);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetCardIconUrlWithNoCard() {
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.empty());
    String iconUrl = cardServiceImpl.getCardIconUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER);
    assertEquals(null, iconUrl);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetCardIconUrlWithEmptyIconUrl() {
    CustomLinkCardEntity cardEntity = (CustomLinkCardEntity) getCardEntity();
    cardEntity.setIconUrl("");
    when(cardRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER))
        .thenReturn(Optional.of(cardEntity));
    String iconUrl = cardServiceImpl.getCardIconUrl(TEST_ACCOUNT_IDENTIFIER, TEST_CARD_IDENTIFIER);
    assertEquals(null, iconUrl);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetAllActiveCardsForAccountWithEmptyList() {
    when(cardRepository.findAllByAccountIdentifierAndIsDraft(TEST_ACCOUNT_IDENTIFIER, false))
        .thenReturn(new ArrayList<>());
    List<CardResponse> cardResponses = cardServiceImpl.getAllActiveCardsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(0, cardResponses.size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveAllCardsInsertsNewAndNeverDeletes() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    // No existing entity for the incoming identifier -> insert path; saveAllCards must not delete anything.
    when(cardRepository.findByAccountIdentifierAndIdentifierIn(any(), any())).thenReturn(new ArrayList<>());
    when(cardRepository.saveAll(any()))
        .thenReturn(new ArrayList<>())
        .thenReturn(new ArrayList<>(List.of(getCardEntity())));

    List<Card> cards = cardServiceImpl.saveAllCards(List.of(getCard()), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(1, cards.size());
    assertEquals(TEST_CARD_IDENTIFIER, cards.get(0).getIdentifier());
    verify(cardRepository, never()).deleteByAccountIdentifierAndIdentifierIn(any(), any());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveAllCardsWithEmptyListIsNoOp() {
    List<Card> cards = cardServiceImpl.saveAllCards(new ArrayList<>(), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(0, cards.size());
    verify(cardRepository, never()).saveAll(any());
    verify(cardRepository, never()).deleteByAccountIdentifierAndIdentifierIn(any(), any());
  }

  private static final String OOTB_INCIDENTS_ID = "ootb:incidents";
  private static final String OOTB_CARD_TITLE = "Incidents";
  private static final String CATALOG_SIZE = "medium";
  private static final String CUSTOM_SIZE = "large";

  private void stubOotbCatalog() {
    when(cardRepository.findAllByAccountIdentifierAndIsDraft(Constants.GLOBAL_ACCOUNT_ID, false))
        .thenReturn(List.of(getOotbIncidentsCatalogEntity()));
  }

  private IncidentsCardEntity getOotbIncidentsCatalogEntity() {
    IncidentsCardEntity entity = IncidentsCardEntity.builder().size(CATALOG_SIZE).build();
    entity.setIdentifier(OOTB_INCIDENTS_ID);
    entity.setTitle(OOTB_CARD_TITLE);
    entity.setIsDefault(true);
    entity.setIsDraft(false);
    entity.setAccountIdentifier(Constants.GLOBAL_ACCOUNT_ID);
    return entity;
  }

  private IncidentsCard getIncidentsCard(String identifier, String size) {
    IncidentsCard card = new IncidentsCard();
    card.setIdentifier(identifier);
    card.setType(Card.TypeEnum.INCIDENTS);
    card.setTitle(OOTB_CARD_TITLE);
    card.setDefaultCard(true);
    card.setDraft(false);
    card.setSize(size);
    return card;
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResolveCardReferencesKeepsUntouchedOotbAsReference() {
    stubOotbCatalog();
    IncidentsCard incoming = getIncidentsCard(OOTB_INCIDENTS_ID, CATALOG_SIZE);

    CardReferenceResolution resolution = cardServiceImpl.resolveCardReferences(new ArrayList<>(List.of(incoming)));

    // Card unchanged from the catalog -> stays a lightweight ootb ref, no account row.
    assertEquals(1, resolution.getOrderedIdentifiers().size());
    assertEquals(OOTB_INCIDENTS_ID, resolution.getOrderedIdentifiers().get(0));
    assertEquals(0, resolution.getAccountOwnedCards().size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResolveCardReferencesMintsAccountCardForCustomizedOotb() {
    stubOotbCatalog();
    IncidentsCard incoming = getIncidentsCard(OOTB_INCIDENTS_ID, CUSTOM_SIZE);

    CardReferenceResolution resolution = cardServiceImpl.resolveCardReferences(new ArrayList<>(List.of(incoming)));

    // Customized size -> materialized as an account-owned card with a fresh (non-ootb) identifier.
    assertEquals(1, resolution.getAccountOwnedCards().size());
    String mintedId = resolution.getAccountOwnedCards().get(0).getIdentifier();
    assertFalse(Constants.isOotbCardIdentifier(mintedId));
    assertEquals(1, resolution.getOrderedIdentifiers().size());
    assertEquals(mintedId, resolution.getOrderedIdentifiers().get(0));
    assertEquals(CUSTOM_SIZE, ((IncidentsCard) resolution.getAccountOwnedCards().get(0)).getSize());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResolveCardReferencesMintsIdentifierForNewUserCardInCustomView() {
    when(cardRepository.findAllByAccountIdentifierAndIsDraft(Constants.GLOBAL_ACCOUNT_ID, false))
        .thenReturn(new ArrayList<>());
    CustomLinkCard incoming = getCard();
    incoming.setIdentifier(null);

    CardReferenceResolution resolution = cardServiceImpl.resolveCardReferences(new ArrayList<>(List.of(incoming)));

    assertEquals(1, resolution.getAccountOwnedCards().size());
    assertNotNull(resolution.getAccountOwnedCards().get(0).getIdentifier());
    assertEquals(1, resolution.getOrderedIdentifiers().size());
    assertTrue(!Constants.isOotbCardIdentifier(resolution.getOrderedIdentifiers().get(0)));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResolveCardReferencesIsStableForAlreadyMaterializedOotbCard() {
    // Simulates the second save: the UI has adopted the minted account identifier returned by the first GET and
    // sends the customized card back with that (non-ootb) identifier. It must stay the SAME account card -> no new
    // identifier is minted, no churn, and it is not collapsed into an ootb ref (decision 2.1a).
    stubOotbCatalog();
    String materializedId = "11111111-2222-3333-4444-555555555555";
    IncidentsCard incoming = getIncidentsCard(materializedId, CUSTOM_SIZE);

    CardReferenceResolution resolution = cardServiceImpl.resolveCardReferences(new ArrayList<>(List.of(incoming)));

    assertEquals(1, resolution.getOrderedIdentifiers().size());
    assertEquals(materializedId, resolution.getOrderedIdentifiers().get(0));
    assertEquals(1, resolution.getAccountOwnedCards().size());
    assertEquals(materializedId, resolution.getAccountOwnedCards().get(0).getIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResolveCardReferencesKeepsMaterializedOotbCardEvenWhenRevertedToDefault() {
    // Decision 2.1a: once materialized as an account card, reverting attributes back to the catalog default does
    // NOT convert it back to an ootb ref; it stays an account-owned card under its identifier.
    stubOotbCatalog();
    String materializedId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    IncidentsCard incoming = getIncidentsCard(materializedId, CATALOG_SIZE);

    CardReferenceResolution resolution = cardServiceImpl.resolveCardReferences(new ArrayList<>(List.of(incoming)));

    assertEquals(1, resolution.getOrderedIdentifiers().size());
    assertEquals(materializedId, resolution.getOrderedIdentifiers().get(0));
    assertEquals(1, resolution.getAccountOwnedCards().size());
    assertFalse(Constants.isOotbCardIdentifier(resolution.getOrderedIdentifiers().get(0)));
  }
}
