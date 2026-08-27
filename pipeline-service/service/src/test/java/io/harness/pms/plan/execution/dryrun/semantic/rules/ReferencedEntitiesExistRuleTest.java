/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic.rules;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ReferencedEntitiesExistRuleTest extends CategoryTest {
  private final ReferencedEntitiesExistRule rule = new ReferencedEntitiesExistRule();

  private EntityDetailProtoDTO connector(String identifier) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(EntityTypeProtoEnum.CONNECTORS)
        .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                              .setScope(ScopeProtoEnum.ACCOUNT)
                              .setAccountIdentifier(StringValue.of("acct"))
                              .setIdentifier(StringValue.of(identifier))
                              .build())
        .build();
  }

  private EntityDetailProtoDTO template(String identifier) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(EntityTypeProtoEnum.TEMPLATE)
        .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                              .setScope(ScopeProtoEnum.ACCOUNT)
                              .setAccountIdentifier(StringValue.of("acct"))
                              .setIdentifier(StringValue.of(identifier))
                              .build())
        .build();
  }

  private SemanticValidationContext ctx(
      List<EntityDetailProtoDTO> referred, Map<String, ConnectorInfoDTO> connectorsByRef) {
    return SemanticValidationContext.builder()
        .referredEntities(referred)
        .connectorsByRef(connectorsByRef)
        .accountIdentifier("acct")
        .build();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void resolvedConnectorProducesNoFinding() {
    Map<String, ConnectorInfoDTO> byRef = new HashMap<>();
    byRef.put("account.myConn", ConnectorInfoDTO.builder().identifier("myConn").build());
    List<DryRunPipelineValidationResult> findings = rule.apply(ctx(List.of(connector("myConn")), byRef));
    assertThat(findings).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void missingConnectorProducesError() {
    List<DryRunPipelineValidationResult> findings =
        rule.apply(ctx(List.of(connector("ghost")), Collections.emptyMap()));
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(findings.get(0).getEntityType()).isEqualTo("CONNECTOR");
    assertThat(findings.get(0).getEntityIdentifier()).isEqualTo("account.ghost");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorFetchFailedSkipsExistenceCheck() {
    // When the batch fetch threw, connectorsByRef is empty for a transient reason. The rule must
    // skip rather than flag every reference as missing (fail-open).
    SemanticValidationContext ctx = SemanticValidationContext.builder()
                                        .referredEntities(List.of(connector("ghost")))
                                        .connectorsByRef(Collections.emptyMap())
                                        .connectorFetchFailed(true)
                                        .accountIdentifier("acct")
                                        .build();
    assertThat(rule.apply(ctx)).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void templateEntryIsOutOfScope() {
    List<DryRunPipelineValidationResult> findings =
        rule.apply(ctx(List.of(template("myTemplate")), Collections.emptyMap()));
    assertThat(findings).isEmpty();
  }
}
