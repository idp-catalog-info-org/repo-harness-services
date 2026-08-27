/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import static io.harness.idp.common.Constants.GITHUB_APP_PRIVATE_KEY_REF;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_PRIVATE_KEY;
import static io.harness.idp.common.Constants.LAST_UPDATED_TIMESTAMP;
import static io.harness.idp.common.Constants.PRIVATE_KEY_END;
import static io.harness.idp.common.Constants.PRIVATE_KEY_START;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptedSecretValue;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.encryption.EncryptionUtils;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.outbox.api.OutboxService;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariable;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.math3.util.Pair;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class BackstageEnvVariableServiceImpl implements BackstageEnvVariableService {
  private static final Pattern UNSUPPORTED_SECRET_MANAGER_MESSAGE = Pattern.compile(
      "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers");
  private static final Pattern SECRET_NOT_FOUND_PATTERN =
      Pattern.compile("Invalid request: Secret with identifier .* does not exist in this scope");
  private static final String SECRET_EXPRESSION_FORMAT = "<+secrets.getValue('account.%s')>";
  private final BackstageEnvVariableRepository backstageEnvVariableRepository;
  private final K8sClient k8sClient;
  private final SecretManagerClientService ngSecretService;
  private final NamespaceService namespaceService;
  private final Map<BackstageEnvVariableType, BackstageEnvVariableMapper> envVariableMap;
  private final SetupUsageProducer setupUsageProducer;
  private final String idpEncryptionSecret;
  private static final Gson gson = new Gson();

  private static final String ENV_VARIABLE_ONLY_UPDATE_ERROR_MESSAGE = "Env variables can only be updated";

  private static final String DUMMY_RSA_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n"
      + "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCWas/zWSOuISc9\n"
      + "sH8D31+mXOPCYJnAY8VwKNJzf/kc8yy+seDla5EPmHSRuc18WJ/CNpjhmXs53XrR\n"
      + "6e8n3plR5jsr4xFv6aJsVlwzP7/lHffrKM023IsiQdChql5bO2mdKKX0ktSbiQd1\n"
      + "z2B9R/K08DCa8zKIx8arIjSxMkR3MvcgPzWFnoL+jFnDHdBbVSu+dm4R3OqqXoxA\n"
      + "VbVWZIp8NeZRDCdQVq4oWIxOgLvFqEnPDkPHec0wGM4Hh4D7nOVq8Wra9q8cTPfu\n"
      + "cQ+5yOX4nCnDVkUOP0xWYGlLCs713ksmyoQccf0cxmCscPC9rPTBTGLVwpGJv/Hy\n"
      + "qtui1/GBAgMBAAECggEASMcujapUGrT81RrYIeoK0CZCpzJxQgakKZP+25aQVGMO\n"
      + "g8fyLl8A5YBY6odxdpg02FXtW76Uwlc4zWc4aVyJZ3iTqbToo3LyPSP94WgXc4aw\n"
      + "BU6NGF3WTgF1LjuOAut4uutHfNIsX4MKIoTvxT4yHzzV76r7CPZMWFIpF5FgUiGO\n"
      + "wdaCIbc31jUgs1IMqwFPnC11yTwQp0LXoyWVdzjxlxJS8Zx9wGRmiZKavsir0Vj0\n"
      + "fpGi4YYJDCEnrO0Tw+qA3dxShvJky1Rtxatk4w5kYwzpEcWbbrywZ1g53fdZ64ER\n"
      + "z7B0Xe7ugXJq3YeDeXP0NyDzk1Y/tSPHxfjcLsA1VwKBgQDTx5qNw5iJSQRXO9fg\n"
      + "8SCkiOfLi6+JfPNQDAD80qkXE4wOo6uK/ZWbB81SDrkmt+03KDq2UALmO3JYQS8Z\n"
      + "JBQoRDNSo0CGHQBkoKTFLNhMhXrqJR+j+45x+X7J4buNfHRVAVEluRMOwd0Fmr7p\n"
      + "WkD2v0QBiA4n0lAT7REggyH6lwKBgQC10ylKqLDJH+jG7oporu3izP/Z1zryml4z\n"
      + "oGsccoREcT30d5XoVpiKDxvC1odf4f7bg9LVoOOh9srEaHFe6Kz0EBuJKCBwZD/x\n"
      + "pL76V8JCWfuz5GGHbOirqqiK9tYueZZrJ48Ty8CEeIqITNdhQafoIwCxUtteyUVe\n"
      + "w7zO4upvpwKBgHsyJipJmjZij2/flBl6q66LJaw2ugqU8UWjdf+c3FhcOqFZfLUC\n"
      + "B0GELGCLyBFJ9WicsmrT6JveAQpuAOPzJPa3ldOAvExIGq5u9Orux3TcQUBsEBfo\n"
      + "gliy9pqiAeSwfUvl1DrJitiO1fAosN42bowbf4gUiYeIxKSSx9/N6LpJAoGAX/aT\n"
      + "u3iu3We+9odde4Sfvu0NN871qKc6gqru/TOfhXPzC/y1nMtfdLYmo72P81YWqYq4\n"
      + "ktF4croLKIArHblV1vZNYiVQgaEXcpTNytjYiSZuxvIJW21qm3fVvooqXpsDfYiC\n"
      + "ZiNKd2AbVXag0g7R7J3UtsIRT8SQnURXeSWgL88CgYBgw4wgJk30u4iLK/s/lph9\n"
      + "EjrsewFPHblF+vjgA3rHbjcdNPHg8B+67LgqaPnYer17JsBJf9NzMy391rXxdGbP\n"
      + "KO+H8tyPxpwDFQ+E+Or9euhoEe9KqL/hIwbNowNym0zYfs+ZQVgL78ANlX0D5bq7\n"
      + "yY2H5dnCrBMzuycUwQDo7A==\n"
      + "-----END PRIVATE KEY-----";

  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public BackstageEnvVariableServiceImpl(BackstageEnvVariableRepository backstageEnvVariableRepository,
      K8sClient k8sClient, @Named("PRIVILEGED") SecretManagerClientService ngSecretService,
      NamespaceService namespaceService, Map<BackstageEnvVariableType, BackstageEnvVariableMapper> envVariableMap,
      SetupUsageProducer setupUsageProducer, @Named("idpEncryptionSecret") String idpEncryptionSecret,
      TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.backstageEnvVariableRepository = backstageEnvVariableRepository;
    this.k8sClient = k8sClient;
    this.ngSecretService = ngSecretService;
    this.namespaceService = namespaceService;
    this.envVariableMap = envVariableMap;
    this.setupUsageProducer = setupUsageProducer;
    this.idpEncryptionSecret = idpEncryptionSecret;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public Optional<BackstageEnvVariable> findByIdAndAccountIdentifier(String identifier, String accountIdentifier) {
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByIdAndAccountIdentifier(identifier, accountIdentifier);
    if (envVariableEntityOpt.isEmpty()) {
      return Optional.empty();
    }
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
    return Optional.of(envVariableMapper.toDto(envVariableEntityOpt.get()));
  }

  @Override
  public Optional<BackstageEnvVariable> findByEnvNameAndAccountIdentifier(String envName, String accountIdentifier) {
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
    if (envVariableEntityOpt.isEmpty()) {
      return Optional.empty();
    }
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
    return Optional.of(envVariableMapper.toDto(envVariableEntityOpt.get()));
  }

  @Deprecated(forRemoval = true)
  @Override
  public BackstageEnvVariable create(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
    BackstageEnvVariableEntity backstageEnvVariableEntity =
        envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
    BackstageEnvVariable responseEnvVariable =
        envVariableMapper.toDto(backstageEnvVariableRepository.save(backstageEnvVariableEntity));

    setupUsageProducer.publishEnvVariableSetupUsage(Collections.singletonList(responseEnvVariable), accountIdentifier);

    return responseEnvVariable;
  }

  List<BackstageEnvVariable> createMulti(List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    requestEnvVariables = removeAccountFromIdentifierForBackstageEnvVarList(requestEnvVariables);
    sync(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariableEntity> entities = getEntitiesFromDtos(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariable> responseEnvVariables = new ArrayList<>();
    backstageEnvVariableRepository.saveAll(entities).forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper(envVariableEntity.getType());
      responseEnvVariables.add(envVariableMapper.toDto(envVariableEntity));
    });

    setupUsageProducer.publishEnvVariableSetupUsage(responseEnvVariables, accountIdentifier);

    return responseEnvVariables;
  }

  @Deprecated(forRemoval = true)
  @Override
  public BackstageEnvVariable update(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
    BackstageEnvVariableEntity backstageEnvVariableEntity =
        envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
    backstageEnvVariableEntity.setAccountIdentifier(accountIdentifier);
    BackstageEnvVariable responseVariable =
        envVariableMapper.toDto(backstageEnvVariableRepository.update(backstageEnvVariableEntity));

    List<BackstageEnvVariable> responseList = Collections.singletonList(responseVariable);
    setupUsageProducer.deleteEnvVariableSetupUsage(responseList, accountIdentifier);
    setupUsageProducer.publishEnvVariableSetupUsage(responseList, accountIdentifier);

    return responseVariable;
  }

  List<BackstageEnvVariable> updateMulti(List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    requestEnvVariables = removeAccountFromIdentifierForBackstageEnvVarList(requestEnvVariables);
    sync(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariableEntity> entities = getEntitiesFromDtos(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariable> responseVariables = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      responseVariables.add(envVariableMapper.toDto(backstageEnvVariableRepository.update(entity)));
    });

    setupUsageProducer.deleteEnvVariableSetupUsage(responseVariables, accountIdentifier);
    setupUsageProducer.publishEnvVariableSetupUsage(responseVariables, accountIdentifier);

    return responseVariables;
  }

  @Override
  public List<BackstageEnvVariable> createOrUpdate(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    List<String> envNamesFromRequest =
        envVariables.stream().map(BackstageEnvVariable::getEnvName).collect(Collectors.toList());
    Set<String> envNamesFromDB =
        backstageEnvVariableRepository
            .findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNamesFromRequest)
            .stream()
            .map(BackstageEnvVariableEntity::getEnvName)
            .collect(Collectors.toSet());
    List<BackstageEnvVariable> envVariablesToUpdate = new ArrayList<>();
    List<BackstageEnvVariable> envVariablesToAdd = new ArrayList<>();
    envVariables.forEach(envVariable -> {
      if (envNamesFromDB.contains(envVariable.getEnvName())) {
        envVariablesToUpdate.add(envVariable);
      } else {
        envVariablesToAdd.add(envVariable);
      }
    });
    List<BackstageEnvVariable> response = createMulti(envVariablesToAdd, accountIdentifier);
    response.addAll(updateMulti(envVariablesToUpdate, accountIdentifier));
    return response;
  }

  @Override
  public List<BackstageEnvVariable> findByAccountIdentifier(String accountIdentifier) {
    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findByAccountIdentifier(accountIdentifier);
    List<BackstageEnvVariable> secretDTOs = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      secretDTOs.add(envVariableMapper.toDto(entity));
    });
    return secretDTOs;
  }

  @Override
  public void findAndSync(String accountIdentifier) {
    List<BackstageEnvVariable> variables = findByAccountIdentifier(accountIdentifier);
    createOrUpdate(variables, accountIdentifier);
  }

  @Override
  public void deleteMulti(List<String> secretIdentifiers, String accountIdentifier) {
    Iterable<BackstageEnvVariableEntity> envVariableEntities =
        backstageEnvVariableRepository.findAllById(secretIdentifiers);
    List<String> envNames = new ArrayList<>();
    List<BackstageEnvVariable> deletedVariables = new ArrayList<>();
    envVariableEntities.forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntity.getType()));
      deletedVariables.add(envVariableMapper.toDto(envVariableEntity));
      envNames.add(envVariableEntity.getEnvName());
    });
    log.info("Triggering pod restart as envs [{}] are deleted for account {}", String.join(",", envNames),
        accountIdentifier);
    Map<String, byte[]> secretData = new HashMap<>();
    secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
    k8sClient.updateSecretData(
        accountIdentifier, getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET, secretData);
    backstageEnvVariableRepository.deleteAllById(secretIdentifiers);

    setupUsageProducer.deleteEnvVariableSetupUsage(deletedVariables, accountIdentifier);
  }

  @Override
  public void deleteMultiUsingEnvNames(List<String> envNames, String accountIdentifier) {
    // removing from setup usages
    Iterable<BackstageEnvVariableEntity> envVariableEntities =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNames);
    List<BackstageEnvVariable> deletedVariables = new ArrayList<>();
    envVariableEntities.forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntity.getType()));
      deletedVariables.add(envVariableMapper.toDto(envVariableEntity));
    });
    setupUsageProducer.deleteEnvVariableSetupUsage(deletedVariables, accountIdentifier);

    log.info("Triggering pod restart as envs [{}] are deleted for account {}", String.join(",", envNames),
        accountIdentifier);
    Map<String, byte[]> secretData = new HashMap<>();
    secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
    k8sClient.updateSecretData(
        accountIdentifier, getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET, secretData);
    backstageEnvVariableRepository.deleteAllByAccountIdentifierAndEnvNames(accountIdentifier, envNames);
  }

  @Override
  public void processSecretUpdate(EntityChangeDTO entityChangeDTO) {
    String secretIdentifier = entityChangeDTO.getIdentifier().getValue();
    secretIdentifier = CommonUtils.removeAccountFromIdentifier(secretIdentifier);
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByAccountIdentifierAndHarnessSecretIdentifier(
            accountIdentifier, secretIdentifier);
    if (envVariableEntityOpt.isPresent()) {
      log.info("Secret {} is used by backstage env variable {}. Processing secret update for account {}",
          secretIdentifier, envVariableEntityOpt.get().getEnvName(), accountIdentifier);
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
      sync(Collections.singletonList(envVariableMapper.toDto(envVariableEntityOpt.get())), accountIdentifier);
    }
  }

  @Override
  public void processSecretDelete(EntityChangeDTO entityChangeDTO) {
    String secretIdentifier = entityChangeDTO.getIdentifier().getValue();
    secretIdentifier = CommonUtils.removeAccountFromIdentifier(secretIdentifier);
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Optional<BackstageEnvVariableEntity> backstageEnvVariableOpt =
        backstageEnvVariableRepository.updateSecretIsDeleted(accountIdentifier, secretIdentifier, true);
    if (backstageEnvVariableOpt.isPresent()) {
      log.info("Marking backstage env variable {} as deleted as it uses deleted secret {} for account {}",
          backstageEnvVariableOpt.get().getEnvName(), secretIdentifier, accountIdentifier);
    }
  }

  public void sync(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    if (envVariables.isEmpty()) {
      return;
    }
    envVariables = removeAccountFromIdentifierForBackstageEnvVarList(envVariables);
    Map<String, byte[]> secretData = new HashMap<>();

    Set<String> envNames = envVariables.stream().map(BackstageEnvVariable::getEnvName).collect(Collectors.toSet());
    log.info("Checking if envs {} need to be added/updated for account {}", envNames, accountIdentifier);

    Map<String, BackstageEnvVariableEntity> entitiesMap =
        backstageEnvVariableRepository
            .findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, new ArrayList<>(envNames))
            .stream()
            .collect(Collectors.toMap(BackstageEnvVariableEntity::getEnvName, Function.identity()));

    for (BackstageEnvVariable envVariable : envVariables) {
      BackstageEnvVariableEntity entity = entitiesMap.get(envVariable.getEnvName());
      if (envVariable.getType().name().equals(BackstageEnvVariableType.SECRET.name())) {
        handleSecretEnv(accountIdentifier, entity, envVariable, secretData);
      } else {
        handleConfigEnv(accountIdentifier, entity, envVariable, secretData);
      }
    }
    k8sClient.updateSecretData(
        accountIdentifier, getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET, secretData);
  }

  private void handleSecretEnv(String accountIdentifier, BackstageEnvVariableEntity entity,
      BackstageEnvVariable envVariable, Map<String, byte[]> secretData) {
    BackstageEnvSecretVariable secretEnvVariable = (BackstageEnvSecretVariable) envVariable;
    Pair<String, Long> decryptedValueAndLastModifiedAt = getDecryptedValueAndLastModifiedTime(
        secretEnvVariable.getEnvName(), secretEnvVariable.getHarnessSecretIdentifier(), accountIdentifier, null, null);
    Long lastModifiedAt = decryptedValueAndLastModifiedAt.getSecond();
    BackstageEnvSecretVariableEntity secretEntity = (BackstageEnvSecretVariableEntity) entity;
    if (secretEntity == null // create scenario
        || !secretEntity.getHarnessSecretIdentifier().equals(
            secretEnvVariable.getHarnessSecretIdentifier()) // different secret scenario
        || lastModifiedAt == 0 // old ng-manager scenario
        || secretEntity.getSecretLastModifiedAt() < lastModifiedAt) { // secret update scenario
      log.info("Triggering pod restart as secret value for env {} has "
              + "changed for account {}",
          envVariable.getEnvName(), accountIdentifier);
      secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
    }
  }

  private void handleConfigEnv(String accountIdentifier, BackstageEnvVariableEntity entity,
      BackstageEnvVariable envVariable, Map<String, byte[]> secretData) {
    BackstageEnvConfigVariable configEnvVariable = (BackstageEnvConfigVariable) envVariable;
    if (entity == null
        || !((BackstageEnvConfigVariableEntity) entity).getValue().equals(configEnvVariable.getValue())) {
      log.info("Triggering pod restart as config value for env {} has "
              + "changed for account {}",
          envVariable.getEnvName(), accountIdentifier);
      secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
    }
  }

  @Override
  public List<BackstageEnvSecretVariable> getAllSecretIdentifierForMultipleEnvVariablesInAccount(
      String accountIdentifier, List<String> envVariableNames) {
    List<BackstageEnvSecretVariable> resultList = new ArrayList<>();
    List<BackstageEnvVariableEntity> listEnvVariablesAndSecretId =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
            accountIdentifier, envVariableNames);
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((BackstageEnvVariableType.SECRET));

    for (BackstageEnvVariableEntity backstageEnvVariableEntity : listEnvVariablesAndSecretId) {
      resultList.add((BackstageEnvSecretVariable) envVariableMapper.toDto(backstageEnvVariableEntity));
    }
    return resultList;
  }

  @Override
  public List<BackstageEnvVariable> findByEnvNamesAndAccountIdentifier(
      List<String> envNames, String accountIdentifier) {
    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNames);
    List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      backstageEnvVariables.add(envVariableMapper.toDto(entity));
    });
    return backstageEnvVariables;
  }

  @Override
  public ResolvedEnvVariableResponse resolveEnvVariables(String accountIdentifier, String namespace) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    if (!namespaceInfo.getNamespace().equals(namespace)) {
      throw new InvalidRequestException(
          String.format("The request namespace [%s] does not match with the account namespace [%s] for account [%s]",
              namespace, namespaceInfo.getNamespace(), accountIdentifier));
    }

    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findByAccountIdentifier(accountIdentifier);
    List<ResolvedEnvVariable> resolvedEnvVariables = new ArrayList<>();
    for (BackstageEnvVariableEntity entity : entities) {
      ResolvedEnvVariable resolvedEnv = new ResolvedEnvVariable();
      resolvedEnv.setEnvName(entity.getEnvName());
      if (entity.getType().equals(BackstageEnvVariableType.CONFIG)) {
        resolvedEnv.setDecryptedValue(((BackstageEnvConfigVariableEntity) entity).getValue());
      } else {
        BackstageEnvSecretVariableEntity secretVariableEntity = ((BackstageEnvSecretVariableEntity) entity);
        String decryptedValue;
        try {
          decryptedValue = getDecryptedValueAndLastModifiedTime(secretVariableEntity.getEnvName(),
              secretVariableEntity.getHarnessSecretIdentifier(), accountIdentifier, null, null)
                               .getFirst();
        } catch (Exception ex) {
          log.error("Skipping secret resolution as resolution failed with error", ex);
          decryptedValue = "dummy";
        }
        resolvedEnv.setDecryptedValue(decryptedValue);
      }
      resolvedEnvVariables.add(resolvedEnv);
    }
    String json = gson.toJson(resolvedEnvVariables);
    return BackstageEnvVariableMapper.toResolvedVariableResponse(
        EncryptionUtils.encryptString(json, idpEncryptionSecret));
  }

  private String getNamespaceForAccount(String accountIdentifier) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    return namespaceInfo.getNamespace();
  }

  private BackstageEnvVariableMapper getEnvVariableMapper(BackstageEnvVariableType envVariableType) {
    BackstageEnvVariableMapper envVariableMapper = envVariableMap.get(envVariableType);
    if (envVariableMapper == null) {
      throw new InvalidRequestException("Backstage env variable type not set");
    }
    return envVariableMapper;
  }

  private List<BackstageEnvVariableEntity> getEntitiesFromDtos(
      List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    return requestEnvVariables.stream()
        .map(envVariable -> {
          BackstageEnvVariableMapper envVariableMapper =
              getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
          long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
          return envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
        })
        .collect(Collectors.toList());
  }

  private List<BackstageEnvVariable> removeAccountFromIdentifierForBackstageEnvVarList(
      List<BackstageEnvVariable> backstageEnvVariableList) {
    List<BackstageEnvVariable> returnList = new ArrayList<>();
    for (BackstageEnvVariable backstageEnvVariable : backstageEnvVariableList) {
      returnList.add(removeAccountFromIdentifierForBackstageEnvVariable(backstageEnvVariable));
    }
    return returnList;
  }

  private BackstageEnvVariable removeAccountFromIdentifierForBackstageEnvVariable(
      BackstageEnvVariable backstageEnvVariable) {
    if (backstageEnvVariable.getType().name().equals(BackstageEnvVariableType.SECRET.name())) {
      BackstageEnvSecretVariable backstageEnvSecretVariable = (BackstageEnvSecretVariable) backstageEnvVariable;
      backstageEnvSecretVariable.setHarnessSecretIdentifier(
          CommonUtils.removeAccountFromIdentifier(backstageEnvSecretVariable.getHarnessSecretIdentifier()));
      return backstageEnvSecretVariable;
    }
    return backstageEnvVariable;
  }
  @Override
  public Pair<String, Long> getDecryptedValueAndLastModifiedTime(String envName, String secretIdentifier,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    int maxRetries = 3;
    int baseDelayMillis = 1000;
    int retryAttempts = 0;
    String exceptionMessage = "";

    while (retryAttempts < maxRetries) {
      try {
        DecryptedSecretValue decryptedValue = ngSecretService.getDecryptedSecretValue(
            accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);

        if (envName.equals(GITHUB_APP_PRIVATE_KEY_REF) || envName.contains(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY)
            || envName.equals("GITHUB_API_APP_PRIVATE_KEY")) {
          SecretResponseWrapper secretResponseWrapper =
              ngSecretService.getSecret(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
          if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretFile)) {
            decryptedValue.setDecryptedValue(
                new String(Base64.getDecoder().decode(decryptedValue.getDecryptedValue()), StandardCharsets.UTF_8));
          }
          if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretText)) {
            String privateKeyFormatted = formatPrivateKey(decryptedValue.getDecryptedValue());
            decryptedValue.setDecryptedValue(privateKeyFormatted);
          }
        }
        return new Pair<>(decryptedValue.getDecryptedValue(), decryptedValue.getLastModifiedAt());
      } catch (Exception e) {
        exceptionMessage = e.getMessage();
        if (UNSUPPORTED_SECRET_MANAGER_MESSAGE.matcher(exceptionMessage).find()) {
          log.info("Secret {} in account {} is not encrypted by harness secret manager", secretIdentifier,
              accountIdentifier);
          if (envName.equals(GITHUB_APP_PRIVATE_KEY_REF) || envName.contains(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY)
              || envName.equals("GITHUB_API_APP_PRIVATE_KEY")) {
            return new Pair<>(DUMMY_RSA_PRIVATE_KEY, 0L);
          }
          return new Pair<>(String.format(SECRET_EXPRESSION_FORMAT, secretIdentifier), 0L);
        }
        if (!SECRET_NOT_FOUND_PATTERN.matcher(exceptionMessage).find()) {
          log.warn("Error while decrypting secret {} for account {} org {} proejct {}. Retry: {}, Error: {}",
              secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, retryAttempts + 1, e.getMessage());

          int delayMillis = (int) (baseDelayMillis * Math.pow(2, retryAttempts));

          try {
            Thread.sleep(delayMillis);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }

          retryAttempts++;
        } else {
          break;
        }
      }
    }

    throw new UnexpectedException(String.format("%s. Env %s, Secret %s, Account %s Org %s Project %s", exceptionMessage,
        envName, secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public void reloadSecrets(String harnessAccount, String namespace) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(harnessAccount);
    if (!namespaceInfo.getNamespace().equals(namespace)) {
      throw new InvalidRequestException(
          String.format("The request namespace [%s] does not match with the account namespace [%s] for account [%s]",
              namespace, namespaceInfo.getNamespace(), harnessAccount));
    }
    Map<String, byte[]> secretData = new HashMap<>();
    secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
    k8sClient.updateSecretData(harnessAccount, namespace, BACKSTAGE_SECRET, secretData);
  }

  @Override
  public List<BackstageEnvVariable> updateAndAuditEnvironmentVariables(
      List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    List<String> envNamesFromRequest =
        envVariables.stream().map(BackstageEnvVariable::getEnvName).collect(Collectors.toList());

    Map<String, BackstageEnvSecretVariableEntity> envVarsFromDB =
        backstageEnvVariableRepository
            .findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNamesFromRequest)
            .stream()
            .collect(Collectors.toMap(backstageEnvVariableEntity
                -> backstageEnvVariableEntity.getEnvName(),
                backstageEnvVariableEntity -> ((BackstageEnvSecretVariableEntity) backstageEnvVariableEntity)));

    List<BackstageEnvVariable> envVariablesToUpdate = new ArrayList<>();

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      envVariables.forEach(envVariable -> {
        if (envVarsFromDB.keySet().contains(envVariable.getEnvName())) {
          envVariablesToUpdate.add(envVariable);

          BackstageEnvVariableMapper envVariableMapper =
              getEnvVariableMapper(envVarsFromDB.get(envVariable.getEnvName()).getType());

          BackstageEnvSecretVariable oldBackstageEnvSecretVariable =
              (BackstageEnvSecretVariable) envVariableMapper.toDto(envVarsFromDB.get(envVariable.getEnvName()));

          oldBackstageEnvSecretVariable.setHarnessSecretIdentifier(
              CommonUtils.addAccountScopeInIdentifier(oldBackstageEnvSecretVariable.getHarnessSecretIdentifier()));

          BackstageEnvSecretVariable newBackstageEnvSecretVariable = (BackstageEnvSecretVariable) envVariable;

          if (!newBackstageEnvSecretVariable.getEnvName().equals(oldBackstageEnvSecretVariable.getEnvName())
              || !newBackstageEnvSecretVariable.getHarnessSecretIdentifier().equals(
                  oldBackstageEnvSecretVariable.getHarnessSecretIdentifier())) {
            outboxService.save(new BackstageEnvSecretUpdateEvent(
                accountIdentifier, (BackstageEnvSecretVariable) envVariable, oldBackstageEnvSecretVariable));
          }
        } else {
          throw new InvalidRequestException(ENV_VARIABLE_ONLY_UPDATE_ERROR_MESSAGE);
        }
      });
      return updateMulti(envVariablesToUpdate, accountIdentifier);
    }));
  }

  @Override
  public void cleanupEnvSecret(String accountIdentifier, String namespace) {
    List<BackstageEnvVariableEntity> envVariables =
        backstageEnvVariableRepository.findByAccountIdentifier(accountIdentifier);
    List<String> configEnvNames =
        envVariables.stream()
            .filter(envVariable -> envVariable.getType().equals(BackstageEnvVariableType.CONFIG))
            .map(BackstageEnvVariableEntity::getEnvName)
            .filter(envName -> !envName.equals(LAST_UPDATED_TIMESTAMP))
            .collect(Collectors.toList());

    // remove old env names
    configEnvNames.add("LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES");
    configEnvNames.add("LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG");

    k8sClient.removeSecretData(namespace, BACKSTAGE_SECRET, configEnvNames);
  }

  private String formatPrivateKey(String privateKey) {
    privateKey = privateKey.replace(PRIVATE_KEY_START + " ", "");
    privateKey = privateKey.replace(PRIVATE_KEY_END, "");
    privateKey = privateKey.replace(" ", "\n");
    String privateKeyFormatted = PRIVATE_KEY_START + "\n";
    privateKeyFormatted = privateKeyFormatted + privateKey;
    privateKeyFormatted = privateKeyFormatted + PRIVATE_KEY_END;
    return privateKeyFormatted;
  }

  private long getSecretLastModifiedAt(BackstageEnvVariable envVariable, String accountIdentifier) {
    if (envVariable.getType().equals(BackstageEnvVariable.TypeEnum.CONFIG)) {
      return 0L;
    }
    BackstageEnvSecretVariable secret = (BackstageEnvSecretVariable) envVariable;
    return getDecryptedValueAndLastModifiedTime(
        secret.getEnvName(), secret.getHarnessSecretIdentifier(), accountIdentifier, null, null)
        .getSecond();
  }
}
