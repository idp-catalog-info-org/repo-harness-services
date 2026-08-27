/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.webhook.ManifestType.HELM_MANIFEST;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpecWrapper;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TriggerReferenceHelper {
  public List<EntityDetailProtoDTO> getReferences(
      String accountId, NGTriggerConfigV2 ngTriggerConfigV2, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier())) {
      entityDetailProtoDTOList.add(
          getReferredSecretDetails(ngTriggerConfigV2, accountId, scopeInfo, isParentIdQueryingEnabled));
    }
    List<String> inputSetRefs = new ArrayList<>();
    if (!ngTriggerConfigV2.getInputSetRefs().isExpression()) {
      inputSetRefs = ngTriggerConfigV2.getInputSetRefs().getValue();
    }
    if (EmptyPredicate.isNotEmpty(inputSetRefs)) {
      entityDetailProtoDTOList.addAll(getReferredInputSetRefsDetails(
          ngTriggerConfigV2, accountId, inputSetRefs, scopeInfo, isParentIdQueryingEnabled));
    }
    Set<String> connectorRefs = getConnectorRefs(ngTriggerConfigV2);
    for (String connectorRef : connectorRefs) {
      if (EmptyPredicate.isNotEmpty(connectorRefs)) {
        entityDetailProtoDTOList.add(getReferredConnectorDetails(
            ngTriggerConfigV2, accountId, connectorRef, scopeInfo, isParentIdQueryingEnabled));
      }
    }
    // todo(abhinav): add reference for harness code?
    return entityDetailProtoDTOList;
  }

  public List<EntityDetailProtoDTO> getReferredInputSetRefsDetails(NGTriggerConfigV2 ngTriggerConfigV2,
      String accountId, List<String> inputSetRefs, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    for (String inputSetRef : inputSetRefs) {
      InputSetReferenceProtoDTO.Builder inputSetReferenceProtoDTO =
          InputSetReferenceProtoDTO.newBuilder()
              .setAccountIdentifier(isParentIdQueryingEnabled
                      ? StringValue.of(emptyIfNull(scopeInfo.getAccountIdentifier()))
                      : StringValue.of(accountId))
              .setIdentifier(StringValue.of(inputSetRef))
              .setOrgIdentifier(isParentIdQueryingEnabled
                      ? StringValue.of(emptyIfNull(scopeInfo.getOrgIdentifier()))
                      : StringValue.of(nullIfEmpty(ngTriggerConfigV2.getOrgIdentifier())))
              .setProjectIdentifier(isParentIdQueryingEnabled
                      ? StringValue.of(emptyIfNull(scopeInfo.getProjectIdentifier()))
                      : StringValue.of(nullIfEmpty(ngTriggerConfigV2.getProjectIdentifier())))
              .setPipelineIdentifier(StringValue.of(ngTriggerConfigV2.getPipelineIdentifier()));
      if (isParentIdQueryingEnabled) {
        inputSetReferenceProtoDTO.setParentUniqueId(StringValue.of(emptyIfNull(scopeInfo.getUniqueId())));
      }
      entityDetailProtoDTOList.add(EntityDetailProtoDTO.newBuilder()
                                       .setInputSetRef(inputSetReferenceProtoDTO.build())
                                       .setType(EntityTypeProtoEnum.INPUT_SETS)
                                       .build());
    }
    return entityDetailProtoDTOList;
  }

  public EntityDetailProtoDTO getReferredSecretDetails(
      NGTriggerConfigV2 ngTriggerConfigV2, String accountId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    IdentifierRef secretIdentifierRef = isParentIdQueryingEnabled
        ? IdentifierRefHelper.getIdentifierRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier(), scopeInfo)
        : IdentifierRefHelper.getIdentifierRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier(), accountId,
              ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier());
    IdentifierRefProtoDTO secretReference = isParentIdQueryingEnabled
        ? IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(scopeInfo, secretIdentifierRef.getIdentifier())
        : IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, secretIdentifierRef.getOrgIdentifier(),
              secretIdentifierRef.getProjectIdentifier(), secretIdentifierRef.getIdentifier());

    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(secretReference)
        .setType(EntityTypeProtoEnum.SECRETS)
        .build();
  }

  public EntityDetailProtoDTO getReferredConnectorDetails(NGTriggerConfigV2 ngTriggerConfigV2, String accountId,
      String connectorRef, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    IdentifierRef connectorIdentifierRef = isParentIdQueryingEnabled
        ? IdentifierRefHelper.getIdentifierRef(connectorRef, scopeInfo)
        : IdentifierRefHelper.getIdentifierRef(
              connectorRef, accountId, ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier());
    IdentifierRefProtoDTO connectorReference = isParentIdQueryingEnabled
        ? IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(scopeInfo, connectorIdentifierRef.getIdentifier())
        : IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, connectorIdentifierRef.getOrgIdentifier(),
              connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());

    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(connectorReference)
        .setType(EntityTypeProtoEnum.CONNECTORS)
        .build();
  }

  public Set<String> getConnectorRefs(NGTriggerConfigV2 ngTriggerConfigV2) {
    Set<String> connectorRefs = new HashSet<>();
    if (ngTriggerConfigV2.getSource().getType() == WEBHOOK) {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerConfigV2.getSource().getSpec();
      if (webhookTriggerConfigV2.getSpec() != null && webhookTriggerConfigV2.getSpec().fetchGitAware() != null
          && webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef() != null) {
        connectorRefs.add(webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef());
      }
    } else if (ngTriggerConfigV2.getSource().getType() == ARTIFACT) {
      ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      connectorRefs.add(artifactTriggerConfig.getSpec().fetchConnectorRef());
    } else if (ngTriggerConfigV2.getSource().getType() == MULTI_REGION_ARTIFACT) {
      MultiRegionArtifactTriggerConfig artifactTriggerConfig =
          (MultiRegionArtifactTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      for (ArtifactTypeSpecWrapper artifactSpecWrapper : artifactTriggerConfig.getSources()) {
        connectorRefs.add(artifactSpecWrapper.getSpec().fetchConnectorRef());
      }
    } else if (ngTriggerConfigV2.getSource().getType() == MANIFEST) {
      ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      if (manifestTriggerConfig.getType() == HELM_MANIFEST) {
        HelmManifestSpec helmManifestSpec = (HelmManifestSpec) manifestTriggerConfig.getSpec();
        connectorRefs.add(helmManifestSpec.getStore().fetchConnectorRef());
      }
    }
    return connectorRefs;
  }
}
