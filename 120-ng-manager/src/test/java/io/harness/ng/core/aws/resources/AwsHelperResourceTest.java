/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aws.resources;

import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.service.AwsResourceServiceImpl;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.definition.config.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.AwsListInstancesFilterDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;
import software.wings.service.impl.aws.model.AwsVPC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class AwsHelperResourceTest extends CategoryTest {
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  private static final String ENV_ID = "envId";
  private static final String INFRA_DEFINITION_ID = "infraDefinitionId";
  private static final String REGION = "us-east";

  @Mock AwsResourceServiceImpl awsHelperService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ArtifactResourceUtils artifactResourceUtils;
  @Mock ScopeInfoService scopeInfoService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;

  IdentifierRef identifierRef = mock(IdentifierRef.class);

  @InjectMocks AwsHelperResource awsHelperResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getASGNames() {
    List<String> asgList = Arrays.asList("asg1", "asg2");
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(awsHelperService.getASGNames(any(), anyString(), anyString(), anyString(), any())).thenReturn(asgList);

      ResponseDTO<List<String>> result = awsHelperResource.getASGNames(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGION, null, null);
      assertThat(result.getData()).isEqualTo(asgList);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getASGNamesByInfra() {
    List<String> asgList = Arrays.asList("asg1", "asg2");
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class);
         MockedStatic<InfrastructureEntityConfigMapper> ignore2 = mockStatic(InfrastructureEntityConfigMapper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenAnswer(i
              -> InfrastructureConfig.builder()
                     .infrastructureDefinitionConfig(
                         InfrastructureDefinitionConfig.builder()
                             .spec(AsgInfrastructure.builder()
                                       .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                       .region(ParameterField.createValueField(REGION))
                                       .build())
                             .build())
                     .build());
      when(awsHelperService.getASGNames(any(), anyString(), anyString(), anyString(), any())).thenReturn(asgList);
      when(infrastructureEntityService.get(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
          .thenReturn(Optional.of(InfrastructureEntity.builder().build()));

      ResponseDTO<List<String>> result = awsHelperResource.getASGNames(
          null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, ENV_ID, INFRA_DEFINITION_ID);
      assertThat(result.getData()).isEqualTo(asgList);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getVpcs() {
    List<AwsVPC> vpcs = Arrays.asList(AwsVPC.builder().id("vpc1").build(), AwsVPC.builder().id("vpc2").build());
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(awsHelperService.getVPCs(any(), anyString(), anyString(), anyString(), any())).thenReturn(vpcs);

      ResponseDTO<List<AwsVPC>> result = awsHelperResource.getVpcs(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGION, null, null);
      assertThat(result.getData()).isEqualTo(vpcs);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getVpcsByInfra() {
    List<AwsVPC> vpcs = Arrays.asList(AwsVPC.builder().id("vpc1").build(), AwsVPC.builder().id("vpc2").build());
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class);
         MockedStatic<InfrastructureEntityConfigMapper> ignore2 = mockStatic(InfrastructureEntityConfigMapper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenAnswer(i
              -> InfrastructureConfig.builder()
                     .infrastructureDefinitionConfig(
                         InfrastructureDefinitionConfig.builder()
                             .spec(SshWinRmAwsInfrastructure.builder()
                                       .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                       .region(ParameterField.createValueField(REGION))
                                       .build())
                             .build())
                     .build());
      when(awsHelperService.getVPCs(any(), anyString(), anyString(), anyString(), any())).thenReturn(vpcs);
      when(infrastructureEntityService.get(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
          .thenReturn(Optional.of(InfrastructureEntity.builder().build()));

      ResponseDTO<List<AwsVPC>> result = awsHelperResource.getVpcs(
          null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, ENV_ID, INFRA_DEFINITION_ID);
      assertThat(result.getData()).isEqualTo(vpcs);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void filterHosts() {
    List<AwsEC2Instance> instances = Arrays.asList(
        AwsEC2Instance.builder().publicIp("1.2.3.1").build(), AwsEC2Instance.builder().publicIp("1.2.3.2").build());

    AwsListInstancesFilterDTO filterDTO = AwsListInstancesFilterDTO.builder()
                                              .region(REGION)
                                              .autoScalingGroupName("asg")
                                              .hostConnectionType(HostConnectionTypeKind.PUBLIC_IP)
                                              .vpcIds(List.of("vpc-1"))
                                              .tags(Map.of("tag1", "val1"))
                                              .winRm(false)
                                              .build();

    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(awsHelperService.filterHosts(any(), anyBoolean(), anyString(), anyList(), anyMap(), anyString(), any()))
          .thenReturn(instances);

      ResponseDTO<List<String>> result = awsHelperResource.filterHosts(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null, filterDTO);
      assertThat(result.getData()).contains("1.2.3.1", "1.2.3.2");
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void filterHostsByInfra() {
    List<AwsEC2Instance> instances = Arrays.asList(
        AwsEC2Instance.builder().publicIp("1.2.3.1").build(), AwsEC2Instance.builder().publicIp("1.2.3.2").build());

    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class);
         MockedStatic<InfrastructureEntityConfigMapper> ignore2 = mockStatic(InfrastructureEntityConfigMapper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);

      when(InfrastructureEntityConfigMapper.toInfrastructureConfig(any(InfrastructureEntity.class)))
          .thenAnswer(i
              -> InfrastructureConfig.builder()
                     .infrastructureDefinitionConfig(
                         InfrastructureDefinitionConfig.builder()
                             .spec(SshWinRmAwsInfrastructure.builder()
                                       .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                       .region(ParameterField.createValueField(REGION))
                                       .asgName(ParameterField.createValueField("asg"))
                                       .hostConnectionType(
                                           ParameterField.createValueField(HostConnectionTypeKind.PUBLIC_IP))
                                       .awsInstanceFilter(
                                           AwsInstanceFilter.builder()
                                               .vpcs(ParameterField.createValueField(Collections.EMPTY_LIST))
                                               .tags(ParameterField.createValueField(Collections.EMPTY_MAP))
                                               .build())
                                       .build())
                             .deploymentType(ServiceDefinitionType.WINRM)
                             .build())
                     .build());

      when(awsHelperService.filterHosts(any(), anyBoolean(), anyString(), anyList(), anyMap(), anyString(), any()))
          .thenReturn(instances);
      when(infrastructureEntityService.get(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
          .thenReturn(Optional.of(InfrastructureEntity.builder().build()));

      ResponseDTO<List<String>> result = awsHelperResource.filterHosts(
          null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_ID, INFRA_DEFINITION_ID, null);
      assertThat(result.getData()).contains("1.2.3.1", "1.2.3.2");
    }
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void getASGNamesEnforcesConnectorAccess() {
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(awsHelperService.getASGNames(any(), anyString(), anyString(), anyString(), any()))
          .thenReturn(Collections.emptyList());

      awsHelperResource.getASGNames(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGION, null, null);

      verify(artifactResourceUtils).checkConnectorAccess(identifierRef);
    }
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void getASGNamesDeniedConnectorAccessThrows() {
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      doThrow(new InvalidRequestException("access denied"))
          .when(artifactResourceUtils)
          .checkConnectorAccess(identifierRef);

      assertThatThrownBy(()
                             -> awsHelperResource.getASGNames(CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                                 PROJECT_IDENTIFIER, REGION, null, null))
          .isInstanceOf(InvalidRequestException.class);

      verify(awsHelperService, never()).getASGNames(any(), anyString(), anyString(), anyString(), any());
    }
  }
}
