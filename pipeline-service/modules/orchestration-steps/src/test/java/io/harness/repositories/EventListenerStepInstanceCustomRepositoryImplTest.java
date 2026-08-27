/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationStepsTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;

import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

public class EventListenerStepInstanceCustomRepositoryImplTest extends OrchestrationStepsTestBase {
  @Mock MongoTemplate mongoTemplate;
  EventListenerStepInstanceCustomRepository eventListenerStepInstanceCustomRepository;

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testFindAll() {
    eventListenerStepInstanceCustomRepository = new EventListenerStepInstanceCustomRepositoryImpl(mongoTemplate);
    Criteria criteria = new Criteria();
    Stream<EventListenerStepInstance> mockStream = Stream.<EventListenerStepInstance>builder().build();
    doReturn(mockStream).when(mongoTemplate).stream(any(), any());
    eventListenerStepInstanceCustomRepository.findAll(criteria);
    verify(mongoTemplate, times(1)).stream(any(), any());
  }
}
