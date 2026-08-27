/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Definition of a single OOTB persona view as declared in {@code migrations/persona-views-ootb.json}. Loaded
 * once at startup and used to seed per-account OOTB rows during IDP provisioning (see
 * {@code PersonaViewServiceImpl#seedOotbPersonaViewsIfNotAlready}). Adding a new OOTB view is a JSON-only
 * change — no Java edits required.
 */
@Data
@NoArgsConstructor
@OwnedBy(HarnessTeam.IDP)
public class OotbPersonaViewTemplate {
  private String identifier;
  private String name;
  private String description;
  private List<String> cards;
}
