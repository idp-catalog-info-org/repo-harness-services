/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.k8sinlinemanifest;

import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8sInlineManifestServiceImplTest extends CategoryTest {
  @InjectMocks private K8sInlineManifestServiceImpl k8sInlineManifestService;
  @Mock private K8sEntityHelper k8sEntityHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private InfrastructureMapper infrastructureMapper;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_ID = "envId";
  private static final String INFRA_ID = "infraId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInfraDelegateConfigUsesGetEntityGitDetailsForOutcome() {
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("scopeUniqueId").build();
    doReturn(scopeInfo).when(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    Environment environment = Environment.builder().type(EnvironmentType.Production).build();
    doReturn(Optional.of(environment)).when(environmentService).get(scopeInfo, ENV_ID, false);

    InfrastructureEntity infraEntity = InfrastructureEntity.builder()
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_ID)
                                           .projectIdentifier(PROJECT_ID)
                                           .yaml("infrastructureDefinition:\n"
                                               + "  identifier: " + INFRA_ID + "\n"
                                               + "  type: KubernetesDirect\n"
                                               + "  spec:\n"
                                               + "    connectorRef: k8sConnector\n"
                                               + "    namespace: default\n"
                                               + "    releaseName: release")
                                           .build();
    doReturn(Optional.of(infraEntity))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(boolean.class),
            any(boolean.class));

    EntityGitDetails entityGitDetails =
        EntityGitDetails.builder().repoName("repo").filePath("path").repoUrl("https://url").build();
    doReturn(entityGitDetails).when(gitAwareEntityHelper).getEntityGitDetailsForOutcome(any());

    K8sDirectInfrastructureOutcome infraOutcome =
        K8sDirectInfrastructureOutcome.builder().connectorRef("k8sConnector").namespace("default").build();
    doReturn(infraOutcome)
        .when(infrastructureMapper)
        .toOutcome(any(), any(), any(), any(), any(), any(), any(), any(), any());

    doReturn(null).when(k8sEntityHelper).getK8sInfraDelegateConfig(any(), any(), any());

    try {
      k8sInlineManifestService.applyK8sManifest(K8sManifestRequest.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .orgId(ORG_ID)
                                                    .projectId(PROJECT_ID)
                                                    .k8sConnectorId("")
                                                    .environmentId(ENV_ID)
                                                    .infrastructureId(INFRA_ID)
                                                    .k8sManifest("apiVersion: v1")
                                                    .releaseIdentifier("release")
                                                    .build(),
          "uid", null);
    } catch (Exception e) {
      // Expected — delegateGrpcClientWrapper.submitAsyncTaskV2 not fully mocked
    }

    verify(gitAwareEntityHelper).getEntityGitDetailsForOutcome(infraEntity);
    verify(gitAwareEntityHelper, never()).getEntityGitDetails(any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInfraDelegateConfigThrowsWhenEnvironmentNotFound() {
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("scopeUniqueId").build();
    doReturn(scopeInfo).when(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    doReturn(Optional.empty()).when(environmentService).get(scopeInfo, ENV_ID, false);

    InfrastructureEntity infraEntity = InfrastructureEntity.builder().build();
    doReturn(Optional.of(infraEntity))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(boolean.class),
            any(boolean.class));

    assertThatThrownBy(()
                           -> k8sInlineManifestService.applyK8sManifest(K8sManifestRequest.builder()
                                                                            .accountId(ACCOUNT_ID)
                                                                            .orgId(ORG_ID)
                                                                            .projectId(PROJECT_ID)
                                                                            .k8sConnectorId("")
                                                                            .environmentId(ENV_ID)
                                                                            .infrastructureId(INFRA_ID)
                                                                            .k8sManifest("apiVersion: v1")
                                                                            .releaseIdentifier("release")
                                                                            .build(),
                               "uid", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Environment with identity");
  }
}
