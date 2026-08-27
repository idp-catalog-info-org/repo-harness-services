/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ldap.entity.NGLdapSettings;

import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;

public interface LdapSettingsRepositoryCustom {
  Optional<NGLdapSettings> findOne(Criteria criteria);
}
