/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;
import static io.harness.idp.common.JacksonUtils.readValue;
import static io.harness.idp.personaview.PersonaViewConstants.DEVELOPER_VIEW_IDENTIFIER;
import static io.harness.idp.personaview.PersonaViewConstants.DEVELOPER_VIEW_NAME;
import static io.harness.idp.personaview.PersonaViewConstants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.personaview.PersonaViewConstants.LOG_PREFIX;
import static io.harness.idp.personaview.PersonaViewConstants.OOTB_PERSONA_VIEWS_RESOURCE;
import static io.harness.idp.personaview.PersonaViewConstants.isDeveloperView;
import static io.harness.idp.personaview.PersonaViewConstants.isOotbIdentifier;
import static io.harness.idp.personaview.PersonaViewConstants.isOotbView;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.idp.homepage.repositories.HomePageLayoutRepository;
import io.harness.idp.homepage.service.CardReferenceResolution;
import io.harness.idp.homepage.service.CardService;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.idp.personaview.OotbPersonaViewTemplate;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.personaview.events.PersonaViewCreateEvent;
import io.harness.idp.personaview.events.PersonaViewDeleteEvent;
import io.harness.idp.personaview.events.PersonaViewUpdateEvent;
import io.harness.idp.personaview.mappers.PersonaViewMapper;
import io.harness.idp.personaview.repositories.PersonaViewRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;
import io.harness.spec.server.idp.v1.model.CustomLinkCard;
import io.harness.spec.server.idp.v1.model.GithubCard;
import io.harness.spec.server.idp.v1.model.HarnessCodeCard;
import io.harness.spec.server.idp.v1.model.HeaderInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.JiraCard;
import io.harness.spec.server.idp.v1.model.LearnMoreCard;
import io.harness.spec.server.idp.v1.model.LinksInfo;
import io.harness.spec.server.idp.v1.model.PersonaView;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;
import io.harness.spec.server.idp.v1.model.RecentlyVisitedCard;
import io.harness.spec.server.idp.v1.model.SavePersonaViewBody;
import io.harness.spec.server.idp.v1.model.SavePersonaViewRequest;
import io.harness.spec.server.idp.v1.model.StarredEntitiesCard;
import io.harness.spec.server.idp.v1.model.TopVisitedCard;
import io.harness.spec.server.idp.v1.model.UploadInfo;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PersonaViewServiceImpl implements PersonaViewService {
  private static final String PERSONA_VIEW_NOT_FOUND_ERROR = "Persona view not found for identifier - %s";
  private static final String OOTB_VIEW_DELETE_REJECTED = "OOTB persona view '%s' cannot be deleted";
  private static final String OOTB_VIEW_NON_OOTB_CARD_REJECTED =
      "OOTB persona view '%s' may only reference OOTB catalog cards; '%s' is not an OOTB card";
  private static final String OOTB_VIEW_EDIT_FIELD_REJECTED =
      "Only user_group_identifiers, cards (reorder/removal/customization of OOTB cards), header and banner can be "
      + "edited on OOTB persona view '%s'; name and description are Harness-managed and immutable";
  private static final String DEVELOPER_VIEW_EDIT_REJECTED =
      "The Developer's View is read-only via the persona view APIs; manage its cards, header and banner via "
      + "/v1/home-page-layout";
  /**
   * Identifiers reserved by literal sub-resource paths under {@code /v1/persona-views/*} (e.g. {@code me}).
   * Custom persona views must not use these names — otherwise the literal route would mask the row.
   */
  private static final Set<String> RESERVED_VIEW_IDENTIFIERS = Set.of("me");
  private static final String RESERVED_IDENTIFIER_REJECTED =
      "Persona view identifier '%s' is reserved and cannot be used for a custom view";

  private final PersonaViewRepository personaViewRepository;
  private final PersonaViewMapper personaViewMapper;
  private final CardService cardService;
  private final CatalogEntityRepository catalogEntityRepository;
  private final CloudStorageUtil cloudStorageUtil;
  private final HomePageCardIconConfig homePageCardIconConfig;
  private final HomePageLayoutRepository homePageLayoutRepository;
  private final HomePageLayoutService homePageLayoutService;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  /**
   * OOTB persona view templates loaded once at class init from {@link
   * io.harness.idp.personaview.PersonaViewConstants#OOTB_PERSONA_VIEWS_RESOURCE}. Each entry is materialized
   * per account on first IDP provisioning. Adding a new OOTB view is a JSON-only change.
   */
  private static final List<OotbPersonaViewTemplate> OOTB_VIEW_TEMPLATES = loadOotbViewTemplates();

  private static List<OotbPersonaViewTemplate> loadOotbViewTemplates() {
    try {
      String json = Resources.toString(Resources.getResource(OOTB_PERSONA_VIEWS_RESOURCE), StandardCharsets.UTF_8);
      List<OotbPersonaViewTemplate> templates = readValue(json, OotbPersonaViewTemplate.class);
      log.info("{} Loaded {} OOTB persona view template(s) from {}", LOG_PREFIX,
          templates == null ? 0 : templates.size(), OOTB_PERSONA_VIEWS_RESOURCE);
      return templates == null ? List.of() : templates;
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Failed to load OOTB persona view templates from " + OOTB_PERSONA_VIEWS_RESOURCE, ex);
    }
  }

  @Inject
  public PersonaViewServiceImpl(PersonaViewRepository personaViewRepository, PersonaViewMapper personaViewMapper,
      CardService cardService, CatalogEntityRepository catalogEntityRepository, CloudStorageUtil cloudStorageUtil,
      @Named("homePageCardIconConfig") HomePageCardIconConfig homePageCardIconConfig,
      HomePageLayoutRepository homePageLayoutRepository, HomePageLayoutService homePageLayoutService,
      TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.personaViewRepository = personaViewRepository;
    this.personaViewMapper = personaViewMapper;
    this.cardService = cardService;
    this.catalogEntityRepository = catalogEntityRepository;
    this.cloudStorageUtil = cloudStorageUtil;
    this.homePageCardIconConfig = homePageCardIconConfig;
    this.homePageLayoutRepository = homePageLayoutRepository;
    this.homePageLayoutService = homePageLayoutService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public List<PersonaView> getPersonaViewsForUser(String accountIdentifier) {
    log.info("{} Getting persona views for user in account {}", LOG_PREFIX, accountIdentifier);
    List<String> userGroupIdentifiers = resolveCurrentUserGroupIdentifiers(accountIdentifier);
    log.info(
        "{} Resolved user group identifiers {} for account {}", LOG_PREFIX, userGroupIdentifiers, accountIdentifier);

    List<PersonaViewEntity> matchingViews =
        personaViewRepository.findViewsForUser(accountIdentifier, userGroupIdentifiers);

    // OOTB views first (in their natural identifier order), then custom views ordered by name.
    List<PersonaViewEntity> ordered =
        matchingViews.stream()
            .sorted(Comparator
                        .<PersonaViewEntity, Integer>comparing(entity -> Boolean.TRUE.equals(entity.getOotb()) ? 0 : 1)
                        .thenComparing(PersonaViewEntity::getName))
            .collect(Collectors.toList());

    List<PersonaView> result = new ArrayList<>();
    // Developer's View is the first entry when the account has a homepage configured; it surfaces the homepage
    // as a persona view but is immutable via these APIs. Edits to it go through /v1/home-page-layout. When no
    // homepage row exists, the Developer's View is omitted entirely rather than returned as an empty shell.
    buildDeveloperView(accountIdentifier).ifPresent(result::add);
    for (PersonaViewEntity entity : ordered) {
      result.add(resolveAndPresign(entity));
    }
    log.info("{} Returning {} persona views for user in account {}", LOG_PREFIX, result.size(), accountIdentifier);
    return result;
  }

  @Override
  public Page<PersonaView> listPersonaViews(String accountIdentifier, Pageable pageable, String searchTerm) {
    log.info("{} Listing persona views for account {} with searchTerm={}", LOG_PREFIX, accountIdentifier, searchTerm);

    // Surface every stored persona view (OOTB platform/leadership and custom alike); the Developer's View is
    // injected synthetically since it has no row in the personaViews collection.
    Page<PersonaViewEntity> storedPage =
        personaViewRepository.findViewsForAdmin(accountIdentifier, pageable, searchTerm);
    List<PersonaView> storedViews = storedPage.getContent().stream().map(this::resolveAndPresign).toList();

    // Developer's View is the synthetic first entry on page 0, but only when the account has a homepage
    // configured. Total count is bumped by 1 only when the view is actually included, so clients see a stable
    // total.
    boolean includeDeveloper = matchesSearchTerm(DEVELOPER_VIEW_NAME, searchTerm);
    long totalElements = storedPage.getTotalElements();

    List<PersonaView> content = new ArrayList<>();
    if (includeDeveloper && pageable.getPageNumber() == 0) {
      Optional<PersonaView> developerView = buildDeveloperView(accountIdentifier);
      if (developerView.isPresent()) {
        content.add(developerView.get());
        totalElements += 1;
      }
    }
    content.addAll(storedViews);

    return new PageImpl<>(content, pageable, totalElements);
  }

  @Override
  public PersonaViewResponse getPersonaView(String accountIdentifier, String identifier) {
    log.info("{} Getting persona view {} for account {}", LOG_PREFIX, identifier, accountIdentifier);

    if (isDeveloperView(identifier)) {
      return personaViewMapper.toResponse(
          buildDeveloperView(accountIdentifier)
              .orElseThrow(() -> new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND_ERROR, identifier))));
    }

    PersonaViewEntity entity =
        personaViewRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier)
            .orElseThrow(() -> new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND_ERROR, identifier)));
    return personaViewMapper.toResponse(resolveAndPresign(entity));
  }

  @Override
  public PersonaViewResponse savePersonaView(
      String accountIdentifier, String identifier, SavePersonaViewRequest request) {
    log.info("{} Saving persona view {} for account {}", LOG_PREFIX, identifier, accountIdentifier);

    SavePersonaViewBody saveBody = request.getPersonaView();
    if (saveBody == null) {
      throw new InvalidRequestException("Persona view body is required");
    }

    // Developer's View is the account homepage surfaced as a persona view. Cards (membership/order) plus the
    // header/banner chrome are editable here and persisted through the shared homepage layout, so the same edits
    // remain equally reachable via /v1/home-page-layout.
    if (isDeveloperView(identifier)) {
      return saveDeveloperView(accountIdentifier, saveBody);
    }
    // Block identifiers that are reserved by literal sub-resource routes (e.g. /v1/persona-views/me).
    if (RESERVED_VIEW_IDENTIFIERS.contains(identifier)) {
      throw new InvalidRequestException(String.format(RESERVED_IDENTIFIER_REJECTED, identifier));
    }

    PersonaViewEntity existingEntity =
        personaViewRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier).orElse(null);
    boolean ootb = isOotbView(identifier) || (existingEntity != null && Boolean.TRUE.equals(existingEntity.getOotb()));

    if (ootb) {
      return saveOotbView(accountIdentifier, identifier, saveBody, existingEntity);
    }

    return saveCustomView(accountIdentifier, identifier, saveBody, existingEntity);
  }

  /**
   * OOTB views ({@code platform}, {@code leadership}) are Harness-managed but customer-tunable: the
   * {@code user_group_identifiers} visibility ACL, the {@code header}/{@code banner} chrome, and the card list
   * (reorder, removal, addition, and per-view customization of the Harness-seeded OOTB cards). {@code name} and
   * {@code description} remain Harness-managed and immutable.
   *
   * <p>The card list may only reference cards from the global OOTB catalog — accounts cannot inject their own
   * custom cards (the sole exception being customer-authored {@code MARKDOWN} content cards) — which keeps the view
   * OOTB-curated. That curation is a <em>view</em> policy and is enforced here by
   * {@link #enforceOotbCatalogOnly(String, List)} (it also binds id-less entries the UI added to their catalog
   * {@code ootb:*} id by type). The view-agnostic reference-vs-materialize split is then delegated to
   * {@link CardService#resolveCardReferences(List)}: a card left at its catalog defaults stays a lightweight
   * {@code ootb:*} reference, while a customized one (e.g. a different {@code size}) is materialized as an
   * account-owned card row referenced by a fresh identifier. Account rows the view no longer references are deleted;
   * the global OOTB card itself is never deleted. The OOTB row is seeded during IDP provisioning (see
   * {@link #seedOotbPersonaViewsIfNotAlready(String)}), so we require the row to exist here.
   */
  private PersonaViewResponse saveOotbView(
      String accountIdentifier, String identifier, SavePersonaViewBody saveBody, PersonaViewEntity existing) {
    if (existing == null) {
      throw new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND_ERROR, identifier));
    }

    // Reject name/description edits. We compare against the stored row so that clients echoing the GET payload
    // (which returns name/description) are still accepted as long as those values match what is already on disk.
    if (saveBody.getName() != null && !saveBody.getName().equals(existing.getName())) {
      throw new InvalidRequestException(String.format(OOTB_VIEW_EDIT_FIELD_REJECTED, identifier));
    }
    if (saveBody.getDescription() != null && !saveBody.getDescription().equals(existing.getDescription())) {
      throw new InvalidRequestException(String.format(OOTB_VIEW_EDIT_FIELD_REJECTED, identifier));
    }

    // Cards may be reordered, removed, added, or customized; null leaves the list unchanged (groups/chrome only).
    // Enforce the OOTB-only curation contract (a view policy) before the card-domain split.
    final CardReferenceResolution resolution;
    if (saveBody.getCards() == null) {
      resolution = null;
    } else {
      enforceOotbCatalogOnly(identifier, saveBody.getCards());
      resolution = cardService.resolveCardReferences(saveBody.getCards());
    }
    // Account-owned card rows this view previously referenced (OOTB ids are global refs, never account-owned).
    List<String> priorOwnedIdentifiers = isEmpty(existing.getCards())
        ? List.of()
        : existing.getCards().stream().filter(id -> !isOotbIdentifier(id)).collect(Collectors.toList());

    // Header/banner use preserve semantics: incoming overrides, omitted keeps the stored chrome.
    HeaderInfo header = saveBody.getHeader() != null ? saveBody.getHeader() : existing.getHeader();
    BannerInfo banner = saveBody.getBanner() != null ? saveBody.getBanner() : existing.getBanner();

    PersonaView oldPersonaView = resolveAndPresign(existing);

    PersonaViewEntity savedEntity =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          if (resolution != null) {
            Set<String> newOwnedIds = new HashSet<>(resolution.getOrderedIdentifiers());
            List<String> toDeleteIdentifiers =
                priorOwnedIdentifiers.stream().filter(id -> !newOwnedIds.contains(id)).collect(Collectors.toList());
            cardService.deleteCardsByIdentifiers(accountIdentifier, toDeleteIdentifiers);
            cardService.saveAllCards(resolution.getAccountOwnedCards(), accountIdentifier);
            existing.setCards(resolution.getOrderedIdentifiers());
          }
          existing.setUserGroupIdentifiers(saveBody.getUserGroupIdentifiers());
          existing.setHeader(header);
          existing.setBanner(banner);
          PersonaViewEntity saved = personaViewRepository.save(existing);
          outboxService.save(new PersonaViewUpdateEvent(resolveAndPresign(saved), oldPersonaView, accountIdentifier));
          return saved;
        }));

    log.info("{} Updated OOTB persona view {} for account {}", LOG_PREFIX, identifier, accountIdentifier);
    return personaViewMapper.toResponse(resolveAndPresign(savedEntity));
  }

  /**
   * Enforce the OOTB-view curation contract: an OOTB persona view ({@code platform}, {@code leadership}) may only
   * carry cards drawn from the global OOTB catalog — accounts cannot inject their own custom cards, save for
   * customer-authored {@code MARKDOWN} content cards. This is a <em>view-level</em> policy, so it lives here rather
   * than in {@link CardService}. It mutates id-less entries in place, binding each to its catalog {@code ootb:*}
   * identifier (resolved by {@code type}, since the UI does not know the seeded ids); the reference-vs-materialize
   * decision is then left to {@link CardService#resolveCardReferences(List)}.
   *
   * <ul>
   *   <li>{@code ootb:*} id -> accepted as-is.</li>
   *   <li>{@code MARKDOWN} type -> accepted as an account-owned content card (materialized downstream).</li>
   *   <li>id-less -> bound to the catalog {@code ootb:*} id by {@code type}; rejected if the type is not OOTB.</li>
   *   <li>any other id (a previously-materialized, customized OOTB card) -> accepted only if its {@code type} is
   *       OOTB; rejected otherwise.</li>
   * </ul>
   */
  private void enforceOotbCatalogOnly(String viewIdentifier, List<Card> cards) {
    if (isEmpty(cards)) {
      return;
    }
    Map<Card.TypeEnum, String> ootbIdByType =
        cardService.getAllActiveCardsForAccount(GLOBAL_ACCOUNT_ID)
            .stream()
            .map(CardResponse::getCard)
            .collect(Collectors.toMap(Card::getType, Card::getIdentifier, (a, b) -> a));
    for (Card card : cards) {
      String id = card.getIdentifier();
      if (isOotbIdentifier(id)) {
        continue;
      }
      if (Card.TypeEnum.MARKDOWN == card.getType()) {
        continue;
      }
      if (isEmpty(id)) {
        String resolvedOotbId = card.getType() == null ? null : ootbIdByType.get(card.getType());
        if (resolvedOotbId == null) {
          throw new InvalidRequestException(String.format(
              OOTB_VIEW_NON_OOTB_CARD_REJECTED, viewIdentifier, card.getType() == null ? "<null>" : card.getType()));
        }
        card.setIdentifier(resolvedOotbId);
      } else if (card.getType() == null || !ootbIdByType.containsKey(card.getType())) {
        throw new InvalidRequestException(String.format(OOTB_VIEW_NON_OOTB_CARD_REJECTED, viewIdentifier, id));
      }
    }
  }

  /**
   * Custom (non-OOTB) view save path. Writes the view document and its scoped card rows in a single
   * transaction so we never leak orphan card rows without a referencing view, or vice versa.
   */
  private PersonaViewResponse saveCustomView(
      String accountIdentifier, String identifier, SavePersonaViewBody saveBody, PersonaViewEntity existing) {
    List<Card> incomingCards = saveBody.getCards() == null ? List.of() : saveBody.getCards();

    // Split the incoming cards into the ordered identifier list stored on the view and the account-owned rows to
    // upsert. Untouched OOTB cards stay ootb:* refs (resolved at read time against __GLOBAL_ACCOUNT_ID__); customized
    // OOTB cards and user cards are materialized as account rows. Also validates that any ootb:* ref exists.
    CardReferenceResolution resolution = cardService.resolveCardReferences(incomingCards);
    List<String> newOwnedIdentifiers =
        resolution.getAccountOwnedCards().stream().map(Card::getIdentifier).collect(Collectors.toList());
    // Prior account-owned identifiers come from the existing view document; the cards collection is scope-agnostic
    // so the persona view IS the source of truth for what this view owns.
    List<String> priorOwnedIdentifiers = existing == null || isEmpty(existing.getCards())
        ? List.of()
        : existing.getCards().stream().filter(id -> !isOotbIdentifier(id)).collect(Collectors.toList());
    // Account rows this view previously owned but no longer references are deleted. Priors are already non-ootb, so
    // OOTB cards (global refs under __GLOBAL_ACCOUNT_ID__) can never enter the delete set.
    Set<String> newOwnedSet = new HashSet<>(newOwnedIdentifiers);
    List<String> toDeleteIdentifiers =
        priorOwnedIdentifiers.stream().filter(id -> !newOwnedSet.contains(id)).collect(Collectors.toList());

    // Header/banner use preserve semantics: incoming overrides, omitted keeps the stored chrome.
    HeaderInfo header =
        saveBody.getHeader() != null ? saveBody.getHeader() : (existing == null ? null : existing.getHeader());
    BannerInfo banner =
        saveBody.getBanner() != null ? saveBody.getBanner() : (existing == null ? null : existing.getBanner());

    PersonaView oldPersonaView = existing == null ? null : resolveAndPresign(existing);

    PersonaViewEntity savedEntity =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          // Both card-service calls use REQUIRED propagation so they join this transaction. Delete the dropped
          // cards first, then pure-upsert the cards still referenced by the view.
          cardService.deleteCardsByIdentifiers(accountIdentifier, toDeleteIdentifiers);
          cardService.saveAllCards(resolution.getAccountOwnedCards(), accountIdentifier);

          PersonaViewEntity toSaveEntity =
              personaViewMapper.fromSaveBody(saveBody, accountIdentifier, identifier, false);
          toSaveEntity.setCards(resolution.getOrderedIdentifiers());
          toSaveEntity.setHeader(header);
          toSaveEntity.setBanner(banner);
          if (existing != null) {
            toSaveEntity.setId(existing.getId());
          }
          PersonaViewEntity saved = personaViewRepository.save(toSaveEntity);
          PersonaView newPersonaView = resolveAndPresign(saved);
          if (existing == null) {
            outboxService.save(new PersonaViewCreateEvent(newPersonaView, accountIdentifier));
          } else {
            outboxService.save(new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, accountIdentifier));
          }
          return saved;
        }));

    log.info("{} Saved custom persona view {} for account {} (created={})", LOG_PREFIX, identifier, accountIdentifier,
        existing == null);
    return personaViewMapper.toResponse(resolveAndPresign(savedEntity));
  }

  /**
   * Developer's View save path. The Developer's View has no {@link PersonaViewEntity} row — it is the account
   * homepage ({@code homePageLayouts}) surfaced as a persona view. The card list (membership + order) plus the
   * homepage {@code header}/{@code banner} chrome are all editable through this API; persistence is delegated to
   * the shared homepage layout, so the same edits are equally reachable via {@code /v1/home-page-layout}.
   *
   * <p>Header/banner use preserve semantics: when supplied on the body they overwrite the stored chrome, and
   * when omitted ({@code null}) the existing value is kept. This lets a cards-only edit leave the chrome intact,
   * while still allowing full chrome updates. On the very first save (no homepage row yet) an omitted
   * header/banner is simply persisted as null — identical to creating the homepage via its native API with a
   * null chrome — so there is no special first-save case to reason about.
   *
   * <p>Persistence is delegated to {@link HomePageLayoutService#saveHomePageLayout}, which is create-or-update:
   * it creates the homepage row on first save and updates it thereafter, and (post-refactor) deletes only the
   * cards the homepage itself previously owned — never OOTB refs and never cards owned by other persona views.
   *
   * <p>Assumes {@code saveBody} is non-null — the shared null-body guard lives in {@link #savePersonaView}, the
   * only caller of this method.
   */
  private PersonaViewResponse saveDeveloperView(String accountIdentifier, SavePersonaViewBody saveBody) {
    // Card resolution (identifier assignment, OOTB ref validation, customized-OOTB materialization) is handled by
    // the shared homepage save path; we only assemble the layout request here.
    List<Card> incomingCards = saveBody.getCards() == null ? List.of() : saveBody.getCards();

    Optional<HomePageLayoutEntity> existing = homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);
    PersonaView oldPersonaView = existing.map(this::buildDeveloperView).orElse(null);
    HomePageLayoutInfo layoutInfo = new HomePageLayoutInfo();
    layoutInfo.setCards(incomingCards);
    // Incoming chrome overrides; omitted chrome falls back to the stored value (preserve semantics).
    layoutInfo.setHeader(saveBody.getHeader() != null ? saveBody.getHeader()
                                                      : existing.map(HomePageLayoutEntity::getHeader).orElse(null));
    layoutInfo.setBanner(saveBody.getBanner() != null ? saveBody.getBanner()
                                                      : existing.map(HomePageLayoutEntity::getBanner).orElse(null));
    HomePageLayoutRequest layoutRequest = new HomePageLayoutRequest();
    layoutRequest.setHomePageLayout(layoutInfo);

    homePageLayoutService.saveHomePageLayout(layoutRequest, accountIdentifier);

    log.info("{} Saved Developer's View (homepage) for account {}", LOG_PREFIX, accountIdentifier);
    // Build the response from the persisted homepage snapshot. The saveHomePageLayout response cannot be reused
    // directly: it carries only the user-owned saved cards (OOTB refs resolve at read time) and no audit
    // timestamps. A single read of the just-saved entity yields the authoritative card list and timestamps.
    HomePageLayoutEntity savedEntity =
        homePageLayoutRepository.findByAccountIdentifier(accountIdentifier)
            .orElseThrow(
                () -> new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND_ERROR, DEVELOPER_VIEW_IDENTIFIER)));
    PersonaView newPersonaView = buildDeveloperView(savedEntity);
    if (oldPersonaView == null) {
      outboxService.save(new PersonaViewCreateEvent(newPersonaView, accountIdentifier));
    } else {
      outboxService.save(new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, accountIdentifier));
    }
    return personaViewMapper.toResponse(newPersonaView);
  }

  @Override
  public void deletePersonaView(String accountIdentifier, String identifier) {
    log.info("{} Deleting persona view {} for account {}", LOG_PREFIX, identifier, accountIdentifier);

    if (isDeveloperView(identifier)) {
      throw new InvalidRequestException(DEVELOPER_VIEW_EDIT_REJECTED);
    }

    PersonaViewEntity entity =
        personaViewRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier)
            .orElseThrow(() -> new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND_ERROR, identifier)));

    if (Boolean.TRUE.equals(entity.getOotb()) || isOotbView(identifier)) {
      throw new InvalidRequestException(String.format(OOTB_VIEW_DELETE_REJECTED, identifier));
    }

    // Identifiers of user-owned cards this view references (OOTB ids are global refs, not owned).
    List<String> userOwnedIdentifiers = isEmpty(entity.getCards())
        ? List.of()
        : entity.getCards().stream().filter(id -> !isOotbIdentifier(id)).collect(Collectors.toList());
    PersonaView oldPersonaView = resolveAndPresign(entity);

    // Cascade delete of card rows + the view document must be atomic — otherwise we end up either with a view
    // referencing missing cards, or orphan card rows referencing a deleted view.
    Failsafe.with(transactionRetryPolicy).run(() -> transactionTemplate.execute(status -> {
      cardService.deleteCardsByIdentifiers(accountIdentifier, userOwnedIdentifiers);
      personaViewRepository.deleteByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
      outboxService.save(new PersonaViewDeleteEvent(oldPersonaView, accountIdentifier));
      return null;
    }));
    log.info("{} Deleted persona view {} for account {}", LOG_PREFIX, identifier, accountIdentifier);
  }

  @Override
  public void seedOotbPersonaViewsIfNotAlready(String accountIdentifier) {
    for (OotbPersonaViewTemplate template : OOTB_VIEW_TEMPLATES) {
      seedSingleOotbViewIfNotAlready(accountIdentifier, template);
    }
  }

  /**
   * Insert one OOTB view row for the account if it does not already exist. Existence is established by the
   * unique index on {@code (accountIdentifier, identifier)} — we attempt the insert and treat a duplicate-key
   * failure as a successful no-op (race between two concurrent seeders). We do NOT update an existing row, so
   * customer edits to {@code user_group_identifiers} are preserved across re-runs of this method.
   */
  private void seedSingleOotbViewIfNotAlready(String accountIdentifier, OotbPersonaViewTemplate template) {
    if (personaViewRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, template.getIdentifier())
            .isPresent()) {
      return;
    }
    PersonaViewEntity entity = PersonaViewEntity.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .identifier(template.getIdentifier())
                                   .name(template.getName())
                                   .description(template.getDescription())
                                   .ootb(true)
                                   .cards(template.getCards())
                                   .userGroupIdentifiers(List.of())
                                   .build();
    try {
      personaViewRepository.save(entity);
      log.info(
          "{} Seeded OOTB persona view {} for account {}", LOG_PREFIX, template.getIdentifier(), accountIdentifier);
    } catch (org.springframework.dao.DuplicateKeyException ignored) {
      // Another thread/pod seeded this row first; nothing to do.
      log.debug("{} OOTB persona view {} already seeded for account {}", LOG_PREFIX, template.getIdentifier(),
          accountIdentifier);
    }
  }

  /**
   * Build the synthetic Developer's View DTO for the account by projecting {@link HomePageLayoutEntity} onto
   * {@link PersonaView}. The Developer's View is always visible to every user in the account (no user-group
   * ACL — that is intentionally not stored on the homepage entity) and is read-only via the persona view APIs;
   * all edits go through {@code /v1/home-page-layout}.
   *
   * <p>For this synthetic view, header and banner are projected from the homepage layout entity (other persona
   * views carry their own chrome on their {@link PersonaViewEntity} row).
   *
   * <p>If the account does not yet have a homepage row, {@link Optional#empty()} is returned so the Developer's
   * View is omitted from the response entirely (rather than surfaced as an empty shell). Callers should treat
   * its absence as "no homepage configured yet".
   */
  private Optional<PersonaView> buildDeveloperView(String accountIdentifier) {
    Optional<HomePageLayoutEntity> homePageLayoutEntity =
        homePageLayoutRepository.findByAccountIdentifier(accountIdentifier);
    if (homePageLayoutEntity.isPresent()) {
      return homePageLayoutEntity.map(this::buildDeveloperView);
    }
    HomePageLayoutEntity defaultHomePageLayoutEntity = saveAndGetDefaultHomePageLayoutEntity(accountIdentifier);
    if (defaultHomePageLayoutEntity == null) {
      return Optional.empty();
    }
    return Optional.of(buildDeveloperView(defaultHomePageLayoutEntity));
  }

  private HomePageLayoutEntity saveAndGetDefaultHomePageLayoutEntity(String accountIdentifier) {
    LinksInfo harnessDocs = new LinksInfo();
    harnessDocs.setIdentifier(UUID.randomUUID().toString());
    harnessDocs.setIcon("harness");
    harnessDocs.setTitle("Harness Docs");
    harnessDocs.setUrl("https://developer.harness.io/docs/internal-developer-portal/");

    LinksInfo releaseNotes = new LinksInfo();
    releaseNotes.setIdentifier(UUID.randomUUID().toString());
    releaseNotes.setIcon("harness");
    releaseNotes.setTitle("Release Notes");
    releaseNotes.setUrl("https://developer.harness.io/release-notes/internal-developer-portal");

    HeaderInfo header = new HeaderInfo();
    header.setHeaderText("Welcome to Harness IDP");
    header.setQuickLinks(List.of(harnessDocs, releaseNotes));

    UploadInfo imageUpload = new UploadInfo();
    imageUpload.setUploads(List.of("https://developer.harness.io/img/idp.svg"));
    imageUpload.setSelected("https://developer.harness.io/img/idp.svg");
    imageUpload.setUrl("");

    UploadInfo videoUpload = new UploadInfo();
    videoUpload.setUrl("https://www.youtube.com/embed/sVnI93bCr38?si=zobQ1YJMVVJMccaO");

    BannerInfo banner = new BannerInfo();
    banner.setType("image");
    banner.setBannerEnabled(true);
    banner.setImage(imageUpload);
    banner.setVideo(videoUpload);

    GithubCard githubCard = new GithubCard();
    githubCard.setType(Card.TypeEnum.GITHUB);
    githubCard.setDefaultCard(true);
    githubCard.setTitle("GitHub Pull Requests");
    githubCard.setIdentifier(UUID.randomUUID().toString());
    githubCard.setSize("medium");
    githubCard.setDraft(false);

    HarnessCodeCard harnessCodeCard = new HarnessCodeCard();
    harnessCodeCard.setType(Card.TypeEnum.HARNESS_CODE);
    harnessCodeCard.setDefaultCard(true);
    harnessCodeCard.setTitle("Harness Code Pull Requests");
    harnessCodeCard.setIdentifier(UUID.randomUUID().toString());
    harnessCodeCard.setSize("medium");
    harnessCodeCard.setDraft(false);

    JiraCard jiraCard = new JiraCard();
    jiraCard.setType(Card.TypeEnum.JIRA);
    jiraCard.setDefaultCard(true);
    jiraCard.setTitle("Jira");
    jiraCard.setIdentifier(UUID.randomUUID().toString());
    jiraCard.setSize("medium");
    jiraCard.setDraft(false);

    TopVisitedCard topVisitedCard = new TopVisitedCard();
    topVisitedCard.setType(Card.TypeEnum.TOP_VISITED);
    topVisitedCard.setDefaultCard(true);
    topVisitedCard.setTitle("Top visited");
    topVisitedCard.setIdentifier(UUID.randomUUID().toString());
    topVisitedCard.setSize("medium");
    topVisitedCard.setDraft(false);

    RecentlyVisitedCard recentlyVisitedCard = new RecentlyVisitedCard();
    recentlyVisitedCard.setType(Card.TypeEnum.RECENTLY_VISITED);
    recentlyVisitedCard.setDefaultCard(true);
    recentlyVisitedCard.setTitle("Recently Visited");
    recentlyVisitedCard.setIdentifier(UUID.randomUUID().toString());
    recentlyVisitedCard.setSize("medium");
    recentlyVisitedCard.setDraft(false);

    StarredEntitiesCard starredEntitiesCard = new StarredEntitiesCard();
    starredEntitiesCard.setType(Card.TypeEnum.STARRED_ENTITIES);
    starredEntitiesCard.setDefaultCard(true);
    starredEntitiesCard.setTitle("Starred entities");
    starredEntitiesCard.setIdentifier(UUID.randomUUID().toString());
    starredEntitiesCard.setSize("medium");
    starredEntitiesCard.setDraft(false);

    LearnMoreCard learnMoreCard = new LearnMoreCard();
    learnMoreCard.setType(Card.TypeEnum.LEARN_MORE);
    learnMoreCard.setDefaultCard(true);
    learnMoreCard.setTitle("Learn More");
    learnMoreCard.setIdentifier(UUID.randomUUID().toString());
    learnMoreCard.setSize("medium");
    learnMoreCard.setDraft(false);

    HomePageLayoutInfo layoutInfo = new HomePageLayoutInfo();
    layoutInfo.setHeader(header);
    layoutInfo.setBanner(banner);
    layoutInfo.setCards(List.of(githubCard, harnessCodeCard, jiraCard, topVisitedCard, recentlyVisitedCard,
        starredEntitiesCard, learnMoreCard));

    HomePageLayoutRequest layoutRequest = new HomePageLayoutRequest();
    layoutRequest.setHomePageLayout(layoutInfo);

    try {
      homePageLayoutService.saveHomePageLayout(layoutRequest, accountIdentifier);
    } catch (Exception e) {
      log.warn("{} Failed to save default home page layout for account {} - {}", LOG_PREFIX, accountIdentifier,
          e.getMessage(), e);
    }

    return homePageLayoutRepository.findByAccountIdentifier(accountIdentifier).orElse(null);
  }

  /** Project an already-loaded homepage entity onto the synthetic Developer's View DTO (no repository read). */
  private PersonaView buildDeveloperView(HomePageLayoutEntity entity) {
    List<Card> resolvedCards =
        cardService.getCardsByIdentifiers(List.of(entity.getAccountIdentifier(), GLOBAL_ACCOUNT_ID), entity.getCards());

    PersonaView personaView = new PersonaView();
    personaView.setIdentifier(DEVELOPER_VIEW_IDENTIFIER);
    personaView.setName(DEVELOPER_VIEW_NAME);
    personaView.setOotb(true);
    personaView.setUserGroupRefs(List.of());
    personaView.setCards(resolvedCards);
    personaView.setHeader(entity.getHeader());
    personaView.setBanner(entity.getBanner());
    personaView.setCreatedAt(entity.getCreatedAt());
    personaView.setLastUpdatedAt(entity.getLastUpdatedAt());
    presignPersonaViewUrls(personaView);
    return personaView;
  }

  private boolean matchesSearchTerm(String name, String searchTerm) {
    return isEmpty(searchTerm) || name.toLowerCase().contains(searchTerm.toLowerCase());
  }

  /** Resolve the persona view doc's card identifier list into Card DTOs, presign URLs, and return the DTO. */
  private PersonaView resolveAndPresign(PersonaViewEntity entity) {
    PersonaView personaView = personaViewMapper.toDto(entity);
    List<String> cardIdentifiers = entity.getCards() == null ? List.of() : entity.getCards();
    List<Card> resolvedCards =
        cardService.getCardsByIdentifiers(List.of(entity.getAccountIdentifier(), GLOBAL_ACCOUNT_ID), cardIdentifiers);
    personaView.setCards(resolvedCards);
    presignPersonaViewUrls(personaView);
    return personaView;
  }

  private List<String> resolveCurrentUserGroupIdentifiers(String accountIdentifier) {
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    if (userPrincipal == null || isEmpty(userPrincipal.getEmail())) {
      return List.of();
    }

    CatalogEntity userEntity =
        catalogEntityRepository
            .findByParentUniqueIdAndKindAndIdentifier(accountIdentifier, USER_KIND, userPrincipal.getEmail())
            .orElse(null);
    if (userEntity == null) {
      return List.of();
    }

    Map<String, Set<String>> relations = userEntity.getRelations();
    if (isEmpty(relations) || isEmpty(relations.get(MEMBER_OF))) {
      return List.of();
    }

    // Relation refs in the catalog accept two equivalent shapes for account-scoped members:
    //   "group:<identifier>" (implicit account scope) and "group:account/<identifier>" (explicit account scope).
    // Both must yield the bare identifier — naive prefix-strip would leave "account/<identifier>" for the second
    // form and miss the row at the ACL match step. CatalogUtils.parseRelationRef normalises both into the same shape.
    Set<String> groupIdentifiers = new HashSet<>();
    for (String memberOfRef : relations.get(MEMBER_OF)) {
      CatalogUtils.parseRelationRef(memberOfRef)
          .filter(ref -> GROUP_KIND.equalsIgnoreCase(ref.getKind()))
          .ifPresent(ref -> groupIdentifiers.add(ref.getIdentifier()));
    }
    return new ArrayList<>(groupIdentifiers);
  }

  private void presignPersonaViewUrls(PersonaView personaView) {
    if (!"S3".equalsIgnoreCase(homePageCardIconConfig.getStorageType()) || personaView == null) {
      return;
    }
    // Header and banner are optional on any view; presign them only when present.
    if (personaView.getHeader() != null) {
      presignLinksIcons(personaView.getHeader().getQuickLinks());
    }
    BannerInfo banner = personaView.getBanner();
    if (banner != null) {
      presignUploadInfo(banner.getImage());
      presignUploadInfo(banner.getVideo());
    }
    if (personaView.getCards() != null) {
      for (Card card : personaView.getCards()) {
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
