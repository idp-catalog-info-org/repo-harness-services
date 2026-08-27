/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface WorkflowLibraryRepository extends CrudRepository<WorkflowLibraryEntity, String> {
  List<WorkflowLibraryEntity> findByIsStableTrueAndDeprecatedFalse();
  List<WorkflowLibraryEntity> findByCategoryAndIsStableTrueAndDeprecatedFalse(String category);
  List<WorkflowLibraryEntity> findByStatusAndIsStableTrueAndDeprecatedFalse(String status);
  List<WorkflowLibraryEntity> findByStatusAndCategoryAndIsStableTrueAndDeprecatedFalse(String status, String category);
  WorkflowLibraryEntity findByIdentifierAndIsStableTrue(String identifier);
  WorkflowLibraryEntity findByIdentifierAndVersion(String identifier, String version);
  List<WorkflowLibraryEntity> findByIdentifierOrderByVersionDesc(String identifier);
  List<WorkflowLibraryEntity> findAllByIdentifierIn(List<String> identifiers);
  void deleteByIdentifierAndVersionNot(String identifier, String version);
}
