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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.environment.utils.EnvironmentEntityRbacHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.UnifiedEnvironmentRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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

public class EnvironmentEntityServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_ID = "env1";

  @Mock private Validator validator;
  @Mock private UnifiedEnvironmentRepository environmentRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private EnvironmentEntityRbacHelper environmentEntityRbacHelper;

  private EnvironmentEntityServiceImpl service;
  private EnvironmentEntity testEntity;

  @Before
  public void setUp() {
    service = new EnvironmentEntityServiceImpl(
        validator, environmentRepository, transactionTemplate, environmentEntityRbacHelper);

    testEntity = EnvironmentEntity.builder()
                     .accountId(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJECT_ID)
                     .identifier(ENV_ID)
                     .name("dev-env")
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
  public void testCreate_whenEnvironmentDoesNotExist_shouldSaveAndReturn() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.empty());
    when(environmentRepository.save(testEntity)).thenReturn(testEntity);

    EnvironmentEntity result = service.create(testEntity);

    assertThat(result).as("created entity should be returned").isEqualTo(testEntity);
    verify(environmentRepository).save(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenEnvironmentAlreadyExists_shouldThrowDuplicateEntityException() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.of(testEntity));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("duplicate create should throw DuplicateEntityException")
        .isInstanceOf(DuplicateEntityException.class)
        .hasMessageContaining(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenNameIsBlank_shouldSetNameToIdentifier() {
    EnvironmentEntity noNameEntity = EnvironmentEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .identifier(ENV_ID)
                                         .build();

    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.empty());
    when(environmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    EnvironmentEntity result = service.create(noNameEntity);

    assertThat(result.getName()).as("blank name should default to identifier").isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenValidationFails_shouldThrowValidationException() {
    javax.validation.ConstraintViolation violation =
        org.mockito.Mockito.mock(javax.validation.ConstraintViolation.class);
    javax.validation.Path path = org.mockito.Mockito.mock(javax.validation.Path.class);
    when(path.toString()).thenReturn("identifier");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must not be empty");
    doReturn(Collections.singleton(violation)).when(validator).validate(any());

    assertThatThrownBy(() -> service.create(testEntity))
        .as("validation failure should throw ValidationException")
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("identifier");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_shouldDelegateToRepository() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.of(testEntity));

    Optional<EnvironmentEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);

    assertThat(result).as("should return entity from repository").isPresent();
    assertThat(result.get().getIdentifier()).as("identifier should match").isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenNotFound_shouldReturnEmpty() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "nonexistent"))
        .thenReturn(Optional.empty());

    Optional<EnvironmentEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "nonexistent");

    assertThat(result).as("should return empty for nonexistent entity").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenSuccessful_shouldReturnUpdatedEntity() {
    when(environmentRepository.update(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    EnvironmentEntity result = service.update(testEntity);

    assertThat(result).as("updated entity should be returned").isEqualTo(testEntity);
    verify(environmentRepository).update(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(environmentRepository.update(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.update(testEntity))
        .as("null update result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenSuccessful_shouldReturnUpsertedEntity() {
    when(environmentRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    EnvironmentEntity result = service.upsert(testEntity);

    assertThat(result).as("upserted entity should be returned").isEqualTo(testEntity);
    verify(environmentRepository).upsert(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(environmentRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.upsert(testEntity))
        .as("null upsert result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityExists_shouldDeleteAndReturnTrue() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.of(testEntity));
    when(environmentRepository.delete(any(Criteria.class))).thenReturn(true);

    boolean result = service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);

    assertThat(result).as("delete should return true on success").isTrue();
    verify(environmentRepository).delete(any(Criteria.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityDoesNotExist_shouldThrowNotFoundException() {
    when(environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .as("delete of nonexistent entity should throw NotFoundException")
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithCriteria_shouldDelegateToRepository() {
    Criteria criteria = new Criteria();
    Pageable pageable = Pageable.ofSize(10);
    Page<EnvironmentEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(environmentRepository.findAll(eq(criteria), eq(pageable))).thenReturn(expectedPage);

    Page<EnvironmentEntity> result = service.list(criteria, pageable);

    assertThat(result.getContent()).as("should return entities from repository").hasSize(1);
    assertThat(result.getContent().get(0)).as("entity should match").isEqualTo(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithQuery_shouldDelegateToRepository() {
    Query query = new Query();
    Pageable pageable = Pageable.ofSize(10);
    Page<EnvironmentEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(environmentRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(expectedPage);

    Page<EnvironmentEntity> result = service.list(query, pageable);

    assertThat(result.getContent()).as("should return entities from query").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_shouldFilterByRbacAndReturnPage() {
    Page<EnvironmentEntity> allEnvPage = new PageImpl<>(List.of(testEntity));
    when(environmentRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(allEnvPage);
    when(environmentEntityRbacHelper.getPermittedEnvironments(any(), eq("core_environment_access")))
        .thenReturn(List.of(testEntity));
    when(environmentRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(allEnvPage);

    Page<EnvironmentEntity> result =
        service.list(0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_environment_access");

    assertThat(result).as("paginated result should not be null").isNotNull();
    verify(environmentEntityRbacHelper).getPermittedEnvironments(any(), eq("core_environment_access"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_whenNoEnvironments_shouldReturnEmptyPage() {
    Page<EnvironmentEntity> emptyPage = new PageImpl<>(Collections.emptyList());
    when(environmentRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(emptyPage);
    when(environmentRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(emptyPage);

    Page<EnvironmentEntity> result =
        service.list(0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_environment_access");

    assertThat(result.getContent()).as("empty env list should return empty page").isEmpty();
  }
}
