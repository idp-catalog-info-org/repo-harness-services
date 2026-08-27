/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTaskTest extends CategoryTest {
  @Mock private BackstageScaffolderTaskEntityRepository scaffolderTasksEntityRepository;

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_IDENTIFIER = "test-identifier";
  private static final String TEST_STATUS = "completed";
  private static final String TEST_CREATED_AT = "2025-01-01T10:00:00.000Z";
  private static final String TEST_HEARTBEAT_AT = "2025-01-01T10:05:00.000Z";
  private static final String TEST_SECRETS = "secret-data";
  private static final String TEST_CREATED_BY = "user@example.com";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilder() {
    BackstageCatalogTemplateEntity.Spec spec =
        BackstageCatalogTemplateEntity.Spec.builder().type("template-type").owner("team").build();

    BackstageScaffolderTask task = BackstageScaffolderTask.builder()
                                       .identifier(TEST_IDENTIFIER)
                                       .spec(spec)
                                       .status(TEST_STATUS)
                                       .createdAt(TEST_CREATED_AT)
                                       .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                                       .secrets(TEST_SECRETS)
                                       .createdBy(TEST_CREATED_BY)
                                       .build();

    assertThat(task).isNotNull();
    assertThat(task.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(task.getSpec()).isEqualTo(spec);
    assertThat(task.getStatus()).isEqualTo(TEST_STATUS);
    assertThat(task.getCreatedAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(task.getLastHeartbeatAt()).isEqualTo(TEST_HEARTBEAT_AT);
    assertThat(task.getSecrets()).isEqualTo(TEST_SECRETS);
    assertThat(task.getCreatedBy()).isEqualTo(TEST_CREATED_BY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntity_NewTask() {
    BackstageCatalogTemplateEntity.Spec spec =
        BackstageCatalogTemplateEntity.Spec.builder().type("template-type").owner("team").build();

    BackstageScaffolderTask task = BackstageScaffolderTask.builder()
                                       .identifier(TEST_IDENTIFIER)
                                       .spec(spec)
                                       .status(TEST_STATUS)
                                       .createdAt(TEST_CREATED_AT)
                                       .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                                       .secrets(TEST_SECRETS)
                                       .createdBy(TEST_CREATED_BY)
                                       .build();

    when(scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.empty());

    BackstageScaffolderTaskEntity entity =
        BackstageScaffolderTask.toEntity(TEST_ACCOUNT_ID, task, scaffolderTasksEntityRepository);

    assertThat(entity).isNotNull();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entity.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(entity.getStatus()).isEqualTo(TEST_STATUS);
    assertThat(entity.getTaskCreatedBy()).isEqualTo(TEST_CREATED_BY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntity_ExistingTask() {
    BackstageCatalogTemplateEntity.Spec spec =
        BackstageCatalogTemplateEntity.Spec.builder().type("template-type").owner("team").build();

    BackstageScaffolderTask task = BackstageScaffolderTask.builder()
                                       .identifier(TEST_IDENTIFIER)
                                       .spec(spec)
                                       .status(TEST_STATUS)
                                       .createdAt(TEST_CREATED_AT)
                                       .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                                       .secrets(TEST_SECRETS)
                                       .createdBy(TEST_CREATED_BY)
                                       .build();

    BackstageScaffolderTaskEntity existingEntity = new BackstageScaffolderTaskEntity();
    existingEntity.setId("existing-id");
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_ID);
    existingEntity.setIdentifier(TEST_IDENTIFIER);

    when(scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_ID, TEST_IDENTIFIER))
        .thenReturn(Optional.of(existingEntity));

    BackstageScaffolderTaskEntity entity =
        BackstageScaffolderTask.toEntity(TEST_ACCOUNT_ID, task, scaffolderTasksEntityRepository);

    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("existing-id");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entity.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    BackstageCatalogTemplateEntity.Spec spec =
        BackstageCatalogTemplateEntity.Spec.builder().type("template-type").build();

    BackstageScaffolderTask task1 = BackstageScaffolderTask.builder()
                                        .identifier(TEST_IDENTIFIER)
                                        .spec(spec)
                                        .status(TEST_STATUS)
                                        .createdAt(TEST_CREATED_AT)
                                        .build();

    BackstageScaffolderTask task2 = BackstageScaffolderTask.builder()
                                        .identifier(TEST_IDENTIFIER)
                                        .spec(spec)
                                        .status(TEST_STATUS)
                                        .createdAt(TEST_CREATED_AT)
                                        .build();

    BackstageScaffolderTask task3 = BackstageScaffolderTask.builder()
                                        .identifier("different-id")
                                        .spec(spec)
                                        .status(TEST_STATUS)
                                        .createdAt(TEST_CREATED_AT)
                                        .build();

    assertThat(task1).isEqualTo(task2);
    assertThat(task1).isNotEqualTo(task3);
    assertThat(task1.hashCode()).isEqualTo(task2.hashCode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToString() {
    BackstageCatalogTemplateEntity.Spec spec =
        BackstageCatalogTemplateEntity.Spec.builder().type("template-type").build();

    BackstageScaffolderTask task = BackstageScaffolderTask.builder()
                                       .identifier(TEST_IDENTIFIER)
                                       .spec(spec)
                                       .status(TEST_STATUS)
                                       .createdAt(TEST_CREATED_AT)
                                       .build();

    String toString = task.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(TEST_IDENTIFIER);
    assertThat(toString).contains(TEST_STATUS);
  }
}
