/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.service;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.aggregation.rules.processor.AggregationRulesProcessorFactory;
import io.harness.idp.aggregation.rules.repositories.AggregationRuleRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AggFormula;
import io.harness.spec.server.idp.v1.model.AggregationEntitySelectionCriteria;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsRequest;
import io.harness.spec.server.idp.v1.model.AggregationScopeLevel;
import io.harness.spec.server.idp.v1.model.AggregationType;
import io.harness.springdata.TransactionHelper;
import io.harness.springdata.TransactionHelper.TransactionFunction;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesServiceImplTest extends CategoryTest {
  @Mock private AggregationRuleRepository repository;
  @Mock private AggregationRulesHelper helper;
  @Mock private TransactionHelper txHelper;
  @Mock private io.harness.outbox.api.OutboxService outboxService;
  @Mock private AggregationRulesProcessorFactory processorFactory;

  private AggregationRulesServiceImpl service;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    service = new AggregationRulesServiceImpl(repository, helper, txHelper, outboxService, processorFactory);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testGetAggregationRulesEmpty() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<AggregationRuleEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
    when(helper.checkAggregationRulesRbac(any(), any(), any())).thenReturn(Collections.emptySet());
    when(repository.getAggregationRules(any(), any(Pageable.class), any())).thenReturn(emptyPage);
    Page<AggregationRuleEntity> result = service.getAggregationRules("acc", pageable, null);
    assertEquals(0, result.getContent().size());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testGetAggregationRuleNotFound() {
    when(repository.findByAccountIdentifierAndIdentifier("acc", "id")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.getAggregationRule("acc", "id"));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testCreateAggregationRuleValidationFails() {
    AggregationRuleDetailsRequest req =
        new AggregationRuleDetailsRequest().aggregationRule(new AggregationRuleDetails());
    assertThrows(InvalidRequestException.class, () -> service.createAggregationRule("acc", req));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testUpdateAggregationRuleWhenCalculating() {
    AggregationEntitySelectionCriteria aggregationEntitySelectionCriteria = new AggregationEntitySelectionCriteria();
    aggregationEntitySelectionCriteria.setKind("component");
    AggregationRuleDetails details = new AggregationRuleDetails()
                                         .identifier("id")
                                         .name("n")
                                         .fieldForAgg("f")
                                         .aggFormula(AggFormula.SUM)
                                         .scopesToAggregateAt(List.of(AggregationScopeLevel.ACCOUNT))
                                         .entitySelectionCriteria(aggregationEntitySelectionCriteria)
                                         .aggregationType(AggregationType.METRIC);
    AggregationRuleDetailsRequest req = new AggregationRuleDetailsRequest().aggregationRule(details);

    AggregationRuleEntity existing = new AggregationRuleEntity();
    existing.setAccountIdentifier("acc");
    existing.setIdentifier("id");
    existing.setStatus(AggregationRuleEntity.ComputedStatus.CALCULATING);

    when(helper.hasAggregationRulePermission(any(), any(), any())).thenReturn(true);
    when(repository.findByAccountIdentifierAndIdentifier("acc", "id")).thenReturn(existing);

    when(txHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionFunction<?> function = (TransactionFunction<?>) invocation.getArguments()[0];
      return function.execute();
    });

    // Verify the exception is thrown
    assertThrows(BadRequestException.class, () -> service.updateAggregationRule("acc", req));
  }
}
