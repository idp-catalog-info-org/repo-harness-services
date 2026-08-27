/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class PipelineInputFileUpdateBucketWithLifecycleRuleTest extends CategoryTest {
  private static final int EXPECTED_LIFECYCLE_DAYS = 37;

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMigrate_ClientPresent_CallsConfigureLifecyclePolicy() throws Exception {
    ObjectStoreClient mockClient = mock(ObjectStoreClient.class);

    PipelineInputFileUpdateBucketWithLifecycleRule migration = createMigrationWithClient(mockClient);

    migration.migrate();

    verify(mockClient).configureLifecyclePolicy(eq(EXPECTED_LIFECYCLE_DAYS));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMigrate_ClientPresent_UsesCorrectLifecycleDays() throws Exception {
    ObjectStoreClient mockClient = mock(ObjectStoreClient.class);

    PipelineInputFileUpdateBucketWithLifecycleRule migration = createMigrationWithClient(mockClient);

    migration.migrate();

    ArgumentCaptor<Integer> daysCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(mockClient).configureLifecyclePolicy(daysCaptor.capture());

    assertThat(daysCaptor.getValue()).isEqualTo(EXPECTED_LIFECYCLE_DAYS);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMigrate_ConfigureLifecyclePolicyThrowsException_HandlesGracefully() throws Exception {
    ObjectStoreClient mockClient = mock(ObjectStoreClient.class);
    doThrow(new RuntimeException("Lifecycle configuration failed")).when(mockClient).configureLifecyclePolicy(anyInt());

    PipelineInputFileUpdateBucketWithLifecycleRule migration = createMigrationWithClient(mockClient);

    migration.migrate();

    verify(mockClient).configureLifecyclePolicy(eq(EXPECTED_LIFECYCLE_DAYS));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMigrate_NullClient_DoesNotThrowException() throws Exception {
    PipelineInputFileUpdateBucketWithLifecycleRule migration = createMigrationWithClient(null);

    migration.migrate();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testMigrate_CalledMultipleTimes_ExecutesEachTime() throws Exception {
    ObjectStoreClient mockClient = mock(ObjectStoreClient.class);

    PipelineInputFileUpdateBucketWithLifecycleRule migration = createMigrationWithClient(mockClient);

    migration.migrate();
    migration.migrate();

    verify(mockClient, times(2)).configureLifecyclePolicy(eq(EXPECTED_LIFECYCLE_DAYS));
  }

  private PipelineInputFileUpdateBucketWithLifecycleRule createMigrationWithClient(ObjectStoreClient client)
      throws Exception {
    PipelineInputFileUpdateBucketWithLifecycleRule migration = new PipelineInputFileUpdateBucketWithLifecycleRule();
    injectObjectStoreClient(migration, client);
    return migration;
  }

  private void injectObjectStoreClient(
      PipelineInputFileUpdateBucketWithLifecycleRule migration, ObjectStoreClient client) throws Exception {
    Field field = PipelineInputFileUpdateBucketWithLifecycleRule.class.getDeclaredField("objectStoreClient");
    field.setAccessible(true);
    field.set(migration, client);
  }
}
