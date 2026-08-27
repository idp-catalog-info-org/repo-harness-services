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
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.environment.utils.EnvironmentGroupEntityRbacHelper;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.UnifiedEnvironmentGroupRepository;
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

public class EnvironmentGroupServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_GROUP_ID = "envGroup1";

  @Mock private Validator validator;
  @Mock private UnifiedEnvironmentGroupRepository environmentGroupRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private EnvironmentGroupEntityRbacHelper envGroupEntityRbacHelper;

  private EnvironmentGroupServiceImpl service;
  private EnvironmentGroupEntity testEntity;

  @Before
  public void setUp() {
    service = new EnvironmentGroupServiceImpl(
        validator, environmentGroupRepository, transactionTemplate, envGroupEntityRbacHelper);

    testEntity = EnvironmentGroupEntity.builder()
                     .accountId(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJECT_ID)
                     .identifier(ENV_GROUP_ID)
                     .name("dev-env-group")
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
  public void testCreate_whenEnvGroupDoesNotExist_shouldSaveAndReturn() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.empty());
    when(environmentGroupRepository.save(testEntity)).thenReturn(testEntity);

    EnvironmentGroupEntity result = service.create(testEntity);

    assertThat(result).as("created entity should be returned").isEqualTo(testEntity);
    verify(environmentGroupRepository).save(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenEnvGroupAlreadyExists_shouldThrowDuplicateEntityException() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.of(testEntity));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("duplicate create should throw DuplicateEntityException")
        .isInstanceOf(DuplicateEntityException.class)
        .hasMessageContaining(ENV_GROUP_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenNameIsBlank_shouldSetNameToIdentifier() {
    EnvironmentGroupEntity noNameEntity = EnvironmentGroupEntity.builder()
                                              .accountId(ACCOUNT_ID)
                                              .orgIdentifier(ORG_ID)
                                              .projectIdentifier(PROJECT_ID)
                                              .identifier(ENV_GROUP_ID)
                                              .build();

    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.empty());
    when(environmentGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    EnvironmentGroupEntity result = service.create(noNameEntity);

    assertThat(result.getName()).as("blank name should default to identifier").isEqualTo(ENV_GROUP_ID);
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
  public void testGet_whenSimpleIdentifier_shouldDelegateToRepository() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.of(testEntity));

    Optional<EnvironmentGroupEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID);

    assertThat(result).as("should return entity from repository").isPresent();
    assertThat(result.get().getIdentifier()).as("identifier should match").isEqualTo(ENV_GROUP_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenNotFound_shouldReturnEmpty() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "nonexistent"))
        .thenReturn(Optional.empty());

    Optional<EnvironmentGroupEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "nonexistent");

    assertThat(result).as("should return empty for nonexistent entity").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenDottedRef_shouldResolveViaIdentifierRefHelper() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_GROUP_ID)))
        .thenReturn(Optional.of(testEntity));

    Optional<EnvironmentGroupEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "org." + ENV_GROUP_ID);

    assertThat(result).as("dotted ref should resolve via IdentifierRefHelper").isPresent();
    verify(environmentGroupRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_GROUP_ID));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenSuccessful_shouldReturnUpdatedEntity() {
    when(environmentGroupRepository.update(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    EnvironmentGroupEntity result = service.update(testEntity);

    assertThat(result).as("updated entity should be returned").isEqualTo(testEntity);
    verify(environmentGroupRepository).update(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(environmentGroupRepository.update(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.update(testEntity))
        .as("null update result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(ENV_GROUP_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenSuccessful_shouldReturnUpsertedEntity() {
    when(environmentGroupRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    EnvironmentGroupEntity result = service.upsert(testEntity);

    assertThat(result).as("upserted entity should be returned").isEqualTo(testEntity);
    verify(environmentGroupRepository).upsert(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(environmentGroupRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.upsert(testEntity))
        .as("null upsert result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(ENV_GROUP_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityExists_shouldDeleteAndReturnTrue() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.of(testEntity));
    when(environmentGroupRepository.delete(any(Criteria.class))).thenReturn(true);

    boolean result = service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID);

    assertThat(result).as("delete should return true on success").isTrue();
    verify(environmentGroupRepository).delete(any(Criteria.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityDoesNotExist_shouldThrowNotFoundException() {
    when(environmentGroupRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID))
        .as("delete of nonexistent entity should throw NotFoundException")
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithCriteria_shouldDelegateToRepository() {
    Criteria criteria = new Criteria();
    Pageable pageable = Pageable.ofSize(10);
    Page<EnvironmentGroupEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(environmentGroupRepository.findAll(eq(criteria), eq(pageable))).thenReturn(expectedPage);

    Page<EnvironmentGroupEntity> result = service.list(criteria, pageable);

    assertThat(result.getContent()).as("should return entities from repository").hasSize(1);
    assertThat(result.getContent().get(0)).as("entity should match").isEqualTo(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithQuery_shouldDelegateToRepository() {
    Query query = new Query();
    Pageable pageable = Pageable.ofSize(10);
    Page<EnvironmentGroupEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(environmentGroupRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(expectedPage);

    Page<EnvironmentGroupEntity> result = service.list(query, pageable);

    assertThat(result.getContent()).as("should return entities from query").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_shouldFilterByRbacAndReturnPage() {
    Page<EnvironmentGroupEntity> allEnvGroupPage = new PageImpl<>(List.of(testEntity));
    when(environmentGroupRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(allEnvGroupPage);
    when(envGroupEntityRbacHelper.getPermittedEnvironmentGroups(any(), eq("core_envgroup_access")))
        .thenReturn(List.of(testEntity));
    when(environmentGroupRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(allEnvGroupPage);

    Page<EnvironmentGroupEntity> result =
        service.list(ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_envgroup_access", 0, 10);

    assertThat(result).as("paginated result should not be null").isNotNull();
    verify(envGroupEntityRbacHelper).getPermittedEnvironmentGroups(any(), eq("core_envgroup_access"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListWithPagination_whenNoEnvGroups_shouldReturnEmptyPage() {
    Page<EnvironmentGroupEntity> emptyPage = new PageImpl<>(Collections.emptyList());
    when(environmentGroupRepository.findAll(any(Query.class), any(Pageable.class))).thenReturn(emptyPage);
    when(environmentGroupRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(emptyPage);

    Page<EnvironmentGroupEntity> result =
        service.list(ACCOUNT_ID, ORG_ID, PROJECT_ID, "", false, "core_envgroup_access", 0, 10);

    assertThat(result.getContent()).as("empty env group list should return empty page").isEmpty();
  }
}
