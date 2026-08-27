/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.setupusage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityGitMetadata;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.helpers.ConnectorScopeHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class InputSetSetupUsageHelper {
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject private ConnectorScopeHelper connectorScopeHelper;

  /**
   * Publishes git connector reference for a remote input set to the entity setup usage framework.
   * This enables the connector to show the input set in its "Referenced By" list.
   */
  public void publishSetupUsageEvent(InputSetEntity inputSetEntity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, String branch, String repo) {
    try {
      Optional<EntityDetailProtoDTO> gitConnectorReference =
          getGitConnectorReference(inputSetEntity, scopeInfo, isParentIdQueryingEnabled);

      if (gitConnectorReference.isEmpty()) {
        return;
      }

      String accountId = inputSetEntity.getAccountId();
      EntityDetailProtoDTO inputSetDetails =
          buildInputSetEntityDetail(inputSetEntity, scopeInfo, isParentIdQueryingEnabled, branch, repo);

      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountId)
                                                           .setReferredByEntity(inputSetDetails)
                                                           .addReferredEntities(gitConnectorReference.get())
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();

      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());

      log.info("Published git connector setup usage for input set [{}] in pipeline [{}]",
          inputSetEntity.getIdentifier(), inputSetEntity.getPipelineIdentifier());
    } catch (Exception ex) {
      log.error("Error publishing setup usage for input set [{}] in pipeline [{}]: {}", inputSetEntity.getIdentifier(),
          inputSetEntity.getPipelineIdentifier(), ex.getMessage(), ex);
    }
  }

  /**
   * Deletes existing setup usage records for the input set.
   */
  public void deleteExistingSetupUsages(
      InputSetEntity inputSetEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      String accountId = inputSetEntity.getAccountId();
      EntityDetailProtoDTO inputSetDetails =
          buildInputSetEntityDetail(inputSetEntity, scopeInfo, isParentIdQueryingEnabled, null, null);

      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountId)
                                                           .setReferredByEntity(inputSetDetails)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();

      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting setup usages for input set [{}] in pipeline [{}]: {}", inputSetEntity.getIdentifier(),
          inputSetEntity.getPipelineIdentifier(), ex.getMessage(), ex);
    }
  }

  /**
   * Builds the git connector reference with the correct scope for the connector.
   * Returns empty if the input set is not inline or has no connector ref.
   */
  public Optional<EntityDetailProtoDTO> getGitConnectorReference(
      InputSetEntity inputSetEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (StoreType.INLINE.equals(inputSetEntity.getStoreType())) {
      return Optional.empty();
    }

    String connectorRef = inputSetEntity.getConnectorRef();
    if (GitAwareContextHelper.isNullOrDefault(connectorRef)) {
      return Optional.empty();
    }

    IdentifierRef identifierRef;
    if (scopeInfo != null && isParentIdQueryingEnabled) {
      Scope scope = Scope.of(scopeInfo);
      ScopeInfo connectorScopeInfo = connectorScopeHelper.getConnectorScopeInfo(scope, connectorRef);
      identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, connectorScopeInfo);
      identifierRef.setParentUniqueId(connectorScopeInfo.getUniqueId());
    } else {
      identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, inputSetEntity.getAccountId(),
          inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier());
    }

    IdentifierRefProtoDTO connectorReference = IdentifierRefProtoDTOHelper.fromIdentifierRef(identifierRef);
    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(connectorReference)
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();
    return Optional.of(connectorDetails);
  }

  private EntityDetailProtoDTO buildInputSetEntityDetail(InputSetEntity inputSetEntity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, String branch, String repo) {
    String orgIdentifier;
    String projectIdentifier;

    if (isParentIdQueryingEnabled && scopeInfo != null) {
      orgIdentifier = scopeInfo.getOrgIdentifier();
      projectIdentifier = scopeInfo.getProjectIdentifier();
    } else {
      orgIdentifier = inputSetEntity.getOrgIdentifier();
      projectIdentifier = inputSetEntity.getProjectIdentifier();
    }

    InputSetReferenceProtoDTO.Builder inputSetRefBuilder =
        InputSetReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(inputSetEntity.getAccountId()))
            .setPipelineIdentifier(StringValue.of(inputSetEntity.getPipelineIdentifier()))
            .setIdentifier(StringValue.of(inputSetEntity.getIdentifier()));

    if (isNotEmpty(orgIdentifier)) {
      inputSetRefBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotEmpty(projectIdentifier)) {
      inputSetRefBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }
    if (isParentIdQueryingEnabled && scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      inputSetRefBuilder.setParentUniqueId(StringValue.of(scopeInfo.getUniqueId()));
    }

    EntityDetailProtoDTO.Builder entityBuilder = EntityDetailProtoDTO.newBuilder()
                                                     .setInputSetRef(inputSetRefBuilder.build())
                                                     .setType(EntityTypeProtoEnum.INPUT_SETS);

    if (isNotEmpty(inputSetEntity.getName())) {
      entityBuilder.setName(inputSetEntity.getName());
    }

    if (isNotEmpty(branch) && isNotEmpty(repo)) {
      entityBuilder.setEntityGitMetadata(EntityGitMetadata.newBuilder().setBranch(branch).setRepo(repo).build());
    }

    return entityBuilder.build();
  }
}
