/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.homepage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface CardService {
  List<CardResponse> getAllActiveCardsForAccount(String accountId);

  /** Resolve cards by identifier, restricted to a single account. Order matches {@code cardIdentifiers}. */
  List<Card> getAllCardsForIdentifiers(String accountId, List<String> cardIdentifiers);

  /**
   * Resolve cards by identifier across multiple accounts. Used to materialize card lists that may mix the
   * caller's account with the reserved {@code __GLOBAL_ACCOUNT_ID__} account. Order matches {@code cardIdentifiers};
   * identifiers that do not resolve are dropped.
   */
  List<Card> getCardsByIdentifiers(List<String> accountIds, List<String> cardIdentifiers);

  /**
   * Pure upsert: persist exactly the given cards under {@code accountId} (insert new, update existing by reusing
   * their Mongo {@code _id}). Performs NO deletes. Callers that need to remove cards a view no longer references
   * must diff their own prior identifier list and call {@link #deleteCardsByIdentifiers(String, List)}.
   */
  List<Card> saveAllCards(List<Card> cards, String accountId);

  /** Bulk delete cards by identifier for an account. No-op if the list is empty. */
  void deleteCardsByIdentifiers(String accountId, List<String> identifiers);

  /**
   * Split incoming cards into the identifier list to persist (on whatever owns them) and the account-owned cards
   * to upsert. Purely card-domain: it knows nothing about views.
   *
   * <ul>
   *   <li>A card whose identifier is an {@code ootb:*} reference is diffed against the global catalog: unchanged ->
   *       kept as a lightweight reference (not persisted); customized -> minted as an account-owned card with a
   *       fresh identifier so its overrides (size, title, icon) persist. An {@code ootb:*} id that does not exist
   *       in the catalog is rejected.</li>
   *   <li>Any other card is account-owned; an id-less card is assigned a fresh identifier.</li>
   * </ul>
   *
   * <p>Mutates incoming card identifiers in place. Order is preserved and duplicates collapsed.
   */
  CardReferenceResolution resolveCardReferences(List<Card> incomingCards);

  String getCustomCardQuickLinkUrl(String accountId, String cardIdentifier, String quickLinkIdentifier);
  String getCardIconUrl(String accountId, String cardIdentifier);
}
