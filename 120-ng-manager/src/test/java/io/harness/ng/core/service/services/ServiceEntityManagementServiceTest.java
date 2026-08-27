/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.KAVYA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.service.services.exception.ActiveServiceInstancesPresentException;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instanceorphans.InstanceOrphansService;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class ServiceEntityManagementServiceTest extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  @Mock InstanceService instanceService;
  @Mock InstanceRepository instanceRepository;
  @Mock ServiceSequenceService serviceSequenceService;
  @Mock InstanceOrphansService instanceOrphansService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock ScopeInfoService scopeInfoService;
  @Spy @Inject @InjectMocks ServiceEntityManagementServiceImpl serviceEntityManagementService;

  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  private static final ScopeInfo scopeInfo = ScopeInfo.builder()
                                                 .accountIdentifier(accountIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .scopeType(ScopeLevel.PROJECT)
                                                 .uniqueId("uniqueId")
                                                 .build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldDetectOrphansAndAllowServiceDeletion() {
    List<Instance> instances = List.of(getInstance(), getInstance());

    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong())).thenReturn(instances);
    when(instanceOrphansService.detectOrphanInstances(eq(accountIdentifier), anyList())).thenReturn(true);
    when(serviceEntityService.delete(eq(scopeInfo), eq(identifier), eq(null), eq(false))).thenReturn(true);

    assertThat(serviceEntityManagementService.deleteService(
                   accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false, scopeInfo))
        .isTrue();

    verify(instanceService).deleteAll(notNull());
    verify(serviceSequenceService).delete(scopeInfo, identifier);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenInstancesRunning() {
    List<Instance> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    assertThatThrownBy(()
                           -> serviceEntityManagementService.deleteService(
                               accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false, scopeInfo))
        .isInstanceOf(ActiveServiceInstancesPresentException.class)
        .hasMessage("Service [identifier] under Project[projectIdentifier], Organization [orgIdentifier] couldn't be "
            + "deleted since there are currently 2 active instances for the service");
    verify(instanceService, never()).deleteAll(any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDeleteServiceWhenNoInstances() {
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong())).thenReturn(null);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false, null);
    verify(serviceEntityService).delete(eq(scopeInfo), eq(identifier), eq(null), eq(false));
    verify(instanceService, never()).deleteAll(any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldForceDeleteServiceInstances() {
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    List<Instance> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    when(serviceEntityService.delete(eq(scopeInfo), eq(identifier), eq(null), eq(true))).thenReturn(true);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", true, null);
    verify(instanceService, times(1)).deleteAll(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void ShouldDeleteServiceSequence() {
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    doReturn(true).when(serviceSequenceService).delete(any(), any());

    List<Instance> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    when(serviceEntityService.delete(eq(scopeInfo), eq(identifier), eq(null), eq(true))).thenReturn(true);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", true, null);
    verify(instanceService, times(1)).deleteAll(any());
    verify(serviceSequenceService, times(1)).delete(any(), any());
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void shouldNotThrowNPEWhenScopeInfoIsNull() {
    List<Instance> instances = List.of(getInstance(), getInstance());

    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(instanceRepository.getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong())).thenReturn(instances);
    when(instanceOrphansService.detectOrphanInstances(eq(accountIdentifier), anyList())).thenReturn(true);
    when(serviceEntityService.delete(eq(scopeInfo), eq(identifier), eq(null), eq(false))).thenReturn(true);

    assertThat(serviceEntityManagementService.deleteService(
                   accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false, null))
        .isTrue();

    verify(scopeInfoService).getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(instanceRepository).getInstancesCreatedBefore(eq(scopeInfo), eq(identifier), anyLong());
    verify(serviceEntityService).delete(eq(scopeInfo), eq(identifier), eq(null), eq(false));
    verify(instanceService).deleteAll(notNull());
  }

  private Instance getInstance() {
    return Instance.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .serviceIdentifier(identifier)
        .envIdentifier(identifier)
        .lastPipelineExecutionId("lastPipelineExecutionId")
        .infraIdentifier("infraIdentifier")
        .envName("envName")
        .envType(EnvironmentType.PreProduction)
        .infrastructureKind(KUBERNETES_DIRECT)
        .primaryArtifact(ArtifactDetails.builder().tag("buildId").build())
        .createdAt(0L)
        .deletedAt(10L)
        .createdAt(0L)
        .lastModifiedAt(0L)
        .instanceInfo(K8sInstanceInfo.builder().podName("podName").releaseName("releaseName").build())
        .build();
  }
}