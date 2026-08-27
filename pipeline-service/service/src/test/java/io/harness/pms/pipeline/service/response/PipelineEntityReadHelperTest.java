/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.response;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;

import java.util.Collections;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

public class PipelineEntityReadHelperTest extends CategoryTest {
  @Mock SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;
  @Mock MongoTemplate secondaryMongoTemplate;

  private PipelineEntityReadHelper readHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(secondaryMongoTemplateHolder.getSecondaryMongoTemplate()).thenReturn(secondaryMongoTemplate);
    readHelper = new PipelineEntityReadHelper(secondaryMongoTemplateHolder);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testAggregateDelegatesToSecondaryMongoTemplate() {
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(Criteria.where("identifier").is("p")));
    AggregationResults<TestProjection> mockedResults =
        new AggregationResults<>(Collections.emptyList(), new Document());
    when(secondaryMongoTemplate.aggregate(same(aggregation), eq(PipelineEntity.class), eq(TestProjection.class)))
        .thenReturn(mockedResults);

    AggregationResults<TestProjection> result = readHelper.aggregate(aggregation, TestProjection.class);

    assertThat(result).isSameAs(mockedResults);
    verify(secondaryMongoTemplate, times(1))
        .aggregate(same(aggregation), eq(PipelineEntity.class), eq(TestProjection.class));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testAggregateUsesCallerProvidedOutputType() {
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(Criteria.where("identifier").is("p")));
    AggregationResults<String> mockedResults = new AggregationResults<>(Collections.emptyList(), new Document());
    when(secondaryMongoTemplate.aggregate(same(aggregation), eq(PipelineEntity.class), eq(String.class)))
        .thenReturn(mockedResults);

    AggregationResults<String> result = readHelper.aggregate(aggregation, String.class);

    assertThat(result).isSameAs(mockedResults);
  }

  // Lightweight projection target used to assert the helper forwards the output type generic.
  private static class TestProjection {
    @SuppressWarnings("unused") String repo;
  }
}
