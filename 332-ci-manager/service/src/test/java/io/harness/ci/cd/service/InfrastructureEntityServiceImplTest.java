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
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.repositories.UnifiedInfrastructureRepository;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class InfrastructureEntityServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_ID = "envId";
  private static final String INFRA_ID = "infra1";

  @Mock private Validator validator;
  @Mock private UnifiedInfrastructureRepository infrastructureRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private GitXSettingsHelper gitXSettingsHelper;

  private InfrastructureEntityServiceImpl service;
  private InfrastructureEntity testEntity;

  @Before
  public void setUp() {
    service = new InfrastructureEntityServiceImpl(
        validator, infrastructureRepository, transactionTemplate, gitXSettingsHelper);

    testEntity = InfrastructureEntity.builder()
                     .accountId(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJECT_ID)
                     .envIdentifier(ENV_ID)
                     .identifier(INFRA_ID)
                     .name("dev-infra")
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
  public void testCreate_whenInfraDoesNotExist_shouldSaveAndReturn() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(infrastructureRepository.saveGitAware(testEntity)).thenReturn(testEntity);

    InfrastructureEntity result = service.create(testEntity);

    assertThat(result).as("created entity should be returned").isEqualTo(testEntity);
    verify(infrastructureRepository).saveGitAware(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenInfraAlreadyExists_shouldThrowDuplicateEntityException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.of(testEntity));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("duplicate create should throw DuplicateEntityException")
        .isInstanceOf(DuplicateEntityException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenNameIsBlank_shouldSetNameToIdentifier() {
    InfrastructureEntity noNameEntity = InfrastructureEntity.builder()
                                            .accountId(ACCOUNT_ID)
                                            .orgIdentifier(ORG_ID)
                                            .projectIdentifier(PROJECT_ID)
                                            .envIdentifier(ENV_ID)
                                            .identifier(INFRA_ID)
                                            .build();

    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(infrastructureRepository.saveGitAware(any())).thenAnswer(invocation -> invocation.getArgument(0));

    InfrastructureEntity result = service.create(noNameEntity);

    assertThat(result.getName()).as("blank name should default to identifier").isEqualTo(INFRA_ID);
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
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenDuplicateKeyException_shouldThrowDuplicateFieldException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(infrastructureRepository.saveGitAware(any())).thenThrow(new DuplicateKeyException("dup key"));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("DuplicateKeyException should be wrapped as DuplicateFieldException")
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate_whenUnexpectedException_shouldThrowUnexpectedException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(infrastructureRepository.saveGitAware(any())).thenThrow(new RuntimeException("something broke"));

    assertThatThrownBy(() -> service.create(testEntity))
        .as("generic exception should be wrapped as UnexpectedException")
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenSimpleEnvIdentifier_shouldDelegateToRepository() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<InfrastructureEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);

    assertThat(result).as("should return entity from repository").isPresent();
    assertThat(result.get().getIdentifier()).as("identifier should match").isEqualTo(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenNotFound_shouldReturnEmpty() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq("nonexistent"), eq(false), eq(false)))
        .thenReturn(Optional.empty());

    Optional<InfrastructureEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, "nonexistent");

    assertThat(result).as("should return empty for nonexistent entity").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet_whenDottedEnvRef_shouldResolveViaIdentifierRefHelper() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<InfrastructureEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "org." + ENV_ID, INFRA_ID);

    assertThat(result).as("dotted env ref should resolve via IdentifierRefHelper").isPresent();
    verify(infrastructureRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWithFallbackBranch_shouldPassFallbackFlag() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(true)))
        .thenReturn(Optional.of(testEntity));

    Optional<InfrastructureEntity> result = service.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID, true);

    assertThat(result).as("fallback branch get should return entity").isPresent();
    verify(infrastructureRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(true));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMetadata_shouldDelegateWithMetadataFlag() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(testEntity));

    Optional<InfrastructureEntity> result = service.getMetadata(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);

    assertThat(result).as("getMetadata should pass metadataOnly=true").isPresent();
    verify(infrastructureRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(true), eq(false));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenEntityExists_shouldMergeFieldsAndReturnUpdatedEntity() {
    InfrastructureEntity existingEntity = InfrastructureEntity.builder()
                                              .accountId(ACCOUNT_ID)
                                              .orgIdentifier(ORG_ID)
                                              .projectIdentifier(PROJECT_ID)
                                              .envIdentifier(ENV_ID)
                                              .identifier(INFRA_ID)
                                              .name("old-name")
                                              .description("old-desc")
                                              .build();

    InfrastructureEntity updateRequest = InfrastructureEntity.builder()
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_ID)
                                             .projectIdentifier(PROJECT_ID)
                                             .envIdentifier(ENV_ID)
                                             .identifier(INFRA_ID)
                                             .name("new-name")
                                             .description("new-desc")
                                             .build();

    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(existingEntity));

    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    when(infrastructureRepository.update(any(Criteria.class), entityCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    InfrastructureEntity result = service.update(updateRequest);

    assertThat(result.getName()).as("name should be updated from request").isEqualTo("new-name");
    assertThat(result.getDescription()).as("description should be updated from request").isEqualTo("new-desc");
    InfrastructureEntity captured = entityCaptor.getValue();
    assertThat(captured.getIdentifier()).as("identifier should be preserved from existing entity").isEqualTo(INFRA_ID);
    assertThat(captured.getName()).as("captured entity should have new name").isEqualTo("new-name");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenEntityDoesNotExist_shouldThrowInvalidRequestException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(true), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(testEntity))
        .as("update of nonexistent entity should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(true), eq(false)))
        .thenReturn(Optional.of(testEntity));
    when(infrastructureRepository.update(any(Criteria.class), any(InfrastructureEntity.class))).thenReturn(null);

    assertThatThrownBy(() -> service.update(testEntity))
        .as("null update result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenSuccessful_shouldReturnUpsertedEntity() {
    when(infrastructureRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(testEntity);

    InfrastructureEntity result = service.upsert(testEntity);

    assertThat(result).as("upserted entity should be returned").isEqualTo(testEntity);
    verify(infrastructureRepository).upsert(any(Criteria.class), eq(testEntity));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpsert_whenRepositoryReturnsNull_shouldThrowInvalidRequestException() {
    when(infrastructureRepository.upsert(any(Criteria.class), eq(testEntity))).thenReturn(null);

    assertThatThrownBy(() -> service.upsert(testEntity))
        .as("null upsert result should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testList_shouldDelegateToRepository() {
    Criteria criteria = new Criteria();
    Pageable pageable = Pageable.ofSize(10);
    Page<InfrastructureEntity> expectedPage = new PageImpl<>(List.of(testEntity));
    when(infrastructureRepository.findAll(eq(criteria), eq(pageable))).thenReturn(expectedPage);

    Page<InfrastructureEntity> result = service.list(criteria, pageable);

    assertThat(result.getContent()).as("should return entities from repository").hasSize(1);
    assertThat(result.getContent().get(0)).as("entity should match").isEqualTo(testEntity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListByEnvRef_shouldBuildCriteriaFromEnvRef() {
    Pageable pageable = Pageable.ofSize(10);
    List<String> projections = List.of("identifier", "name");
    when(infrastructureRepository.findAll(any(Criteria.class), eq(projections), eq(pageable)))
        .thenReturn(List.of(testEntity));

    List<InfrastructureEntity> result =
        service.listByEnvRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, projections, pageable);

    assertThat(result).as("should return entities matching env ref").hasSize(1);
    verify(infrastructureRepository).findAll(any(Criteria.class), eq(projections), eq(pageable));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityExists_shouldDeleteAndReturnTrue() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(false)))
        .thenReturn(Optional.of(testEntity));
    when(infrastructureRepository.delete(any(Criteria.class))).thenReturn(true);

    boolean result = service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);

    assertThat(result).as("delete should return true on success").isTrue();
    verify(infrastructureRepository).delete(any(Criteria.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete_whenEntityDoesNotExist_shouldThrowNotFoundException() {
    when(infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq(INFRA_ID), eq(false), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .as("delete of nonexistent entity should throw NotFoundException")
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDuplicateErrorMessage_whenOrgIsEmpty_shouldReturnAccountScopeMessage() {
    String message = service.getDuplicateInfrastructureExistsErrorMessage(ACCOUNT_ID, "", PROJECT_ID, ENV_ID, INFRA_ID);

    assertThat(message)
        .as("account-scope message should mention infra and account")
        .contains(INFRA_ID)
        .contains(ACCOUNT_ID);
    assertThat(message).as("account-scope message should not mention org").doesNotContain(ORG_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDuplicateErrorMessage_whenProjectIsEmpty_shouldReturnOrgScopeMessage() {
    String message = service.getDuplicateInfrastructureExistsErrorMessage(ACCOUNT_ID, ORG_ID, "", ENV_ID, INFRA_ID);

    assertThat(message)
        .as("org-scope message should mention infra, org, and account")
        .contains(INFRA_ID)
        .contains(ORG_ID)
        .contains(ACCOUNT_ID);
    assertThat(message).as("org-scope message should not mention project").doesNotContain(PROJECT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDuplicateErrorMessage_whenAllScopesPresent_shouldReturnProjectScopeMessage() {
    String message =
        service.getDuplicateInfrastructureExistsErrorMessage(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);

    assertThat(message)
        .as("project-scope message should mention all identifiers")
        .contains(INFRA_ID)
        .contains(ENV_ID)
        .contains(PROJECT_ID)
        .contains(ORG_ID)
        .contains(ACCOUNT_ID);
  }
}
