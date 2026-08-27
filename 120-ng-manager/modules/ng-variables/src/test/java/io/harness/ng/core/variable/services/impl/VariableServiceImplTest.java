/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl;

import static io.harness.ng.core.variable.VariableType.STRING;
import static io.harness.ng.core.variable.VariableValueType.FIXED;
import static io.harness.ng.core.variable.VariableValueType.FIXED_SET;
import static io.harness.ng.core.variable.VariableValueType.REGEX;
import static io.harness.rule.OwnerRule.ABHISHEK_DAS;
import static io.harness.rule.OwnerRule.MADHU;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.utils.PageTestUtils.getPage;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.events.VariableUpdateEvent;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO.StringVariableConfigDTOKeys;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.ng.opa.entities.variable.VariableOpaService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.variable.spring.VariableRepository;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class VariableServiceImplTest extends CategoryTest {
  @Mock private VariableRepository variableRepository;
  @Mock private VariableMapper variableMapper;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;
  @Mock private OrganizationService organizationService;
  @Mock private ProjectService projectService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private VariableOpaService variableOpaService;
  @Mock private Project project;
  @Mock Organization organization;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  private VariableServiceImpl variableService;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  @Captor private ArgumentCaptor<VariableCreateEvent> variableCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<VariableUpdateEvent> variableUpdateEventArgumentCaptor;
  PageRequest pageRequest;
  Pageable pageable;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    pageRequest = PageRequest.builder()
                      .pageIndex(0)
                      .pageSize(10)
                      .sortOrders(List.of(
                          SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
                      .build();
    pageable = getPageRequest(pageRequest);
    this.variableService =
        new VariableServiceImpl(variableRepository, variableMapper, transactionTemplate, outboxService, projectService,
            organizationService, scopeInfoService, accessControlClient, variableOpaService, featureFlagHelperService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringFixedValueMissingValue() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED).build())
                                  .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Value for field [%s] must be provide when value type is [%s]",
        StringVariableConfigDTOKeys.fixedValue, VariableValueType.FIXED));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringFixedSetMissingAllowedValues() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED_SET).build())
                                  .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringRegexMissingRegex() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(REGEX).build())
                                  .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringRegexInvalidRegex() {
    String regex = "[a-z]\\i";
    VariableDTO variableDTO =
        VariableDTO.builder()
            .type(STRING)
            .variableConfig(StringVariableConfigDTO.builder().valueType(REGEX).regex(regex).build())
            .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  private VariableDTO getVariableDTO(String identifier, String orgIdentifier, String projectIdentifier, String value) {
    return VariableDTO.builder()
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED).fixedValue(value).build())
        .build();
  }

  private VariableResponseDTO getVariableResponseDTO(
      String identifier, String orgIdentifier, String projectIdentifier, String value) {
    return VariableResponseDTO.builder()
        .variable(getVariableDTO(identifier, orgIdentifier, projectIdentifier, value))
        .build();
  }

  private Variable getVariable(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String value) {
    Variable variable = StringVariable.builder().fixedValue(value).build();
    variable.setAccountIdentifier(accountIdentifier);
    variable.setOrgIdentifier(orgIdentifier);
    variable.setProjectIdentifier(projectIdentifier);
    variable.setIdentifier(identifier);
    variable.setValueType(FIXED);
    variable.setType(STRING);
    variable.setParentUniqueId("uniqueId");
    return variable;
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    String orgUniqueIdentifier = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(scopeInfo, variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(projectService.get(scopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(organizationService.get(any(ScopeInfo.class), any())).thenReturn(Optional.of(organization));
    when(featureFlagHelperService.isEnabled(anyString(), any())).thenReturn(false);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    variableService.create(scopeInfo, variableDTO);
    verify(variableMapper, times(1)).toVariable(scopeInfo, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(featureFlagHelperService, times(1)).isEnabled(anyString(), any());
    verify(outboxService, times(1)).save(variableCreateEventArgumentCaptor.capture());
    VariableCreateEvent capturedVariableCreateEvent = variableCreateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableCreateEvent.getVariableDTO());
  }

  @Test(expected = DuplicateFieldException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate_duplicateKeyException() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    String orgUniqueIdentifier = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableRepository.save(variable)).thenThrow(new DuplicateKeyException(""));
    when(projectService.get(scopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(featureFlagHelperService.isEnabled(anyString(), any())).thenReturn(false);
    when(organizationService.get(any(ScopeInfo.class), anyString())).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    variableService.create(scopeInfo, variableDTO);
    verify(featureFlagHelperService, times(1)).isEnabled(anyString(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate_invalidRequestException() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    VariableDTO variableDTO = VariableDTO.builder()
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    variableService.create(scopeInfo, variableDTO);
  }

  @Test
  @Owner(developers = ABHISHEK_DAS)
  @Category(UnitTests.class)
  public void testCreateVariableWithOPAPolicies_FeatureFlagEnabled_PolicyPasses() {
    // Arrange
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);

    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(randomAlphabetic(10))
                              .build();

    // Mock feature flag enabled
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(scopeInfo, variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(projectService.get(scopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES))
        .thenReturn(true);
    when(organizationService.get(any(ScopeInfo.class), anyString())).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    // Mock OPA service response with successful governance metadata
    GovernanceMetadata successMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_PASS).build();

    when(variableOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(successMetadata);

    // Act - this should not throw an exception
    variableService.create(scopeInfo, variableDTO);

    // Assert
    verify(featureFlagHelperService, times(1)).isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES);
    verify(variableOpaService, times(1))
        .evaluatePoliciesWithEntity(
            eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier));
    verify(variableMapper, times(1)).toVariable(scopeInfo, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(outboxService, times(1)).save(variableCreateEventArgumentCaptor.capture());
    VariableCreateEvent capturedVariableCreateEvent = variableCreateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableCreateEvent.getVariableDTO());
  }

  @Test
  @Owner(developers = ABHISHEK_DAS)
  @Category(UnitTests.class)
  public void testCreateVariableWithOPAPolicies_FeatureFlagEnabled_PolicyFails() {
    // Arrange
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);

    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(randomAlphabetic(10))
                              .build();

    // Mock feature flag enabled
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(scopeInfo, variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(projectService.get(scopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES))
        .thenReturn(true);
    when(organizationService.get(any(ScopeInfo.class), anyString())).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    // Mock OPA service response with successful governance metadata
    GovernanceMetadata failureMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();

    when(variableOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(failureMetadata);

    // Act - this should throw an exception
    VariableDTO variableResponse = variableService.create(scopeInfo, variableDTO);
    GovernanceMetadata governanceMetadata = variableResponse.getGovernanceMetadata();
    assertThat(governanceMetadata).isEqualTo(failureMetadata);

    // Assert
    verify(featureFlagHelperService, times(1)).isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES);
    verify(variableOpaService, times(1))
        .evaluatePoliciesWithEntity(
            eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier));
    verify(variableMapper, times(1)).toVariable(scopeInfo, variableDTO);
    verify(transactionTemplate, times(0)).execute(any());
    verify(variableRepository, times(0)).save(variable);
    verify(outboxService, times(0)).save(variableCreateEventArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = ABHISHEK_DAS)
  @Category(UnitTests.class)
  public void testCreateVariableWithOPAPolicies_FeatureFlagEnabled_PolicyPasses_NullGovernanceMetadata() {
    // Arrange
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);

    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(randomAlphabetic(10))
                              .build();

    // Mock feature flag enabled
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(scopeInfo, variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(projectService.get(scopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES))
        .thenReturn(true);
    when(organizationService.get(any(ScopeInfo.class), anyString())).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    // Mock OPA service response with successful governance metadata
    VariableDTO opaResponse = VariableDTO.builder().build();
    opaResponse.setGovernanceMetadata(null);

    when(variableOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(null);

    // Act - this should not throw an exception
    variableService.create(scopeInfo, variableDTO);

    // Assert
    verify(featureFlagHelperService, times(1)).isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES);
    verify(variableOpaService, times(1))
        .evaluatePoliciesWithEntity(
            eq(scopeInfo), eq(variableDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier));
    verify(variableMapper, times(1)).toVariable(scopeInfo, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(outboxService, times(1)).save(variableCreateEventArgumentCaptor.capture());
    VariableCreateEvent capturedVariableCreateEvent = variableCreateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableCreateEvent.getVariableDTO());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String var1 = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, var1, var1);
    VariableDTO varADTO = getVariableDTO(var1, orgIdentifier, projectIdentifier, var1);
    List<Variable> variables = new ArrayList<>(Collections.singletonList(varA));
    when(variableRepository.findAllByAccountIdentifierAndParentUniqueId(anyString(), anyString()))
        .thenReturn(variables);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(variableMapper.writeDTO(scopeInfo, varA)).thenReturn(varADTO);

    List<VariableDTO> varList = variableService.list(scopeInfo);
    verify(variableRepository, times(1)).findAllByAccountIdentifierAndParentUniqueId(any(), any());
    assertThat(varList).hasOnlyElementsOfType(VariableDTO.class);
    assertThat(varList.size()).isEqualTo(variables.size());
    assertThat(varList).contains(varADTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForAccountScope() {
    String accountIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, null, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varA.setParentUniqueId(accountIdentifier);
    varList.add(varA);
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build();
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, null, null, varID);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    PageResponse<VariableResponseDTO> list = variableService.list(scopeInfo, null, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testPagedListForAccountScopeWithIdentifier() {
    String accountIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, null, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varA.setParentUniqueId(accountIdentifier);
    varList.add(varA);
    VariableListRequestDTO variableListRequestDTO =
        VariableListRequestDTO.builder().identifiers(Collections.singletonList(varA.getIdentifier())).build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build();
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, null, null, varID);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    PageResponse<VariableResponseDTO> list =
        variableService.list(scopeInfo, variableListRequestDTO, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void testPagedListForAccountScopeWithNullIdentifiers() {
    String accountIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, null, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varA.setParentUniqueId(accountIdentifier);
    varList.add(varA);
    VariableListRequestDTO variableListRequestDTO = VariableListRequestDTO.builder().build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build();
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, null, null, varID);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    PageResponse<VariableResponseDTO> list =
        variableService.list(scopeInfo, variableListRequestDTO, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void testPagedListForAccountScopeWithEmptyIdentifiers() {
    String accountIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, null, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varA.setParentUniqueId(accountIdentifier);
    varList.add(varA);
    VariableListRequestDTO variableListRequestDTO =
        VariableListRequestDTO.builder().identifiers(Collections.emptyList()).build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build();
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, null, null, varID);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    PageResponse<VariableResponseDTO> list =
        variableService.list(scopeInfo, variableListRequestDTO, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForOrgScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, null, varID, varID);
    varA.setParentUniqueId(uniqueId);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, null, varID);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    PageResponse<VariableResponseDTO> list = variableService.list(scopeInfo, null, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForProjectScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, varID, varID);
    varA.setParentUniqueId(uniqueId);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, projectIdentifier, varID);
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    PageResponse<VariableResponseDTO> list = variableService.list(scopeInfo, null, null, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForProjectScopeWithSearchTerm() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, varID, varID);
    varA.setParentUniqueId(uniqueId);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, projectIdentifier, varID);
    when(variableMapper.toResponseWrapper(scopeInfo, varA)).thenReturn(variableResponseDTO);
    when(variableRepository.findAllWithCollation(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(variableResponseDTO);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    PageResponse<VariableResponseDTO> list = variableService.list(scopeInfo, null, varID, false, pageable);
    verify(variableRepository, times(1)).findAllWithCollation(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(list.getContent().get(0).getVariable().getIdentifier()).isEqualTo(varID);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String uniqueId = randomAlphabetic(10);
    String value = randomAlphabetic(7);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    VariableResponseDTO variableResponseDTO =
        getVariableResponseDTO(identifier, orgIdentifier, projectIdentifier, value);
    when(
        variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(variable));
    when(variableMapper.toResponseWrapper(scopeInfo, variable)).thenReturn(variableResponseDTO);
    assertThat(variableService.get(scopeInfo, identifier)).isNotNull();
    verify(variableRepository, times(1))
        .findByAccountIdentifierAndParentUniqueIdAndIdentifier(accountIdentifier, uniqueId, identifier);
    verify(variableMapper, times(1)).toResponseWrapper(scopeInfo, variable);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    String orgUniqueIdentifier = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .uniqueId(orgUniqueIdentifier)
                                 .build();
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    variableDTO.setType(STRING);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableMapper.toVariable(scopeInfo, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(scopeInfo, variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             accountIdentifier, orgUniqueIdentifier, identifier))
        .thenReturn(Optional.of(variable));
    when(projectService.get(orgScopeInfo, projectIdentifier)).thenReturn(Optional.of(project));
    when(organizationService.get(any(ScopeInfo.class), any())).thenReturn(Optional.of(organization));
    when(featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OPA_FOR_VARIABLES))
        .thenReturn(false);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    variableService.update(scopeInfo, variableDTO);
    verify(variableMapper, times(1)).toVariable(scopeInfo, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(outboxService, times(1)).save(variableUpdateEventArgumentCaptor.capture());
    verify(featureFlagHelperService, times(1))
        .isEnabled(scopeInfo.getAccountIdentifier(), FeatureName.PL_ENABLE_OPA_FOR_VARIABLES);
    VariableUpdateEvent capturedVariableUpdateEvent = variableUpdateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableUpdateEvent.getNewVariableDTO());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate_changeValueType() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    variableDTO.setType(STRING);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    Variable variable_edited = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    variable_edited.setValueType(REGEX);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(variableRepository.save(variable)).thenReturn(variable);
    when(variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             accountIdentifier, uniqueId, identifier))
        .thenReturn(Optional.of(variable_edited));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Variable Value Type cannot be changed");
    variableService.update(scopeInfo, variableDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void tesDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(
        variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(variable));
    variableService.delete(scopeInfo, identifier);
    verify(variableRepository, times(1)).delete(variable);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void tesDelete_VariableNotExists() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(
        variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    exceptionRule.expect(NotFoundException.class);
    variableService.delete(scopeInfo, identifier);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteBatch() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    List<String> variableIdentifiers = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      variableIdentifiers.add(randomAlphabetic(10));
    }
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(variable));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    variableService.deleteBatch(scopeInfo, variableIdentifiers);
    verify(variableRepository, times(5)).delete(variable);
  }
}
