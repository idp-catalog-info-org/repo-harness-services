/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.support;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates the inline text secrets that connectors reference during onboarding. Owns the default secret-manager
 * selection. Extracted verbatim from the former orchestration god object so behavior is unchanged: a secret whose
 * identifier already exists is updated (upsert) rather than duplicated, and a blank value materializes nothing.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class OnboardingSecretCreation {
  private static final String DEFAULT_SECRET_MANAGER = "harnessSecretManager";

  private final SecretCrudService secretCrudService;

  @Inject
  public OnboardingSecretCreation(SecretCrudService secretCrudService) {
    this.secretCrudService = secretCrudService;
  }

  /**
   * Creates (or updates, if the identifier already exists) an inline text secret and returns its identifier for use
   * as a connector secret ref. Returns null when there is no value to store, and records every created/updated
   * identifier into {@code createdSecrets}.
   */
  public String upsertSecret(ScopeInfo scopeInfo, String orgIdentifier, String projectIdentifier, String identifier,
      String value, List<String> createdSecrets) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String secretIdentifier = OnboardingIdentifiers.sanitizeIdentifier(identifier);
    SecretTextSpecDTO spec = SecretTextSpecDTO.builder()
                                 .secretManagerIdentifier(DEFAULT_SECRET_MANAGER)
                                 .valueType(ValueType.Inline)
                                 .value(value)
                                 .build();
    SecretDTOV2 secretDTO = SecretDTOV2.builder()
                                .identifier(secretIdentifier)
                                .name(secretIdentifier)
                                .type(SecretType.SecretText)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .spec(spec)
                                .build();
    Optional<SecretResponseWrapper> existing = secretCrudService.get(scopeInfo, secretIdentifier);
    SecretResponseWrapper saved = existing.isPresent()
        ? secretCrudService.update(scopeInfo, secretIdentifier, secretDTO)
        : secretCrudService.create(scopeInfo, secretDTO);
    createdSecrets.add(saved.getSecret().getIdentifier());
    return saved.getSecret().getIdentifier();
  }
}
