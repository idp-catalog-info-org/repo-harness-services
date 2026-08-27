/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.helper.MongoConstants.TERTIARY_COLLATION_CONSTANT;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentEntity.EnvironmentEntityKeys;
import io.harness.cd.mappers.EnvironmentEntityMapper;
import io.harness.ci.environment.utils.EnvironmentEntityMongoOperationsHelper;
import io.harness.ci.environment.utils.EnvironmentEntityRbacHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.UnifiedEnvironmentRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class EnvironmentEntityServiceImpl implements EnvironmentEntityService {
  private final Validator validator;
  private final UnifiedEnvironmentRepository environmentRepository;
  @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final EnvironmentEntityRbacHelper environmentEntityRbacHelper;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Override
  public EnvironmentEntity create(EnvironmentEntity environment) {
    validateRequestEntity(environment);
    setNonRequiredFields(environment);
    Optional<EnvironmentEntity> environmentEntityOptional =
        environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier(),
            environment.getIdentifier());
    if (environmentEntityOptional.isPresent()) {
      throw new DuplicateEntityException(
          String.format("Environment with identifier: [%s], already exists in project [%s], org [%s]",
              environment.getIdentifier(), environment.getProjectIdentifier(), environment.getOrgIdentifier()));
    }
    return Failsafe.with(transactionRetryPolicy)
        .get(() -> transactionTemplate.execute(status -> environmentRepository.save(environment)));
  }

  @Override
  public Optional<EnvironmentEntity> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);
  }

  @Override
  public EnvironmentEntity update(EnvironmentEntity environment) {
    validateRequestEntity(environment);
    setNonRequiredFields(environment);
    Criteria criteria =
        EnvironmentEntityMongoOperationsHelper.getEnvironmentEqualityCriteria(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier());
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      EnvironmentEntity updatedResult = environmentRepository.update(criteria, environment);
      if (updatedResult == null) {
        throw new InvalidRequestException(
            String.format("Environment [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                environment.getIdentifier(), environment.getProjectIdentifier(), environment.getOrgIdentifier()));
      }
      return updatedResult;
    }));
  }

  @Override
  public EnvironmentEntity upsert(EnvironmentEntity environment) {
    validateRequestEntity(environment);
    setNonRequiredFields(environment);
    Criteria criteria =
        EnvironmentEntityMongoOperationsHelper.getEnvironmentEqualityCriteria(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      EnvironmentEntity upsertedResult = environmentRepository.upsert(criteria, environment);
      if (upsertedResult == null) {
        throw new InvalidRequestException(
            String.format("Environment [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                environment.getIdentifier(), environment.getProjectIdentifier(), environment.getOrgIdentifier()));
      }
      return upsertedResult;
    }));
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    Criteria criteria = EnvironmentEntityMongoOperationsHelper.getEnvironmentEqualityCriteria(
        accountId, orgIdentifier, projectIdentifier, envIdentifier);
    Optional<EnvironmentEntity> environmentEntity = get(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    if (environmentEntity.isEmpty()) {
      throw new NotFoundException(
          EnvironmentEntityMapper.getEnvironmentNotFoundError(orgIdentifier, projectIdentifier, envIdentifier));
    }
    return environmentRepository.delete(criteria);
  }

  @Override
  public Page<EnvironmentEntity> list(Criteria criteria, Pageable pageable) {
    return environmentRepository.findAll(criteria, pageable);
  }

  @Override
  public Page<EnvironmentEntity> list(Query query, Pageable pageable) {
    return environmentRepository.findAll(query, pageable);
  }

  @Override
  public Page<EnvironmentEntity> list(int page, int size, String accountId, String orgIdentifier,
      String projectIdentifier, String searchTerm, boolean includeChildrenScope, String permissionToCheck) {
    List<EnvironmentEntity> allPermittedEnvironments = getAllPermittedEnvironmentEntities(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permissionToCheck);

    Criteria permittedEnvListCriteria = EnvironmentEntityMongoOperationsHelper.getEnvironmentListCriteria(accountId,
        orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope,
        allPermittedEnvironments.stream().map(EnvironmentEntity::getIdentifier).toList());
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentEntityKeys.createdAt));
    return list(permittedEnvListCriteria, pageRequest);
  }

  private List<EnvironmentEntity> getAllPermittedEnvironmentEntities(String accountId, String orgIdentifier,
      String projectIdentifier, String searchTerm, boolean includeChildrenScope, String permission) {
    Criteria envListCriteria = EnvironmentEntityMongoOperationsHelper.getEnvironmentListCriteria(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, new ArrayList<>());
    Pageable pageable = Pageable.ofSize(5000); // keeping the default max supported value
    Query query = new Query(envListCriteria)
                      .with(pageable)
                      .collation(Collation.of(Locale.ENGLISH).strength(TERTIARY_COLLATION_CONSTANT));
    query.fields().include(EnvironmentEntityKeys.accountId, EnvironmentEntityKeys.orgIdentifier,
        EnvironmentEntityKeys.projectIdentifier, EnvironmentEntityKeys.identifier);

    Page<EnvironmentEntity> allEnvPage = list(query, pageable);
    List<EnvironmentEntity> allEnvList = allEnvPage.getContent();

    return isEmpty(allEnvList) ? new ArrayList<>()
                               : environmentEntityRbacHelper.getPermittedEnvironments(allEnvList, permission);
  }

  private void validateRequestEntity(EnvironmentEntity requestEnvironment) {
    Set<ConstraintViolation<EnvironmentEntity>> violations = validator.validate(requestEnvironment);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void setNonRequiredFields(EnvironmentEntity requestEnvironment) {
    if (isBlank(requestEnvironment.getName())) {
      requestEnvironment.setName(requestEnvironment.getIdentifier());
    }
  }
}
