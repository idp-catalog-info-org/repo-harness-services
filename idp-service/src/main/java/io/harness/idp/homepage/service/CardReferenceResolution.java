/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.Card;

import java.util.List;
import lombok.Value;

/**
 * Outcome of splitting a view's incoming cards into what is stored on the owning view and what is persisted as
 * account-owned card rows.
 *
 * <ul>
 *   <li>{@link #orderedIdentifiers} — the ordered, de-duplicated identifier list to persist on the view/layout. It
 *       mixes {@code ootb:*} references (cards left at their global catalog defaults, resolved at read time) and
 *       account-scoped identifiers (user-owned cards and customized OOTB cards).</li>
 *   <li>{@link #accountOwnedCards} — the cards to upsert under the account via
 *       {@link CardService#saveAllCards(List, String)}. Excludes pure {@code ootb:*} references, which are never
 *       written under a customer account.</li>
 * </ul>
 */
@Value
@OwnedBy(HarnessTeam.IDP)
public class CardReferenceResolution {
  List<String> orderedIdentifiers;
  List<Card> accountOwnedCards;
}
