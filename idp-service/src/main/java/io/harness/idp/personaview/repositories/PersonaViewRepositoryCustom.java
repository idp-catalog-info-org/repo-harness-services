/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.personaview.entities.PersonaViewEntity;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface PersonaViewRepositoryCustom {
  /**
   * Admin list — every persona view row stored for the account (both OOTB and custom). Used by
   * {@code GET /v1/persona-views}. The service layer prepends the synthetic Developer's View before returning.
   */
  Page<PersonaViewEntity> findViewsForAdmin(String accountIdentifier, Pageable pageable, String searchTerm);

  /**
   * All views (OOTB + custom) visible to a user based on their user-group membership. Used by
   * {@code GET /v1/persona-views/me}.
   */
  List<PersonaViewEntity> findViewsForUser(String accountIdentifier, List<String> userGroupIdentifiers);
}
