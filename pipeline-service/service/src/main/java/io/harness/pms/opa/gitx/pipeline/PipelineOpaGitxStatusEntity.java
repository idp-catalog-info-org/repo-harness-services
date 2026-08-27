/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa.gitx.pipeline;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.opa.gitx.OpaGitxStatusEntity;

import dev.morphia.annotations.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/** OPA onSave status entity for pipeline entities (pms-harness). Fields/indexes live in {@link OpaGitxStatusEntity}. */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineEntityOpaStatus", noClassnameStored = true)
@Document("pipelineEntityOpaStatus")
@TypeAlias("pipelineEntityOpaStatus")
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineOpaGitxStatusEntity extends OpaGitxStatusEntity {}
