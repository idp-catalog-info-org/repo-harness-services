/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import static io.harness.exception.WingsException.USER;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.app.beans.entities.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.beans.IdentifierRef;
import io.harness.cd.mappers.InfrastructureEntityMapper;
import io.harness.ci.environment.utils.InfrastructureMongoOperationsSpringHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.repositories.UnifiedInfrastructureRepository;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class InfrastructureEntityServiceImpl implements InfrastructureEntityService {
  private final Validator validator;
  private final UnifiedInfrastructureRepository infrastructureRepository;
  @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final GitXSettingsHelper gitXSettingsHelper;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Infrastructure [%s] under Environment [%s] Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Infrastructure [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT =
      "Infrastructure [%s] in Account [%s] already exists";

  @Override
  public InfrastructureEntity create(InfrastructureEntity infrastructure) {
    try {
      validateRequestEntity(infrastructure);
      setNonRequiredFields(infrastructure);
      Optional<InfrastructureEntity> infraEntityOptional =
          infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
              infrastructure.getAccountId(), infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier(),
              infrastructure.getEnvIdentifier(), infrastructure.getIdentifier());
      if (infraEntityOptional.isPresent()) {
        throw new DuplicateEntityException(String.format(
            "Infrastructure with identifier: [%s], already exists in environment [%s] in project [%s], org [%s]",
            infrastructure.getIdentifier(), infrastructure.getEnvIdentifier(), infrastructure.getProjectIdentifier(),
            infrastructure.getOrgIdentifier()));
      }

      if (GitAwareContextHelper.isRemoteEntity()) {
        applyGitXSettingsIfApplicable(
            infrastructure.getAccountId(), infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier());
      }

      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> infrastructureRepository.saveGitAware(infrastructure)));
    } catch (WingsException ex) {
      log.error(String.format("Error while saving infrastructure: [%s]", infrastructure.getIdentifier()), ex);
      throw ex;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateInfrastructureExistsErrorMessage(infrastructure.getAccountId(), infrastructure.getOrgIdentifier(),
              infrastructure.getProjectIdentifier(), infrastructure.getEnvIdentifier(), infrastructure.getIdentifier()),
          USER, ex);
    } catch (Exception ex) {
      log.error("Unexpected error when saving infrastructure: {}", infrastructure.getIdentifier(), ex);
      throw new UnexpectedException(
          String.format("Unexpected error when saving infrastructure: %s", infrastructure.getIdentifier()));
    }
  }

  String getDuplicateInfrastructureExistsErrorMessage(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String infraIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT, infraIdentifier, accountIdentifier);
    } else if (EmptyPredicate.isEmpty(projectIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ORG, infraIdentifier, orgIdentifier, accountIdentifier);
    }
    return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT, infraIdentifier, envIdentifier, projectIdentifier,
        orgIdentifier, accountIdentifier);
  }

  @Override
  public Optional<InfrastructureEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String identifier) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, identifier, false, false);
  }

  @Override
  public Optional<InfrastructureEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String identifier, boolean loadFromFallbackBranch) {
    return get(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, identifier, false, loadFromFallbackBranch);
  }

  @Override
  public Optional<InfrastructureEntity> getMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String identifier) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, identifier, true, false);
  }

  private Optional<InfrastructureEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String identifier, boolean getMetadataOnly, boolean loadFromFallbackBranch) {
    // handles both env ref and identifier
    String[] envRefSplit = StringUtils.split(envIdentifier, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      return infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, identifier, getMetadataOnly,
          loadFromFallbackBranch);
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
      return infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
          envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
          envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), identifier, getMetadataOnly,
          loadFromFallbackBranch);
    }
  }

  @Override
  public InfrastructureEntity update(InfrastructureEntity infrastructure) {
    validateRequestEntity(infrastructure);
    setNonRequiredFields(infrastructure);
    Criteria criteria = InfrastructureMongoOperationsSpringHelper.getInfrastructureEqualityCriteria(
        infrastructure.getAccountId(), infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier(),
        infrastructure.getEnvIdentifier(), infrastructure.getIdentifier());
    Optional<InfrastructureEntity> optionalInfrastructure =
        getMetadata(infrastructure.getAccountIdentifier(), infrastructure.getOrgIdentifier(),
            infrastructure.getProjectIdentifier(), infrastructure.getEnvIdentifier(), infrastructure.getIdentifier());

    if (optionalInfrastructure.isPresent()) {
      InfrastructureEntity oldInfrastructureEntity = optionalInfrastructure.get();
      InfrastructureEntity infraToUpdate = oldInfrastructureEntity.withName(infrastructure.getName())
                                               .withYaml(infrastructure.getYaml())
                                               .withDescription(infrastructure.getDescription())
                                               .withTags(infrastructure.getTags());

      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        InfrastructureEntity updatedResult = infrastructureRepository.update(criteria, infraToUpdate);
        if (updatedResult == null) {
          throw new InvalidRequestException(String.format(
              "Infrastructure [%s] under Project[%s], Organization [%s] , Environment [%s] couldn't be updated or doesn't exist.",
              infraToUpdate.getIdentifier(), infraToUpdate.getProjectIdentifier(), infraToUpdate.getOrgIdentifier(),
              infraToUpdate.getEnvIdentifier()));
        }
        return updatedResult;
      }));
    } else {
      throw new InvalidRequestException(
          String.format("Infrastructure [%s] under Env [%s], Project [%s], Organization [%s] doesn't exist.",
              infrastructure.getIdentifier(), infrastructure.getEnvIdentifier(), infrastructure.getProjectIdentifier(),
              infrastructure.getOrgIdentifier()));
    }
  }

  @Override
  public InfrastructureEntity upsert(InfrastructureEntity infrastructure) {
    validateRequestEntity(infrastructure);
    setNonRequiredFields(infrastructure);
    Criteria criteria = InfrastructureMongoOperationsSpringHelper.getInfrastructureEqualityCriteria(
        infrastructure.getAccountId(), infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier(),
        infrastructure.getEnvIdentifier(), infrastructure.getIdentifier());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      InfrastructureEntity upsertedResult = infrastructureRepository.upsert(criteria, infrastructure);
      if (upsertedResult == null) {
        throw new InvalidRequestException(String.format(
            "Environment [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
            infrastructure.getIdentifier(), infrastructure.getProjectIdentifier(), infrastructure.getOrgIdentifier()));
      }
      return upsertedResult;
    }));
  }

  @Override
  public Page<InfrastructureEntity> list(Criteria criteria, Pageable pageable) {
    return infrastructureRepository.findAll(criteria, pageable);
  }

  @Override
  public List<InfrastructureEntity> listByEnvRef(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String environmentRef, List<String> projections, Pageable pageRequest) {
    IdentifierRef envIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(environmentRef, accountIdentifier, orgIdentifier, projectIdentifier);
    Criteria criteria = Criteria.where(InfrastructureEntityKeys.accountId)
                            .is(envIdentifierRef.getAccountIdentifier())
                            .and(InfrastructureEntityKeys.orgIdentifier)
                            .is(envIdentifierRef.getOrgIdentifier())
                            .and(InfrastructureEntityKeys.projectIdentifier)
                            .is(envIdentifierRef.getProjectIdentifier())
                            .and(InfrastructureEntityKeys.envIdentifier)
                            .is(envIdentifierRef.getIdentifier());
    return infrastructureRepository.findAll(criteria, projections, pageRequest);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String identifier) {
    Criteria criteria = InfrastructureMongoOperationsSpringHelper.getInfrastructureEqualityCriteria(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, identifier);
    Optional<InfrastructureEntity> infrastructureEntity =
        get(accountId, orgIdentifier, projectIdentifier, envIdentifier, identifier);
    if (infrastructureEntity.isEmpty()) {
      throw new NotFoundException(InfrastructureEntityMapper.getInfraNotFoundError(
          orgIdentifier, projectIdentifier, envIdentifier, identifier));
    }
    return infrastructureRepository.delete(criteria);
  }

  private void validateRequestEntity(InfrastructureEntity infrastructure) {
    Set<ConstraintViolation<InfrastructureEntity>> violations = validator.validate(infrastructure);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void setNonRequiredFields(InfrastructureEntity infrastructure) {
    if (isBlank(infrastructure.getName())) {
      infrastructure.setName(infrastructure.getIdentifier());
    }
  }

  private void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.INFRASTRUCTURE);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
  }
}
