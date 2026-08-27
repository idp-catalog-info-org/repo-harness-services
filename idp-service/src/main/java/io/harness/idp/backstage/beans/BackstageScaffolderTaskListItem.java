/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.common.DateUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTaskListItem {
  @JsonProperty("id") @NotNull String identifier;
  @NotNull String spec;
  @NotNull String status;
  @NotNull @JsonProperty("created_at") String createdAt;
  @JsonProperty("last_heartbeat_at") String lastHeartbeatAt;
  String secrets;
  @JsonProperty("created_by") String createdBy;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  static final String TIMESTAMP_WITH_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  public static List<BackstageScaffolderTaskEntity> toEntities(String accountIdentifier,
      List<BackstageScaffolderTaskListItem> backstageScaffolderTaskListItems,
      BackstageScaffolderTaskEntityRepository scaffolderTasksEntityRepository) {
    List<BackstageScaffolderTaskEntity> backstageScaffolderTasksEntities = new ArrayList<>();
    backstageScaffolderTaskListItems.forEach(backstageScaffolderTaskListItem -> {
      BackstageScaffolderTaskEntity backstageScaffolderTaskEntity = new BackstageScaffolderTaskEntity();

      Optional<BackstageScaffolderTaskEntity> optionalBackstageScaffolderTaskEntity =
          scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(
              accountIdentifier, backstageScaffolderTaskListItem.getIdentifier());
      optionalBackstageScaffolderTaskEntity.ifPresent(existingBackstageScaffolderTaskEntity
          -> backstageScaffolderTaskEntity.setId(existingBackstageScaffolderTaskEntity.getId()));

      backstageScaffolderTaskEntity.setAccountIdentifier(accountIdentifier);
      backstageScaffolderTaskEntity.setIdentifier(backstageScaffolderTaskListItem.getIdentifier());
      backstageScaffolderTaskEntity.setSpec(backstageScaffolderTaskListItem.getSpec());
      backstageScaffolderTaskEntity.setStatus(backstageScaffolderTaskListItem.getStatus());
      backstageScaffolderTaskEntity.setTaskCreatedAt(
          DateUtils.parseTimestamp(backstageScaffolderTaskListItem.getCreatedAt(), TIMESTAMP_WITH_TIMEZONE));
      backstageScaffolderTaskEntity.setLastHeartbeatAt(
          DateUtils.parseTimestamp(backstageScaffolderTaskListItem.getLastHeartbeatAt(), TIMESTAMP_WITH_TIMEZONE));
      backstageScaffolderTaskEntity.setSecrets(backstageScaffolderTaskListItem.getSecrets());
      backstageScaffolderTaskEntity.setTaskCreatedBy(backstageScaffolderTaskListItem.getCreatedBy());
      try {
        JsonNode spec = objectMapper.readTree(backstageScaffolderTaskListItem.getSpec());
        JsonNode templateInfo = spec.get("templateInfo");
        String entityRef = templateInfo.get("entityRef").asText();
        backstageScaffolderTaskEntity.setEntityRef(entityRef);
        if (templateInfo.get("entity") != null && templateInfo.get("entity").get("metadata") != null) {
          JsonNode metadata = templateInfo.get("entity").get("metadata");
          if (metadata.get("title") != null) {
            backstageScaffolderTaskEntity.setName(metadata.get("title").asText());
          } else if (metadata.get("name") != null) {
            backstageScaffolderTaskEntity.setName(metadata.get("name").asText());
          } else {
            backstageScaffolderTaskEntity.setName(entityRef.split("/")[1]);
          }
        } else {
          backstageScaffolderTaskEntity.setName(entityRef.split("/")[1]);
        }
      } catch (Exception e) {
        log.warn("Error enriching task {} for account {}: {}", backstageScaffolderTaskEntity.getIdentifier(),
            accountIdentifier, e.getMessage());
      }

      backstageScaffolderTasksEntities.add(backstageScaffolderTaskEntity);
    });
    return backstageScaffolderTasksEntities;
  }
}
