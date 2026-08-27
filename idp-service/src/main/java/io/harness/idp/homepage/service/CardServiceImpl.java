/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.homepage.entities.CardEntity;
import io.harness.idp.homepage.entities.CardEntity.CardMapper;
import io.harness.idp.homepage.entities.CustomLinkCardEntity;
import io.harness.idp.homepage.repositories.CardRepository;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;
import io.harness.spec.server.idp.v1.model.LinksInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class CardServiceImpl implements CardService {
  CardRepository cardRepository;
  private final Map<Card.TypeEnum, CardMapper> cardMap;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private static final String DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE = "Card with identifier - %s already exists";
  private static final String UNRESOLVABLE_OOTB_CARD_ERROR =
      "OOTB card identifier '%s' does not exist; it must be seeded under the global Harness account before it can be "
      + "referenced";
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public CardServiceImpl(
      CardRepository cardRepository, Map<Card.TypeEnum, CardMapper> cardMap, TransactionTemplate transactionTemplate) {
    this.cardRepository = cardRepository;
    this.cardMap = cardMap;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public List<CardResponse> getAllActiveCardsForAccount(String accountId) {
    List<CardEntity> cardEntities = cardRepository.findAllByAccountIdentifierAndIsDraft(accountId, false);

    /* Not throwing exception if list is empty as this is used in service layer of Home page to fetch the cards.
     * So for any home page if there is no card which can be possible we will get an exception */

    List<Card> cards = new ArrayList<>();
    for (CardEntity cardEntity : cardEntities) {
      cards.add(getDTO(cardEntity));
    }
    return CardMapper.toResponseList(cards);
  }

  @Override
  public List<Card> getAllCardsForIdentifiers(String accountId, List<String> cardIdentifiers) {
    if (isEmpty(cardIdentifiers)) {
      return new ArrayList<>();
    }
    List<CardEntity> cardEntities = cardRepository.findByAccountIdentifierAndIdentifierIn(accountId, cardIdentifiers);
    return materializeInOrder(cardIdentifiers, cardEntities);
  }

  @Override
  public List<Card> getCardsByIdentifiers(List<String> accountIds, List<String> cardIdentifiers) {
    if (isEmpty(accountIds) || isEmpty(cardIdentifiers)) {
      return new ArrayList<>();
    }
    List<CardEntity> cardEntities =
        cardRepository.findByAccountIdentifierInAndIdentifierIn(accountIds, cardIdentifiers);
    return materializeInOrder(cardIdentifiers, cardEntities);
  }

  /**
   * Pure upsert: persist exactly the cards handed in and nothing more. Existing rows (matched by
   * {@code (accountIdentifier, identifier)}) are updated in place by reusing their Mongo {@code _id}; the rest are
   * inserted. This method intentionally performs NO deletes — deciding which cards to remove requires knowledge of
   * a specific view's prior card list, which only the caller has. Callers diff their own previous identifiers
   * against the new request and call {@link #deleteCardsByIdentifiers(String, List)} explicitly.
   */
  @Override
  public List<Card> saveAllCards(List<Card> cards, String accountId) {
    List<Card> incomingCards = isEmpty(cards) ? List.of() : cards;
    if (incomingCards.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> incomingIdentifiers = incomingCards.stream().map(Card::getIdentifier).collect(Collectors.toList());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      // Resolve current entities inside the transaction (only for the incoming identifiers) so we can preserve
      // their Mongo _id on update and narrow the read-then-write window under concurrent upserts.
      List<CardEntity> existingEntities =
          cardRepository.findByAccountIdentifierAndIdentifierIn(accountId, incomingIdentifiers);
      Map<String, String> identifierAndMongoIdMapping =
          existingEntities.stream().collect(Collectors.toMap(CardEntity::getIdentifier, CardEntity::getId));

      List<CardEntity> toSaveCardEntities = new ArrayList<>();
      List<CardEntity> toUpdateCardEntities = new ArrayList<>();
      for (Card card : incomingCards) {
        CardMapper cardMapper = getCardMapper(card.getType());
        CardEntity cardEntityToSave = cardMapper.fromDto(card, accountId);
        if (identifierAndMongoIdMapping.containsKey(cardEntityToSave.getIdentifier())) {
          cardEntityToSave.setId(identifierAndMongoIdMapping.get(cardEntityToSave.getIdentifier()));
          toUpdateCardEntities.add(cardEntityToSave);
        } else {
          toSaveCardEntities.add(cardEntityToSave);
        }
      }

      List<CardEntity> updatedEntities = (List<CardEntity>) cardRepository.saveAll(toUpdateCardEntities);
      try {
        updatedEntities.addAll((List<CardEntity>) cardRepository.saveAll(toSaveCardEntities));
      } catch (DuplicateKeyException e) {
        throw new InvalidRequestException(String.format(DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE,
            CommonUtils.extractDuplicateValueFromDuplicateKeyException(e.getMessage())));
      }

      List<Card> toReturnCards = new ArrayList<>();
      for (CardEntity cardEntity : updatedEntities) {
        toReturnCards.add(getDTO(cardEntity));
      }
      return toReturnCards;
    }));
  }

  @Override
  public void deleteCardsByIdentifiers(String accountId, List<String> identifiers) {
    if (isEmpty(identifiers)) {
      return;
    }
    cardRepository.deleteByAccountIdentifierAndIdentifierIn(accountId, identifiers);
  }

  @Override
  public CardReferenceResolution resolveCardReferences(List<Card> incomingCards) {
    List<Card> incoming = isEmpty(incomingCards) ? List.of() : incomingCards;
    if (incoming.isEmpty()) {
      return new CardReferenceResolution(new ArrayList<>(), new ArrayList<>());
    }

    // Global OOTB catalog (seeded once under __GLOBAL_ACCOUNT_ID__), indexed by identifier.
    Map<String, Card> catalogById =
        getAllActiveCardsForAccount(Constants.GLOBAL_ACCOUNT_ID)
            .stream()
            .map(CardResponse::getCard)
            .collect(Collectors.toMap(Card::getIdentifier, Function.identity(), (a, b) -> a));

    List<String> orderedIdentifiers = new ArrayList<>();
    List<Card> accountOwnedCards = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (Card card : incoming) {
      String id = card.getIdentifier();
      if (Constants.isOotbCardIdentifier(id)) {
        Card catalogMatch = catalogById.get(id);
        if (catalogMatch == null) {
          throw new InvalidRequestException(String.format(UNRESOLVABLE_OOTB_CARD_ERROR, id));
        }
        if (isCardCustomizedFromCatalog(card, catalogMatch)) {
          // Customized OOTB card: persist an account-owned copy with a fresh identifier so the overrides stick.
          String mintedId = UUID.randomUUID().toString();
          card.setIdentifier(mintedId);
          if (seen.add(mintedId)) {
            orderedIdentifiers.add(mintedId);
            accountOwnedCards.add(card);
          }
        } else if (seen.add(catalogMatch.getIdentifier())) {
          // Untouched OOTB card: keep a lightweight global reference; never write an account row.
          orderedIdentifiers.add(catalogMatch.getIdentifier());
        }
      } else {
        // User-owned card (incl. a previously-materialized customized OOTB card); id-less ones get a fresh id.
        if (isEmpty(id)) {
          id = UUID.randomUUID().toString();
          card.setIdentifier(id);
        }
        if (seen.add(id)) {
          orderedIdentifiers.add(id);
          accountOwnedCards.add(card);
        }
      }
    }
    return new CardReferenceResolution(orderedIdentifiers, accountOwnedCards);
  }

  /**
   * True if the incoming card differs from its global OOTB catalog counterpart in any user-facing attribute
   * (size, title, icon, and any future per-type field). Identity/system fields — identifier, default_card, draft —
   * are ignored, and null/empty values are normalised so an omitted field equals an empty one. A full structural
   * compare (rather than a fixed field list) keeps this correct as new card attributes are added.
   */
  private boolean isCardCustomizedFromCatalog(Card incoming, Card catalogCard) {
    return !normalizeForComparison(incoming).equals(normalizeForComparison(catalogCard));
  }

  private ObjectNode normalizeForComparison(Card card) {
    ObjectNode node = objectMapper.valueToTree(card);
    node.remove(List.of("identifier", "default_card", "draft"));
    List<String> emptyFields = new ArrayList<>();
    node.fields().forEachRemaining(entry -> {
      JsonNode value = entry.getValue();
      if (value == null || value.isNull() || (value.isTextual() && value.asText().isEmpty())) {
        emptyFields.add(entry.getKey());
      }
    });
    emptyFields.forEach(node::remove);
    return node;
  }

  @Override
  public String getCustomCardQuickLinkUrl(String accountId, String cardIdentifier, String quickLinkIdentifier) {
    Optional<CardEntity> customCard = cardRepository.findByAccountIdentifierAndIdentifier(accountId, cardIdentifier);
    String urlToReturn = null;
    if (customCard.isPresent()) {
      CustomLinkCardEntity customLinkCardEntity = (CustomLinkCardEntity) customCard.get();
      if (!isEmpty(customLinkCardEntity.getLinks())) {
        Optional<LinksInfo> linkInfo = customLinkCardEntity.getLinks()
                                           .stream()
                                           .filter(linksInfo -> linksInfo.getIdentifier().equals(quickLinkIdentifier))
                                           .findFirst();
        if (linkInfo.isPresent()) {
          urlToReturn = linkInfo.get().getIcon();
        }
      }
    }
    return urlToReturn;
  }

  @Override
  public String getCardIconUrl(String accountId, String cardIdentifier) {
    Optional<CardEntity> optionalCardEntity =
        cardRepository.findByAccountIdentifierAndIdentifier(accountId, cardIdentifier);
    String urlToReturn = null;
    if (!optionalCardEntity.isPresent()) {
      return urlToReturn;
    }
    CardEntity cardEntity = optionalCardEntity.get();
    if (!isEmpty(cardEntity.getIconUrl())) {
      urlToReturn = cardEntity.getIconUrl();
    }
    return urlToReturn;
  }

  private Card getDTO(CardEntity cardEntity) {
    CardMapper cardMapper = getCardMapper(cardEntity.getType());
    return cardMapper.toDto(cardEntity);
  }

  private CardMapper getCardMapper(Card.TypeEnum cardType) {
    CardMapper cardMapper = cardMap.get(cardType);
    if (cardMapper == null) {
      throw new InvalidRequestException("Card type not set");
    }
    return cardMapper;
  }

  private List<Card> materializeInOrder(List<String> orderedIdentifiers, List<CardEntity> cardEntities) {
    if (isEmpty(cardEntities)) {
      return new ArrayList<>();
    }
    Map<String, CardEntity> byIdentifier =
        cardEntities.stream().collect(Collectors.toMap(CardEntity::getIdentifier, Function.identity(), (a, b) -> a));
    List<Card> cards = new ArrayList<>();
    for (String identifier : orderedIdentifiers) {
      CardEntity entity = byIdentifier.get(identifier);
      if (entity != null) {
        cards.add(getDTO(entity));
      }
    }
    return cards;
  }
}
