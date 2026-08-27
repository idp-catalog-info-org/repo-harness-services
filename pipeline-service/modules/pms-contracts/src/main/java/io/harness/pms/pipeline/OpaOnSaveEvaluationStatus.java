/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

/**
 * OPA onSave evaluation status for the GET Pipeline API response.
 * Mirrors the internal {@code OpaGitxStatus} enum constants.
 */
@OwnedBy(PIPELINE) public enum OpaOnSaveEvaluationStatus { SUCCESS, WARNING, ERROR, UNKNOWN, NOT_EVALUATED }
