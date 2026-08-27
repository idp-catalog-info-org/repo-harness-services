/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CompositeAggregationProcessorTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock private AggregationProcessor processor1;
  @Mock private AggregationProcessor processor2;
  @Mock private AggregationProcessor processor3;

  CompositeAggregationProcessor compositeProcessor;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMultipleProcessors() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 10.0, AggregationRuleEntity.Scope.ACCOUNT);
    AggregationRulesDTO dto2 = createAggregationRulesDTO("entity-2", 20.0, AggregationRuleEntity.Scope.ORGANIZATION);
    AggregationRulesDTO dto3 = createAggregationRulesDTO("entity-3", 30.0, AggregationRuleEntity.Scope.PROJECT);
    AggregationRulesDTO dto4 = createAggregationRulesDTO("entity-4", 40.0, AggregationRuleEntity.Scope.SYSTEM);

    when(processor1.process()).thenReturn(Arrays.asList(dto1, dto2));
    when(processor2.process()).thenReturn(List.of(dto3));
    when(processor3.process()).thenReturn(List.of(dto4));

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2, processor3);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    List<AggregationRulesDTO> result = compositeProcessor.process();
    assertThat(result).hasSize(4);
    assertThat(result).containsExactly(dto1, dto2, dto3, dto4);

    verify(processor1, times(1)).process();
    verify(processor2, times(1)).process();
    verify(processor3, times(1)).process();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithSingleProcessor() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 50.0, AggregationRuleEntity.Scope.ACCOUNT);

    when(processor1.process()).thenReturn(List.of(dto1));

    List<AggregationProcessor> processors = List.of(processor1);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    List<AggregationRulesDTO> result = compositeProcessor.process();
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(dto1);

    verify(processor1, times(1)).process();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithEmptyProcessorList() {
    List<AggregationProcessor> processors = Collections.emptyList();
    compositeProcessor = new CompositeAggregationProcessor(processors);

    List<AggregationRulesDTO> result = compositeProcessor.process();

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithProcessorsReturningEmptyLists() {
    when(processor1.process()).thenReturn(Collections.emptyList());
    when(processor2.process()).thenReturn(Collections.emptyList());

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2);
    compositeProcessor = new CompositeAggregationProcessor(processors);
    List<AggregationRulesDTO> result = compositeProcessor.process();
    assertThat(result).isEmpty();

    verify(processor1, times(1)).process();
    verify(processor2, times(1)).process();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMixedResults() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 100.0, AggregationRuleEntity.Scope.ACCOUNT);

    when(processor1.process()).thenReturn(List.of(dto1));
    when(processor2.process()).thenReturn(Collections.emptyList());
    when(processor3.process()).thenReturn(Collections.emptyList());

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2, processor3);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    List<AggregationRulesDTO> result = compositeProcessor.process();

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(dto1);

    verify(processor1, times(1)).process();
    verify(processor2, times(1)).process();
    verify(processor3, times(1)).process();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithMultipleProcessors() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 10.0, AggregationRuleEntity.Scope.ACCOUNT);
    AggregationRulesDTO dto2 = createAggregationRulesDTO("entity-2", 20.0, AggregationRuleEntity.Scope.ORGANIZATION);
    List<AggregationRulesDTO> aggregationRulesDTOs = Arrays.asList(dto1, dto2);

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2, processor3);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    compositeProcessor.save(aggregationRulesDTOs);

    verify(processor1, times(1)).save(aggregationRulesDTOs);
    verify(processor2, times(1)).save(aggregationRulesDTOs);
    verify(processor3, times(1)).save(aggregationRulesDTOs);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithEmptyDTOList() {
    List<AggregationRulesDTO> emptyList = Collections.emptyList();

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    compositeProcessor.save(emptyList);
    verify(processor1, times(1)).save(emptyList);
    verify(processor2, times(1)).save(emptyList);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithSingleProcessor() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 75.0, AggregationRuleEntity.Scope.PROJECT);
    List<AggregationRulesDTO> aggregationRulesDTOs = List.of(dto1);

    List<AggregationProcessor> processors = List.of(processor1);
    compositeProcessor = new CompositeAggregationProcessor(processors);
    compositeProcessor.save(aggregationRulesDTOs);
    verify(processor1, times(1)).save(aggregationRulesDTOs);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessOrderPreservation() {
    AggregationRulesDTO dto1 = createAggregationRulesDTO("entity-1", 1.0, AggregationRuleEntity.Scope.ACCOUNT);
    AggregationRulesDTO dto2 = createAggregationRulesDTO("entity-2", 2.0, AggregationRuleEntity.Scope.ORGANIZATION);
    AggregationRulesDTO dto3 = createAggregationRulesDTO("entity-3", 3.0, AggregationRuleEntity.Scope.PROJECT);
    AggregationRulesDTO dto4 = createAggregationRulesDTO("entity-4", 4.0, AggregationRuleEntity.Scope.SYSTEM);

    when(processor1.process()).thenReturn(Arrays.asList(dto1, dto2));
    when(processor2.process()).thenReturn(List.of(dto3));
    when(processor3.process()).thenReturn(List.of(dto4));

    List<AggregationProcessor> processors = Arrays.asList(processor1, processor2, processor3);
    compositeProcessor = new CompositeAggregationProcessor(processors);

    List<AggregationRulesDTO> result = compositeProcessor.process();
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).isEqualTo(dto1);
    assertThat(result.get(1)).isEqualTo(dto2);
    assertThat(result.get(2)).isEqualTo(dto3);
    assertThat(result.get(3)).isEqualTo(dto4);
  }

  private AggregationRulesDTO createAggregationRulesDTO(
      String uniqueId, Double aggregationValue, AggregationRuleEntity.Scope scope) {
    return AggregationRulesDTO.builder()
        .uniqueId(uniqueId)
        .aggregationValue(aggregationValue)
        .processedScope(scope)
        .children(Collections.emptyList())
        .build();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
