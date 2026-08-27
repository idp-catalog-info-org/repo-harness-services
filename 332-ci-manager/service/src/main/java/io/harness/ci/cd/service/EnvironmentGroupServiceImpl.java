/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.helper.MongoConstants.TERTIARY_COLLATION_CONSTANT;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cd.mappers.EnvironmentGroupEntityMapper;
import io.harness.ci.environment.utils.EnvironmentGroupEntityRbacHelper;
import io.harness.ci.environment.utils.EnvironmentGroupMongoOperationsHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.UnifiedEnvironmentGroupRepository;
import io.harness.utils.IdentifierRefHelper;

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
import org.apache.commons.lang3.StringUtils;
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
@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class EnvironmentGroupServiceImpl implements EnvironmentGroupService {
  private final Validator validator;
  private final UnifiedEnvironmentGroupRepository environmentGroupRepository;
  @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final EnvironmentGroupEntityRbacHelper envGroupEntityRbacHelper;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final int MAX_LIMIT = 1000;

  @Override
  public EnvironmentGroupEntity create(EnvironmentGroupEntity requestedEntity) {
    validateRequestEntity(requestedEntity);
    setNonRequiredFields(requestedEntity);
    Optional<EnvironmentGroupEntity> envGroupOptional =
        environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
            requestedEntity.getIdentifier());
    if (envGroupOptional.isPresent()) {
      throw new DuplicateEntityException(String.format(
          "Environment Group with identifier: [%s], already exists in project [%s], org [%s]",
          requestedEntity.getIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier()));
    }
    return Failsafe.with(transactionRetryPolicy)
        .get(() -> transactionTemplate.execute(status -> environmentGroupRepository.save(requestedEntity)));
  }

  @Override
  public Optional<EnvironmentGroupEntity> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String environmentGroupIdentifier) {
    return getByRef(accountIdentifier, orgIdentifier, projectIdentifier, environmentGroupIdentifier);
  }

  @Override
  public EnvironmentGroupEntity update(EnvironmentGroupEntity requestedEntity) {
    validateRequestEntity(requestedEntity);
    setNonRequiredFields(requestedEntity);
    Criteria criteria = EnvironmentGroupMongoOperationsHelper.getEnvironmentGroupEqualityCriteria(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      EnvironmentGroupEntity updatedResult = environmentGroupRepository.update(criteria, requestedEntity);
      if (updatedResult == null) {
        throw new InvalidRequestException(String.format(
            "Environment Group [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
            requestedEntity.getIdentifier(), requestedEntity.getProjectIdentifier(),
            requestedEntity.getOrgIdentifier()));
      }
      return updatedResult;
    }));
  }

  @Override
  public EnvironmentGroupEntity upsert(EnvironmentGroupEntity requestedEntity) {
    validateRequestEntity(requestedEntity);
    setNonRequiredFields(requestedEntity);
    Criteria criteria = EnvironmentGroupMongoOperationsHelper.getEnvironmentGroupEqualityCriteria(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      EnvironmentGroupEntity upsertedResult = environmentGroupRepository.upsert(criteria, requestedEntity);
      if (upsertedResult == null) {
        throw new InvalidRequestException(String.format(
            "Environment Group [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
            requestedEntity.getIdentifier(), requestedEntity.getProjectIdentifier(),
            requestedEntity.getOrgIdentifier()));
      }
      return upsertedResult;
    }));
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentGroupIdentifier) {
    Criteria criteria = EnvironmentGroupMongoOperationsHelper.getEnvironmentGroupEqualityCriteria(
        accountId, orgIdentifier, projectIdentifier, environmentGroupIdentifier);
    Optional<EnvironmentGroupEntity> environmentGroupEntity =
        get(accountId, orgIdentifier, projectIdentifier, environmentGroupIdentifier);
    if (environmentGroupEntity.isEmpty()) {
      throw new NotFoundException(EnvironmentGroupEntityMapper.getEnvironmentGroupNotFoundError(
          orgIdentifier, projectIdentifier, environmentGroupIdentifier));
    }
    return environmentGroupRepository.delete(criteria);
  }

  @Override
  public Page<EnvironmentGroupEntity> list(Criteria criteria, Pageable pageable) {
    return environmentGroupRepository.findAll(criteria, pageable);
  }

  @Override
  public Page<EnvironmentGroupEntity> list(Query query, Pageable pageable) {
    return environmentGroupRepository.findAll(query, pageable);
  }

  @Override
  public Page<EnvironmentGroupEntity> list(String accountId, String orgIdentifier, String projectIdentifier,
      String searchTerm, boolean includeChildrenScope, String permission, int page, int size) {
    List<EnvironmentGroupEntity> allPermittedEnvGroups = getAllPermittedEnvGroupEntities(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permission);

    Criteria permittedEnvGroupListCriteria = EnvironmentGroupMongoOperationsHelper.getEnvironmentGroupListCriteria(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope,
        allPermittedEnvGroups.stream().map(EnvironmentGroupEntity::getIdentifier).toList());
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.createdAt));
    return list(permittedEnvGroupListCriteria, pageRequest);
  }

  private void validateRequestEntity(EnvironmentGroupEntity requestedEntity) {
    Set<ConstraintViolation<EnvironmentGroupEntity>> violations = validator.validate(requestedEntity);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void setNonRequiredFields(EnvironmentGroupEntity requestedEntity) {
    if (isBlank(requestedEntity.getName())) {
      requestedEntity.setName(requestedEntity.getIdentifier());
    }
  }

  private Optional<EnvironmentGroupEntity> getByRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envGroupRef) {
    // handles both env ref and identifier
    String[] envRefSplit = StringUtils.split(envGroupRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      return environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, envGroupRef);
    } else {
      IdentifierRef envGroupIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envGroupRef, accountIdentifier, orgIdentifier, projectIdentifier);
      return environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
          envGroupIdentifierRef.getAccountIdentifier(), envGroupIdentifierRef.getOrgIdentifier(),
          envGroupIdentifierRef.getProjectIdentifier(), envGroupIdentifierRef.getIdentifier());
    }
  }

  private List<EnvironmentGroupEntity> getAllPermittedEnvGroupEntities(String accountId, String orgIdentifier,
      String projectIdentifier, String searchTerm, boolean includeChildrenScope, String permission) {
    Criteria envGroupListCriteria = EnvironmentGroupMongoOperationsHelper.getEnvironmentGroupListCriteria(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, new ArrayList<>());
    Pageable pageable = Pageable.ofSize(MAX_LIMIT); // keeping the default max supported value
    Query query = new Query(envGroupListCriteria)
                      .with(pageable)
                      .collation(Collation.of(Locale.ENGLISH).strength(TERTIARY_COLLATION_CONSTANT));
    query.fields().include(EnvironmentGroupEntity.EnvironmentGroupKeys.accountId,
        EnvironmentGroupEntity.EnvironmentGroupKeys.orgIdentifier,
        EnvironmentGroupEntity.EnvironmentGroupKeys.projectIdentifier,
        EnvironmentGroupEntity.EnvironmentGroupKeys.identifier);

    Page<EnvironmentGroupEntity> allEnvGroupsPage = list(query, pageable);
    List<EnvironmentGroupEntity> allEnvGroups = allEnvGroupsPage.getContent();

    return isEmpty(allEnvGroups) ? new ArrayList<>()
                                 : envGroupEntityRbacHelper.getPermittedEnvironmentGroups(allEnvGroups, permission);
  }
}
