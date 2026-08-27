/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.service.ServiceEntityMongoOperationsHelper.getServiceEqualityCriteria;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.helper.MongoConstants.TERTIARY_COLLATION_CONSTANT;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.app.beans.entities.ServiceEntity.ServiceEntityKeys;
import io.harness.beans.IdentifierRef;
import io.harness.cd.mappers.UnifiedServiceEntityMapper;
import io.harness.ci.service.ServiceEntityMongoOperationsHelper;
import io.harness.ci.service.ServiceEntityRbacHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.repositories.UnifiedServiceRepository;
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
import javax.validation.Valid;
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
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CI)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class ServiceEntityServiceImpl implements ServiceEntityService {
  private final Validator validator;
  private final UnifiedServiceRepository serviceRepository;
  @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final GitXSettingsHelper gitXSettingsHelper;
  private final ServiceEntityRbacHelper serviceEntityRbacHelper;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Override
  public ServiceEntity create(@Valid ServiceEntity requestService) {
    try {
      validateRequestEntity(requestService);
      setNonRequiredFields(requestService);
      validateIdentifierIsUnique(requestService);

      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> serviceRepository.saveGitAware(requestService)));
    } catch (WingsException ex) {
      log.error(String.format("Error while saving service: [%s]", requestService.getIdentifier()), ex);
      throw ex;
    } catch (Exception ex) {
      log.error(String.format("Unexpected error while saving service: [%s]", requestService.getIdentifier()), ex);
      throw new UnexpectedException(
          String.format("Error while saving service [%s]: %s", requestService.getIdentifier(), ex.getMessage()));
    }
  }

  private void validateIdentifierIsUnique(ServiceEntity requestService) {
    Optional<ServiceEntity> serviceEntityOptional =
        serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            requestService.getAccountId(), requestService.getOrgIdentifier(), requestService.getProjectIdentifier(),
            requestService.getIdentifier());

    if (serviceEntityOptional.isPresent()) {
      throw new DuplicateEntityException(String.format(
          "Service with identifier: [%s], already exists in project [%s], org [%s]", requestService.getIdentifier(),
          requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
    }
  }

  @Override
  public Optional<ServiceEntity> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, false, false);
  }

  @Override
  public Optional<ServiceEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean loadFromFallbackBranch) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, false, loadFromFallbackBranch);
  }

  @Override
  public Optional<ServiceEntity> getMetadata(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, true, false);
  }

  private Optional<ServiceEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean getMetadataOnly, boolean loadFromFallbackBranch) {
    String[] serviceRefSplit = StringUtils.split(serviceIdentifier, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    // converted to service identifier
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(accountIdentifier,
          orgIdentifier, projectIdentifier, serviceIdentifier, getMetadataOnly, loadFromFallbackBranch);
    } else {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
      return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
          identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
          identifierRef.getIdentifier(), getMetadataOnly, loadFromFallbackBranch);
    }
  }

  @Override
  public ServiceEntity update(ServiceEntity requestService) {
    validateRequestEntity(requestService);
    setNonRequiredFields(requestService);
    Criteria criteria = getServiceEqualityCriteria(requestService.getAccountId(), requestService.getOrgIdentifier(),
        requestService.getProjectIdentifier(), requestService.getIdentifier());

    Optional<ServiceEntity> optionalServiceEntity = getMetadata(requestService.getAccountId(),
        requestService.getOrgIdentifier(), requestService.getProjectIdentifier(), requestService.getIdentifier());
    if (optionalServiceEntity.isPresent()) {
      ServiceEntity oldService = optionalServiceEntity.get();
      ServiceEntity serviceToUpdate = oldService.withYaml(requestService.getYaml())
                                          .withDescription(requestService.getDescription())
                                          .withName(requestService.getName());

      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity updatedResult = serviceRepository.update(criteria, serviceToUpdate);
        if (updatedResult == null) {
          throw new InvalidRequestException(String.format(
              "Service [%s] under Project[%s], Organization [%s] couldn't be updated.", serviceToUpdate.getIdentifier(),
              serviceToUpdate.getProjectIdentifier(), serviceToUpdate.getOrgIdentifier()));
        }
        return updatedResult;
      }));
    } else {
      throw new InvalidRequestException(String.format(
          "Service [%s] under Project[%s], Organization [%s] doesn't exist.", requestService.getIdentifier(),
          requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
    }
  }

  @Override
  public ServiceEntity upsert(ServiceEntity requestService) {
    validateRequestEntity(requestService);
    setNonRequiredFields(requestService);
    Criteria criteria = getServiceEqualityCriteria(requestService.getAccountId(), requestService.getOrgIdentifier(),
        requestService.getProjectIdentifier(), requestService.getIdentifier());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ServiceEntity upsertedResult = serviceRepository.upsert(criteria, requestService);
      if (upsertedResult == null) {
        throw new InvalidRequestException(String.format(
            "Service [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
            requestService.getIdentifier(), requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
      }
      return upsertedResult;
    }));
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    Criteria criteria = getServiceEqualityCriteria(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    Optional<ServiceEntity> serviceEntity = get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    if (serviceEntity.isEmpty()) {
      throw new NotFoundException(
          UnifiedServiceEntityMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
    return serviceRepository.delete(criteria);
  }

  @Override
  public Page<ServiceEntity> list(Criteria criteria, Pageable pageable) {
    return serviceRepository.findAll(criteria, pageable);
  }

  @Override
  public Page<ServiceEntity> list(Query query, Pageable pageable) {
    return serviceRepository.findAll(query, pageable);
  }

  @Override
  public Page<ServiceEntity> list(String accountId, String orgIdentifier, String projectIdentifier, String searchTerm,
      boolean includeChildrenScope, String permission, int page, int size) {
    List<ServiceEntity> allPermittedServices = getAllPermittedServiceEntities(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permission);

    Criteria permittedServiceListCriteria =
        ServiceEntityMongoOperationsHelper.getServiceListCriteria(accountId, orgIdentifier, projectIdentifier,
            searchTerm, includeChildrenScope, allPermittedServices.stream().map(ServiceEntity::getIdentifier).toList());
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
    return list(permittedServiceListCriteria, pageRequest);
  }

  private List<ServiceEntity> getAllPermittedServiceEntities(String accountId, String orgIdentifier,
      String projectIdentifier, String searchTerm, boolean includeChildrenScope, String permission) {
    Criteria serviceListCriteria = ServiceEntityMongoOperationsHelper.getServiceListCriteria(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, new ArrayList<>());
    Pageable pageable = Pageable.ofSize(5000); // keeping the default max supported value
    Query query = new Query(serviceListCriteria)
                      .with(pageable)
                      .collation(Collation.of(Locale.ENGLISH).strength(TERTIARY_COLLATION_CONSTANT));
    query.fields().include(ServiceEntityKeys.accountId, ServiceEntityKeys.orgIdentifier,
        ServiceEntityKeys.projectIdentifier, ServiceEntityKeys.identifier);

    Page<ServiceEntity> allServicesPage = list(query, pageable);
    List<ServiceEntity> allServices = allServicesPage.getContent();

    return isEmpty(allServices) ? new ArrayList<>()
                                : serviceEntityRbacHelper.getPermittedServices(allServices, permission);
  }

  // There are some fields that are optional in request, and in that case auto-generated by harness
  private void setNonRequiredFields(ServiceEntity requestService) {
    if (isBlank(requestService.getName())) {
      requestService.setName(requestService.getIdentifier());
    }
  }

  private void validateRequestEntity(ServiceEntity requestService) {
    Set<ConstraintViolation<ServiceEntity>> violations = validator.validate(requestService);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.SERVICE);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
  }
}
