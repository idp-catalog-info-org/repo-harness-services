/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.entities;

import static io.harness.idp.common.CommonUtils.getFilePathForGitIntegrations;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.exception.UnexpectedException;
import io.harness.git.GitClientHelper;
import io.harness.idp.common.Constants;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "OnboardingFlowKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.IDP)
@Entity(value = "onboardingFlow", noClassnameStored = true)
@Document("onboardingFlow")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingFlowEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  @Id private String id;
  @FdUniqueIndex @NotNull private String accountIdentifier;
  @NotNull private SkippedAt skippedAt;
  private Set<WriteDetails> writeDetails;
  private boolean importedSampleEntityDefinition;
  private int numberOfCDEntitiesImported;
  private Set<String> importedCDEntities;
  private Map<String, Set<String>> importedCDEntitiesRef;
  private ImmutableTriple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>>
      entitiesToImport;
  private long registerEntitiesOnIdpAt;
  private List<String> entitiesToRegisterOnIdp;
  @NotNull private String currentStatus;
  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public enum SkippedAt { GET_STARTED, WITHOUT_INTEGRATION, WITH_INTEGRATION_NO_IMPORT, NA }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldNameConstants(innerTypeName = "OnboardingFlowWriteDetailsKeys")
  public static class WriteDetails {
    @NotNull private String connectorIdentifier;
    @NotNull private String repositoryUrl;
    @NotNull private String branch;
    @NotNull private String path;
  }

  public static WriteDetails from(GitIntegrationRequest gitIntegrationRequest) {
    return OnboardingFlowEntity.WriteDetails.builder()
        .connectorIdentifier(gitIntegrationRequest.getConnectorIdentifier())
        .repositoryUrl(gitIntegrationRequest.getWriteValidationDetails().getRepository())
        .branch(gitIntegrationRequest.getWriteValidationDetails().getBranch())
        .path(gitIntegrationRequest.getWriteValidationDetails().getPath())
        .build();
  }

  public long calculateRegisterEntitiesOnIdpAt() {
    return this.getRegisterEntitiesOnIdpAt() == Long.MAX_VALUE ? System.currentTimeMillis() + 90000
                                                               : this.getRegisterEntitiesOnIdpAt();
  }

  public String idpCatalogSourceLocation(
      String gitIntegrationType, GitIntegrationRequest gitIntegrationRequest, String filePath) {
    String repositoryUrl;

    WriteValidationDetails validationDetails = gitIntegrationRequest.getWriteValidationDetails();
    if (validationDetails == null) {
      throw new UnexpectedException("WriteValidationDetails cannot be null");
    }

    switch (gitIntegrationType) {
      case Constants.AZURE_REPO:
        repositoryUrl = GitClientHelper.getCompleteHTTPRepoUrlForAzureRepoSaas(validationDetails.getRepository());
        break;

      case Constants.BITBUCKET_CLOUD:
        repositoryUrl = GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(validationDetails.getRepository());
        break;

      case Constants.BITBUCKET_SERVER:
        repositoryUrl = StringUtils.removeEnd(validationDetails.getRepository(), SLASH_DELIMITER);
        repositoryUrl = StringUtils.removeEnd(repositoryUrl, ".git");
        repositoryUrl = repositoryUrl.replace("/scm/", "/projects/");
        try {
          URL url = new URL(repositoryUrl);
          String name = url.getPath().split("/")[2];
          repositoryUrl = repositoryUrl.replace("/" + name + "/", "/" + name + "/repos/");
        } catch (Exception e) {
          throw new UnexpectedException("Error in preparing catalog source location URL", e);
        }
        break;

      case Constants.GITHUB:
        repositoryUrl = GitClientHelper.getCompleteHTTPUrlForGithub(validationDetails.getRepository());
        break;

      case Constants.GITLAB:
        repositoryUrl = GitClientHelper.getCompleteHTTPUrlForGitLab(validationDetails.getRepository());
        break;

      case Constants.HARNESS:
        repositoryUrl = validationDetails.getRepository();
        break;

      default:
        throw new UnexpectedException("GitIntegrationType " + gitIntegrationType + " not supported yet");
    }

    return getFilePathForGitIntegrations(
        gitIntegrationType, repositoryUrl, validationDetails.getBranch(), validationDetails.getPath(), filePath);
  }

  public static GitIntegrationRequest from(WriteDetails writeDetails) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(writeDetails.getConnectorIdentifier());
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(writeDetails.getRepositoryUrl());
    writeValidationDetails.setBranch(writeDetails.getBranch());
    writeValidationDetails.setPath(writeDetails.getPath());
    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    return gitIntegrationRequest;
  }
}
