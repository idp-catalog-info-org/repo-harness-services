/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.AKSHAT_GOYAL;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.secrets.SecretPermissions.SECRET_VIEW_PERMISSION;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.SecretScopeService;
import io.harness.ng.core.api.impl.SecretPermissionValidator;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SecretResourceFilterDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.serializer.JsonUtils;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPoolConfig;

import software.wings.service.impl.security.NGEncryptorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@Slf4j
@OwnedBy(PL)
@RunWith(MockitoJUnitRunner.class)
public class NGSecretResourceV2Test extends CategoryTest {
  private final ExecutorService executorService = new ScalingThreadPoolExecutor(
      ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(30).timeUnit(TimeUnit.SECONDS).build(),
      "batchSecretsExecutorService-%d");
  private final PageRequest pageRequest = PageRequest.builder().pageSize(10).pageIndex(1).build();

  @Mock private SecretCrudService ngSecretService;
  @Mock private SecretScopeService secretScopeService;
  @Mock private Validator validator;
  @Mock private NGEncryptedDataService encryptedDataService;
  @Mock private SecretPermissionValidator secretPermissionValidator;
  @Mock private NGEncryptorService ngEncryptorService;
  @Mock private ScopeInfoService scopeResolverService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private FeatureFlagService featureFlagService;
  private SecretApiUtils secretApiUtils;
  private NGSecretResourceV2 ngSecretResourceV2;

