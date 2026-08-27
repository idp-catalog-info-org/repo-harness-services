/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.ng.core.dto.UserBasicInfo;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Slf4j
public class UserGroupsFunctor extends HashMap<String, Object> implements LateBindingValue, RuntimeAbstractFunctor {
  private final transient UserGroupClient userGroupClient;
  private final transient Ambiance ambiance;
  private static final String USER_GROUPS_FUNCTOR_KEY = "userGroups";

  public UserGroupsFunctor(UserGroupClient userGroupClient, Ambiance ambiance) {
    this.userGroupClient = userGroupClient;
    this.ambiance = ambiance;
  }

  @Override
  public Object get(Object key) {
    String groupIdentifier = String.valueOf(key);

    if (EmptyPredicate.isEmpty(groupIdentifier)) {
      return null;
    }

    try {
      return getEmailsForUserGroup(groupIdentifier);
    } catch (Exception ex) {
      log.error("Error retrieving UserGroups for group id: {}", groupIdentifier, ex);
      throw new EngineFunctorException("Error retrieving UserGroups for group id: " + groupIdentifier, ex);
    }
  }

  @Override
  public Object bind() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (EmptyPredicate.isEmpty(accountId)) {
      return null;
    }

    return this;
  }

  private Set<String> getEmailsForUserGroup(String groupIdentifier) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(groupIdentifier, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    UserGroupResponseV2DTO userGroup = NGRestUtils.getResponse(
        userGroupClient.getUserGroupV2(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));

    if (userGroup == null || EmptyPredicate.isEmpty(userGroup.getUsers())) {
      log.info("User group {} does not have a users list.", groupIdentifier);
      return Collections.emptySet();
    }
    Set<String> groupEmails = userGroup.getUsers()
                                  .stream()
                                  .filter(Objects::nonNull)
                                  .map(UserBasicInfo::getEmail)
                                  .filter(StringUtils::isNotBlank)
                                  .collect(Collectors.toSet());
    if (EmptyPredicate.isEmpty(groupEmails)) {
      log.info("User group {} does not have any valid users (empty email list).", groupIdentifier);
      return Collections.emptySet();
    }

    return groupEmails;
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(USER_GROUPS_FUNCTOR_KEY);
  }
}
