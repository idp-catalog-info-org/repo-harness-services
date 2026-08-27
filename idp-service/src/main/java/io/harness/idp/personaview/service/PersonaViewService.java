/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.PersonaView;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;
import io.harness.spec.server.idp.v1.model.SavePersonaViewRequest;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface PersonaViewService {
  /**
   * Returns the synthetic Developer's View (always first, resolved from the account's homepage) followed by
   * OOTB views ({@code platform}, {@code leadership}) and the custom views the user belongs to (by user group),
   * with custom views ordered by name.
   */
  List<PersonaView> getPersonaViewsForUser(String accountIdentifier);

  /**
   * Admin list — surfaces every persona view manageable by the account admin: the synthetic Developer's View
   * (always first, on page 0), the OOTB views ({@code platform}, {@code leadership}) and all custom views.
   * Pagination, sort and search apply across the union. Edits on OOTB rows go through
   * {@link #savePersonaView(String, String, SavePersonaViewRequest)} as usual; the Developer's View itself
   * is read-only via these APIs.
   */
  Page<PersonaView> listPersonaViews(String accountIdentifier, Pageable pageable, String searchTerm);

  PersonaViewResponse getPersonaView(String accountIdentifier, String identifier);

  PersonaViewResponse savePersonaView(String accountIdentifier, String identifier, SavePersonaViewRequest request);

  void deletePersonaView(String accountIdentifier, String identifier);

  /**
   * Seed the OOTB persona views ({@code platform}, {@code leadership}) for {@code accountIdentifier} if they are
   * not already present. Idempotent and race-safe: relies on the unique index on {@code (accountIdentifier,
   * identifier)} for concurrency. Called from {@code ProvisionServiceImpl#provision} for new accounts and from
   * the catch-up migration for accounts provisioned before this code shipped.
   */
  void seedOotbPersonaViewsIfNotAlready(String accountIdentifier);
}
