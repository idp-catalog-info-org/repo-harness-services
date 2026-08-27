/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.migration.beans.NGMigration;
import io.harness.serializer.KryoSerializer;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.ecs.EcsInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ExperianEcsPerpetualTaskDeDuplicationMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Inject private EcsInstanceSyncPerpetualTaskHandler ecsInstanceSyncPerpetualTaskHandler;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;

  private static final String DEBUG_LOG = "[ExperianEcsPerpetualTaskDeDuplicationMigration]";

  @Override
  public void migrate() {
    try {
      // Experian prod2 accountId
      List<String> accountIds = List.of(
          "cpbandfuSD-hva0LU8wz0g", "OgiB4-xETamKNVAz-wQRjw", "px7xd_BFRCi-pfWPYXVjvw", "rXUXvbFqRr2XwcjBu3Oq-Q");

      EcsPerpetualTaskDeDuplicationRunner ecsPerpetualTaskDeDuplicationRunner =
          new EcsPerpetualTaskDeDuplicationRunner(mongoTemplate, delegateServiceGrpcClient,
              instanceSyncPerpetualTaskInfoService, ecsInstanceSyncPerpetualTaskHandler, kryoSerializer, accountIds);
      ecsPerpetualTaskDeDuplicationRunner.run();
    } catch (Exception e) {
      log.error("{} Migration failed with error: ", DEBUG_LOG, e);
    }
  }
}
