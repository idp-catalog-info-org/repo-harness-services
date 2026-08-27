/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.impl;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.SERVICEACCOUNT;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.JOHANNES;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.YASH;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.ListUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.PlatformPermissions;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.accesscontrol.scopes.ScopeNameMapper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.core.dto.ServiceAccountFilterType;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.opa.entities.serviceaccount.ServiceAccountOpaService;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.service.ServiceAccountDTOMapper;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class ServiceAccountServiceImplTest extends NgManagerTestBase {
  private ServiceAccountService serviceAccountService;
  private ServiceAccountRepository serviceAccountRepository;
  private String accountIdentifier;
  private String orgIdentifier;
  private String orgIdentifier2;
  private String projectIdentifier;
  private String identifier;
  private String name;
  private String description;
  private ServiceAccountDTO serviceAccountRequestDTO;
  private AccessControlClient accessControlClient;
  private AccessControlAdminClient accessControlAdminClient;
  private ApiKeyService apiKeyService;
  private TransactionTemplate transactionTemplate;

  private ProjectService projectService;
  private OrganizationService organizationService;
  private ServiceAccountOpaService serviceAccountOpaService;
  private OutboxService outboxService;
  private ScopeInfoService scopeInfoService;
  private ScopeNameMapper scopeNameMapper;
  private ScopeInfo scopeInfo;
  private ScopeInfo scopeInfoAccount;
  String PROJECT_SCOPE_UNIQUE_ID = randomAlphabetic(10);
  String ORG_SCOPE_UNIQUE_ID = randomAlphabetic(10);
  String PROJECT_SCOPE_SERVICE_ACCOUNT = randomAlphabetic(10);
  String ACCOUNT_SCOPE_SERVICE_ACCOUNT = randomAlphabetic(10);

  String ORG_SCOPE_SERVICE_ACCOUNT = randomAlphabetic(10);

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = "accountId";
    orgIdentifier = "orgId";
    orgIdentifier2 = "orgId2";
    projectIdentifier = "projectId";
    identifier = "serviceaccountId";
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .uniqueId("scopeUniqueId")
                    .build();
    scopeInfoAccount = ScopeInfo.builder()
                           .accountIdentifier(accountIdentifier)
                           .uniqueId(accountIdentifier)
                           .scopeType(ScopeLevel.ACCOUNT)
                           .build();
    name = generateUuid();
    description = generateUuid();
    serviceAccountRepository = mock(ServiceAccountRepository.class);
    serviceAccountService = new ServiceAccountServiceImpl();
    accessControlClient = mock(AccessControlClient.class);
    accessControlAdminClient = mock(AccessControlAdminClient.class);
    apiKeyService = mock(ApiKeyService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    serviceAccountOpaService = mock(ServiceAccountOpaService.class);
    outboxService = mock(OutboxService.class);
    scopeInfoService = mock(ScopeInfoService.class);
    scopeNameMapper = mock(ScopeNameMapper.class);

    serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                   .identifier(identifier)
                                   .name(name)
                                   .email(name + "@harness.io")
                                   .description(description)
                                   .tags(new HashMap<>())
                                   .accountIdentifier(accountIdentifier)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .build();
    FieldUtils.writeField(serviceAccountService, "serviceAccountRepository", serviceAccountRepository, true);
    FieldUtils.writeField(serviceAccountService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(serviceAccountService, "accessControlClient", accessControlClient, true);
    FieldUtils.writeField(serviceAccountService, "accessControlAdminClient", accessControlAdminClient, true);
    FieldUtils.writeField(serviceAccountService, "apiKeyService", apiKeyService, true);
    FieldUtils.writeField(serviceAccountService, "projectService", projectService, true);
    FieldUtils.writeField(serviceAccountService, "organizationService", organizationService, true);
    FieldUtils.writeField(serviceAccountService, "serviceAccountOpaService", serviceAccountOpaService, true);
    FieldUtils.writeField(serviceAccountService, "outboxService", outboxService, true);
    FieldUtils.writeField(serviceAccountService, "scopeInfoService", scopeInfoService, true);
    FieldUtils.writeField(serviceAccountService, "scopeNameMapper", scopeNameMapper, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateServiceAccount_WithoutIdentifier() {
    ServiceAccountDTO serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                                     .identifier(null)
                                                     .name(name)
                                                     .email(name + "@harness.io")
                                                     .description(description)
                                                     .tags(new HashMap<>())
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .build();
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any(), any(), any()))
        .thenReturn(null);
    assertThatThrownBy(() -> serviceAccountService.createServiceAccount(scopeInfo, serviceAccountRequestDTO))
        .isInstanceOf(JerseyViolationException.class);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateServiceAccount_WithoutDescription() {
    ServiceAccountDTO serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                                     .identifier(identifier)
                                                     .name(name)
                                                     .email(name + "@harness.io")
                                                     .tags(new HashMap<>())
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .build();
    when(transactionTemplate.execute(any())).thenReturn(serviceAccountRequestDTO);
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any(), any(), any()))
        .thenReturn(null);
    ServiceAccount serviceAccount =
        ServiceAccountDTOMapper.getServiceAccountFromDTO(serviceAccountRequestDTO, scopeInfo);
    doReturn(serviceAccount).when(serviceAccountRepository).save(any());

    ServiceAccountDTO serviceAccountResponse =
        serviceAccountService.createServiceAccount(scopeInfo, serviceAccountRequestDTO);
    assertThat(serviceAccountResponse.getDescription()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateServiceAccount_noAccountExists() {
    assertThatThrownBy(
        () -> serviceAccountService.updateServiceAccount(scopeInfo, identifier, serviceAccountRequestDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateServiceAccount_updateEmail() {
    doReturn(ServiceAccount.builder()
                 .name(name)
                 .identifier(identifier)
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(accountIdentifier, "scopeUniqueId", identifier);

    assertThatThrownBy(
        () -> serviceAccountService.updateServiceAccount(scopeInfo, identifier, serviceAccountRequestDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void listServiceAccountDTO() {
    doReturn(ListUtils.newArrayList(ServiceAccount.builder()
                                        .name(name)
                                        .identifier(identifier)
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndParentUniqueId(accountIdentifier, scopeInfo.getUniqueId());
    List<ServiceAccount> accounts = serviceAccountService.listServiceAccounts(scopeInfo, Collections.emptyList());
    assertThat(accounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void listServiceAccountDTOWithIdentifiers() {
    doReturn(ListUtils.newArrayList(ServiceAccount.builder()
                                        .name(name)
                                        .identifier(identifier)
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndParentUniqueIdAndIdentifierIsIn(
            accountIdentifier, scopeInfo.getUniqueId(), Collections.singletonList(identifier));
    List<ServiceAccount> accounts =
        serviceAccountService.listServiceAccounts(scopeInfo, Collections.singletonList(identifier));
    assertThat(accounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccounts() throws IOException {
    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name(name)
                                                       .identifier(identifier)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(any(), any(), any())).thenReturn(Collections.emptyMap());
    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID, Optional.of(ScopeInfo.builder().uniqueId("unique-id").build()));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(map);

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(ScopeInfo.builder()
                                                               .accountIdentifier(accountIdentifier)
                                                               .orgIdentifier(orgIdentifier)
                                                               .projectIdentifier(projectIdentifier)
                                                               .uniqueId("unique-id")
                                                               .build(),
            Collections.singletonList(identifier), PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void listAggregateServiceAccounts_includingInheritedServiceAccounts() throws IOException {
    mockServiceCallsForInheritedServiceAccounts();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    doReturn(getResponseWithInheritedServiceAccounts())
        .when(serviceAccountRepository)
        .findAll(criteriaArgumentCaptor.capture(), any());
    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(ScopeInfo.builder()
                                                               .accountIdentifier(accountIdentifier)
                                                               .orgIdentifier(orgIdentifier)
                                                               .projectIdentifier(projectIdentifier)
                                                               .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                               .build(),
            Collections.emptyList(), PageRequest.ofSize(10),
            ServiceAccountFilterDTO.builder()
                .filterType(ServiceAccountFilterType.INCLUDE_INHERITED_SERVICE_ACCOUNTS)
                .build());

    Criteria actualCriteria = criteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = getExpectedCriteriaWithInheritedServiceAccounts();
    assertEquals(expectedCriteria, actualCriteria);

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(2);
    Set<String> serviceAccountsParentUniqueId = serviceAccountAggregateDTOPageResponse.getContent()
                                                    .stream()
                                                    .map(ServiceAccountAggregateDTO::getServiceAccount)
                                                    .toList()
                                                    .stream()
                                                    .map(ServiceAccountDTO::getParentUniqueId)
                                                    .collect(Collectors.toSet());

    Set<String> expectedServiceAccountsParentUniqueId = Set.of(PROJECT_SCOPE_UNIQUE_ID, accountIdentifier);
    assertEquals(serviceAccountsParentUniqueId, expectedServiceAccountsParentUniqueId);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void listAggregateServiceAccounts_includingChildScopeServiceAccounts() throws IOException {
    mockServiceCallsForIncludingChildScopeServiceAccounts();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    doReturn(getResponseWithIncludingChildScopeServiceAccounts())
        .when(serviceAccountRepository)
        .findAll(criteriaArgumentCaptor.capture(), any());
    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(ScopeInfo.builder()
                                                               .accountIdentifier(accountIdentifier)
                                                               .uniqueId(accountIdentifier)
                                                               .scopeType(ScopeLevel.ACCOUNT)
                                                               .build(),
            Collections.emptyList(), PageRequest.ofSize(10),
            ServiceAccountFilterDTO.builder()
                .filterType(ServiceAccountFilterType.INCLUDE_CHILD_SCOPE_SERVICE_ACCOUNTS)
                .build());

    Criteria actualCriteria = criteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = getExpectedCriteriaForChildScopeServiceAccounts();
    assertEquals(new Query(expectedCriteria), new Query(actualCriteria));

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(3);
    Set<String> serviceAccountsParentUniqueId = serviceAccountAggregateDTOPageResponse.getContent()
                                                    .stream()
                                                    .map(ServiceAccountAggregateDTO::getServiceAccount)
                                                    .toList()
                                                    .stream()
                                                    .map(ServiceAccountDTO::getParentUniqueId)
                                                    .collect(Collectors.toSet());

    Set<String> expectedServiceAccountsParentUniqueId =
        Set.of(PROJECT_SCOPE_UNIQUE_ID, ORG_SCOPE_UNIQUE_ID, accountIdentifier);
    assertEquals(serviceAccountsParentUniqueId, expectedServiceAccountsParentUniqueId);
  }

  private Object getResponseWithIncludingChildScopeServiceAccounts() {
    return new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                     .name(name)
                                                     .identifier(PROJECT_SCOPE_SERVICE_ACCOUNT)
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                     .build(),
        ServiceAccount.builder()
            .name(name)
            .identifier(ORG_SCOPE_SERVICE_ACCOUNT)
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .parentUniqueId(ORG_SCOPE_UNIQUE_ID)
            .build(),
        ServiceAccount.builder()
            .name(name)
            .identifier(ACCOUNT_SCOPE_SERVICE_ACCOUNT)
            .accountIdentifier(accountIdentifier)
            .parentUniqueId(accountIdentifier)
            .build()));
  }

  private Criteria getExpectedCriteriaForChildScopeServiceAccounts() {
    Criteria criteria = new Criteria();
    criteria.and("accountIdentifier")
        .is(scopeInfo.getAccountIdentifier())
        .and("parentUniqueId")
        .in(Set.of(PROJECT_SCOPE_UNIQUE_ID, ORG_SCOPE_UNIQUE_ID, accountIdentifier));
    return criteria;
  }

  private void mockServiceCallsForIncludingChildScopeServiceAccounts() throws IOException {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    when(organizationService.get((ScopeInfo) any()))
        .thenReturn(List.of(Organization.builder()
                                .identifier(orgIdentifier)
                                .accountIdentifier(accountIdentifier)
                                .uniqueId(ORG_SCOPE_UNIQUE_ID)
                                .parentUniqueId(accountIdentifier)
                                .build()));

    when(projectService.get((ScopeInfo) any()))
        .thenReturn(List.of(Project.builder()
                                .accountIdentifier(accountIdentifier)
                                .orgIdentifier(orgIdentifier)
                                .identifier(projectIdentifier)
                                .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                .parentUniqueId(ORG_SCOPE_UNIQUE_ID)
                                .build()));

    ResponseDTO<RoleAssignmentAggregateResponseDTO> roleAssignmentAggregateResponse = ResponseDTO.newResponse(
        RoleAssignmentAggregateResponseDTO.builder()
            .roles(Collections.emptyList())
            .roleAssignments(List.of(
                RoleAssignmentDTO.builder()
                    .principal(
                        PrincipalDTO.builder().identifier(ACCOUNT_SCOPE_SERVICE_ACCOUNT).scopeLevel("account").build())
                    .build()))
            .resourceGroups(Collections.emptyList())
            .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response =
        Response.success(roleAssignmentAggregateResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(any(), any(), any())).thenReturn(Collections.emptyMap());
    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    map.put(ORG_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .uniqueId(ORG_SCOPE_UNIQUE_ID)
                        .build()));
    map.put(accountIdentifier,
        Optional.of(ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build()));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(map);
  }

  private Criteria getExpectedCriteriaWithInheritedServiceAccounts() {
    return new Criteria().orOperator(
        Criteria.where("identifier")
            .is(ACCOUNT_SCOPE_SERVICE_ACCOUNT)
            .andOperator(
                Criteria.where("accountIdentifier").is(accountIdentifier).and("parentUniqueId").is(accountIdentifier)),
        Criteria.where("accountIdentifier").is(accountIdentifier).and("parentUniqueId").is(PROJECT_SCOPE_UNIQUE_ID));
  }

  private void mockServiceCallsForInheritedServiceAccounts() throws IOException {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse = ResponseDTO.newResponse(
        RoleAssignmentAggregateResponseDTO.builder()
            .roles(Collections.emptyList())
            .roleAssignments(List.of(
                RoleAssignmentDTO.builder()
                    .principal(
                        PrincipalDTO.builder().identifier(ACCOUNT_SCOPE_SERVICE_ACCOUNT).scopeLevel("account").build())
                    .build()))
            .resourceGroups(Collections.emptyList())
            .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(any(), any(), any())).thenReturn(Collections.emptyMap());
    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    map.put(accountIdentifier,
        Optional.of(ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build()));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(map);
  }

  private Object getResponseWithInheritedServiceAccounts() {
    return new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                     .name(name)
                                                     .identifier(PROJECT_SCOPE_SERVICE_ACCOUNT)
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                     .build(),
        ServiceAccount.builder()
            .name(name)
            .identifier(ACCOUNT_SCOPE_SERVICE_ACCOUNT)
            .accountIdentifier(accountIdentifier)
            .parentUniqueId(accountIdentifier)
            .build()));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccountsWithPermitted() throws IOException {
    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name(name)
                                                       .identifier(identifier)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(any(), any(), any())).thenReturn(Collections.emptyMap());
    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID)))).thenReturn(map);
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(identifier)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(true)
                                           .build()))
            .build();
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(ScopeInfo.builder()
                                                               .accountIdentifier(accountIdentifier)
                                                               .orgIdentifier(orgIdentifier)
                                                               .projectIdentifier(projectIdentifier)
                                                               .uniqueId("unique-id")
                                                               .build(),
            Collections.singletonList(identifier), PageRequest.ofSize(1),
            ServiceAccountFilterDTO.builder()
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .identifiers(Collections.singletonList(identifier))
                .build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccountsWithNonePermitted() throws IOException {
    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name(name)
                                                       .identifier(identifier)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID))))
        .thenReturn(scopeInfoMap);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(any(), any(), any())).thenReturn(Collections.emptyMap());
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(identifier)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(false)
                                           .build()))
            .build();
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(ScopeInfo.builder()
                                                               .accountIdentifier(accountIdentifier)
                                                               .orgIdentifier(orgIdentifier)
                                                               .projectIdentifier(projectIdentifier)
                                                               .uniqueId("unique-id")
                                                               .build(),
            Collections.singletonList(identifier), PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testGetServiceAccountDTOWithoutOrgAndProject() {
    doReturn(ServiceAccount.builder()
                 .name(name)
                 .identifier(identifier)
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(accountIdentifier, accountIdentifier, identifier);

    ServiceAccountDTO account = serviceAccountService.getServiceAccountDTO(ScopeInfo.builder()
                                                                               .accountIdentifier(accountIdentifier)
                                                                               .uniqueId(accountIdentifier)
                                                                               .scopeType(ScopeLevel.ACCOUNT)
                                                                               .build(),
        identifier);
    assertThat(account).isNotNull();
    assertThat(account.getName()).isEqualTo(name);
    assertThat(account.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(account.getParentUniqueId()).isEqualTo(accountIdentifier);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetInheritingChildScopeList_serviceAccountDoesNotExist() {
    doReturn(null)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(accountIdentifier, accountIdentifier, identifier);
    assertThatThrownBy(() -> serviceAccountService.getInheritingChildScopeList(scopeInfoAccount, identifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Service account with identifier: %s doesn't exist", identifier));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetInheritingChildScopeList_serviceAccountExists_notInheritedInOtherScopes() throws IOException {
    ServiceAccount serviceAccount = ServiceAccount.builder()
                                        .name(name)
                                        .identifier(identifier)
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build();
    doReturn(serviceAccount)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(any(), any(), any());
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder()
            .principalFilter(Collections.singleton(
                PrincipalDTO.builder()
                    .identifier(identifier)
                    .type(PrincipalType.SERVICE_ACCOUNT)
                    .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                    .build()))
            .build();

    Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getFilteredRoleAssignmentsIncludingChildScopes(accountIdentifier, null, null, roleAssignmentFilterDTO);
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder().accountIdentifier(accountIdentifier).build();

    doReturn(Response.success(ResponseDTO.newResponse(new ArrayList<>(Collections.singletonList(
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA4")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier(identifier)
                                     .type(SERVICE_ACCOUNT)
                                     .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build())))))
        .when(request)
        .execute();

    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfoAccount);
    List<ScopeNameDTO> returnedValue = serviceAccountService.getInheritingChildScopeList(scopeInfoAccount, identifier);
    assertThat(returnedValue.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetInheritingChildScopeList_serviceAccountExists_inheritedInOtherScopes() throws IOException {
    ServiceAccount serviceAccount =
        ServiceAccount.builder().name(name).identifier(identifier).accountIdentifier(accountIdentifier).build();
    doReturn(serviceAccount)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(any(), any(), any());
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder()
            .principalFilter(Collections.singleton(
                PrincipalDTO.builder()
                    .identifier(identifier)
                    .type(PrincipalType.SERVICE_ACCOUNT)
                    .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                    .build()))
            .build();

    Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getFilteredRoleAssignmentsIncludingChildScopes(accountIdentifier, null, null, roleAssignmentFilterDTO);
    ScopeDTO roleAssignmentScopeDTO1 = ScopeDTO.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .build();
    ScopeDTO roleAssignmentScopeDTO2 =
        ScopeDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier2).build();
    ScopeDTO roleAssignmentScopeDTO3 = ScopeDTO.builder().accountIdentifier(accountIdentifier).build();

    doReturn(Response.success(ResponseDTO.newResponse(new ArrayList<>(Arrays.asList(
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO1)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA1")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier(identifier)
                                     .type(SERVICE_ACCOUNT)
                                     .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO1)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA2")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier(identifier)
                                     .type(SERVICE_ACCOUNT)
                                     .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO2)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA3")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier(identifier)
                                     .type(SERVICE_ACCOUNT)
                                     .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build(),
                 RoleAssignmentResponseDTO.builder()
                     .scope(roleAssignmentScopeDTO3)
                     .roleAssignment(
                         RoleAssignmentDTO.builder()
                             .identifier("RA4")
                             .roleIdentifier("ROLE1")
                             .resourceGroupIdentifier("RG1")
                             .disabled(false)
                             .managed(false)
                             .principal(
                                 PrincipalDTO.builder()
                                     .identifier(identifier)
                                     .type(SERVICE_ACCOUNT)
                                     .scopeLevel(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase())
                                     .build())
                             .build())
                     .build())))))
        .when(request)
        .execute();
    ScopeNameDTO scopeNameDTO1 = ScopeNameDTO.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .orgName("ORG")
                                     .projectName("PROJ")
                                     .build();
    ScopeNameDTO scopeNameDTO2 = ScopeNameDTO.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .orgIdentifier(orgIdentifier2)
                                     .orgName("ORG2")
                                     .build();

    when(scopeNameMapper.toScopeNameDTO(roleAssignmentScopeDTO2)).thenReturn(scopeNameDTO2);
    when(scopeNameMapper.toScopeNameDTO(roleAssignmentScopeDTO1)).thenReturn(scopeNameDTO1);
    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfoAccount);
    List<ScopeNameDTO> returnedValue = serviceAccountService.getInheritingChildScopeList(scopeInfoAccount, identifier);
    assertThat(returnedValue.size()).isEqualTo(2);
    assertThat(returnedValue).isEqualTo(new ArrayList<>(Arrays.asList(scopeNameDTO1, scopeNameDTO2)));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateServiceAccount_AndOPAPolicyReturnsError_ThenReturnWithGovernanceMetadata() {
    GovernanceMetadata errorMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(eq(accountIdentifier), eq(serviceAccountRequestDTO),
             eq(orgIdentifier), eq(projectIdentifier), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(errorMetadata);

    ServiceAccountDTO result = serviceAccountService.createServiceAccount(scopeInfo, serviceAccountRequestDTO);

    assertThat(result.getGovernanceMetadata()).isEqualTo(errorMetadata);
    assertThat(result.getIdentifier()).isNull();
    assertThat(result.getUniqueId()).isNull();
    assertThat(result.getParentUniqueId()).isNull();
    assertThat(result.getName()).isNull();
    assertThat(result.getEmail()).isNull();
    assertThat(result.getDescription()).isNull();
    assertThat(result.getTags()).isNull();
    assertThat(result.getAccountIdentifier()).isNull();
    assertThat(result.getOrgIdentifier()).isNull();
    assertThat(result.getProjectIdentifier()).isNull();
    verify(transactionTemplate, times(0)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateServiceAccount_AndOPAPolicyReturnsWarning_ThenSaveAndReturnWithGovernanceMetadata() {
    GovernanceMetadata warnMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_WARNING).setDeny(false).build();
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(eq(accountIdentifier), eq(serviceAccountRequestDTO),
             eq(orgIdentifier), eq(projectIdentifier), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(warnMetadata);

    ServiceAccount serviceAccount =
        ServiceAccountDTOMapper.getServiceAccountFromDTO(serviceAccountRequestDTO, scopeInfo);
    doReturn(serviceAccount).when(serviceAccountRepository).save(any());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocation
            -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(new SimpleTransactionStatus()));

    ServiceAccountDTO result = serviceAccountService.createServiceAccount(scopeInfo, serviceAccountRequestDTO);

    ServiceAccountDTO expected = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, scopeInfo);
    expected.setGovernanceMetadata(warnMetadata);

    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    assertThat(result.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(result.getName()).isEqualTo(expected.getName());
    assertThat(result.getEmail()).isEqualTo(expected.getEmail());
    assertThat(result.getAccountIdentifier()).isEqualTo(expected.getAccountIdentifier());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenUpdateServiceAccount_AndOPAPolicyReturnsError_ThenReturnWithGovernanceMetadata() {
    ServiceAccount existingAccount = ServiceAccount.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .accountIdentifier(accountIdentifier)
                                         .email(name + "@harness.io")
                                         .parentUniqueId("scopeUniqueId")
                                         .build();
    doReturn(existingAccount)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(
            eq(accountIdentifier), eq("scopeUniqueId"), eq(identifier));

    GovernanceMetadata errorMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(eq(accountIdentifier), eq(serviceAccountRequestDTO),
             eq(orgIdentifier), eq(projectIdentifier), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(errorMetadata);

    ServiceAccountDTO result =
        serviceAccountService.updateServiceAccount(scopeInfo, identifier, serviceAccountRequestDTO);

    assertThat(result.getGovernanceMetadata()).isEqualTo(errorMetadata);
    assertThat(result.getIdentifier()).isNull();
    assertThat(result.getUniqueId()).isNull();
    assertThat(result.getParentUniqueId()).isNull();
    assertThat(result.getName()).isNull();
    assertThat(result.getEmail()).isNull();
    assertThat(result.getDescription()).isNull();
    assertThat(result.getTags()).isNull();
    assertThat(result.getAccountIdentifier()).isNull();
    assertThat(result.getOrgIdentifier()).isNull();
    assertThat(result.getProjectIdentifier()).isNull();
    verify(transactionTemplate, times(0)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenUpdateServiceAccountAnd_OPAPolicyReturnsWarning_ThenSaveAndReturnWithGovernanceMetadata() {
    ServiceAccount existingAccount = ServiceAccount.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .accountIdentifier(accountIdentifier)
                                         .email(name + "@harness.io")
                                         .parentUniqueId("scopeUniqueId")
                                         .build();
    doReturn(existingAccount)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(
            eq(accountIdentifier), eq("scopeUniqueId"), eq(identifier));

    GovernanceMetadata warnMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_WARNING).setDeny(false).build();
    when(serviceAccountOpaService.evaluatePoliciesWithEntity(eq(accountIdentifier), eq(serviceAccountRequestDTO),
             eq(orgIdentifier), eq(projectIdentifier), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(warnMetadata);

    doReturn(existingAccount).when(serviceAccountRepository).save(any());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocation
            -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(new SimpleTransactionStatus()));

    ServiceAccountDTO result =
        serviceAccountService.updateServiceAccount(scopeInfo, identifier, serviceAccountRequestDTO);

    ServiceAccountDTO expected = ServiceAccountDTOMapper.getDTOFromServiceAccount(existingAccount, scopeInfo);
    expected.setGovernanceMetadata(warnMetadata);

    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    assertThat(result.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(result.getName()).isEqualTo(expected.getName());
    assertThat(result.getEmail()).isEqualTo(expected.getEmail());
    assertThat(result.getAccountIdentifier()).isEqualTo(expected.getAccountIdentifier());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void
  listManageableServiceAccounts_WhenUserHasManagePermission_ThenReturnsAllServiceAccountsWithoutAggregateData() {
    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name(name)
                                                       .identifier(identifier)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build())))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));
    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);

    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID, Optional.of(ScopeInfo.builder().uniqueId("unique-id").build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID)))).thenReturn(map);

    PageResponse<ServiceAccountDTO> serviceAccountDTOPageResponse =
        serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                .accountIdentifier(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .uniqueId("unique-id")
                                                                .build(),
            PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build());

    assertThat(serviceAccountDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountDTOPageResponse.getContent().size()).isEqualTo(1);
    assertThat(serviceAccountDTOPageResponse.getContent().get(0).getIdentifier()).isEqualTo(identifier);

    verify(accessControlAdminClient, never()).getAggregatedFilteredRoleAssignments(any(), any(), any(), any());
    verify(apiKeyService, never()).getApiKeysPerParentIdentifier(any(), any(), any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void
  listManageableServiceAccounts_WhenFilterIncludesInheritedServiceAccounts_ThenThrowsInvalidRequestException() {
    assertThatThrownBy(
        ()
            -> serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                       .accountIdentifier(accountIdentifier)
                                                                       .orgIdentifier(orgIdentifier)
                                                                       .projectIdentifier(projectIdentifier)
                                                                       .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                                       .build(),
                PageRequest.ofSize(10),
                ServiceAccountFilterDTO.builder()
                    .filterType(ServiceAccountFilterType.INCLUDE_INHERITED_SERVICE_ACCOUNTS)
                    .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("INCLUDE_INHERITED_SERVICE_ACCOUNTS is not supported for manageable service accounts");
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void
  listManageableServiceAccounts_WhenFilterIncludesChildScopeServiceAccounts_ThenReturnsServiceAccountsFromChildScopes()
      throws IOException {
    mockServiceCallsForIncludingChildScopeServiceAccounts();
    doReturn(getResponseWithIncludingChildScopeServiceAccounts())
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(PROJECT_SCOPE_SERVICE_ACCOUNT)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(true)
                                           .build(),
                AccessControlDTO.builder()
                    .resourceIdentifier(ORG_SCOPE_SERVICE_ACCOUNT)
                    .resourceScope(ResourceScope.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .build())
                    .permitted(true)
                    .build(),
                AccessControlDTO.builder()
                    .resourceIdentifier(ACCOUNT_SCOPE_SERVICE_ACCOUNT)
                    .resourceScope(ResourceScope.builder().accountIdentifier(accountIdentifier).build())
                    .permitted(true)
                    .build()))
            .build();
    ArgumentCaptor<List<PermissionCheckDTO>> permissionChecksCaptor = ArgumentCaptor.forClass(List.class);
    when(accessControlClient.checkForAccessOrThrow(permissionChecksCaptor.capture()))
        .thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountDTO> serviceAccountDTOPageResponse =
        serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                .accountIdentifier(accountIdentifier)
                                                                .uniqueId(accountIdentifier)
                                                                .scopeType(ScopeLevel.ACCOUNT)
                                                                .build(),
            PageRequest.ofSize(10),
            ServiceAccountFilterDTO.builder()
                .filterType(ServiceAccountFilterType.INCLUDE_CHILD_SCOPE_SERVICE_ACCOUNTS)
                .build());

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any());
    List<PermissionCheckDTO> permissionChecks = permissionChecksCaptor.getValue();
    assertThat(permissionChecks).hasSize(3);
    assertThat(permissionChecks.stream().map(PermissionCheckDTO::getPermission))
        .allMatch(p -> p.equals(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION));

    assertThat(serviceAccountDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountDTOPageResponse.getContent().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void
  listManageableServiceAccounts_WhenUserHasManagePermissionOnSpecificServiceAccounts_ThenReturnsOnlyPermittedServiceAccounts()
      throws IOException {
    String permittedIdentifier1 = "permittedSA1";
    String permittedIdentifier2 = "permittedSA2";
    String deniedIdentifier = "deniedSA";

    // First call returns all 3 service accounts, second call returns only the 2 permitted ones
    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name("Permitted SA 1")
                                                       .identifier(permittedIdentifier1)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build(),
                 ServiceAccount.builder()
                     .name("Permitted SA 2")
                     .identifier(permittedIdentifier2)
                     .accountIdentifier(accountIdentifier)
                     .orgIdentifier(orgIdentifier)
                     .projectIdentifier(projectIdentifier)
                     .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                     .build(),
                 ServiceAccount.builder()
                     .name("Denied SA")
                     .identifier(deniedIdentifier)
                     .accountIdentifier(accountIdentifier)
                     .orgIdentifier(orgIdentifier)
                     .projectIdentifier(projectIdentifier)
                     .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                     .build())))
        .doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                            .name("Permitted SA 1")
                                                            .identifier(permittedIdentifier1)
                                                            .accountIdentifier(accountIdentifier)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                            .build(),
            ServiceAccount.builder()
                .name("Permitted SA 2")
                .identifier(permittedIdentifier2)
                .accountIdentifier(accountIdentifier)
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                .build())))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));

    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(false);

    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID)))).thenReturn(map);

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(permittedIdentifier1)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(true)
                                           .build(),
                AccessControlDTO.builder()
                    .resourceIdentifier(permittedIdentifier2)
                    .resourceScope(ResourceScope.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build())
                    .permitted(true)
                    .build(),
                AccessControlDTO.builder()
                    .resourceIdentifier(deniedIdentifier)
                    .resourceScope(ResourceScope.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build())
                    .permitted(false)
                    .build()))
            .build();

    ArgumentCaptor<List<PermissionCheckDTO>> permissionChecksCaptor = ArgumentCaptor.forClass(List.class);
    when(accessControlClient.checkForAccessOrThrow(permissionChecksCaptor.capture()))
        .thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountDTO> serviceAccountDTOPageResponse =
        serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                .accountIdentifier(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .uniqueId("unique-id")
                                                                .build(),
            PageRequest.ofSize(10), ServiceAccountFilterDTO.builder().build());

    // Verify only the 2 permitted service accounts are returned (denied one is filtered out)
    assertThat(serviceAccountDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountDTOPageResponse.getContent().size()).isEqualTo(2);

    List<String> returnedIdentifiers =
        serviceAccountDTOPageResponse.getContent().stream().map(ServiceAccountDTO::getIdentifier).toList();
    assertThat(returnedIdentifiers).containsExactlyInAnyOrder(permittedIdentifier1, permittedIdentifier2);
    assertThat(returnedIdentifiers).doesNotContain(deniedIdentifier);

    // Verify permission check was called for all 3 service accounts
    List<PermissionCheckDTO> permissionChecks = permissionChecksCaptor.getValue();
    assertThat(permissionChecks).hasSize(3);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void listManageableServiceAccounts_WhenUserHasNoManageApiKeyPermission_ThenThrowsAccessDeniedException() {
    String userId = randomAlphabetic(10);

    doReturn(new PageImpl<>(ListUtils.newArrayList(ServiceAccount.builder()
                                                       .name(name)
                                                       .identifier(identifier)
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                       .build())))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));
    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(false);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID))))
        .thenReturn(scopeInfoMap);

    String exceptionMessage = String.format("Principal of type USER with identifier %s : Missing permission "
            + "core_serviceaccount_manageapikey on serviceaccount",
        userId);
    ArgumentCaptor<List<PermissionCheckDTO>> permissionChecksCaptor = ArgumentCaptor.forClass(List.class);
    when(accessControlClient.checkForAccessOrThrow(permissionChecksCaptor.capture()))
        .thenThrow(new NGAccessDeniedException(exceptionMessage, USER, emptyList()));

    assertThatThrownBy(
        ()
            -> serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                       .accountIdentifier(accountIdentifier)
                                                                       .orgIdentifier(orgIdentifier)
                                                                       .projectIdentifier(projectIdentifier)
                                                                       .uniqueId("unique-id")
                                                                       .build(),
                PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build()))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Missing permission core_serviceaccount_manageapikey");
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void listManageableServiceAccounts_WhenNoServiceAccountsExistInScope_ReturnsEmptyPage() {
    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(false);

    doReturn(new PageImpl<>(Collections.emptyList()))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), eq(Pageable.unpaged()));

    PageResponse<ServiceAccountDTO> result =
        serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                .accountIdentifier(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .uniqueId("unique-id")
                                                                .build(),
            PageRequest.ofSize(10), ServiceAccountFilterDTO.builder().build());

    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getContent().size()).isEqualTo(0);
    assertThat(result.getTotalPages()).isEqualTo(1);

    verify(accessControlClient, times(1)).hasAccess(any(), any(), anyString());

    verify(accessControlClient, never()).checkForAccessOrThrow(any());

    verify(serviceAccountRepository, times(1)).findAll(any(Criteria.class), eq(Pageable.unpaged()));
    verify(serviceAccountRepository, never()).findAll(any(Criteria.class), any(PageRequest.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void listManageableServiceAccounts_WithDefaultFilterType_ExcludesInheritedServiceAccounts() {
    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);

    ServiceAccount projectScopeAccount = ServiceAccount.builder()
                                             .name(name)
                                             .identifier(PROJECT_SCOPE_SERVICE_ACCOUNT)
                                             .accountIdentifier(accountIdentifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                             .build();

    doReturn(new PageImpl<>(ListUtils.newArrayList(projectScopeAccount)))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));

    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID)))).thenReturn(map);

    serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                            .accountIdentifier(accountIdentifier)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                            .build(),
        PageRequest.ofSize(10),
        ServiceAccountFilterDTO.builder()
            .filterType(ServiceAccountFilterType.EXCLUDE_INHERITED_SERVICE_ACCOUNTS)
            .build());

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceAccountRepository).findAll(criteriaCaptor.capture(), any(Pageable.class));
    Criteria capturedCriteria = criteriaCaptor.getValue();

    Criteria expectedCriteria =
        Criteria.where("accountIdentifier").is(accountIdentifier).and("parentUniqueId").is(PROJECT_SCOPE_UNIQUE_ID);

    assertThat(new Query(capturedCriteria)).isEqualTo(new Query(expectedCriteria));

    Document queryDocument = capturedCriteria.getCriteriaObject();
    assertThat(queryDocument.containsKey("$or"))
        .as("Criteria should not use $or operator for inherited scopes")
        .isFalse();
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void listManageableServiceAccounts_WithSearchTerm_FiltersByNameIdentifierAndTags() {
    NGTag tag1 = NGTag.builder().key("env").value("prod1").build();

    NGTag tag2 = NGTag.builder().key("env").value("qa").build();

    ServiceAccount sa1 = ServiceAccount.builder()
                             .name("Bot Account")
                             .identifier("bot_prod_1")
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                             .tags(List.of(tag1))
                             .build();

    ServiceAccount sa2 = ServiceAccount.builder()
                             .name("Service Account")
                             .identifier("service_bot_2")
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                             .tags(List.of(tag2))
                             .build();

    ServiceAccount sa3 = ServiceAccount.builder()
                             .name("Admin Account")
                             .identifier("admin_prod_3")
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .parentUniqueId(PROJECT_SCOPE_UNIQUE_ID)
                             .tags(List.of(tag2))
                             .build();

    doReturn(new PageImpl<>(ListUtils.newArrayList(sa1, sa2)))
        .when(serviceAccountRepository)
        .findAll(any(Criteria.class), any(Pageable.class));

    when(accessControlClient.hasAccess(eq(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier)),
             eq(Resource.of(SERVICEACCOUNT, null)), eq(PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);

    Map<String, Optional<ScopeInfo>> map = new HashMap<>();
    map.put(PROJECT_SCOPE_UNIQUE_ID,
        Optional.of(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                        .build()));
    when(scopeInfoService.getScopeInfo(eq(accountIdentifier), eq(Set.of(PROJECT_SCOPE_UNIQUE_ID)))).thenReturn(map);

    // When: Search with term "bot"
    PageResponse<ServiceAccountDTO> result =
        serviceAccountService.listManageableServiceAccounts(ScopeInfo.builder()
                                                                .accountIdentifier(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .uniqueId(PROJECT_SCOPE_UNIQUE_ID)
                                                                .build(),
            PageRequest.ofSize(10), ServiceAccountFilterDTO.builder().searchTerm("bot").build());

    List<String> returnedIdentifiers =
        result.getContent().stream().map(ServiceAccountDTO::getIdentifier).collect(Collectors.toList());
    assertThat(returnedIdentifiers).containsExactlyInAnyOrder("bot_prod_1", "service_bot_2");
    assertThat(returnedIdentifiers).doesNotContain("admin_prod_3");

    // Verify: Repository was called with criteria containing search term
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceAccountRepository).findAll(criteriaCaptor.capture(), any(Pageable.class));
    Criteria capturedCriteria = criteriaCaptor.getValue();
    assertThat(capturedCriteria).isNotNull();

    Document queryDocument = capturedCriteria.getCriteriaObject();
    List<Document> andConditions = (List<Document>) queryDocument.get("$and");

    Document orDocuments = andConditions.stream()
                               .filter(doc -> doc.containsKey("$or"))
                               .findFirst()
                               .orElseThrow(() -> new AssertionError("Search $or condition not found"));

    List<Document> searchConditions = (List<Document>) orDocuments.get("$or");

    assertThat(searchConditions).hasSize(4);

    Set<String> searchedFields =
        searchConditions.stream().map(doc -> doc.keySet().iterator().next()).collect(Collectors.toSet());

    assertThat(searchedFields).containsExactlyInAnyOrder("name", "identifier", "tags.key", "tags.value");
  }
}
