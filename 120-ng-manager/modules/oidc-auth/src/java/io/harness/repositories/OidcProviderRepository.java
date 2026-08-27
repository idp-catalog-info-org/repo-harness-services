/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.oidc_auth.entity.OidcProviderSettings;

import software.wings.beans.sso.SSOType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface OidcProviderRepository
    extends PagingAndSortingRepository<OidcProviderSettings, String>, CrudRepository<OidcProviderSettings, String> {
  Optional<OidcProviderSettings> findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
  Optional<OidcProviderSettings> findByIdentifierAndType(String identifier, SSOType ssoType);
  List<OidcProviderSettings> findByAccountIdentifierAndType(String accountIdentifier, SSOType ssoType);
  void deleteByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
}
