/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa.gitx.pipeline;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.opa.gitx.AbstractOpaGitxStatusRepository;
import io.harness.opa.gitx.OpaGitxStatusEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.springframework.data.mongodb.core.MongoTemplate;

/** OPA onSave status repository for pipeline entities (pms-harness DB). Binds the concrete Morphia class. */
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineEntityOpaStatusRepository extends AbstractOpaGitxStatusRepository {
  @Inject
  public PipelineEntityOpaStatusRepository(MongoTemplate mongoTemplate) {
    super(mongoTemplate);
  }

  @Override
  protected Class<? extends OpaGitxStatusEntity> getEntityClass() {
    return PipelineOpaGitxStatusEntity.class;
  }
}
