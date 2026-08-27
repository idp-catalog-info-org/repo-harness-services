/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class UpdateNextIterationMigration implements NGMigration {
  @Inject private NamespaceServiceImpl namespaceService;
  @Inject private NamespaceRepository namespaceRepository;

  @Override
  public void migrate() {
    try {
      OffsetDateTime currentTime = OffsetDateTime.now();
      OffsetDateTime nextIterationTime = currentTime.plusMinutes(30);
      long nextIterationTimeMillis = nextIterationTime.toInstant().toEpochMilli();
      List<NamespaceEntity> namespacesToUpdate =
          namespaceRepository.findAllByIsDeleted(false)
              .stream()
              .peek(namespace -> namespace.setNextIteration(nextIterationTimeMillis))
              .collect(Collectors.toList());

      if (!namespacesToUpdate.isEmpty()) {
        namespaceRepository.saveAll(namespacesToUpdate);
        log.info("Successfully updated nextIteration to {} for {} namespace entities", nextIterationTime,
            namespacesToUpdate.size());
      } else {
        log.info("No namespace entities found for the enabled accounts");
      }
    } catch (Exception e) {
      log.error("Error bulk updating nextIteration for namespaces: {}", e.getMessage(), e);
    }
  }
}
