/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.common.JacksonUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTask {
  @JsonProperty("id") @NotNull String identifier;
  @NotNull BackstageCatalogTemplateEntity.Spec spec;
  @NotNull String status;
  @NotNull String createdAt;
  String lastHeartbeatAt;
  String secrets;
  String createdBy;

  public static BackstageScaffolderTaskEntity toEntity(String accountIdentifier, BackstageScaffolderTask task,
      BackstageScaffolderTaskEntityRepository scaffolderTasksEntityRepository) {
    BackstageScaffolderTaskListItem taskListItem = toListItem(task);
    return BackstageScaffolderTaskListItem
        .toEntities(accountIdentifier, Collections.singletonList(taskListItem), scaffolderTasksEntityRepository)
        .get(0);
  }

  private static BackstageScaffolderTaskListItem toListItem(BackstageScaffolderTask task) {
    return BackstageScaffolderTaskListItem.builder()
        .identifier(task.getIdentifier())
        .spec(JacksonUtils.write(task.getSpec()))
        .status(task.getStatus())
        .createdAt(task.getCreatedAt())
        .lastHeartbeatAt(task.getLastHeartbeatAt())
        .secrets(task.getSecrets())
        .createdBy(task.getCreatedBy())
        .build();
  }
}
