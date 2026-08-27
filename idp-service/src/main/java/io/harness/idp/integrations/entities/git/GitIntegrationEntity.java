/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.entities.git;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.entities.IntegrationEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "parentType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureIntegrationEntity.class, name = "AZURE")
  , @JsonSubTypes.Type(value = BitbucketCloudIntegrationEntity.class, name = "BITBUCKET_CLOUD"),
      @JsonSubTypes.Type(value = BitbucketServerIntegrationEntity.class, name = "BITBUCKET_SERVER"),
      @JsonSubTypes.Type(value = GithubIntegrationEntity.class, name = "GITHUB"),
      @JsonSubTypes.Type(value = GitlabIntegrationEntity.class, name = "GITLAB"),
      @JsonSubTypes.Type(value = HarnessCodeRepoIntegrationEntity.class, name = "HARNESS_CODE_REPO")
})
@OwnedBy(HarnessTeam.IDP)
public abstract class GitIntegrationEntity extends IntegrationEntity {
  @NotNull private String connectorIdentifier;
  @NotNull private String host;
  @NotNull private AuthMode authMode;
  @NotNull private boolean executeOnDelegate;
  private Set<String> delegateSelectors;
  private ReadPermissionValidation readPermissionValidation;

  public enum AuthMode { TOKEN, USERNAME_PASSWORD, GITHUB_APP, MANAGED_TOKEN }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldNameConstants(innerTypeName = "IntegrationsReadValidationDetailsKeys")
  public static class ReadPermissionValidation {
    @NotNull private String fileUrl;
    @NotNull private String status;
    private String error;
    @NotNull private long lastValidatedAt;
  }

  public String getHostForHostProxy() {
    return this.getHost()
        + (this.getAdditionalIndexer() != null && !this.getAdditionalIndexer().equals(this.getHost())
                ? "_" + this.getAdditionalIndexer()
                : "");
  }
}
