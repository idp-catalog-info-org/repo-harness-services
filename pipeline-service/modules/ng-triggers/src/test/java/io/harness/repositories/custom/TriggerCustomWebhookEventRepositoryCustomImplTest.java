/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.repositories.custom;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventStatus;
import io.harness.repositories.custom.impl.TriggerCustomWebhookEventRepositoryCustomImpl;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

public class TriggerCustomWebhookEventRepositoryCustomImplTest {
  @InjectMocks TriggerCustomWebhookEventRepositoryCustomImpl triggerCustomWebhookEventRepositoryCustom;
  @Mock MongoTemplate mongoTemplate;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdate() {
    TriggerCustomWebhookEvent triggerCustomWebhookEvent = TriggerCustomWebhookEvent.builder().build();

    Criteria criteria = new Criteria();
    when(mongoTemplate.findAndModify((Query) any(Query.class), (UpdateDefinition) any(UpdateDefinition.class), any(),
             eq(TriggerCustomWebhookEvent.class)))
        .thenReturn(triggerCustomWebhookEvent);
    assertThat(triggerCustomWebhookEventRepositoryCustom.update(
                   criteria, 1, TriggerCustomWebhookEventStatus.PROCESSING.name()))
        .isEqualTo(triggerCustomWebhookEvent);

    // Exception
    when(mongoTemplate.findAndModify((Query) any(Query.class), (UpdateDefinition) any(UpdateDefinition.class), any(),
             eq(TriggerCustomWebhookEvent.class)))
        .thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(()
                           -> triggerCustomWebhookEventRepositoryCustom.update(
                               criteria, 1, TriggerCustomWebhookEventStatus.PROCESSING.name()))
        .hasMessage("message")
        .isInstanceOf(InvalidRequestException.class);
  }
}
