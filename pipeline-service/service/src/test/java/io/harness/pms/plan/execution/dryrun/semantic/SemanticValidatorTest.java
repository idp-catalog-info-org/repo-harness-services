/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.pms.plan.execution.dryrun.semantic.rules.ReferencedEntitiesExistRule;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SemanticValidatorTest extends CategoryTest {
  @Mock ConnectorResourceClient connectorClient;

  private static final String VALID_YAML = "pipeline:\n  identifier: p1\n";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private SemanticValidator validatorWith(Set<SemanticRule> rules) {
    return new SemanticValidator(connectorClient, rules);
  }

  private DryRunPipelineValidationResult finding(String id) {
    DryRunPipelineValidationResult r = new DryRunPipelineValidationResult();
    r.setValidationType("SEMANTIC");
    r.setEntityIdentifier(id);
    return r;
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void blankYamlReturnsEmpty() {
    SemanticValidator validator = validatorWith(Collections.emptySet());
    assertThat(validator.validate("", Collections.emptyList(), "acct", null, null, "0")).isEmpty();
    assertThat(validator.validate(null, Collections.emptyList(), "acct", null, null, "0")).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void invalidYamlReturnsSingleWarningAndSkipsRules() {
    Set<SemanticRule> rules = new HashSet<>();
    boolean[] invoked = {false};
    rules.add(ctx -> {
      invoked[0] = true;
      return Collections.emptyList();
    });
    SemanticValidator validator = validatorWith(rules);

    List<DryRunPipelineValidationResult> results =
        validator.validate("\t: : bad\n  - [unbalanced", Collections.emptyList(), "acct", null, null, "0");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getSeverity()).isEqualTo("WARNING");
    assertThat(results.get(0).getErrorMessage()).contains("YAML");
    assertThat(invoked[0]).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void twoRulesEachReturningOneFindingAggregates() {
    Set<SemanticRule> rules = new HashSet<>();
    rules.add(ctx -> Collections.singletonList(finding("a")));
    rules.add(ctx -> Collections.singletonList(finding("b")));

    List<DryRunPipelineValidationResult> results =
        validatorWith(rules).validate(VALID_YAML, Collections.emptyList(), "acct", null, null, "0");

    assertThat(results).hasSize(2);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void ruleThrowBecomesWarningAndSiblingStillRuns() {
    Set<SemanticRule> rules = new HashSet<>();
    rules.add(ctx -> { throw new RuntimeException("boom"); });
    rules.add(ctx -> Collections.singletonList(finding("sibling")));

    List<DryRunPipelineValidationResult> results =
        validatorWith(rules).validate(VALID_YAML, Collections.emptyList(), "acct", null, null, "0");

    assertThat(results).hasSize(2);
    assertThat(results).anyMatch(r -> "WARNING".equals(r.getSeverity()));
    assertThat(results).anyMatch(r -> "sibling".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorFetchFailureEmitsWarningAndStillRunsRules() {
    Set<SemanticRule> rules = new HashSet<>();
    boolean[] invoked = {false};
    rules.add(ctx -> {
      invoked[0] = true;
      assertThat(ctx.getConnectorsByRef()).isEmpty();
      return Collections.emptyList();
    });

    io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO connectorEntity =
        io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO.newBuilder()
            .setType(io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO.newBuilder()
                                  .setScope(io.harness.eventsframework.schemas.entity.ScopeProtoEnum.ACCOUNT)
                                  .setAccountIdentifier(com.google.protobuf.StringValue.of("acct"))
                                  .setIdentifier(com.google.protobuf.StringValue.of("myConn"))
                                  .build())
            .build();

    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("network down"));

      List<DryRunPipelineValidationResult> results = validatorWith(rules).validate(
          VALID_YAML, Collections.singletonList(connectorEntity), "acct", null, null, "0");

      assertThat(invoked[0]).isTrue();
      assertThat(results).anyMatch(r -> "WARNING".equals(r.getSeverity()));
    }
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorFetchFailureDoesNotFlagReferencedConnectorsAsMissing() {
    // Regression: a thrown connector fetch must NOT make Rule 1 report every referenced connector as
    // missing (which would flip is_valid=false on a transient outage). Only a fail-open WARNING.
    Set<SemanticRule> rules = new HashSet<>();
    rules.add(new ReferencedEntitiesExistRule());

    io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO connectorEntity =
        io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO.newBuilder()
            .setType(io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS)
            .setIdentifierRef(io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO.newBuilder()
                                  .setScope(io.harness.eventsframework.schemas.entity.ScopeProtoEnum.ACCOUNT)
                                  .setAccountIdentifier(com.google.protobuf.StringValue.of("acct"))
                                  .setIdentifier(com.google.protobuf.StringValue.of("myConn"))
                                  .build())
            .build();

    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("network down"));

      List<DryRunPipelineValidationResult> results = validatorWith(rules).validate(
          VALID_YAML, Collections.singletonList(connectorEntity), "acct", null, null, "0");

      // Zero ERROR findings from Rule 1 despite a referenced connector being unresolved.
      assertThat(results).noneMatch(r -> "ERROR".equals(r.getSeverity()));
      // Exactly the fail-open fetch WARNING is present.
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getSeverity()).isEqualTo("WARNING");
    }
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1ExtractsConnectorFromYamlAndFlagsMissing() {
    String v1Yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s\n"
        + "      run:\n"
        + "        container:\n"
        + "          connector: account.missingV1Conn\n";
    try (MockedStatic<NGRestUtils> mocked = Mockito.mockStatic(NGRestUtils.class)) {
      mocked.when(() -> NGRestUtils.getResponse(any())).thenReturn(List.of());
      List<DryRunPipelineValidationResult> results =
          validatorWith(Collections.singleton(new ReferencedEntitiesExistRule()))
              .validate(v1Yaml, List.of(), "acct", null, null, "1");
      assertThat(results).anyMatch(r
          -> "SEMANTIC".equals(r.getValidationType()) && "ERROR".equals(r.getSeverity())
              && "account.missingV1Conn".equals(r.getEntityIdentifier()));
    }
  }
}
