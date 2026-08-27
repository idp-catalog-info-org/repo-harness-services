/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.ldap.entity.NGLdapSettings;

import software.wings.beans.sso.SSOType;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface LdapSettingsRepository extends PagingAndSortingRepository<NGLdapSettings, String>,
                                                CrudRepository<NGLdapSettings, String>, LdapSettingsRepositoryCustom {
  NGLdapSettings findByIdentifier(String identifier);
  NGLdapSettings findByIdentifierAndType(String identifier, SSOType ssoType);
  NGLdapSettings findByAccountIdentifierAndType(String identifier, SSOType ssoType);
}
