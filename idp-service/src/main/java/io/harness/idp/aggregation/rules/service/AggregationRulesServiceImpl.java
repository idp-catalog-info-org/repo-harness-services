/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.service;

import static io.harness.exception.WingsException.USER;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_COMPUTE;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_VIEW;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.entity.EntityDiffUtils;
import io.harness.idp.aggregation.rules.events.AggregationRuleComputeInitiatedEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleCreateEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleDeleteEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleUpdateEvent;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.aggregation.rules.mapper.AggregationRulesMapper;
import io.harness.idp.aggregation.rules.processor.AggregationProcessor;
import io.harness.idp.aggregation.rules.processor.AggregationRulesProcessorFactory;
import io.harness.idp.aggregation.rules.repositories.AggregationRuleRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsRequest;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;
import io.harness.spec.server.idp.v1.model.AggregationScopeLevel;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewRequest;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewResponse;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class AggregationRulesServiceImpl implements AggregationRulesService {
  private static final String AGG_RULE_NOT_FOUND_MSG = "Aggregation rule not found for accountId [%s], identifier [%s]";
  private final AggregationRuleRepository aggregationRuleRepository;
  private final AggregationRulesHelper aggregationRulesHelper;
  private final TransactionHelper transactionHelper;
  private final OutboxService outboxService;
  private final AggregationRulesProcessorFactory aggregationRulesProcessorFactory;

  @Inject
  public AggregationRulesServiceImpl(AggregationRuleRepository aggregationRuleRepository,
      AggregationRulesHelper aggregationRulesHelper, TransactionHelper transactionHelper, OutboxService outboxService,
      AggregationRulesProcessorFactory aggregationRulesProcessorFactory) {
    this.aggregationRuleRepository = aggregationRuleRepository;
    this.aggregationRulesHelper = aggregationRulesHelper;
    this.transactionHelper = transactionHelper;
    this.outboxService = outboxService;
    this.aggregationRulesProcessorFactory = aggregationRulesProcessorFactory;
  }

  @Override
  public Page<AggregationRuleEntity> getAggregationRules(
      String accountIdentifier, Pageable pageable, String searchTerm) {
    Set<String> permittedIdentifiers =
        aggregationRulesHelper.checkAggregationRulesRbac(accountIdentifier, IDP_AGGREGATION_RULE_VIEW, searchTerm);
    return aggregationRuleRepository.getAggregationRules(accountIdentifier, pageable, permittedIdentifiers);
  }

  @Override
  public AggregationRuleDetailsResponse getAggregationRule(String accountIdentifier, String aggregationRuleIdentifier) {
    AggregationRuleEntity entity =
        aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, aggregationRuleIdentifier);
    if (entity == null) {
      throw new NotFoundException(String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
    }
    if (!aggregationRulesHelper.hasAggregationRulePermission(
            accountIdentifier, aggregationRuleIdentifier, IDP_AGGREGATION_RULE_VIEW)) {
      log.warn("Access denied: User does not have permission to view aggregation rule: {} in account: {}",
          aggregationRuleIdentifier, accountIdentifier);
      throw new NGAccessDeniedException(
          String.format("You do not have permission to view aggregation rule: %s", aggregationRuleIdentifier), USER,
          null);
    }

    return AggregationRulesMapper.toDetailsResponseDTO(entity);
  }

  @Override
  public AggregationRuleDetailsResponse createAggregationRule(
      String accountIdentifier, AggregationRuleDetailsRequest aggregationRuleRequest) {
    validateCreateOrUpdateRequest(accountIdentifier, aggregationRuleRequest);
    AggregationRuleDetails details = aggregationRuleRequest.getAggregationRule();
    return transactionHelper.performTransaction(() -> {
      AggregationRuleEntity ruleEntity = AggregationRulesMapper.fromDTO(accountIdentifier, details);
      ruleEntity.setStatus(AggregationRuleEntity.ComputedStatus.CALCULATING);
      AggregationRuleEntity savedEntity = aggregationRuleRepository.save(ruleEntity);
      AggregationRuleDetailsResponse detailsResponse = AggregationRulesMapper.toDetailsResponseDTO(savedEntity);
      outboxService.save(new AggregationRuleCreateEvent(accountIdentifier, detailsResponse));
      return detailsResponse;
    });
  }

  @Override
  public AggregationRuleDetailsResponse updateAggregationRule(
      String accountIdentifier, AggregationRuleDetailsRequest aggregationRuleRequest) {
    validateCreateOrUpdateRequest(accountIdentifier, aggregationRuleRequest);
    AggregationRuleDetails details = aggregationRuleRequest.getAggregationRule();

    if (!aggregationRulesHelper.hasAggregationRulePermission(
            accountIdentifier, details.getIdentifier(), IDP_AGGREGATION_RULE_EDIT)) {
      log.warn("Access denied: User does not have permission to update aggregation rule: {} in account: {}",
          details.getIdentifier(), accountIdentifier);
      throw new NGAccessDeniedException(
          String.format("You do not have permission to update aggregation rule: %s", details.getIdentifier()), USER,
          null);
    }

    return transactionHelper.performTransaction(() -> {
      AggregationRuleEntity existing =
          aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, details.getIdentifier());
      if (existing == null) {
        throw new InvalidRequestException(
            String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, details.getIdentifier()));
      }
      if (existing.getStatus() != null
          && existing.getStatus().equals(AggregationRuleEntity.ComputedStatus.CALCULATING)) {
        throw new BadRequestException("Aggregation rule cannot be updated while a previous computation is in progress");
      }
      if (existing.getAggregationType() == null
          || !existing.getAggregationType().name().equals(details.getAggregationType().name())) {
        throw new BadRequestException("Aggregation type cannot be updated");
      }
      AggregationRuleEntity toUpdate = AggregationRulesMapper.fromDTO(accountIdentifier, details);
      // set internal fields
      toUpdate.setId(existing.getId());
      toUpdate.setStatus(AggregationRuleEntity.ComputedStatus.CALCULATING);
      toUpdate.setLastUpdatedAt(existing.getLastUpdatedAt());
      toUpdate.setLastErrorMessage(existing.getLastErrorMessage());
      toUpdate.setNextIteration(existing.getNextIteration());
      toUpdate.setLastComputedAt(existing.getLastComputedAt());

      AggregationRuleEntity updatedEntity = aggregationRuleRepository.save(toUpdate);
      AggregationRuleDetailsResponse detailsResponse = AggregationRulesMapper.toDetailsResponseDTO(updatedEntity);
      AggregationRuleDetailsResponse oldDetailsResponse = AggregationRulesMapper.toDetailsResponseDTO(existing);
      outboxService.save(new AggregationRuleUpdateEvent(accountIdentifier, detailsResponse, oldDetailsResponse));
      return detailsResponse;
    });
  }

  @Override
  public void deleteAggregationRule(String accountIdentifier, String aggregationRuleIdentifier) {
    if (!aggregationRulesHelper.hasAggregationRulePermission(
            accountIdentifier, aggregationRuleIdentifier, IDP_AGGREGATION_RULE_DELETE)) {
      log.warn("Access denied: User does not have permission to delete aggregation rule: {} in account: {}",
          aggregationRuleIdentifier, accountIdentifier);
      throw new NGAccessDeniedException(
          String.format("You do not have permission to delete aggregation rule: %s", aggregationRuleIdentifier), USER,
          null);
    }

    AggregationRuleEntity entity =
        aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, aggregationRuleIdentifier);
    if (entity == null) {
      throw new NotFoundException(String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
    }
    transactionHelper.performTransaction(() -> {
      long deleted = aggregationRuleRepository.deleteByAccountIdentifierAndIdentifier(
          accountIdentifier, aggregationRuleIdentifier);
      if (deleted == 0) {
        throw new InvalidRequestException(
            String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
      }
      AggregationRuleDetailsResponse detailsResponse = AggregationRulesMapper.toDetailsResponseDTO(entity);
      outboxService.save(new AggregationRuleDeleteEvent(accountIdentifier, detailsResponse));
      return true;
    });
  }

  @Override
  public void triggerComputation(String accountIdentifier, String aggregationRuleIdentifier) {
    if (!aggregationRulesHelper.hasAggregationRulePermission(
            accountIdentifier, aggregationRuleIdentifier, IDP_AGGREGATION_RULE_COMPUTE)) {
      log.warn(
          "Access denied: User does not have permission to trigger computation for aggregation rule: {} in account: {}",
          aggregationRuleIdentifier, accountIdentifier);
      throw new NGAccessDeniedException(
          String.format(
              "You do not have permission to trigger computation for aggregation rule: %s", aggregationRuleIdentifier),
          USER, null);
    }

    transactionHelper.performTransaction(() -> {
      AggregationRuleEntity entity =
          aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, aggregationRuleIdentifier);
      if (entity == null) {
        throw new InvalidRequestException(
            String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
      }
      if (entity.getStatus() == AggregationRuleEntity.ComputedStatus.CALCULATING) {
        log.info("Computation already in progress for aggregation rule: {}", aggregationRuleIdentifier);
        throw new InvalidRequestException(String.format("Computation already in progress for aggregation rule: %s/%s",
            accountIdentifier, aggregationRuleIdentifier));
      }
      entity.setStatus(AggregationRuleEntity.ComputedStatus.CALCULATING);
      AggregationRuleEntity saved = aggregationRuleRepository.save(entity);
      outboxService.save(
          new AggregationRuleComputeInitiatedEvent(accountIdentifier, aggregationRuleIdentifier, entity.getName()));
      return true;
    });
  }

  @Override
  public AggregationSelectionReviewResponse reviewAggregationRuleSelection(
      String accountIdentifier, AggregationSelectionReviewRequest aggregationSelectionReviewRequest) {
    validateReviewRequest(aggregationSelectionReviewRequest);
    List<String> scopes = aggregationSelectionReviewRequest.getScopes() != null
        ? aggregationSelectionReviewRequest.getScopes()
        : aggregationRulesHelper.getDefaultScopeSelector();
    return aggregationRulesHelper.buildAggregationResponse(
        accountIdentifier, scopes, aggregationSelectionReviewRequest.getScopesToAggregateAt());
  }

  private void validateCreateOrUpdateRequest(String accountIdentifier, AggregationRuleDetailsRequest request) {
    if (request == null || request.getAggregationRule() == null) {
      throw new InvalidRequestException("Aggregation Rule Request cannot be empty");
    }
    AggregationRuleDetails details = request.getAggregationRule();
    if (Strings.isBlank(details.getIdentifier())) {
      throw new InvalidRequestException("Aggregation rule identifier cannot be empty");
    }
    if (Strings.isBlank(details.getName())) {
      throw new InvalidRequestException("Aggregation rule name cannot be empty");
    }
    if (Strings.isBlank(details.getFieldForAgg())) {
      throw new InvalidRequestException("The field for aggregation cannot be empty");
    }
    if (details.getAggFormula() == null) {
      throw new InvalidRequestException("The aggregation formula cannot be empty");
    }
    if (details.getScopesToAggregateAt() == null || details.getScopesToAggregateAt().isEmpty()) {
      throw new InvalidRequestException("The scopes to aggregate at cannot be empty");
    } else {
      for (AggregationScopeLevel level : details.getScopesToAggregateAt()) {
        if (level == null) {
          throw new InvalidRequestException("The scopes to aggregate at contains invalid values");
        }
      }
    }
    if (details.getAggregationType() == null) {
      throw new InvalidRequestException("The aggregation type cannot be empty");
    }
    aggregationRulesHelper.validateKind(accountIdentifier, details.getEntitySelectionCriteria().getKind());
  }

  private void validateReviewRequest(AggregationSelectionReviewRequest request) {
    if (request.getScopesToAggregateAt() == null || request.getScopesToAggregateAt().isEmpty()) {
      throw new InvalidRequestException("The scopes to aggregate at cannot be empty");
    } else {
      for (AggregationScopeLevel level : request.getScopesToAggregateAt()) {
        if (level == null) {
          throw new InvalidRequestException("The scopes to aggregate at contains invalid values");
        }
      }
    }
  }

  @Override
  public void compute(AggregationRuleEntity aggregationRuleEntity) {
    try {
      AggregationProcessor aggregationProcessor =
          aggregationRulesProcessorFactory.createProcessor(aggregationRuleEntity);
      List<AggregationRulesDTO> aggregationRulesDTOS = aggregationProcessor.process();
      aggregationProcessor.save(aggregationRulesDTOS);
      aggregationRulesHelper.updateEntity(aggregationRuleEntity, AggregationRuleEntity.ComputedStatus.SUCCESS, null);
    } catch (Exception e) {
      log.error("Error while computing aggregation rules for account - {} identifier - {}",
          aggregationRuleEntity.getAccountIdentifier(), aggregationRuleEntity.getIdentifier(), e);
      aggregationRulesHelper.updateEntity(
          aggregationRuleEntity, AggregationRuleEntity.ComputedStatus.ERROR, e.getMessage());
    }
  }

  @Override
  public void compute(String accountIdentifier, String aggregationRuleIdentifier) {
    AggregationRuleEntity aggregationRuleEntity =
        aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, aggregationRuleIdentifier);
    if (aggregationRuleEntity == null) {
      throw new NotFoundException(String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
    }
    compute(aggregationRuleEntity);
  }

  @Override
  public void compute(
      String accountIdentifier, AggregationRuleDetails oldAggregationRule, AggregationRuleDetails newAggregationRule) {
    boolean isOnlyNameChanged = EntityDiffUtils.isOnlyNameChanged(oldAggregationRule, newAggregationRule);
    boolean isOnlyDescriptionChanged = EntityDiffUtils.isOnlyDescriptionChanged(oldAggregationRule, newAggregationRule);
    boolean isOnlyScopesToAggregateAtChanged =
        EntityDiffUtils.isOnlyScopesToAggregateAtChanged(oldAggregationRule, newAggregationRule);
    boolean isOnlyScopeFilterChanged = EntityDiffUtils.isOnlyScopeFilterChanged(oldAggregationRule, newAggregationRule);

    boolean isNameChanged = EntityDiffUtils.isNameChanged(oldAggregationRule, newAggregationRule);
    boolean isFieldForAggChanged = EntityDiffUtils.isFieldForAggChanged(oldAggregationRule, newAggregationRule);
    boolean isAggFormulaChanged = EntityDiffUtils.isAggFormulaChanged(oldAggregationRule, newAggregationRule);
    boolean isAggregationTypeChanged = EntityDiffUtils.isAggregationTypeChanged(oldAggregationRule, newAggregationRule);
    boolean isScopesToAggregateAtChanged =
        EntityDiffUtils.isScopesToAggregateAtChanged(oldAggregationRule, newAggregationRule);
    boolean isScopeFilterChanged = EntityDiffUtils.isScopeFilterChanged(oldAggregationRule, newAggregationRule);
    boolean hasOtherFilterChanged = EntityDiffUtils.hasOtherFilterChanged(oldAggregationRule, newAggregationRule);

    if (isOnlyNameChanged
        || (isNameChanged && !isFieldForAggChanged && !isAggFormulaChanged && !isAggregationTypeChanged
            && !isScopesToAggregateAtChanged && !isScopeFilterChanged && !hasOtherFilterChanged)) {
      rename(accountIdentifier, newAggregationRule.getIdentifier(), oldAggregationRule.getName());
      return;
    }

    if (isOnlyDescriptionChanged) {
      return;
    }

    if (isOnlyScopesToAggregateAtChanged || isOnlyScopeFilterChanged || isScopesToAggregateAtChanged
        || isScopeFilterChanged) {
      deleteRuleFieldsFromHierarchicalEntities(accountIdentifier, oldAggregationRule);
      compute(accountIdentifier, newAggregationRule.getIdentifier());
      return;
    }

    compute(accountIdentifier, newAggregationRule.getIdentifier());
  }

  @Override
  public void rename(String accountIdentifier, String aggregationRuleIdentifier, String oldName) {
    AggregationRuleEntity aggregationRuleEntity =
        aggregationRuleRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, aggregationRuleIdentifier);
    if (aggregationRuleEntity == null) {
      throw new NotFoundException(String.format(AGG_RULE_NOT_FOUND_MSG, accountIdentifier, aggregationRuleIdentifier));
    }
    AggregationProcessor aggregationProcessor = aggregationRulesProcessorFactory.createProcessor(aggregationRuleEntity);
    List<AggregationRulesDTO> aggregationRulesDTOS = aggregationProcessor.rename(oldName);
    aggregationProcessor.save(aggregationRulesDTOS);
    aggregationRulesHelper.updateEntity(aggregationRuleEntity, AggregationRuleEntity.ComputedStatus.SUCCESS, null);
  }

  @Override
  public void deleteRuleFieldsFromHierarchicalEntities(
      String accountIdentifier, AggregationRuleDetails aggregationRuleDetails) {
    AggregationRuleEntity aggregationRuleEntity =
        AggregationRulesMapper.fromDTO(accountIdentifier, aggregationRuleDetails);
    AggregationProcessor aggregationProcessor = aggregationRulesProcessorFactory.createProcessor(aggregationRuleEntity);
    List<AggregationRulesDTO> aggregationRulesDTOS = aggregationProcessor.cleanup();
    aggregationProcessor.save(aggregationRulesDTOS);
  }

  @Override
  public void triggerAggregationRulesForScorecard(String accountIdentifier, String scorecardIdentifier) {
    log.info("Starting aggregation rules computation for scorecard: {} in account: {}", scorecardIdentifier,
        accountIdentifier);
    int pageNumber = 0;
    int pageSize = 100;
    int triggeredCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    Page<AggregationRuleEntity> scorecardRulesPage;

    try {
      do {
        scorecardRulesPage = aggregationRuleRepository.findByAccountIdentifierAndAggregationTypeAndFieldForAgg(
            accountIdentifier, AggregationRuleEntity.AggregationType.SCORECARD, scorecardIdentifier,
            PageRequest.of(pageNumber, pageSize));

        for (AggregationRuleEntity rule : scorecardRulesPage.getContent()) {
          try {
            if (rule.getStatus() == AggregationRuleEntity.ComputedStatus.CALCULATING) {
              log.info("Skipping aggregation rule: {} for scorecard: {} in account: {} as computation is already in "
                      + "progress",
                  rule.getIdentifier(), scorecardIdentifier, accountIdentifier);
              skippedCount++;
              continue;
            }
            log.debug("Triggering computation for aggregation rule: {} for scorecard: {} in account: {}",
                rule.getIdentifier(), scorecardIdentifier, accountIdentifier);
            transactionHelper.performTransaction(() -> {
              rule.setStatus(AggregationRuleEntity.ComputedStatus.CALCULATING);
              aggregationRuleRepository.save(rule);
              outboxService.save(
                  new AggregationRuleComputeInitiatedEvent(accountIdentifier, rule.getIdentifier(), rule.getName()));
              return true;
            });
            triggeredCount++;
          } catch (Exception e) {
            failedCount++;
            log.error("Failed to trigger computation for aggregation rule: {} for scorecard: {} in account: {}",
                rule.getIdentifier(), scorecardIdentifier, accountIdentifier, e);
          }
        }
        pageNumber++;
      } while (scorecardRulesPage.hasNext());

      log.info("Completed aggregation rules computation trigger for scorecard: {} in account: {}. Triggered: {}, "
              + "Skipped: {}, Failed: {}",
          scorecardIdentifier, accountIdentifier, triggeredCount, skippedCount, failedCount);
    } catch (Exception e) {
      log.error("Error while triggering aggregation rules computation for scorecard: {} in account: {}",
          scorecardIdentifier, accountIdentifier, e);
    }
  }
}