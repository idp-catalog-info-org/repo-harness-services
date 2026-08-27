/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.BACKSTAGE_KINDS;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.DateUtils.startOfTheDayInMilliseconds;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.events.CheckCreateEvent;
import io.harness.idp.scorecard.checks.events.CheckDeleteEvent;
import io.harness.idp.scorecard.checks.events.CheckUpdateEvent;
import io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.checks.mappers.CheckStatsMapper;
import io.harness.idp.scorecard.checks.mappers.ComplexCheckParser;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatsDuplicateEntry;
import io.harness.idp.scorecard.checks.repositories.CheckStatsRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatusEntityByIdentifier;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepository;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStatsResponse;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.InputDetails;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.ParseAndValidate;
import io.harness.spec.server.idp.v1.model.Rule;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jooq.tools.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CheckServiceImpl implements CheckService {
  private final CheckRepository checkRepository;
  private final CheckStatusRepository checkStatusRepository;
  private final CheckStatsRepository checkStatsRepository;
  private final ScorecardService scorecardService;
  private final NGSettingsClient settingsClient;
  private final DataPointService dataPointService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private final OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject
  public CheckServiceImpl(CheckRepository checkRepository, CheckStatusRepository checkStatusRepository,
      CheckStatsRepository checkStatsRepository, ScorecardService scorecardService, NGSettingsClient settingsClient,
      DataPointService dataPointService, TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.checkRepository = checkRepository;
    this.checkStatusRepository = checkStatusRepository;
    this.checkStatsRepository = checkStatsRepository;
    this.scorecardService = scorecardService;
    this.settingsClient = settingsClient;
    this.dataPointService = dataPointService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public void createCheck(CheckDetails checkDetails, String accountIdentifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (checkDetails.getRuleStrategy().equals(CheckDetails.RuleStrategyEnum.ADVANCED)) {
        ParseAndValidate response = parseAndValidateComplexCheck(checkDetails.getExpression(), accountIdentifier);
        response.getRules().forEach(rule -> rule.setRuleDescription(checkDetails.getRuleDescription()));
        checkDetails.setRules(response.getRules());
        generateRuleIdentifiersIfNotPresent(checkDetails);
      } else {
        generateRuleIdentifiersIfNotPresent(checkDetails);
        validateCheckSaveRequest(checkDetails, accountIdentifier);
      }

      if (isCheckAlreadyDeleted(accountIdentifier, checkDetails.getIdentifier())) {
        checkDetails.setIdentifier(checkDetails.getIdentifier() + new Random().nextInt(9999));
      }
      CheckEntity savedCheckEntity = checkRepository.save(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
      outboxService.save(new CheckCreateEvent(accountIdentifier, CheckDetailsMapper.toDTO(savedCheckEntity, null)));
      return true;
    }));
  }

  @Override
  public void updateCheck(CheckDetails checkDetails, String accountIdentifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (checkDetails.getRuleStrategy().equals(CheckDetails.RuleStrategyEnum.ADVANCED)) {
        ParseAndValidate response = parseAndValidateComplexCheck(checkDetails.getExpression(), accountIdentifier);
        response.getRules().forEach(rule -> rule.setRuleDescription(checkDetails.getRuleDescription()));
        checkDetails.setRules(response.getRules());
        generateRuleIdentifiersIfNotPresent(checkDetails);
      } else {
        generateRuleIdentifiersIfNotPresent(checkDetails);
        validateCheckSaveRequest(checkDetails, accountIdentifier);
      }

      CheckEntity oldCheckEntity =
          checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, checkDetails.getIdentifier());
      CheckEntity updatedCheckEntity =
          checkRepository.update(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
      if (updatedCheckEntity == null) {
        throw new InvalidRequestException("Default checks cannot be updated");
      }
      outboxService.save(new CheckUpdateEvent(accountIdentifier, CheckDetailsMapper.toDTO(updatedCheckEntity, null),
          CheckDetailsMapper.toDTO(oldCheckEntity, null)));
      return true;
    }));
  }

  @Override
  public Page<CheckEntity> getChecksByAccountId(
      Boolean custom, String accountIdentifier, Pageable pageRequest, String searchTerm) {
    Criteria criteria = buildCriteriaForChecksList(accountIdentifier, custom, searchTerm);
    return checkRepository.findAll(criteria, pageRequest);
  }

  @Override
  public List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers) {
    return checkRepository.findByAccountIdentifierInAndIsDeletedAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), false, checkIdentifiers);
  }

  @Override
  public CheckDetails getCheckDetails(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity;
    if (Boolean.TRUE.equals(custom)) {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    } else {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, identifier);
    }
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check details not found for checkId [%s]", identifier));
    }
    CheckStatusEntity checkStatusEntity =
        getCheckStatusByAccountIdAndIdentifiers(accountIdentifier, List.of(identifier))
            .get((custom ? accountIdentifier : GLOBAL_ACCOUNT_ID) + DOT_SEPARATOR + identifier);
    return CheckDetailsMapper.toDTO(checkEntity, checkStatusEntity);
  }

  @Override
  public List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, Set<String> identifiers) {
    return checkRepository.findByAccountIdentifierInAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), identifiers);
  }

  @Override
  public CheckStatsResponse getCheckStats(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(
        custom ? accountIdentifier : GLOBAL_ACCOUNT_ID, identifier);
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check stats not found for checkId [%s]", identifier));
    }
    Optional<CheckStatsEntity> lastUpdatedRecord =
        checkStatsRepository.findFirstByAccountIdentifierAndCheckIdentifierAndIsCustomOrderByLastUpdatedAtDesc(
            accountIdentifier, identifier, custom);
    List<CheckStatsEntity> checkStatsEntities = new ArrayList<>();
    if (lastUpdatedRecord.isPresent()) {
      checkStatsEntities =
          checkStatsRepository.findByAccountIdentifierAndCheckIdentifierAndIsCustomAndLastUpdatedAtGreaterThan(
              accountIdentifier, identifier, custom,
              startOfTheDayInMilliseconds(lastUpdatedRecord.get().getLastUpdatedAt()));
    }

    return CheckStatsMapper.toDTO(checkStatsEntities, checkEntity.getName());
  }

  @Override
  public List<CheckGraph> getCheckGraph(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(
        custom ? accountIdentifier : GLOBAL_ACCOUNT_ID, identifier);
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check graph not found for checkId [%s]", identifier));
    }
    return CheckStatsMapper.toDTO(
        checkStatusRepository.findByAccountIdentifierAndIdentifierAndIsCustom(accountIdentifier, identifier, custom));
  }

  @Override
  public Map<String, CheckStatusEntity> getCheckStatusByAccountIdAndIdentifiers(
      String accountIdentifier, List<String> identifiers) {
    List<CheckStatusEntityByIdentifier> lastComputedCheckStatus =
        checkStatusRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, identifiers);
    return lastComputedCheckStatus.stream().collect(Collectors.toMap(checkStatusEntityByIdentifier
        -> (checkStatusEntityByIdentifier.isCustom() ? accountIdentifier : GLOBAL_ACCOUNT_ID) + DOT_SEPARATOR
            + checkStatusEntityByIdentifier.getIdentifier(),
        CheckStatusEntityByIdentifier::getCheckStatusEntity));
  }

  @Override
  public void migrateEntityIdentifier(Map<String, String> entityIdentifiersMap, String accountIdentifier) {
    List<String> entityIdentifiers = checkStatsRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in CheckStats collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      if (entityIdentifiersMap.containsKey(entityIdentifier)) {
        UpdateResult updateResult = checkStatsRepository.updateEntityIdentifier(
            accountIdentifier, entityIdentifier, entityIdentifiersMap.get(entityIdentifier));
        log.info("Totally {} records modified in CheckStats collection for account {}, identifier {}",
            updateResult.getModifiedCount(), accountIdentifier, entityIdentifiersMap.get(entityIdentifier));
      }
    });
  }

  @Override
  public void modifyEntityIdentifier(String accountIdentifier) {
    List<String> entityIdentifiers = checkStatsRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in CheckStats collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      String[] kindNamespaceAndName = entityIdentifier.split("/");
      if (kindNamespaceAndName.length == 3 && BACKSTAGE_KINDS.contains(kindNamespaceAndName[0])) {
        String modifiedEntityIdentifier =
            getEntityUniqueId(kindNamespaceAndName[1], kindNamespaceAndName[0], kindNamespaceAndName[2]);
        try {
          UpdateResult updateResult = checkStatsRepository.updateEntityIdentifier(
              accountIdentifier, entityIdentifier, modifiedEntityIdentifier);
          log.info("Totally {} records modified in CheckStats collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityIdentifier);
        } catch (Exception e) {
          log.error("Error occurred while modifying CheckStats collection for account {}, identifier {}",
              accountIdentifier, entityIdentifier, e);
        }
      }
    });
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids) {
    List<CheckStatsDuplicateEntry> duplicateEntries = checkStatsRepository.findDuplicateEntities(accountIdentifier);
    List<String> idsToBeRemoved = new ArrayList<>();
    for (CheckStatsDuplicateEntry entry : duplicateEntries) {
      String[] namespaceKindName = entry.getEntityIdentifier().split("/");
      if (namespaceKindName.length == 3 && namespaceKindName[0].equals("account")
          && namespaceKindName[0].contains(".")) {
        continue;
      }
      log.info("Duplicate entries found in CheckStats collection for accountId: {}, entityIdentifier: {}, "
              + "checkIdentifier: {}, ids: {}",
          entry.getAccountIdentifier(), entry.getEntityIdentifier(), entry.getCheckIdentifier(), entry.getDuplicates());
      idsToBeRemoved.addAll(entry.getDuplicates().subList(1, entry.getDuplicates().size()).stream().toList());
    }
    if (!isEmpty(idsToBeRemoved)) {
      checkStatsRepository.deleteAllById(idsToBeRemoved);
    }
    List<CheckStatsEntity> checkStatsEntities = checkStatsRepository.findByAccountIdentifier(accountIdentifier);
    log.info("Totally {} records present in CheckStats collection for account {}", checkStatsEntities.size(),
        accountIdentifier);
    List<CheckStatsEntity> entitiesToSave = new ArrayList<>();
    for (CheckStatsEntity checkStatsEntity : checkStatsEntities) {
      String entityUid = checkStatsEntity.getEntityIdentifier();
      String[] namespaceKindName = entityUid.split("/");
      if (namespaceKindName.length == 3 && !namespaceKindName[0].equals("account")
          && !namespaceKindName[0].contains(".")) {
        String namespace = namespaceKindName[0].toLowerCase();
        String kind = namespaceKindName[1].toLowerCase();
        String name = namespaceKindName[2].toLowerCase();
        String modifiedEntityUid = "account/" + kind + "/" + name;
        String modifiedEntityUidForConflict = "account/" + kind + "/" + namespace + "_" + name;
        if (conflictedEntityUids.contains(modifiedEntityUidForConflict)) {
          name = namespace + "_" + name;
          modifiedEntityUid = modifiedEntityUidForConflict;
        }
        checkStatsEntity.setEntityIdentifier(modifiedEntityUid);
        StatsMetadata metadata = checkStatsEntity.getMetadata();
        if (metadata != null) {
          metadata.setNamespace("account");
          metadata.setName(name);
          checkStatsEntity.setMetadata(metadata);
        }
        checkStatsEntity.setMetadata(metadata);
        boolean isCheckStatsEntityPresent = checkStatsEntities.stream().anyMatch(entity
            -> entity.getEntityIdentifier().equals(checkStatsEntity.getEntityIdentifier())
                && entity.getCheckIdentifier().equals(checkStatsEntity.getCheckIdentifier()));
        if (!isCheckStatsEntityPresent) {
          entitiesToSave.add(checkStatsEntity);
        }
      }
    }
    log.info("Totally {} records to be modified in ScorecardStats collection for account {}", entitiesToSave.size(),
        accountIdentifier);
    checkStatsRepository.saveAll(entitiesToSave);
  }

  @Override
  public void modifyScopeForEntityIdentifier(
      String accountIdentifier, String existingEntityIdentifier, String modifiedEntityIdentifier) {
    try {
      UpdateResult updateResult = checkStatsRepository.updateEntityIdentifier(
          accountIdentifier, existingEntityIdentifier, modifiedEntityIdentifier);
      log.info("Totally {} records modified in CheckStats collection for IDP 2.0 MigrationAPI Operation for account "
              + "{}, identifier {}",
          updateResult.getModifiedCount(), accountIdentifier, existingEntityIdentifier);
    } catch (Exception e) {
      log.error("Error occurred while modifying CheckStats collection for IDP 2.0 MigrationAPI Operation for account "
              + "{}, identifier {}",
          accountIdentifier, existingEntityIdentifier, e);
    }
  }

  @Override
  public void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (forceDelete && !isForceDeleteSettingEnabled(accountIdentifier)) {
        throw new InvalidRequestException(
            format("Parameter forceDelete cannot be true. Force deletion of check is not enabled for this account [%s]",
                accountIdentifier));
      }
      int numberOfUsages = getCountOfScorecardsReferences(accountIdentifier, identifier);
      if (numberOfUsages < 1) {
        CheckEntity oldCheckEntity =
            checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
        outboxService.save(new CheckDeleteEvent(accountIdentifier, CheckDetailsMapper.toDTO(oldCheckEntity, null)));

        checkRepository.deleteByAccountIdentifierAndIdentifierAndIsCustom(accountIdentifier, identifier, true);
      } else if (!forceDelete) {
        throw new ReferencedEntityException(
            format("Could not delete the check [%s] as it is referenced by other scorecards", identifier));
      } else {
        CheckEntity oldCheckEntity =
            checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
        outboxService.save(new CheckDeleteEvent(accountIdentifier, CheckDetailsMapper.toDTO(oldCheckEntity, null)));

        UpdateResult updateResult = checkRepository.updateDeleted(accountIdentifier, identifier);
        if (updateResult.getModifiedCount() == 0) {
          throw new InvalidRequestException("Default checks cannot be deleted");
        }
      }
      return true;
    }));
  }

  private int getCountOfScorecardsReferences(String accountIdentifier, String checkIdentifier) {
    List<String> scorecardIdentifiers =
        scorecardService.getScorecardIdentifiers(accountIdentifier, checkIdentifier, true);
    return scorecardIdentifiers.size();
  }

  protected boolean isForceDeleteSettingEnabled(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }

  private Criteria buildCriteriaForChecksList(String accountIdentifier, Boolean custom, String searchTerm) {
    Criteria criteria = new Criteria();
    if (custom == null) {
      criteria.and(CheckEntity.CheckKeys.accountIdentifier).in(addGlobalAccountIdentifierAlong(accountIdentifier));
    } else {
      String accountId = custom ? accountIdentifier : GLOBAL_ACCOUNT_ID;
      criteria.and(CheckEntity.CheckKeys.accountIdentifier)
          .is(accountId)
          .and(CheckEntity.CheckKeys.isCustom)
          .is(custom);
    }

    if (isNotEmpty(searchTerm)) {
      criteria.andOperator(buildSearchCriteria(searchTerm));
    }

    criteria.and(CheckEntity.CheckKeys.isDeleted).is(false);
    return criteria;
  }

  private Criteria buildSearchCriteria(String searchTerm) {
    return new Criteria().orOperator(
        where(CheckEntity.CheckKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.identifier)
            .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.tags).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
  }

  private void validateCheckSaveRequest(CheckDetails checkDetails, String accountIdentifier) {
    Map<String, DataPoint> dataPointMap = dataPointService.getDataPointsMap(accountIdentifier);
    for (Rule rule : checkDetails.getRules()) {
      String key = rule.getDataSourceIdentifier() + DOT_SEPARATOR + rule.getDataPointIdentifier();
      if (!dataPointMap.containsKey(key)) {
        throw new InvalidRequestException(format("Data point not found for dataSource: %s, dataPoint: %s",
            rule.getDataSourceIdentifier(), rule.getDataPointIdentifier()));
      }

      // TODO: Remove this condition once UI sends in new format
      if (rule.getInputValues() != null) {
        DataPoint dataPoint = dataPointMap.get(key);
        List<InputValue> inputValues = rule.getInputValues();
        for (InputValue inputValue : inputValues) {
          Optional<InputDetails> inputDetailsOpt =
              dataPoint.getInputDetails()
                  .stream()
                  .filter(inputDetails -> inputDetails.getKey().equals(inputValue.getKey()))
                  .findFirst();
          if (inputDetailsOpt.isEmpty()) {
            throw new InvalidRequestException(String.format(
                "Conditional input value for key %s does not match any data point input details", inputValue.getKey()));
          }
          String value = inputValue.getValue();
          if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }
          if (inputDetailsOpt.get().isRequired() && isEmpty(value)) {
            throw new InvalidRequestException(
                String.format("Conditional input value for key %s is required", inputValue.getKey()));
          }
        }
      }
    }
  }

  private boolean isCheckAlreadyDeleted(String accountIdentifier, String checkId) {
    CheckEntity checkEntity =
        checkRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(accountIdentifier, checkId, true);
    return checkEntity != null;
  }

  private void generateRuleIdentifiersIfNotPresent(CheckDetails checkDetails) {
    for (Rule rule : checkDetails.getRules()) {
      if (StringUtils.isBlank(rule.getIdentifier())) {
        rule.setIdentifier(UUID.randomUUID().toString());
      }
    }
  }

  private ParseAndValidate parseAndValidateComplexCheck(String expression, String accountIdentifier) {
    final String QUOTED_SEGMENT = "\"(?:[^\"\\\\]|\\\\.)*\"";
    final Pattern PART_EXTRACTOR = Pattern.compile("\"((?:\\\\.|[^\"])*)\"|([^.]+)");

    final String OPERATORS = "==|!=|>=|<=|=~|!~|=\\^|!\\^|=\\$|!\\$|>|<";
    final String RHS_VALUE = "(?:[0-9]+|true|false|" + QUOTED_SEGMENT + ")";
    final String ARRAY_OF_VALUES = "\\[\\s*" + RHS_VALUE + "(?:\\s*,\\s*" + RHS_VALUE + ")*\\s*\\]";
    final Pattern RULE_PATTERN = Pattern.compile(
        "([a-zA-Z_-]+\\.[a-zA-Z_-]+(?:\\." + QUOTED_SEGMENT + "){0,3})" + // identifiers + optional quoted
        "\\s+(" + OPERATORS + ")\\s+" + // operator
        "(" + RHS_VALUE + "|" + ARRAY_OF_VALUES + ")" // RHS value (single value or array)
    );

    // Validate Syntax
    ComplexCheckParser.validateExpression(expression);

    Map<String, DataPoint> dataPointMap = dataPointService.getDataPointsMap(accountIdentifier);
    ParseAndValidate response = new ParseAndValidate();
    final Set<Rule> result = new HashSet<>();
    final Matcher ruleMatcher = RULE_PATTERN.matcher(expression);

    while (ruleMatcher.find()) {
      String fullIdentifier = ruleMatcher.group(1).trim();
      String operator = ruleMatcher.group(2).trim();
      String rawValue = ruleMatcher.group(3).trim();

      String finalValue = rawValue;
      if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
        finalValue = rawValue.substring(1, rawValue.length() - 1);
      }

      List<String> parts = new ArrayList<>();
      Matcher partMatcher = PART_EXTRACTOR.matcher(fullIdentifier);
      while (partMatcher.find()) {
        parts.add(partMatcher.group(1) != null ? partMatcher.group(1) : partMatcher.group(2));
      }

      if (parts.size() < 2) {
        continue;
      }

      String dataSource = parts.get(0);
      String dataPoint = parts.get(1);

      Rule rule = new Rule();
      rule.setDataSourceIdentifier(dataSource);
      rule.setDataPointIdentifier(dataPoint);
      rule.setOperator(operator);
      rule.setValue(CheckDetailsMapper.cleanComplexCheck(finalValue));

      DataPoint dataPointKey = dataPointMap.get(dataSource + DOT_SEPARATOR + dataPoint);
      List<InputDetails> dataPointInputDetails;
      if (dataPointKey != null) {
        dataPointInputDetails = dataPointKey.getInputDetails();
      } else {
        String errorMessage = String.format("Datapoint %s.%s doesn't exist", dataSource, dataPoint);
        log.error("Error while parsing check expression for accountIdentifier = {}, error = {}", accountIdentifier,
            errorMessage);
        throw new InvalidRequestException(errorMessage);
      }

      if (parts.size() > 2) {
        List<String> datapointInputValues = parts.subList(2, parts.size());

        validateDataPointInputValues(
            datapointInputValues, dataSource, dataPoint, dataPointMap, accountIdentifier, dataPointInputDetails);

        List<InputValue> inputValuesList = new ArrayList<>();
        for (int i = 0; i < datapointInputValues.size(); i++) {
          InputValue inputValue = new InputValue();
          if (dataPointInputDetails.get(i).isRequired() && datapointInputValues.get(i).trim().isEmpty()) {
            String errorMessage = String.format("Required input value '%s' cannot be empty for data point %s.%s",
                dataPointInputDetails.get(i).getKey(), dataSource, dataPoint);
            log.error("Error while parsing check expression for accountIdentifier = {}, error = {}", accountIdentifier,
                errorMessage);
            throw new InvalidRequestException(errorMessage);
          }
          inputValue.setKey(dataPointInputDetails.get(i).getKey());

          StringBuilder cleanInputValue = new StringBuilder();

          cleanInputValue.append("\"")
              .append(CheckDetailsMapper.cleanComplexCheck(datapointInputValues.get(i)))
              .append("\"");
          inputValue.setValue(cleanInputValue.toString());
          inputValuesList.add(inputValue);
        }
        rule.setInputValues(inputValuesList);
      } else {
        if (dataPointInputDetails != null && !dataPointInputDetails.isEmpty()) {
          // this means the data point has input values but user hasn't provided any
          validateDataPointInputValues(
              new ArrayList<>(), dataSource, dataPoint, dataPointMap, accountIdentifier, dataPointInputDetails);
        }
      }
      result.add(rule);
    }

    response.setIsExpressionValid(true);
    response.setRules(result.stream().toList());
    return response;
  }

  private void validateDataPointInputValues(List<String> datapointInputValues, String dataSource, String dataPoint,
      Map<String, DataPoint> dataPointMap, String accountIdentifier, List<InputDetails> dataPointInputDetails) {
    String key = dataSource + DOT_SEPARATOR + dataPoint;
    if (!dataPointMap.containsKey(key)) {
      String errorMessage =
          String.format("Data point not found for dataSource: %s, dataPoint: %s", dataSource, dataPoint);
      log.error("Error while parsing check expression for accountIdentifier = {}, error = {}", accountIdentifier,
          errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    if (datapointInputValues.size() != dataPointInputDetails.size()) {
      String errorMessage =
          String.format("Expected Data point input values size is %d but received %d for data point %s.%s",
              dataPointInputDetails.size(), datapointInputValues.size(), dataSource, dataPoint);
      log.error("Error while parsing check expression for accountIdentifier = {}, error = {}", accountIdentifier,
          errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
  }
  @Override
  public Boolean validateComplexCheck(String expression, String accountIdentifier) {
    try {
      ParseAndValidate response = parseAndValidateComplexCheck(expression, accountIdentifier);
      return response.isIsExpressionValid();
    } catch (Exception e) {
      log.info("Complex check validation failed for account: {}, error: {}", accountIdentifier, e.getMessage());

      throw new InvalidRequestException(String.format(e.getMessage()));
    }
  }

  @Override
  public List<String> getCheckTags(String accountIdentifier, String searchTerm) {
    return checkRepository.findUniqueTags(accountIdentifier, searchTerm, 10);
  }
}
