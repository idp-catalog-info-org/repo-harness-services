/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTaskListItemTest extends CategoryTest {
  @Mock private BackstageScaffolderTaskEntityRepository scaffolderTasksEntityRepository;

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_IDENTIFIER = "test-identifier";
  private static final String TEST_SPEC = "{\"type\":\"template\"}";
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
    BackstageScaffolderTaskListItem item = BackstageScaffolderTaskListItem.builder()
                                               .identifier(TEST_IDENTIFIER)
                                               .spec(TEST_SPEC)
                                               .status(TEST_STATUS)
                                               .createdAt(TEST_CREATED_AT)
                                               .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                                               .secrets(TEST_SECRETS)
                                               .createdBy(TEST_CREATED_BY)
                                               .build();

    assertThat(item).isNotNull();
    assertThat(item.getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(item.getSpec()).isEqualTo(TEST_SPEC);
    assertThat(item.getStatus()).isEqualTo(TEST_STATUS);
    assertThat(item.getCreatedAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(item.getLastHeartbeatAt()).isEqualTo(TEST_HEARTBEAT_AT);
    assertThat(item.getSecrets()).isEqualTo(TEST_SECRETS);
    assertThat(item.getCreatedBy()).isEqualTo(TEST_CREATED_BY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntities_NewTasks() {
    List<BackstageScaffolderTaskListItem> items = new ArrayList<>();
    items.add(BackstageScaffolderTaskListItem.builder()
                  .identifier(TEST_IDENTIFIER)
                  .spec(TEST_SPEC)
                  .status(TEST_STATUS)
                  .createdAt(TEST_CREATED_AT)
                  .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                  .secrets(TEST_SECRETS)
                  .createdBy(TEST_CREATED_BY)
                  .build());

    items.add(BackstageScaffolderTaskListItem.builder()
                  .identifier("identifier-2")
                  .spec(TEST_SPEC)
                  .status("processing")
                  .createdAt(TEST_CREATED_AT)
                  .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                  .build());

    when(scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<BackstageScaffolderTaskEntity> entities =
        BackstageScaffolderTaskListItem.toEntities(TEST_ACCOUNT_ID, items, scaffolderTasksEntityRepository);

    assertThat(entities).isNotNull();
    assertThat(entities).hasSize(2);
    assertThat(entities.get(0).getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entities.get(0).getIdentifier()).isEqualTo(TEST_IDENTIFIER);
    assertThat(entities.get(0).getStatus()).isEqualTo(TEST_STATUS);
    assertThat(entities.get(1).getIdentifier()).isEqualTo("identifier-2");
    assertThat(entities.get(1).getStatus()).isEqualTo("processing");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntities_ExistingTasks() {
    List<BackstageScaffolderTaskListItem> items = new ArrayList<>();
    items.add(BackstageScaffolderTaskListItem.builder()
                  .identifier(TEST_IDENTIFIER)
                  .spec(TEST_SPEC)
                  .status(TEST_STATUS)
                  .createdAt(TEST_CREATED_AT)
                  .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                  .build());

    BackstageScaffolderTaskEntity existingEntity = new BackstageScaffolderTaskEntity();
    existingEntity.setId("existing-id");
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_ID);
    existingEntity.setIdentifier(TEST_IDENTIFIER);

    when(scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_ID, TEST_IDENTIFIER))
        .thenReturn(Optional.of(existingEntity));

    List<BackstageScaffolderTaskEntity> entities =
        BackstageScaffolderTaskListItem.toEntities(TEST_ACCOUNT_ID, items, scaffolderTasksEntityRepository);

    assertThat(entities).isNotNull();
    assertThat(entities).hasSize(1);
    assertThat(entities.get(0).getId()).isEqualTo("existing-id");
    assertThat(entities.get(0).getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entities.get(0).getIdentifier()).isEqualTo(TEST_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntities_EmptyList() {
    List<BackstageScaffolderTaskListItem> items = new ArrayList<>();

    List<BackstageScaffolderTaskEntity> entities =
        BackstageScaffolderTaskListItem.toEntities(TEST_ACCOUNT_ID, items, scaffolderTasksEntityRepository);

    assertThat(entities).isNotNull();
    assertThat(entities).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToEntities_WithHeartbeat() {
    List<BackstageScaffolderTaskListItem> items = new ArrayList<>();
    items.add(BackstageScaffolderTaskListItem.builder()
                  .identifier(TEST_IDENTIFIER)
                  .spec(TEST_SPEC)
                  .status(TEST_STATUS)
                  .createdAt(TEST_CREATED_AT)
                  .lastHeartbeatAt(TEST_HEARTBEAT_AT)
                  .build());

    when(scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<BackstageScaffolderTaskEntity> entities =
        BackstageScaffolderTaskListItem.toEntities(TEST_ACCOUNT_ID, items, scaffolderTasksEntityRepository);

    assertThat(entities).isNotNull();
    assertThat(entities).hasSize(1);
    assertThat(entities.get(0).getLastHeartbeatAt()).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    BackstageScaffolderTaskListItem item1 = BackstageScaffolderTaskListItem.builder()
                                                .identifier(TEST_IDENTIFIER)
                                                .spec(TEST_SPEC)
                                                .status(TEST_STATUS)
                                                .createdAt(TEST_CREATED_AT)
                                                .build();

    BackstageScaffolderTaskListItem item2 = BackstageScaffolderTaskListItem.builder()
                                                .identifier(TEST_IDENTIFIER)
                                                .spec(TEST_SPEC)
                                                .status(TEST_STATUS)
                                                .createdAt(TEST_CREATED_AT)
                                                .build();

    BackstageScaffolderTaskListItem item3 = BackstageScaffolderTaskListItem.builder()
                                                .identifier("different")
                                                .spec(TEST_SPEC)
                                                .status(TEST_STATUS)
                                                .createdAt(TEST_CREATED_AT)
                                                .build();

    assertThat(item1).isEqualTo(item2);
    assertThat(item1).isNotEqualTo(item3);
    assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
  }
}
