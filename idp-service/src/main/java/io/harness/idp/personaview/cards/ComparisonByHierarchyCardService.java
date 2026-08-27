/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.cards;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataRequest;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataResponse;

@OwnedBy(HarnessTeam.IDP)
public interface ComparisonByHierarchyCardService {
  /**
   * Build the Comparison by Hierarchy card payload for {@code personaViewIdentifier} under
   * {@code accountIdentifier}. The persona view must exist and must list a card of type
   * {@code COMPARISON_BY_HIERARCHY}; otherwise a {@link javax.ws.rs.NotFoundException} is thrown.
   *
   * <p>Rows are every Org ({@code scope=ORG}) or every Project ({@code scope=PROJECT}) in the account.
   * Each row carries the values of the requested aggregation rules (defaulting to the first 5 rules of
   * the account by {@code createdAt ASC} when none are specified) and a Gold/Silver/Bronze scorecard
   * compliance tally computed against fixed thresholds (90 / 70 / 50).
   */
  ComparisonByHierarchyCardDataResponse getData(
      String accountIdentifier, String personaViewIdentifier, ComparisonByHierarchyCardDataRequest request);
}
