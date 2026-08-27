/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.ecs.helper.EcsDeploymentReleaseData;
import io.harness.entities.deploymentinfo.EcsDeploymentInfo;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.DeploymentInfoDetails;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.ecs.EcsInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class EcsPerpetualTaskDeDuplicationRunnerTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock private EcsInstanceSyncPerpetualTaskHandler ecsInstanceSyncPerpetualTaskHandler;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;

  private EcsPerpetualTaskDeDuplicationRunner ecsPerpetualTaskDeDuplicationRunner;
  private AutoCloseable closeable;

  @Before
  public void setup() {
    closeable = MockitoAnnotations.openMocks(this);
    ecsPerpetualTaskDeDuplicationRunner =
        new EcsPerpetualTaskDeDuplicationRunner(mongoTemplate, delegateServiceGrpcClient,
            instanceSyncPerpetualTaskInfoService, ecsInstanceSyncPerpetualTaskHandler, kryoSerializer, List.of());
  }

  @After
  public void cleanup() throws Exception {
    closeable.close();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testMatchingEcsDeploymentInfo() {
    InstanceSyncPerpetualTaskInfo emptyPerpetualTaskInfo = InstanceSyncPerpetualTaskInfo.builder().build();
    assertThat(ecsPerpetualTaskDeDuplicationRunner.containsMatchingDeploymentInfo(emptyPerpetualTaskInfo)).isFalse();

    InstanceSyncPerpetualTaskInfo ecsPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .deploymentInfoDetailsList(
                List.of(DeploymentInfoDetails.builder().deploymentInfo(EcsDeploymentInfo.builder().build()).build()))
            .build();
    assertThat(ecsPerpetualTaskDeDuplicationRunner.containsMatchingDeploymentInfo(ecsPerpetualTaskInfo)).isTrue();

    InstanceSyncPerpetualTaskInfo nonEcsPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .deploymentInfoDetailsList(
                List.of(DeploymentInfoDetails.builder().deploymentInfo(K8sDeploymentInfo.builder().build()).build()))
            .build();
    assertThat(ecsPerpetualTaskDeDuplicationRunner.containsMatchingDeploymentInfo(nonEcsPerpetualTaskInfo)).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEcsDeDuplication() {
    assertThat(ecsPerpetualTaskDeDuplicationRunner.deduplicateReleaseData(List.of())).isEmpty();
    assertThat(ecsPerpetualTaskDeDuplicationRunner.deduplicateReleaseData(null)).isEmpty();
    List<EcsDeploymentReleaseData> deploymentReleases =
        List.of(EcsDeploymentReleaseData.builder().serviceName("ecscanary").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecscanary__canary").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbluegreen__1").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbluegreen__2").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__1").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__2").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__3").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__4").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__5").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasic__6").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasicV2__8").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasicV2__9").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasicV2__10").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasicV2__11").build(),
            EcsDeploymentReleaseData.builder().serviceName("ecsbasicV2__12").build());
    List<EcsDeploymentReleaseData> distinctDeploymentReleases =
        ecsPerpetualTaskDeDuplicationRunner.deduplicateReleaseData(deploymentReleases);
    assertThat(distinctDeploymentReleases).hasSize(8);
    assertThat(distinctDeploymentReleases.stream().map(EcsDeploymentReleaseData::getServiceName).toList())
        .containsExactlyInAnyOrder("ecscanary", "ecscanary__canary", "ecsbluegreen__1", "ecsbluegreen__2",
            "ecsbasic__5", "ecsbasic__6", "ecsbasicV2__11", "ecsbasicV2__12");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEcsDeploymentInfoDeDuplication() {
    assertThat(ecsPerpetualTaskDeDuplicationRunner.deduplicateDeploymentInfos(null)).isEmpty();
    assertThat(ecsPerpetualTaskDeDuplicationRunner.deduplicateDeploymentInfos(List.of())).isEmpty();

    List<DeploymentInfoDetails> deploymentInfoDetails =
        List.of(DeploymentInfoDetails.builder()
                    .deploymentInfo(K8sDeploymentInfo.builder()
                                        .releaseName("releaseName")
                                        .namespaces(new LinkedHashSet<>(List.of("ns11")))
                                        .build())
                    .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecscanary").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecscanary__canary").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbluegreen__1").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbluegreen__2").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__1").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__2").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__3").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__4").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__5").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasic__6").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasicV2__8").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasicV2__9").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasicV2__10").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasicV2__11").build())
                .build(),
            DeploymentInfoDetails.builder()
                .deploymentInfo(EcsDeploymentInfo.builder().serviceName("ecsbasicV2__12").build())
                .build()

        );

    List<DeploymentInfoDetails> distinctDeploymentInfos =
        ecsPerpetualTaskDeDuplicationRunner.deduplicateDeploymentInfos(deploymentInfoDetails);
    assertThat(distinctDeploymentInfos).hasSize(9);
    assertThat(distinctDeploymentInfos.stream()
                   .map(DeploymentInfoDetails::getDeploymentInfo)
                   .filter(EcsDeploymentInfo.class ::isInstance)
                   .map(EcsDeploymentInfo.class ::cast)
                   .map(EcsDeploymentInfo::getServiceName)
                   .toList())
        .containsExactlyInAnyOrder("ecscanary", "ecscanary__canary", "ecsbluegreen__1", "ecsbluegreen__2",
            "ecsbasic__5", "ecsbasic__6", "ecsbasicV2__11", "ecsbasicV2__12");
    assertThat(distinctDeploymentInfos.stream()
                   .map(DeploymentInfoDetails::getDeploymentInfo)
                   .filter(K8sDeploymentInfo.class ::isInstance)
                   .map(K8sDeploymentInfo.class ::cast)
                   .map(K8sDeploymentInfo::getReleaseName)
                   .toList())
        .containsExactlyInAnyOrder("releaseName");
  }
}
