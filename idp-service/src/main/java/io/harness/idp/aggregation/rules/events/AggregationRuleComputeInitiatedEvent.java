/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_AGGREGATION_RULE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AggregationRuleComputeInitiatedEvent implements Event {
  public static final String AGGREGATION_RULE_COMPUTE_INITIATED = "AggregationRuleComputeInitiated";
  private String accountIdentifier;
  private String aggregationRuleIdentifier;
  private String aggregationRuleName;

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, aggregationRuleName);
    return Resource.builder().identifier(aggregationRuleIdentifier).type(IDP_AGGREGATION_RULE).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return AGGREGATION_RULE_COMPUTE_INITIATED;
  }
}
