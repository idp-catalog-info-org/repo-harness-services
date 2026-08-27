/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.service.impl;

import static io.harness.rule.OwnerRule.RISHABH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity.ExecutionRetentionReconciliationEntityKeys;
import io.harness.repositories.reconciliation.ExecutionRetentionReconciliationEntityRepository;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionRetentionReconciliationEntityServiceImplTest extends CategoryTest {
  @Mock ExecutionRetentionReconciliationEntityRepository reconciliationEntityRepository;
  @InjectMocks ExecutionRetentionReconciliationEntityServiceImpl reconciliationEntityService;

  private static final String uuid = "uuid";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSave() {
    ExecutionRetentionReconciliationEntity reconciliationEntity =
        ExecutionRetentionReconciliationEntity.builder().build();
    when(reconciliationEntityRepository.save(eq(reconciliationEntity))).thenReturn(reconciliationEntity);
    ExecutionRetentionReconciliationEntity gotReconciliationEntity =
        reconciliationEntityService.save(reconciliationEntity);
    assertEquals(gotReconciliationEntity, reconciliationEntity);
    verify(reconciliationEntityRepository, times(1)).save(reconciliationEntity);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateSyncCompletedUntil() {
    ExecutionRetentionReconciliationEntity reconciliationEntity =
        ExecutionRetentionReconciliationEntity.builder().uuid(uuid).syncCompletedUntil(1000L).build();
    Update update = new Update();
    update.set(ExecutionRetentionReconciliationEntityKeys.syncCompletedUntil, 1000L);
    when(reconciliationEntityRepository.update(eq(uuid), eq(update))).thenReturn(reconciliationEntity);

    ExecutionRetentionReconciliationEntity gotReconciliationEntity =
        reconciliationEntityService.updateSyncCompletedUntil(uuid, 1000L);
    assertEquals(gotReconciliationEntity, reconciliationEntity);
    verify(reconciliationEntityRepository, times(1)).update(uuid, update);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateNextIteration() {
    ExecutionRetentionReconciliationEntity reconciliationEntity =
        ExecutionRetentionReconciliationEntity.builder().uuid(uuid).nextIteration(1000L).build();
    Update update = new Update();
    update.set(ExecutionRetentionReconciliationEntityKeys.nextIteration, 1000L);
    when(reconciliationEntityRepository.update(eq(uuid), eq(update))).thenReturn(reconciliationEntity);

    ExecutionRetentionReconciliationEntity gotReconciliationEntity =
        reconciliationEntityService.updateNextIteration(uuid, 1000L);
    assertEquals(gotReconciliationEntity, reconciliationEntity);
    verify(reconciliationEntityRepository, times(1)).update(uuid, update);
  }
}
