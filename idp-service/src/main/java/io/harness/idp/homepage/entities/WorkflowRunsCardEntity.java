/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.WorkflowRunsCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "WorkflowRunsCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.WorkflowRunsCardEntity")
public class WorkflowRunsCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.WORKFLOW_RUNS;
  }

  public static class WorkflowRunsCardMapper implements CardMapper<WorkflowRunsCard, WorkflowRunsCardEntity> {
    @Override
    public WorkflowRunsCardEntity fromDto(WorkflowRunsCard workflowRunsCard, String accountIdentifier) {
      WorkflowRunsCardEntity workflowRunsCardEntity =
          WorkflowRunsCardEntity.builder().size(workflowRunsCard.getSize()).build();
      setCommonFieldsEntity(workflowRunsCard, workflowRunsCardEntity, accountIdentifier);
      return workflowRunsCardEntity;
    }

    @Override
    public WorkflowRunsCard toDto(WorkflowRunsCardEntity workflowRunsCardEntity) {
      WorkflowRunsCard workflowRunsCard = new WorkflowRunsCard();
      workflowRunsCard.setSize(workflowRunsCardEntity.getSize());
      setCommonFieldsDto(workflowRunsCardEntity, workflowRunsCard);
      return workflowRunsCard;
    }
  }
}
