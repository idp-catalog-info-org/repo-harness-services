/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.git;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;

@Data
@JsonSubTypes({
  @JsonSubTypes.Type(value = GitIntegrationTokenAuth.class, name = "TOKEN")
  , @JsonSubTypes.Type(value = GitIntegrationUsernamePasswordAuth.class, name = "USERNAME_PASSWORD"),
      @JsonSubTypes.Type(value = GitIntegrationGithubAppAuth.class, name = "GITHUB_APP"),
      @JsonSubTypes.Type(value = GitIntegrationManagedTokenAuth.class, name = "MANAGED_TOKEN")
})
@OwnedBy(HarnessTeam.IDP)
public abstract class GitIntegrationAuth {}
