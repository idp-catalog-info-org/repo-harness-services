/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.service.ServiceEntityRbacHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.repositories.UnifiedServiceRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class ServiceEntityServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID = "svc1";

  @Mock private Validator validator;
  @Mock private UnifiedServiceRepository serviceRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private GitXSettingsHelper gitXSettingsHelper;
  @Mock private ServiceEntityRbacHelper serviceEntityRbacHelper;

  private ServiceEntityServiceImpl service;
  private ServiceEntity testEntity;

  @Before
  public void setUp() {
    service = new ServiceEntityServiceImpl(
        validator, serviceRepository, transactionTemplate, gitXSettingsHelper, serviceEntityRbacHelper);

    testEntity = ServiceEntity.builder()
                     .accountId(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJECT_ID)
                     .identifier(SERVICE_ID)
                     .name("dev-svc")
                     .build();

    when(validator.validate(any())).thenReturn(new HashSet<>());
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenServiceDoesNotExist_shouldSaveAndReturn() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.empty());
    when(serviceRepository.saveGitAware(testEntity)).thenReturn(testEntity);

    ServiceEntity result = service.create(testEntity);

    assertThat(result).as("created entity should be returned").isEqualTo(testEntity);
    verify(serviceRepository).saveGitAware(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenServiceAlreadyExists_shouldThrowDuplicateEntityException() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(testEntity));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("duplicate create should throw DuplicateEntityException")
        .isInstanceOf(DuplicateEntityException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenNameIsBlank_shouldSetNameToIdentifier() {
    ServiceEntity noNameEntity = ServiceEntity.builder()
                                     .accountId(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .identifier(SERVICE_ID)
                                     .build();

    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.empty());
    when(serviceRepository.saveGitAware(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ServiceEntity result = service.create(noNameEntity);

    assertThat(result.getName()).as("blank name should default to identifier").isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenValidationFails_shouldThrowUnexpectedException() {
    javax.validation.ConstraintViolation violation =
        org.mockito.Mockito.mock(javax.validation.ConstraintViolation.class);
    javax.validation.Path path = org.mockito.Mockito.mock(javax.validation.Path.class);
    when(path.toString()).thenReturn("identifier");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must not be empty");
    doReturn(Collections.singleton(violation)).when(validator).validate(any());

    assertThatThrownBy(() -> service.create(testEntity))
        .as("validation failure should be wrapped as UnexpectedException")
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenUnexpectedException_shouldWrapAndRethrow() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.empty());
    when(serviceRepository.saveGitAware(any())).thenThrow(new RuntimeException("connection failed"));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("generic exception should be wrapped as UnexpectedException")
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenSimpleIdentifier_shouldDelegateToRepository() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<ServiceEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(result).as("should return entity from repository").isPresent();
    assertThat(result.get().getIdentifier()).as("identifier should match").isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenNotFound_shouldReturnEmpty() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("nonexistent"), eq(false), eq(false)))
        .thenReturn(Optional.empty());

    Optional<ServiceEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "nonexistent");

    assertThat(result).as("should return empty for nonexistent entity").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenDottedRef_shouldResolveViaIdentifierRefHelper() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(SERVICE_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<ServiceEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "org." + SERVICE_ID);

    assertThat(result).as("dotted ref should resolve via IdentifierRefHelper").isPresent();
    verify(serviceRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(SERVICE_ID), eq(false), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWithFallbackBranch_shouldPassFallbackFlag() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(false), eq(true)))
        .thenReturn(Optional.of(testEntity));

    Optional<ServiceEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, true);

    assertThat(result).as("fallback branch get should return entity").isPresent();
    verify(serviceRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(false), eq(true));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMetadata_shouldDelegateWithMetadataFlag() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<ServiceEntity> result = service.getMetadata(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(result).as("getMetadata should pass metadataOnly=true").isPresent();
    verify(serviceRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(true), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenEntityExists_shouldMergeFieldsAndReturn() {
    ServiceEntity existingEntity = ServiceEntity.builder()
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_ID)
                                       .projectIdentifier(PROJECT_ID)
                                       .identifier(SERVICE_ID)
                                       .name("old-name")
                                       .description("old-desc")
                                       .yaml("old-yaml")
                                       .build();

    ServiceEntity updateRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .identifier(SERVICE_ID)
                                      .name("new-name")
                                      .description("new-desc")
                                      .yaml("new-yaml")
                                      .build();

    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(existingEntity));

    ArgumentCaptor<ServiceEntity> entityCaptor = ArgumentCaptor.forClass(ServiceEntity.class);
    when(serviceRepository.update(any(Criteria.class), entityCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    ServiceEntity result = service.update(updateRequest);

    assertThat(result.getName()).as("name should be updated from request").isEqualTo("new-name");
    assertThat(result.getDescription()).as("description should be updated from request").isEqualTo("new-desc");
    assertThat(result.getYaml()).as("yaml should be updated from request").isEqualTo("new-yaml");
    ServiceEntity captured = entityCaptor.getValue();
    assertThat(captured.getIdentifier())
        .as("identifier should be preserved from existing entity")
        .isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenEntityDoesNotExist_shouldThrowInvalidRequestException() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(true), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(testEntity))
        .as("update of nonexistent entity should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(testEntity));
    when(serviceRepository.update(any(Criteria.class), any(ServiceEntity.class))).thenReturn(null);

    assertThatThrownBy(() -> service.update(testEntity))
        .as("null update result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenSuccessful_shouldReturnUpsertedEntity() {
    when(serviceRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    ServiceEntity result = service.upsert(testEntity);

    assertThat(result).as("upserted entity should be returned").isEqualTo(testEntity);
    verify(serviceRepository).upsert(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(serviceRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.upsert(testEntity))
        .as("null upsert result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(SERVICE_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityExists_shouldDeleteAndReturnTrue() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));
    when(serviceRepository.delete(any(Criteria.class))).thenReturn(true);

    boolean result = service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(result).as("delete should return true on success").isTrue();
    verify(serviceRepository).delete(any(Criteria.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityDoesNotExist_shouldThrowNotFoundException() {
    when(serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), eq(false), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .as("delete of nonexistent entity should throw NotFoundException")
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithCriteria_shouldDelegateToRepository() {
    Criteria criteria = new Criteria();
    Pageable pageable = Pageable.ofSize(10);
    Page<ServiceEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(serviceRepository.findAll(eq(criteria), eq(pageable))).thenReturn(expectedPage);

    Page<ServiceEntity> result = service.list(criteria, pageable);

    assertThat(result.getContent()).as("should return entities from repository").hasSize(1);
    assertThat(result.getContent().get(0)).as("entity should match").isEqualTo(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithQuery_shouldDelegateToRepository() {
    Query query = new Query();
    Pageable pageable = Pageable.ofSize(10);
    Page<ServiceEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(serviceRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(expectedPage);

    Page<ServiceEntity> result = service.list(query, pageable);

    assertThat(result.getContent()).as("should return entities from query").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_shouldFilterByRbacAndReturnPage() {
    Page<ServiceEntity> allServicesPage = new PageImpl<>(List.of(testEntity));
    when(serviceRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(allServicesPage);
    when(serviceEntityRbacHelper.getPermittedServices(any(), eq("core_service_access")))
        .thenReturn(List.of(testEntity));
    when(serviceRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(allServicesPage);

    Page<ServiceEntity> result = service.list(ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_service_access", 0, 10);

    assertThat(result).as("paginated result should not be null").isNotNull();
    verify(serviceEntityRbacHelper).getPermittedServices(any(), eq("core_service_access"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_whenNoServices_shouldReturnEmptyPage() {
    Page<ServiceEntity> emptyPage = new PageImpl<>(Collections.emptyList());
    when(serviceRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(emptyPage);
    when(serviceRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(emptyPage);

    Page<ServiceEntity> result = service.list(ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_service_access", 0, 10);

    assertThat(result.getContent()).as("empty service list should return empty page").isEmpty();
  }
}
