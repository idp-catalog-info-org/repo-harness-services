/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class PersonaViewConstants {
  public static final String LOG_PREFIX = "[PersonaViews]";

  /**
   * Reserved account identifier under which OOTB (Harness-managed) cards are stored once globally. Single source of
   * truth lives in {@link Constants#GLOBAL_ACCOUNT_ID} so the homepage layer can share it without a package cycle.
   */
  public static final String GLOBAL_ACCOUNT_ID = Constants.GLOBAL_ACCOUNT_ID;

  /**
   * Identifier prefix reserved for OOTB cards. Any card identifier starting with this prefix resolves under
   * {@link #GLOBAL_ACCOUNT_ID} and is rejected on user-facing write paths. Single source of truth lives in
   * {@link Constants#OOTB_CARD_IDENTIFIER_PREFIX} so the homepage layer can share it without a package cycle.
   */
  public static final String OOTB_IDENTIFIER_PREFIX = Constants.OOTB_CARD_IDENTIFIER_PREFIX;

  public static final String PLATFORM_VIEW_IDENTIFIER = "platform";
  public static final String LEADERSHIP_VIEW_IDENTIFIER = "leadership";

  /**
   * Classpath resource defining the OOTB persona view templates ({@code platform}, {@code leadership}) seeded
   * per account during IDP provisioning. Each entry is a {@code (identifier, name, cards)} tuple. Cards are
   * {@code ootb:*} references resolved at read time against {@link #GLOBAL_ACCOUNT_ID}.
   */
  public static final String OOTB_PERSONA_VIEWS_RESOURCE = "migrations/persona-views-ootb.json";

  /**
   * Synthetic identifier for the homepage surfaced inside the persona view list. The underlying data lives in the
   * {@code homePageLayouts} collection — there is no {@link io.harness.idp.personaview.entities.PersonaViewEntity}
   * row for this identifier. The Developer's View is fully immutable through the persona view APIs (no card,
   * name, description, or user-group edits); all edits must go through {@code /v1/home-page-layout}. It is
   * always visible to every user in the account.
   */
  public static final String DEVELOPER_VIEW_IDENTIFIER = "developer";
  public static final String DEVELOPER_VIEW_NAME = "Developer's View";

  public static final List<String> OOTB_VIEW_IDENTIFIERS =
      List.of(PLATFORM_VIEW_IDENTIFIER, LEADERSHIP_VIEW_IDENTIFIER);

  public static boolean isOotbIdentifier(String identifier) {
    return Constants.isOotbCardIdentifier(identifier);
  }

  public static boolean isOotbView(String viewIdentifier) {
    return OOTB_VIEW_IDENTIFIERS.contains(viewIdentifier) || isDeveloperView(viewIdentifier);
  }

  public static boolean isDeveloperView(String viewIdentifier) {
    return DEVELOPER_VIEW_IDENTIFIER.equals(viewIdentifier);
  }
}