  @Before
  public void setup() {
    secretApiUtils = new SecretApiUtils(validator, featureFlagService);
    ngSecretResourceV2 = new NGSecretResourceV2(ngSecretService, secretScopeService, validator, encryptedDataService,
        secretPermissionValidator, ngEncryptorService, scopeResolverService, secretApiUtils, featureFlagService,
        executorService);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testIfListSecretsPostCallReturnsSuccessfully() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("Test")
                              .orgIdentifier("TestOrg")
                              .projectIdentifier("TestProj")
                              .uniqueId(randomAlphabetic(10))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page).when(ngSecretService).list(scopeInfo, null, null, false, null, null, false, pageRequest, null);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list = ngSecretResourceV2.listSecrets("Test", "TestOrg",
        "TestProj", SecretResourceFilterDTO.builder().identifiers(null).build(), pageRequest, null, scopeInfo);
    assertThat(list.getData().getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListSecretsAvailableAllScopeForTrue() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("Test")
                              .orgIdentifier("TestOrg")
                              .projectIdentifier("TestProj")
                              .uniqueId(randomAlphabetic(10))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page).when(ngSecretService).list(scopeInfo, null, null, false, null, null, true, pageRequest, null);
    ngSecretResourceV2.listSecrets("Test", "TestOrg", "TestProj",
        SecretResourceFilterDTO.builder().identifiers(null).includeAllSecretsAccessibleAtScope(true).build(),
        pageRequest, null, scopeInfo);
    verify(ngSecretService).list(scopeInfo, null, null, false, null, null, true, pageRequest, null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListSecretsAvailableAllScopeIfNotInFilterShouldBeFalse() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("Test")
                              .orgIdentifier("TestOrg")
                              .projectIdentifier("TestProj")
                              .uniqueId(randomAlphabetic(10))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page).when(ngSecretService).list(scopeInfo, null, null, false, null, null, false, pageRequest, null);
    ngSecretResourceV2.listSecrets("Test", "TestOrg", "TestProj",
        SecretResourceFilterDTO.builder().identifiers(null).build(), pageRequest, null, scopeInfo);
    verify(ngSecretService).list(scopeInfo, null, null, false, null, null, false, pageRequest, null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListV2ForSecretsAvailableAllScopeAsTrue() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("Test")
                              .orgIdentifier("TestOrg")
                              .projectIdentifier("TestProj")
                              .uniqueId(randomAlphabetic(10))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page).when(ngSecretService).list(scopeInfo, null, null, false, null, null, true, pageRequest, null);
    ngSecretResourceV2.list(
        "Test", "TestOrg", "TestProj", null, null, null, null, null, false, true, pageRequest, null, scopeInfo);
    verify(ngSecretService).list(scopeInfo, null, null, false, null, null, true, pageRequest, null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListV2ForSecretsAvailableAllScopeIfNotInQueryParamShouldBeFalse() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("Test")
                              .orgIdentifier("TestOrg")
                              .projectIdentifier("TestProj")
                              .uniqueId(randomAlphabetic(10))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page).when(ngSecretService).list(scopeInfo, null, null, false, null, null, false, pageRequest, null);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list = ngSecretResourceV2.list(
        "Test", "TestOrg", "TestProj", null, null, null, null, null, false, false, pageRequest, null, scopeInfo);
    verify(ngSecretService).list(scopeInfo, null, null, false, null, null, false, pageRequest, null);
  }

  @NotNull
  private List<SecretResponseWrapper> getMockResponse() {
    List<SecretResponseWrapper> mockResponse = new ArrayList<>();
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().identifier("Test").name("TestName").build())
            .build();
    mockResponse.add(secretResponseWrapper);
    return mockResponse;
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateRequestPayload() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .uniqueId(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    String spec =
        "{\"secret\":{\"type\":\"SecretFile\",\"identifier\":\"test_identifier\",\"description\":\"\",\"tags\":{},"
        + "\"spec\":{\"secretManagerIdentifier\":\"harnessSecretManager\"}}}";
    ConstraintViolation<Object> mockviolation = mock(ConstraintViolation.class);
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    violations.add(mockviolation);
    when(validator.validate(any())).thenReturn(violations);
    ngSecretResourceV2.createSecretFile(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, spec, scopeInfo);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public <T> void testJsonDeserialize_inSecretFileCreationFlow() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .uniqueId(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    String spec =
        "{\"secret\":{\"type\":\"SecretFile\",\"identifier\":\"test_identifier\",\"description\":\"\",\"tags\":{},"
        + "\"spec\":{\"secretManagerIdentifier\":\"harnessSecretManager\"}}}";
    try (MockedStatic<JsonUtils> aStatic = mockStatic(JsonUtils.class, CALLS_REAL_METHODS)) {
      try {
        ngSecretResourceV2.createSecretFile(
            accountIdentifier, orgIdentifier, projectIdentifier, false, null, spec, scopeInfo);
      } catch (Exception ignored) {
      }
      aStatic.verify(() -> {
        try {
          JsonUtils.asObjectWithExceptionHandlingType(spec, SecretRequestWrapper.class);
        } catch (JsonProcessingException e) {
          log.error("Unexpected error :", e);
        }
      }, times(1));
    }
  }

  @Test(expected = JsonMappingException.class)
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecretFile_withWrongSpec_shouldThrowException() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .uniqueId(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    String spec =
        "{\"secret\":{\"type\":\"SecretFile\",\"identifier\":\"test_identifier\",\"description\":\"\",\"tags\":,"
        + "\"spec\":{\"secretManagerIdentifier\":\"harnessSecretManager\"}}}";
    // passed tags with null in spec
    ngSecretResourceV2.createSecretFile(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, spec, scopeInfo);
  }

  private NGAccessWithEncryptionConsumer setupMocksForGetEncryptionDetails(NGAccess ngAccess, String secretId) {
    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        NGAccessWithEncryptionConsumer.builder()
            .ngAccess(ngAccess)
            .decryptableEntity(SecretVariableDTO.builder()
                                   .name(secretId)
                                   .secret(SecretRefData.builder().identifier(secretId).scope(Scope.PROJECT).build())
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .build())
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("unique-id").build();

    when(scopeResolverService.getScopeInfo(
             ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier()))
        .thenReturn(scopeInfo);
    when(ngSecretService.get(scopeInfo, secretId))
        .thenReturn(Optional.of(SecretResponseWrapper.builder().secret(SecretDTOV2.builder().build()).build()));
    doNothing()
        .when(secretPermissionValidator)
        .checkForAccessOrThrow(ResourceScope.of(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
                                   ngAccess.getProjectIdentifier()),
            Resource.of(SECRET_RESOURCE_TYPE, secretId), SECRET_ACCESS_PERMISSION, null);
    return ngAccessWithEncryptionConsumer;
  }

  private NGAccessWithEncryptionConsumer setupMocksForGetEncryptionDetailsForSecretWithOwner(
      NGAccess ngAccess, String secretId, String owner) throws IllegalAccessException {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    SecretPermissionValidator permissionValidator = new SecretPermissionValidator(accessControlClient);
    FieldUtils.writeField(ngSecretResourceV2, "secretPermissionValidator", permissionValidator, true);
    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        NGAccessWithEncryptionConsumer.builder()
            .ngAccess(ngAccess)
            .decryptableEntity(SecretVariableDTO.builder()
                                   .name(secretId)
                                   .secret(SecretRefData.builder().identifier(secretId).scope(Scope.PROJECT).build())
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .build())
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("unique-id").build();
    when(scopeResolverService.getScopeInfo(
             ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier()))
        .thenReturn(scopeInfo);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().build();
    secretDTOV2.setOwner(new UserPrincipal(owner, "email", owner, "accountId"));
    when(ngSecretService.get(scopeInfo, secretId))
        .thenReturn(Optional.of(SecretResponseWrapper.builder().secret(secretDTOV2).build()));
    return ngAccessWithEncryptionConsumer;
  }
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails() {
    String accountIdentifier = "accountId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "projId";
    String secretId = "secretId";
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    EncryptedRecordData encryptedRecordData =
        EncryptedRecordData.builder().uuid(secretId).name(secretId).encryptionType(LOCAL).build();

    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        setupMocksForGetEncryptionDetails(ngAccess, secretId);
    when(encryptedDataService.getEncryptionDetails(
             ngAccess, ngAccessWithEncryptionConsumer.getDecryptableEntity(), false))
        .thenReturn(List.of(EncryptedDataDetail.builder().encryptedData(encryptedRecordData).build()));
    ResponseDTO<List<EncryptedDataDetail>> result =
        ngSecretResourceV2.getEncryptionDetails(ngAccessWithEncryptionConsumer, accountIdentifier);

    verify(scopeResolverService, times(1)).getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(ngSecretService, times(1)).get(any(), eq(secretId));
    verify(encryptedDataService, times(1))
        .getEncryptionDetails(ngAccess, ngAccessWithEncryptionConsumer.getDecryptableEntity(), false);

    assertThat(result.getData().size()).isEqualTo(1);
    EncryptedDataDetail encryptedDataDetail = result.getData().get(0);
    assertThat(encryptedDataDetail.getEncryptedData().getUuid()).isEqualTo(secretId);
    assertThat(encryptedDataDetail.getEncryptedData().getName()).isEqualTo(secretId);
    assertThat(encryptedDataDetail.getEncryptedData().getEncryptionType()).isEqualTo(LOCAL);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithDifferentOwnerPrincipal() throws IllegalAccessException {
    String accountIdentifier = "accountId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "projId";
    String secretId = "secretId";
    String secretOwnerUsername = "user1";
    String currentUser = "user2";
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    SecurityContextBuilder.setContext(new UserPrincipal(currentUser, "email", currentUser, accountIdentifier, "role"));
    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        setupMocksForGetEncryptionDetailsForSecretWithOwner(ngAccess, secretId, secretOwnerUsername);
    assertThatThrownBy(() -> ngSecretResourceV2.getEncryptionDetails(ngAccessWithEncryptionConsumer, accountIdentifier))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Not authorized");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithSameOwnerPrincipal() throws IllegalAccessException {
    String accountIdentifier = "accountId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "projId";
    String secretId = "secretId";
    String currentUser = "user1";
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    SecurityContextBuilder.setContext(new UserPrincipal(currentUser, "email", currentUser, accountIdentifier));
    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        setupMocksForGetEncryptionDetailsForSecretWithOwner(ngAccess, secretId, currentUser);
    when(encryptedDataService.getEncryptionDetails(
             ngAccess, ngAccessWithEncryptionConsumer.getDecryptableEntity(), false))
        .thenReturn(List.of(
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().uuid(secretId).build()).build()));
    ResponseDTO<List<EncryptedDataDetail>> result =
        ngSecretResourceV2.getEncryptionDetails(ngAccessWithEncryptionConsumer, accountIdentifier);
    assertThat(result.getData().size()).isEqualTo(1);
    EncryptedDataDetail encryptedDataDetail = result.getData().get(0);
    assertThat(encryptedDataDetail.getEncryptedData().getUuid()).isEqualTo(secretId);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithServicePrincipal() throws IllegalAccessException {
    String accountIdentifier = "accountId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "projId";
    String secretId = "secretId";
    String secretOwnerUsername = "user1";
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    SecurityContextBuilder.setContext(new ServicePrincipal("NGManagerService"));
    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer =
        setupMocksForGetEncryptionDetailsForSecretWithOwner(ngAccess, secretId, secretOwnerUsername);
    when(encryptedDataService.getEncryptionDetails(
             ngAccess, ngAccessWithEncryptionConsumer.getDecryptableEntity(), false))
        .thenReturn(List.of(
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().uuid(secretId).build()).build()));
    ResponseDTO<List<EncryptedDataDetail>> result =
        ngSecretResourceV2.getEncryptionDetails(ngAccessWithEncryptionConsumer, accountIdentifier);
    assertThat(result.getData().size()).isEqualTo(1);
    EncryptedDataDetail encryptedDataDetail = result.getData().get(0);
    assertThat(encryptedDataDetail.getEncryptedData().getUuid()).isEqualTo(secretId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenGetEncryptionDetailsBulk_thenScopeResolvedOnce() {
    final var accountId = "accountId";
    final var orgId = "orgId";
    final var projId = "projId";
    final var projectScope = ScopeInfo.builder()
                                 .scopeType(ScopeLevel.PROJECT)
                                 .uniqueId("uniqueId1")
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .build();
    final var orgScope = ScopeInfo.builder()
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId("uniqueId2")
                             .accountIdentifier(accountId)
                             .orgIdentifier(orgId)
                             .build();
    final var secrets = Set.of("secret1", "org.secret2", "secret3", "org.secret4");

    when(scopeResolverService.getScopeInfo(accountId, orgId, projId)).thenReturn(projectScope);
    when(scopeResolverService.getScopeInfo(accountId, orgId, null)).thenReturn(orgScope);
    SecurityContextBuilder.setContext(new ServicePrincipal("service test"));

    ngSecretResourceV2.getEncryptionDetailsBulk(accountId, orgId, projId, secrets);

    verify(scopeResolverService, times(1)).getScopeInfo(accountId, orgId, projId);
    verify(scopeResolverService, times(1)).getScopeInfo(accountId, orgId, null);

    ArgumentCaptor<Set<String>> secretIdCaptor = ArgumentCaptor.forClass(Set.class);
    verify(encryptedDataService).getEncryptionDetailsBulk(eq(projectScope), secretIdCaptor.capture());
    assertThat(secretIdCaptor.getValue()).containsExactlyInAnyOrder("secret1", "secret3");
    verify(encryptedDataService).getEncryptionDetailsBulk(eq(orgScope), secretIdCaptor.capture());
    assertThat(secretIdCaptor.getValue()).containsExactlyInAnyOrder("secret2", "secret4");
    verifyNoMoreInteractions(scopeResolverService, encryptedDataService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenGetEncryptionDetailsBulkAndDynamicSecretRef_thenOk() {
    final var secretId = "hashicorpvault://connector_id/this/is/dynamic/secret#id.contains.dot";
    final var orgSecretId = "hashicorpvault://connector_id/this/is/org/dynamic/secret#id.contains.dot";
    final var accountId = "accountId";
    final var orgId = "orgId";
    final var projId = "projId";
    final var projectScope = ScopeInfo.builder()
                                 .scopeType(ScopeLevel.PROJECT)
                                 .uniqueId("uniqueId1")
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .build();
    final var orgScope = ScopeInfo.builder()
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId("uniqueId2")
                             .accountIdentifier(accountId)
                             .orgIdentifier(orgId)
                             .build();
    final var secrets = Set.of(secretId, "org." + orgSecretId);

    when(scopeResolverService.getScopeInfo(accountId, orgId, projId)).thenReturn(projectScope);
    when(scopeResolverService.getScopeInfo(accountId, orgId, null)).thenReturn(orgScope);
    SecurityContextBuilder.setContext(new ServicePrincipal("service test"));

    ngSecretResourceV2.getEncryptionDetailsBulk(accountId, orgId, projId, secrets);

    verify(scopeResolverService, times(1)).getScopeInfo(accountId, orgId, projId);
    verify(scopeResolverService, times(1)).getScopeInfo(accountId, orgId, null);

    ArgumentCaptor<Set<String>> secretIdCaptor = ArgumentCaptor.forClass(Set.class);
    verify(encryptedDataService).getEncryptionDetailsBulk(eq(projectScope), secretIdCaptor.capture());
    assertThat(secretIdCaptor.getValue()).containsExactlyInAnyOrder(secretId);
    verify(encryptedDataService).getEncryptionDetailsBulk(eq(orgScope), secretIdCaptor.capture());
    assertThat(secretIdCaptor.getValue()).containsExactlyInAnyOrder(orgSecretId);
    verifyNoMoreInteractions(scopeResolverService, encryptedDataService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenGetEncryptionDetailsBulkAndNonServicePrincipalThenNotAuthorized() {
    final var accountId = "accountId";
    final var orgId = "orgId";
    final var projId = "projId";
    final var secrets = Set.of("secret1", "org.secret2", "secret3", "org.secret4");

    SecurityContextBuilder.setContext(new UserPrincipal("test", "test@harness.io", "test@harness.io", accountId));

    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(() -> ngSecretResourceV2.getEncryptionDetailsBulk(accountId, orgId, projId, secrets))
        .has(new Condition<>(e -> e.getCode() == ErrorCode.NG_ACCESS_DENIED, "Access Denied"))
        .withMessage("Not authorized")
        .withNoCause();
    verifyNoInteractions(encryptedDataService, scopeResolverService);
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void testGetUsesScopeInfoAccountForAcl() {
    String queryAccountIdentifier = "ownAccount";
    String scopeAccountIdentifier = "otherAccount";
    String identifier = "GitLab";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(scopeAccountIdentifier)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(scopeAccountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().identifier(identifier).name(identifier).build();
    when(ngSecretService.get(scopeInfo, identifier))
        .thenReturn(Optional.of(SecretResponseWrapper.builder().secret(secretDTOV2).build()));
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());

    ngSecretResourceV2.get(identifier, queryAccountIdentifier, null, null, scopeInfo);

    verify(secretPermissionValidator)
        .checkForAccessOrThrow(eq(ResourceScope.of(scopeAccountIdentifier, null, null)),
            eq(Resource.of(SECRET_RESOURCE_TYPE, identifier)), eq(SECRET_VIEW_PERMISSION), any());
  }
}
