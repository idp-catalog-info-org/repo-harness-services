/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event;

import static io.harness.annotations.dev.HarnessTeam.HAR;
import static io.harness.ngtriggers.Constants.HAR_ARTIFACT_TRIGGER_TYPE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = HarArtifactEventSpec.class, name = HAR_ARTIFACT_TRIGGER_TYPE) })
@OwnedBy(HAR)
public interface HarnessArtifactRegistryEventSpec extends PayloadAware {
  boolean fetchAutoAbortPreviousExecutions();
}
