/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;

import lombok.experimental.UtilityClass;

/**
 * Shared helpers for turning connector references (proto or DTO) into the scoped YAML ref string
 * (e.g. {@code account.harnessImage}) used to key {@code connectorsByRef}. Keeping this in one place
 * guarantees the validator's map keys and the rules' lookups agree.
 */
@UtilityClass
@OwnedBy(PIPELINE)
public class SemanticRefUtils {
  /** Scoped ref for a referred-entity {@link IdentifierRefProtoDTO}, or null if unusable. */
  public String scopedRef(IdentifierRefProtoDTO proto) {
    if (proto == null) {
      return null;
    }
    IdentifierRef ref = IdentifierRef.builder()
                            .scope(mapScope(proto.getScope()))
                            .identifier(proto.getIdentifier().getValue())
                            .accountIdentifier(proto.getAccountIdentifier().getValue())
                            .orgIdentifier(emptyToNull(proto.getOrgIdentifier().getValue()))
                            .projectIdentifier(emptyToNull(proto.getProjectIdentifier().getValue()))
                            .build();
    String scopedRef = ref.buildScopedIdentifier();
    return scopedRef == null || scopedRef.isEmpty() ? null : scopedRef;
  }

  /**
   * Scoped ref for a resolved connector's scope + identifier. Scope is derived from the presence of
   * org/project (project-most wins), independent of the account id, so an account-scoped connector
   * (no org, no project) keys as {@code account.<id>} to match the referred-entity ref.
   */
  public String scopedRef(String orgIdentifier, String projectIdentifier, String identifier) {
    Scope scope;
    if (!isEmpty(projectIdentifier)) {
      scope = Scope.PROJECT;
    } else if (!isEmpty(orgIdentifier)) {
      scope = Scope.ORG;
    } else {
      scope = Scope.ACCOUNT;
    }
    return IdentifierRef.builder()
        .scope(scope)
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build()
        .buildScopedIdentifier();
  }

  private boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private Scope mapScope(ScopeProtoEnum scope) {
    switch (scope) {
      case ACCOUNT:
        return Scope.ACCOUNT;
      case ORG:
        return Scope.ORG;
      case PROJECT:
        return Scope.PROJECT;
      default:
        return Scope.UNKNOWN;
    }
  }

  /** Null when the value is null or empty, otherwise the value unchanged. Shared with the validator. */
  String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
