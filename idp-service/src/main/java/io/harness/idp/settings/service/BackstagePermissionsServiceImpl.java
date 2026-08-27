/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.PERMISSIONS;
import static io.harness.idp.common.Constants.USERGROUP;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.settings.events.PermissionsCreateEvent;
import io.harness.idp.settings.events.PermissionsUpdateEvent;
import io.harness.idp.settings.mappers.BackstagePermissionsMapper;
import io.harness.idp.settings.repositories.BackstagePermissionsRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class BackstagePermissionsServiceImpl implements BackstagePermissionsService {
  private BackstagePermissionsRepository backstagePermissionsRepository;
  private BackstageEnvVariableService backstageEnvVariableService;
  private TransactionTemplate transactionTemplate;
  private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public BackstagePermissionsServiceImpl(BackstagePermissionsRepository backstagePermissionsRepository,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      BackstageEnvVariableService backstageEnvVariableService, OutboxService outboxService) {
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.backstagePermissionsRepository = backstagePermissionsRepository;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public Optional<BackstagePermissions> findByAccountIdentifier(String accountIdentifier) {
    Optional<BackstagePermissionsEntity> permissions =
        backstagePermissionsRepository.findByAccountIdentifier(accountIdentifier);
    return permissions.map(BackstagePermissionsMapper::toDTO);
  }

  @Override
  public BackstagePermissions updatePermissions(BackstagePermissions backstagePermissions, String accountIdentifier) {
    BackstagePermissionsEntity permissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, accountIdentifier);

    Optional<BackstagePermissionsEntity> oldPermissionsEntity =
        backstagePermissionsRepository.findByAccountIdentifier(accountIdentifier);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      BackstagePermissions updatedBackstagePermissions =
          BackstagePermissionsMapper.toDTO(backstagePermissionsRepository.update(permissionsEntity));
      BackstagePermissions oldBackstagePermissions = oldPermissionsEntity.map(BackstagePermissionsMapper::toDTO).get();

      if (!updatedBackstagePermissions.getPermissions().equals(oldBackstagePermissions.getPermissions())
          || !updatedBackstagePermissions.getUserGroups().equals(oldBackstagePermissions.getUserGroups())) {
        outboxService.save(
            new PermissionsUpdateEvent(accountIdentifier, updatedBackstagePermissions, oldBackstagePermissions));
      }
      updateSecret(backstagePermissions, accountIdentifier);
      return updatedBackstagePermissions;
    }));
  }

  @Override
  public BackstagePermissions createPermissions(BackstagePermissions backstagePermissions, String accountIdentifier) {
    updateSecret(backstagePermissions, accountIdentifier);
    BackstagePermissionsEntity permissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, accountIdentifier);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      BackstagePermissions savedBackstagePermissions =
          BackstagePermissionsMapper.toDTO(backstagePermissionsRepository.save(permissionsEntity));
      outboxService.save(new PermissionsCreateEvent(accountIdentifier, savedBackstagePermissions));
      return savedBackstagePermissions;
    }));
  }

  @Override
  public void findAndSyncPermissions(String accountIdentifier) {
    Optional<BackstagePermissionsEntity> permissionsEntity =
        backstagePermissionsRepository.findByAccountIdentifier(accountIdentifier);
    permissionsEntity.map(BackstagePermissionsMapper::toDTO)
        .ifPresent(backstagePermissions -> updateSecret(backstagePermissions, accountIdentifier));
  }

  @Override
  public Iterable<BackstagePermissionsEntity> getAllBackstagePermissions() {
    return backstagePermissionsRepository.findAll();
  }

  @Override
  public void savePermission(BackstagePermissionsEntity backstagePermissionsEntity) {
    updateSecret(BackstagePermissionsMapper.toDTO(backstagePermissionsEntity),
        backstagePermissionsEntity.getAccountIdentifier());
    backstagePermissionsRepository.save(backstagePermissionsEntity);
  }

  private void updateSecret(BackstagePermissions backstagePermissions, String accountIdentifier) {
    List<String> permissions = backstagePermissions.getPermissions();
    String configPermissions = String.join(",", permissions);
    String userGroups = String.join(",", backstagePermissions.getUserGroups());
    if (isEmpty(userGroups)) {
      userGroups = " ";
    }

    if (isEmpty(configPermissions)) {
      configPermissions = " ";
    }

    List<BackstageEnvVariable> variables = new ArrayList<>();
    BackstageEnvConfigVariable permissionsVariable = new BackstageEnvConfigVariable();
    permissionsVariable.setEnvName(PERMISSIONS);
    permissionsVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    permissionsVariable.setValue(configPermissions);
    variables.add(permissionsVariable);
    BackstageEnvConfigVariable userGroupsVariable = new BackstageEnvConfigVariable();
    userGroupsVariable.setEnvName(USERGROUP);
    userGroupsVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    userGroupsVariable.setValue(userGroups);
    variables.add(userGroupsVariable);
    backstageEnvVariableService.createOrUpdate(variables, accountIdentifier);
  }
}
