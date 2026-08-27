/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import static io.harness.idp.common.Constants.SMP_DEPLOYMENT_TYPE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.NamespaceRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class NamespaceServiceImpl implements NamespaceService {
  private NamespaceRepository namespaceRepository;
  private static final String IDP_NOT_ENABLED = "IDP has not been set up for account [%s]";
  private static final String IDP_NAMESPACE_NOT_LINKED = "Namespace - [%s] is not linked to any account";
  private K8sClient k8sClient;
  private String deploymentType;
  private String deploymentNamespace;

  @Inject
  public NamespaceServiceImpl(NamespaceRepository namespaceRepository, K8sClient k8sClient,
      @Named("deploymentType") String deploymentType, @Named("deploymentNamespace") String deploymentNamespace) {
    this.namespaceRepository = namespaceRepository;
    this.k8sClient = k8sClient;
    this.deploymentType = deploymentType;
    this.deploymentNamespace = deploymentNamespace;
  }

  @Override
  public NamespaceInfo getNamespaceForAccountIdentifier(String accountId) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findByAccountIdentifier(accountId);
    if (namespaceName.isEmpty()) {
      throw new InvalidRequestException(format(IDP_NOT_ENABLED, accountId));
    }
    return namespaceName.map(NamespaceMapper::toDTO).get();
  }

  @Override
  public NamespaceInfo getAccountIdForNamespace(String namespace) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findById(namespace);
    if (namespaceName.isEmpty()) {
      throw new InvalidRequestException(format(IDP_NAMESPACE_NOT_LINKED, namespace));
    }
    return namespaceName.map(NamespaceMapper::toDTO).get();
  }

  @Override
  public NamespaceEntity saveAccountIdNamespace(String accountId) {
    NamespaceEntity dataToInsert = (deploymentType.equals(SMP_DEPLOYMENT_TYPE))
        ? NamespaceEntity.builder()
              .accountIdentifier(accountId)
              .id(deploymentNamespace)
              .nextIteration(System.currentTimeMillis())
              .build()
        : NamespaceEntity.builder().accountIdentifier(accountId).nextIteration(System.currentTimeMillis()).build();
    NamespaceEntity insertedData = namespaceRepository.save(dataToInsert);
    k8sClient.createNamespace(insertedData.getId());
    return insertedData;
  }

  @Override
  public NamespaceInfo update(String accountIdentifier, NamespaceRequest namespaceRequest) {
    Optional<NamespaceEntity> optionalNamespaceEntity = getEntityForAccountIdentifier(accountIdentifier);
    if (optionalNamespaceEntity.isEmpty()) {
      throw new InvalidRequestException(format(IDP_NOT_ENABLED, accountIdentifier));
    }
    NamespaceEntity namespaceEntity = optionalNamespaceEntity.get();
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    if (metadata.isPostgresIdpV2MigrationCompleted()
        != namespaceRequest.getMetadata().isPostgresIdpV2MigrationCompleted()) {
      metadata.setPostgresIdpV2MigrationCompleted(namespaceRequest.getMetadata().isPostgresIdpV2MigrationCompleted());
      namespaceEntity.setMetadata(metadata);
      return NamespaceMapper.toDTO(namespaceRepository.save(namespaceEntity));
    }
    return NamespaceMapper.toDTO(namespaceEntity);
  }

  @Override
  public List<String> getAccountIds() {
    List<NamespaceEntity> namespaceEntities = namespaceRepository.findAllByIsDeleted(false);
    List<String> accountIdsList =
        namespaceEntities.stream().map(entity -> entity.getAccountIdentifier()).collect(Collectors.toList());
    return accountIdsList;
  }
  @Override
  public Boolean getAccountIdpStatus(String accountIdentifier) {
    Optional<NamespaceEntity> namespaceEntity =
        namespaceRepository.findByAccountIdentifierAndIsDeleted(accountIdentifier, false);
    return namespaceEntity.isPresent();
  }

  @Override
  public NamespaceEntity createDevSpaceEnvDefaultMappingEntry(String accountIdentifier, String namespace) {
    NamespaceEntity existingMappingEntry =
        namespaceRepository.findByAccountIdentifierAndId(accountIdentifier, namespace);
    if (existingMappingEntry == null) {
      NamespaceEntity namespaceEntity =
          NamespaceEntity.builder().id(namespace).accountIdentifier(accountIdentifier).build();
      return namespaceRepository.save(namespaceEntity);
    }
    return existingMappingEntry;
  }

  @Override
  public List<NamespaceEntity> getActiveAccounts() {
    return namespaceRepository.findAllByIsDeleted(false);
  }

  @Override
  public Optional<NamespaceEntity> getEntityForAccountIdentifier(String accountIdentifier) {
    return namespaceRepository.findByAccountIdentifier(accountIdentifier);
  }

  @Override
  public void save(NamespaceEntity namespaceEntity) {
    namespaceRepository.save(namespaceEntity);
  }

  @Override
  public void updateIdpV2MigrationInfoAndSave(NamespaceEntity namespaceEntity, boolean idpV2FFState) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    if (metadata.getIdpV2MigrationInfo() == null && idpV2FFState) {
      NamespaceEntity.Metadata.IdpV2MigrationInfo idpV2MigrationInfo =
          NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
              .migrateDefaultToAccountNamespaceInBackstageCompleted(false)
              .migrateDefaultToAccountNamespaceInBackstageFrom(System.currentTimeMillis() + (5 * 60 * 1000))
              .migrateDefaultToAccountNamespaceInDependentsCompleted(false)
              .migrateDefaultToAccountNamespaceInDependentsFrom(System.currentTimeMillis() + (5 * 60 * 1000))
              .migrateWorkflowFormContextDataCompleted(false)
              .migrateWorkflowFormContextDataFrom(System.currentTimeMillis() + (5 * 60 * 1000))
              .populateQueryableEntityRefInCatalogCompleted(false)
              .populateQueryableEntityRefInCatalogFrom(System.currentTimeMillis() + (5 * 60 * 1000))
              .build();
      metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
    }
    metadata.setIdpV2FFState(idpV2FFState);
    namespaceEntity.setMetadata(metadata);
    namespaceRepository.save(namespaceEntity);
  }
}
