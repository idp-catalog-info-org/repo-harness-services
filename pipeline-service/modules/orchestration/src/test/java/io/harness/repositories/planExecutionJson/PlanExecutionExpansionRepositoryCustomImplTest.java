/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.planExecutionJson;

import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.PlanExecutionExpansion;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class PlanExecutionExpansionRepositoryCustomImplTest extends CategoryTest {
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock MongoTemplate mongoTemplate;
  @Mock PersistentLocker persistentLocker;
  @InjectMocks PlanExecutionExpansionRepositoryCustomImpl repository;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateSkipsLockWhenTimeoutIsZero() {
    Update update = new Update().set("field", "value");

    repository.update(PLAN_EXECUTION_ID, update, 0);

    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
    verify(mongoTemplate, times(1)).updateFirst(any(Query.class), eq(update), eq(PlanExecutionExpansion.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateSkipsLockWhenTimeoutIsNegative() {
    Update update = new Update().set("field", "value");

    repository.update(PLAN_EXECUTION_ID, update, -1);

    verify(persistentLocker, never()).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
    verify(mongoTemplate, times(1)).updateFirst(any(Query.class), eq(update), eq(PlanExecutionExpansion.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateAcquiresLockWhenTimeoutIsPositive() {
    Update update = new Update().set("field", "value");
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(RedisAcquiredLock.builder().build());

    repository.update(PLAN_EXECUTION_ID, update, 1);

    verify(persistentLocker, times(1)).waitToAcquireLockOptional(anyString(), any(Duration.class), any(Duration.class));
    verify(mongoTemplate, times(1)).updateFirst(any(Query.class), eq(update), eq(PlanExecutionExpansion.class));
  }
}
