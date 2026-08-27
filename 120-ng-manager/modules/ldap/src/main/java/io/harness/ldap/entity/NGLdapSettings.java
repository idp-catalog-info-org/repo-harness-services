/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.entity;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.sso.entity.SSOSettings;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonCreator;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "NgLdapSettingsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@Persistent
@TypeAlias("NGLdapSettings")
public class NGLdapSettings extends SSOSettings implements ExecutionCapabilityDemander {
  @NotNull @Valid LdapConnectionSettings connectionSettings;

  @Valid List<LdapUserSettings> userSettingsList;

  @Valid List<LdapGroupSettings> groupSettingsList;

  private String cronExpression;

  boolean disabled;

  @Override
  public SSOType getType() {
    return SSOType.LDAP;
  }

  @JsonCreator
  @Builder
  public NGLdapSettings(String identifier, String name, String accountIdentifier, SSOType type, String url,
      LdapConnectionSettings connectionSettings, List<LdapUserSettings> userSettingsList,
      List<LdapGroupSettings> groupSettingsList, String cronExpression, boolean disabled) {
    super(type, name, identifier, url, accountIdentifier);
    this.connectionSettings = connectionSettings;
    this.userSettingsList = userSettingsList;
    this.groupSettingsList = groupSettingsList;
    this.cronExpression = cronExpression;
    this.disabled = disabled;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.add(SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(
        connectionSettings.getHost(), Integer.toString(connectionSettings.getPort())));
    if (isNotEmpty(connectionSettings.getDelegateSelectors())) {
      executionCapabilities.add(
          SelectorCapability.builder().selectors(connectionSettings.getDelegateSelectors()).build());
    }
    return executionCapabilities;
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    List<Long> nextIterations = getNextIterations();
    nextIterations = isEmpty(nextIterations) ? new ArrayList<>() : nextIterations;

    if (expandNextIterations(skipMissed, throttled, getCronExpression(), nextIterations)) {
      return isNotEmpty(nextIterations) ? nextIterations : Collections.singletonList(Long.MAX_VALUE);
    }

    return Collections.singletonList(Long.MAX_VALUE);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return EmptyPredicate.isEmpty(getNextIterations()) ? null : getNextIterations().get(0);
  }

  @Override
  public String getUuid() {
    return getId();
  }
}
