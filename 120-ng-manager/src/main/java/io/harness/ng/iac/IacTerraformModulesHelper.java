/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iac;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;
import static io.harness.delegate.beans.connector.utils.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.utils.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.utils.ConnectorType.HARNESS;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;
import static org.joda.time.Minutes.minutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.TagFilterParameters;
import io.harness.beans.entities.TerraformGitTags;
import io.harness.beans.entities.TerraformModule;
import io.harness.beans.request.TerraformRequestTaskParams;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.connector.CiIntegrationStageUtils;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.GitConfigDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.common.dtos.GitTagDTO;
import io.harness.gitsync.common.dtos.GitTagsResponseDTO;
import io.harness.gitsync.common.dtos.GitTerraformModuleDTO;
import io.harness.gitsync.common.service.GitSyncConnectorService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.iacmserviceclient.IACMServiceClient;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextTaskWrapper;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.security.JWTTokenServiceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@OwnedBy(IACM)
@Singleton
@Slf4j
public class IacTerraformModulesHelper {
  private static final String ERR_MESSAGE = "message";
  private static final String ERR_STACK_TRACE = "stackTrace";
  private static final String ROOT_MODULE = "root";

  @Inject ScmFacilitatorService scmFacilitatorService;
  @Inject IACMServiceClient iacmServiceClient;
  @Inject NextGenConfiguration nextGenConfiguration;
  @Inject ScmGitProviderHelper scmGitProviderHelper;
  @Inject GitSyncConnectorService gitSyncConnectorService;
  @Inject private SecretUtils secretUtils;
  @Inject DecryptionHelper decryptionHelper;

  public void sendListTags(String accountIdentifier, String connectorOrg, String connectorProject, String connectorRef,
      String repoName, PageRequest pageRequest, String name, String system, String org, String project) {
    TagFilterParameters tagFilterParameters = TagFilterParameters.builder().build();

    String iacToken = generateIacJWTToken(accountIdentifier, org, project);

    Runnable task = () -> {
      Map<String, String> error = null;
      GitTagsResponseDTO gitTagsResponseDTO = null;
      try {
        gitTagsResponseDTO = scmFacilitatorService.listTags(accountIdentifier, connectorOrg, connectorProject,
            connectorRef, repoName, pageRequest, tagFilterParameters, null, false);
      } catch (Exception e) {
        log.error("Failed to get terraform module List tags for connector={}, repoName={}", connectorRef, repoName, e);
        error = generateError(e);
      }

      RetryPolicy<Response> retryPolicy = getRetryPolicy(
          format("[Attempt {} failed call to post terraform List tags for connector=%s", connectorRef),
          format("Failed to post terraform module List tags for connector=%s after retrying {} times", connectorRef));
      List<String> tags;
      if (gitTagsResponseDTO == null) {
        tags = new ArrayList<>();
      } else {
        tags = emptyIfNull(gitTagsResponseDTO.getTags()).stream().map(GitTagDTO::getName).collect(Collectors.toList());
      }

      log.info("Sending tags={} for connector={}, repoName={} to iac-server", tags, connectorRef, repoName);

      TerraformGitTags reqBody = TerraformGitTags.builder()
                                     .account(accountIdentifier)
                                     .name(name)
                                     .system(system)
                                     .tags(tags)
                                     .error(error)
                                     .build();

      Failsafe.with(retryPolicy)
          .get(()
                   -> iacmServiceClient
                          .postTerraformModuleListTags(name, system, reqBody, iacToken, accountIdentifier, org, project)
                          .execute());
    };

    GlobalContext globalContext = GlobalContextManager.obtainGlobalContextCopy();
    GlobalContextTaskWrapper globalContextTaskWrapper =
        GlobalContextTaskWrapper.builder().task(task).context(globalContext).build();
    // Initiate a daemon Thread that will take care of time-consuming operation in order to unblock the req/resp thread.
    // GC will automatically release memory allocated when thread finishes the job
    Thread daemonThread = new Thread(globalContextTaskWrapper);
    daemonThread.setDaemon(true);
    daemonThread.setName("terraform-module-list-tags-task");
    daemonThread.start();
  }

  public void sendTerraformModule(String accountIdentifier, String connectorOrg, String connectorProject,
      String connectorRef, String repoName, String[] gitTag, String path, String name, String system, String org,
      String project) {
    String iacToken = generateIacJWTToken(accountIdentifier, org, project);

    try {
      Runnable task = () -> {
        Map<String, Map<String, GitTerraformModuleDTO>> wrapMap;
        Map<String, GitTerraformModuleDTO> moduleMap;

        try {
          final ScmConnector scmConnector = gitSyncConnectorService.getScmConnectorForCloning(
              accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName);

          char[] sshKey = extractSHHSecret(accountIdentifier, scmConnector);
          String domain = extractDomain(repoName, scmConnector);
          Integer port = extractPort(repoName, scmConnector);
          wrapMap = scmFacilitatorService.getTerraformModule(TerraformRequestTaskParams.builder()
                                                                 .accountIdentifier(accountIdentifier)
                                                                 .orgIdentifier(connectorOrg)
                                                                 .projectIdentifier(connectorProject)
                                                                 .connectorRef(connectorRef)
                                                                 .repoName(repoName)
                                                                 .gitTag(gitTag)
                                                                 .path(path)
                                                                 .domain(domain)
                                                                 .port(port)
                                                                 .sshKey(new String(sshKey))
                                                                 .build());
        } catch (Exception e) {
          handleGetTerraformModuleFails(
              e, accountIdentifier, connectorRef, name, system, gitTag, iacToken, org, project);
          throw e;
        }

        for (Map.Entry<String, Map<String, GitTerraformModuleDTO>> it : wrapMap.entrySet()) {
          moduleMap = it.getValue();
          GitTerraformModuleDTO rootModuleDTO = moduleMap.get(ROOT_MODULE);
          if (rootModuleDTO != null) {
            sendMetadataToIacServer(accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName, path,
                name, system, ROOT_MODULE, iacToken, rootModuleDTO, org, project);
          }
          moduleMap.remove(ROOT_MODULE);

          for (Map.Entry<String, GitTerraformModuleDTO> entry : moduleMap.entrySet()) {
            sendMetadataToIacServer(accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName, path,
                name, system, entry.getKey(), iacToken, entry.getValue(), org, project);
          }
        }
      };

      GlobalContext globalContext = GlobalContextManager.obtainGlobalContextCopy();
      GlobalContextTaskWrapper globalContextTaskWrapper =
          GlobalContextTaskWrapper.builder().task(task).context(globalContext).build();
      // Initiate a daemon Thread that will take care of time-consuming operation in order to unblock the req/resp
      // thread. GC will automatically release memory allocated when thread finishes the job
      Thread daemonThread = new Thread(globalContextTaskWrapper);
      daemonThread.setDaemon(true);
      daemonThread.setName("terraform-module-task");
      daemonThread.start();
    } catch (Exception e) {
      log.error(
          "Failed to get terraform module for connector={}, gitTags={}", connectorRef, Arrays.toString(gitTag), e);
    }
  }

  public void sendTerraformModule(String accountIdentifier, String connectorOrg, String connectorProject,
      String connectorRef, String repoName, String[] gitTag, String[] path, String name, String system, String org,
      String project) {
    String iacToken = generateIacJWTToken(accountIdentifier, org, project);

    try {
      Runnable task = () -> {
        Map<String, Map<String, GitTerraformModuleDTO>> wrapMap;
        Map<String, GitTerraformModuleDTO> moduleMap;

        try {
          final ScmConnector scmConnector = gitSyncConnectorService.getScmConnectorForCloning(
              accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName);
          char[] sshKey = extractSHHSecret(accountIdentifier, scmConnector);
          String domain = extractDomain(repoName, scmConnector);
          Integer port = extractPort(repoName, scmConnector);
          wrapMap = scmFacilitatorService.getTerraformModule(TerraformRequestTaskParams.builder()
                                                                 .accountIdentifier(accountIdentifier)
                                                                 .orgIdentifier(connectorOrg)
                                                                 .projectIdentifier(connectorProject)
                                                                 .connectorRef(connectorRef)
                                                                 .repoName(repoName)
                                                                 .gitTag(gitTag)
                                                                 .paths(path)
                                                                 .domain(domain)
                                                                 .port(port)
                                                                 .sshKey(new String(sshKey))
                                                                 .build());
        } catch (Exception e) {
          handleGetTerraformModuleFails(
              e, accountIdentifier, connectorRef, name, system, gitTag, iacToken, org, project);
          throw e;
        }

        for (Map.Entry<String, Map<String, GitTerraformModuleDTO>> it : wrapMap.entrySet()) {
          moduleMap = it.getValue();
          GitTerraformModuleDTO rootModuleDTO = moduleMap.get(ROOT_MODULE);
          if (rootModuleDTO != null) {
            sendMetadataToIacServer(accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName, "", name,
                system, ROOT_MODULE, iacToken, rootModuleDTO, org, project);
          }
          moduleMap.remove(ROOT_MODULE);

          for (Map.Entry<String, GitTerraformModuleDTO> entry : moduleMap.entrySet()) {
            sendMetadataToIacServer(accountIdentifier, connectorOrg, connectorProject, connectorRef, repoName, "", name,
                system, entry.getKey(), iacToken, entry.getValue(), org, project);
          }
        }
      };

      GlobalContext globalContext = GlobalContextManager.obtainGlobalContextCopy();
      GlobalContextTaskWrapper globalContextTaskWrapper =
          GlobalContextTaskWrapper.builder().task(task).context(globalContext).build();
      // Initiate a daemon Thread that will take care of time-consuming operation in order to unblock the req/resp
      // thread. GC will automatically release memory allocated when thread finishes the job
      Thread daemonThread = new Thread(globalContextTaskWrapper);
      daemonThread.setDaemon(true);
      daemonThread.setName("terraform-module-task");
      daemonThread.start();
    } catch (Exception e) {
      log.error(
          "Failed to get terraform module for connector={}, gitTags={}", connectorRef, Arrays.toString(gitTag), e);
    }
  }

  private void handleGetTerraformModuleFails(Exception e, String accountIdentifier, String connectorRef, String name,
      String system, String[] gitTag, String iacToken, String org, String project) {
    Map<String, String> error = generateError(e);
    for (String tag : gitTag) {
      RetryPolicy<Response> retryPolicy =
          getRetryPolicy(format("[handleGetTerraformModuleFails {} failed call to post terraform module for "
                                 + "connector=%s, gitTag=%s, submodule=%s {} {} ",
                             connectorRef, tag, ROOT_MODULE),
              format("handleGetTerraformModuleFails failed to post terraform module for connector=%s, gitTag=%s, "
                      + "submodule=%s after retrying {} times {} {}",
                  connectorRef, tag, ROOT_MODULE));

      TerraformModule reqBody = TerraformModule.builder()
                                    .account(accountIdentifier)
                                    .name(name)
                                    .system(system)
                                    .gitTag(tag)
                                    .version(tag)
                                    .metadata("")
                                    .submoduleName("")
                                    .downloadUrl("")
                                    .repoUrl("")
                                    .submodulePaths(Collections.emptyList())
                                    .error(error)
                                    .build();

      Failsafe.with(retryPolicy)
          .get(()
                   -> iacmServiceClient
                          .postTerraformModule(name, system, tag, reqBody, iacToken, accountIdentifier, org, project)
                          .execute());
    }
  }

  private RetryPolicy<Response> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<Response>()
        .handle(Exception.class)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .withMaxAttempts(3)
        .handleResultIf(result -> !result.isSuccessful())
        .onFailedAttempt(event
            -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure(), event.getLastResult()))
        .onFailure(event -> log.warn(failureMessage, event.getAttemptCount(), event.getFailure(), event.getResult()));
  }

  private String generateIacJWTToken(String accountId, String orgId, String projectId) {
    return JWTTokenServiceUtils.generateJWTToken(
        ImmutableMap.of("accountId", accountId, "orgId", orgId, "projectId", projectId),
        minutes(120).toStandardDuration().getMillis(), nextGenConfiguration.getIacmClientConfig().getGlobalToken());
  }

  private String getRepositoryURL(ScmConnector scmConnector) {
    if (scmConnector.getConnectorType() == ConnectorType.GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) scmConnector;
      if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
        return convertSSHToHTTPS(gitConfigDTO.getUrl());
      } else {
        return scmConnector.getGitConnectionUrl();
      }
    } else if (scmConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) scmConnector;
      if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
        return convertSSHToHTTPS(gitConfigDTO.getUrl());
      } else {
        return scmConnector.getGitConnectionUrl();
      }
    } else if (scmConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) scmConnector;
      if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
        return convertSSHToHTTPS(gitConfigDTO.getUrl());
      } else {
        return scmConnector.getGitConnectionUrl();
      }
    } else if (scmConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) scmConnector;
      if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
        return convertSSHToHTTPS(gitConfigDTO.getUrl());
      } else {
        return scmConnector.getGitConnectionUrl();
      }
    } else if (scmConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      if (gitConfigDTO.getGitAuthType() == GitAuthType.SSH) {
        return convertSSHToHTTPS(gitConfigDTO.getUrl());
      } else {
        return scmConnector.getGitConnectionUrl();
      }
    } else if (scmConnector.getConnectorType() == HARNESS) {
      return scmConnector.getGitConnectionUrl();
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + scmConnector.getConnectorType());
    }
  }

  @VisibleForTesting
  String getTagDownloadURL(ScmConnector scmConnector, String hash, String path) {
    if (scmConnector == null) {
      throw new InvalidArgumentsException(format("Invalid connector provided %s", scmConnector), USER_SRE);
    }
    String hostName;
    boolean isSSH = false;

    try {
      URL url;
      if (scmConnector.getConnectorType() == HARNESS) {
        // The harness code url comes from the configuration, for example
        // for prod1 it will be https://git.harness.io
        var baseUrls = nextGenConfiguration.getBaseUrls();
        if (baseUrls == null) {
          throw new IllegalStateException("Base URLs configuration is missing");
        }
        var hcUrl = baseUrls.getHarnessCodeGitUrl();
        if (hcUrl == null) {
          throw new IllegalStateException("Harness Code Git URL is not configured");
        }
        url = new URL(hcUrl);
        hostName = url.getHost();
      } else if (scmConnector.getConnectorType() == ConnectorType.BITBUCKET) {
        if (scmGitProviderHelper.getRepoOwner(scmConnector).equals("scm")) {
          if (GitClientHelper.isHTTPProtocol(scmConnector.getUrl())) {
            url = new URL(scmConnector.getUrl());
            hostName = format("%s/scm", url.getHost());
          } else {
            String[] parts = scmConnector.getUrl().split(":");
            String hostAndPath = parts[0].substring("git@".length());
            String[] hostParts = hostAndPath.split("/");
            hostName = hostParts[0];
            isSSH = true;
          }
        } else {
          if (GitClientHelper.isHTTPProtocol(scmConnector.getUrl())) {
            url = new URL(scmConnector.getUrl());
            hostName = url.getHost();
          } else {
            String[] parts = scmConnector.getUrl().split(":");
            String hostAndPath = parts[0].substring("git@".length());
            String[] hostParts = hostAndPath.split("/");
            hostName = hostParts[0];
            isSSH = true;
          }
        }
      } else if (scmConnector.getConnectorType() == AZURE_REPO) {
        if (GitClientHelper.isHTTPProtocol(scmConnector.getUrl())) {
          String repoName = scmGitProviderHelper.getRepoName(scmConnector);
          String orgName =
              scmGitProviderHelper.getRepoOwner(scmConnector) + "/" + repoName.substring(0, repoName.indexOf("/_git/"));
          url = new URL(scmConnector.getUrl());
          hostName = url.getHost() + "/" + orgName + "/_git";
        } else {
          // SSH format: git@ssh.dev.azure.com:v3/{organization}/{project}/{repository}
          String[] parts = scmConnector.getUrl().split(":");
          String hostPart = parts[0].substring("git@".length());

          String pathPart = parts[1];

          String[] transformedPath = pathPart.split("/"); // org

          hostName = hostPart + "/" + transformedPath[0] + "/" + transformedPath[1] + "/" + transformedPath[2];
          isSSH = true;
        }
      } else {
        if (GitClientHelper.isHTTPProtocol(scmConnector.getUrl())) {
          url = new URL(scmConnector.getUrl());
          hostName = url.getHost();
        } else {
          String[] parts = scmConnector.getUrl().split(":");
          String hostAndPath = parts[0].substring("git@".length());
          String[] hostParts = hostAndPath.split("/");
          hostName = hostParts[0];
          isSSH = true;
        }
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    String slug = scmGitProviderHelper.getSlug(scmConnector);

    if (isEmpty(slug)) {
      throw new InvalidArgumentsException(format("Invalid connector definition provided %s", scmConnector), USER_SRE);
    }

    switch (scmConnector.getConnectorType()) {
      case GITHUB, GITLAB, BITBUCKET, AZURE_REPO -> {
        return getTagDownloadUrl(hostName, slug, hash, path, isSSH);
      }
      case HARNESS -> {
        // Harness slug comes with acount/reponame/+ but this does not work with Terraform.
        // Terraform seems to need account/reponame.git
        slug = slug.replace("/+", ".git");
        return getTagDownloadUrl(hostName, slug, hash, path, isSSH);
      }
      default -> {
        return "Not Implemented";
      }
    }
  }

  private String convertSSHToHTTPS(String sshUrl) {
    String[] parts = sshUrl.split(":");
    String hostAndPath = parts[0].substring("git@".length());
    String repoPath = parts[1].replaceFirst("\\.git$", "");

    String[] hostParts = hostAndPath.split("/");
    String host = hostParts[0];
    return "https://" + host + "/" + repoPath + ".git";
  }

  private String getTagDownloadUrl(String hostName, String slug, String hash, String path, Boolean ssh) {
    if (isEmpty(path)) {
      if (ssh) {
        return format("git::ssh://git@%s/%s?ref=%s", hostName, slug, hash);
      }
      return format("git::https://%s/%s?ref=%s", hostName, slug, hash);
    }
    if (ssh) {
      return format("git::ssh://git@%s/%s//%s?ref=%s", hostName, slug, path, hash);
    }
    return format("git::https://%s/%s//%s?ref=%s", hostName, slug, path, hash);
  }

  // git::https://dev.azure.com/terraform-aws-sqs?ref=ddf0393d6d289c4654b45429680360153356da7b
  private Map<String, String> generateError(Exception e) {
    String stackTrace = "";
    StackTraceElement[] stackTraceElements = e.getStackTrace();
    if (isNotEmpty(stackTraceElements)) {
      stackTrace = Arrays.stream(stackTraceElements).toList().toString();
    }

    return Map.of(ERR_MESSAGE, e.getMessage(), ERR_STACK_TRACE, stackTrace);
  }
  private void sendMetadataToIacServer(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, String path, String name, String system, String submoduleName,
      String iacToken, GitTerraformModuleDTO module, String org, String project) {
    Map<String, String> error = null;
    String downloadURL = null;
    String repoUrl = null;
    String metadata = null;
    List<String> submodulePaths = Collections.emptyList();
    List<String> examplesPaths = Collections.emptyList();

    String version = module.getGitTag();

    try {
      metadata = module.getMetadata();
      submodulePaths = module.getSubmodulePaths();
      examplesPaths = module.getExamplesPaths();
      ScmConnector scmConnector = gitSyncConnectorService.getScmConnectorForNullableRepo(
          accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
      downloadURL = getTagDownloadURL(scmConnector, module.getHeadHash(), path);

      repoUrl = getRepositoryURL(scmConnector);

    } catch (Exception e) {
      log.error("sendTerraformModule failed to get terraform module for connector={}, gitTag={}, submodule={}",
          connectorRef, version, submoduleName, e);
      error = generateError(e);
    }

    RetryPolicy<Response> retryPolicy =
        getRetryPolicy(format("[sendTerraformModule Attempt {} failed call to post terraform module for connector=%s, "
                               + "gitTag=%s, submodule=%s",
                           connectorRef, version, submoduleName),
            format("sendTerraformModule Failed to post terraform module for connector=%s, gitTag=%s, submodule=%s "
                    + "after retrying {} times",
                connectorRef, version, submoduleName));
    if (submoduleName.equals(ROOT_MODULE)) {
      submoduleName = "";
    }

    log.info("Sending downloadUrl=`{}`, repoUrl=`{}`, metadataExists={}, submodulePaths={}, isError={}  "
            + "for connector={}, repoName={}, gitTag={}, submodule={}, name={}, system={}, submodulePath={}  to "
            + "iac-server",
        downloadURL, repoUrl, isNotEmpty(metadata), submodulePaths, isNotEmpty(error), connectorRef, repoName, version,
        submoduleName, name, system, submoduleName);

    TerraformModule reqBody = TerraformModule.builder()
                                  .account(accountIdentifier)
                                  .name(name)
                                  .system(system)
                                  .gitTag(version)
                                  .version(version)
                                  .submoduleName(submoduleName)
                                  .downloadUrl(downloadURL)
                                  .repoUrl(repoUrl)
                                  .metadata(metadata)
                                  .submodulePaths(submodulePaths)
                                  .error(error)
                                  .examplesPaths(examplesPaths)
                                  .build();

    Failsafe.with(retryPolicy)
        .get(()
                 -> iacmServiceClient
                     .postTerraformModule(name, system, version, reqBody, iacToken, accountIdentifier, org, project)
                     .execute());

    // send README
    if (module.getReadme() != null && !module.getReadme().isBlank()) {
      retryPolicy = getRetryPolicy(format("[sendTerraformModule Attempt {} failed call to post README terraform module "
                                           + "for connector=%s, gitTag=%s, submodule=%s",
                                       connectorRef, version, submoduleName),
          format("sendTerraformModule Failed to post README terraform module for connector=%s, gitTag=%s, submodule=%s "
                  + "after retrying {} times",
              connectorRef, version, submoduleName));

      String finalSubmoduleName = submoduleName;
      Failsafe.with(retryPolicy)
          .get(()
                   -> iacmServiceClient
                       .postTerraformModuleReadme(name, system, version, module.getReadme(), finalSubmoduleName,
                           iacToken, accountIdentifier, org, project)
                       .execute());
    }
  }

  @VisibleForTesting
  char[] extractSHHSecret(String accountIdentifier, ScmConnector scmConnector) {
    if (scmConnector.getConnectorType() == ConnectorType.GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) scmConnector;
      return retrieveGitHubSSHKey(gitConfigDTO, accountIdentifier);
    } else if (scmConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) scmConnector;
      return retrieveAzureRepoSSHKey(gitConfigDTO, accountIdentifier);
    } else if (scmConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) scmConnector;
      return retrieveGitlabSSHKey(gitConfigDTO, accountIdentifier);
    } else if (scmConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) scmConnector;
      return retrieveBitbucketSSHKey(gitConfigDTO, accountIdentifier);
    } else if (scmConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      return retrieveGitSSHKey(gitConfigDTO, accountIdentifier);
    } else if (scmConnector.getConnectorType() == HARNESS) {
      log.info("Harness connector type is not supported for SSH key retrieval");
      // harness code does not support ssh so far
      return new char[0];
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + scmConnector.getConnectorType());
    }
  }

  @VisibleForTesting
  String extractDomain(String repoName, ScmConnector scmConnector) {
    if (scmConnector.getConnectorType() == ConnectorType.GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      return GitClientHelper.getGitSCM(gitUrl);
    } else if (scmConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) scmConnector;
      String gitUrl = CiIntegrationStageUtils.getGitURL(
          repoName, mapToGitConnectionType(gitConfigDTO.getConnectionType()), scmConnector.getUrl());
      return GitClientHelper.getGitSCM(gitUrl);
    } else if (scmConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      return GitClientHelper.getGitSCM(gitUrl);
    } else if (scmConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      return GitClientHelper.getGitSCM(gitUrl);
    } else if (scmConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getGitConnectionType(), scmConnector.getUrl());
      return GitClientHelper.getGitSCM(gitUrl);
    } else if (scmConnector.getConnectorType() == HARNESS) {
      log.trace("Harness connector type is not supported for domain retrieval");
      return "";
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + scmConnector.getConnectorType());
    }
  }

  @VisibleForTesting
  Integer extractPort(String repoName, ScmConnector scmConnector) {
    if (scmConnector.getConnectorType() == ConnectorType.GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      Integer port = GitClientHelper.getGitPort(gitUrl);
      if (port == -1) {
        return 22;
      } else {
        return port;
      }
    } else if (scmConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) scmConnector;
      String gitUrl = CiIntegrationStageUtils.getGitURL(
          repoName, mapToGitConnectionType(gitConfigDTO.getConnectionType()), scmConnector.getUrl());
      Integer port = GitClientHelper.getGitPort(gitUrl);
      if (port == -1) {
        return 22;
      } else {
        return port;
      }
    } else if (scmConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      Integer port = GitClientHelper.getGitPort(gitUrl);
      if (port == -1) {
        return 22;
      } else {
        return port;
      }
    } else if (scmConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getConnectionType(), scmConnector.getUrl());
      Integer port = GitClientHelper.getGitPort(gitUrl);
      if (port == -1) {
        return 22;
      } else {
        return port;
      }
    } else if (scmConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      String gitUrl =
          CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getGitConnectionType(), scmConnector.getUrl());
      Integer port = GitClientHelper.getGitPort(gitUrl);
      if (port == -1) {
        return 22;
      } else {
        return port;
      }
    } else if (scmConnector.getConnectorType() == HARNESS) {
      log.trace("Harness connector type is not supported for port retrieval");
      return 22;
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + scmConnector.getConnectorType());
    }
  }

  private char[] retrieveGitHubSSHKey(GithubConnectorDTO gitConfigDTO, String accountIdentifier) {
    GithubAuthenticationDTO githubAuthenticationDTO = gitConfigDTO.getAuthentication();
    if (githubAuthenticationDTO.getAuthType() == GitAuthType.HTTP) {
      return new char[0];
    }
    GithubSshCredentialsDTO githubSshCredentialsDTO =
        (GithubSshCredentialsDTO) githubAuthenticationDTO.getCredentials();
    SecretRefData key = githubSshCredentialsDTO.getSshKeyRef();
    SSHKeyDetails sshKeyDetails =
        secretUtils.getSshKey(BaseNGAccess.builder().accountIdentifier(accountIdentifier).build(), key);
    DecryptableEntity decryptableEntity =
        decryptionHelper.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
    SecretRefData keyData = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
    return keyData.getDecryptedValue();
  }

  private char[] retrieveAzureRepoSSHKey(AzureRepoConnectorDTO gitConfigDTO, String accountIdentifier) {
    AzureRepoAuthenticationDTO azureRepoAuthenticationDTO = gitConfigDTO.getAuthentication();
    if (azureRepoAuthenticationDTO.getAuthType() == GitAuthType.HTTP) {
      return new char[0];
    }
    AzureRepoSshCredentialsDTO azureRepoSshCredentialsDTO =
        (AzureRepoSshCredentialsDTO) azureRepoAuthenticationDTO.getCredentials();
    SecretRefData key = azureRepoSshCredentialsDTO.getSshKeyRef();
    SSHKeyDetails sshKeyDetails =
        secretUtils.getSshKey(BaseNGAccess.builder().accountIdentifier(accountIdentifier).build(), key);
    DecryptableEntity decryptableEntity =
        decryptionHelper.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
    SecretRefData keyData = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
    return keyData.getDecryptedValue();
  }

  private char[] retrieveGitlabSSHKey(GitlabConnectorDTO gitConfigDTO, String accountIdentifier) {
    GitlabAuthenticationDTO gitlabAuthenticationDTO = gitConfigDTO.getAuthentication();
    if (gitlabAuthenticationDTO.getAuthType() == GitAuthType.HTTP) {
      return new char[0];
    }
    GitlabSshCredentialsDTO gitlabSshCredentialsDTO =
        (GitlabSshCredentialsDTO) gitlabAuthenticationDTO.getCredentials();
    SecretRefData key = gitlabSshCredentialsDTO.getSshKeyRef();
    SSHKeyDetails sshKeyDetails =
        secretUtils.getSshKey(BaseNGAccess.builder().accountIdentifier(accountIdentifier).build(), key);
    DecryptableEntity decryptableEntity =
        decryptionHelper.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
    SecretRefData keyData = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
    return keyData.getDecryptedValue();
  }

  private char[] retrieveBitbucketSSHKey(BitbucketConnectorDTO gitConfigDTO, String accountIdentifier) {
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO = gitConfigDTO.getAuthentication();
    if (bitbucketAuthenticationDTO.getAuthType() == GitAuthType.HTTP) {
      return new char[0];
    }
    BitbucketSshCredentialsDTO bitbucketSshCredentialsDTO =
        (BitbucketSshCredentialsDTO) bitbucketAuthenticationDTO.getCredentials();
    SecretRefData key = bitbucketSshCredentialsDTO.getSshKeyRef();
    SSHKeyDetails sshKeyDetails =
        secretUtils.getSshKey(BaseNGAccess.builder().accountIdentifier(accountIdentifier).build(), key);
    DecryptableEntity decryptableEntity =
        decryptionHelper.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
    SecretRefData keyData = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
    return keyData.getDecryptedValue();
  }

  private char[] retrieveGitSSHKey(GitConfigDTO gitConfigDTO, String accountIdentifier) {
    GitAuthenticationDTO gitAuthenticationDTO = gitConfigDTO.getGitAuth();
    if (gitConfigDTO.getGitAuthType() == GitAuthType.HTTP) {
      return new char[0];
    }
    GitSSHAuthenticationDTO gitSSHAuthenticationDTO = (GitSSHAuthenticationDTO) gitAuthenticationDTO;
    SecretRefData key = gitSSHAuthenticationDTO.getEncryptedSshKey();
    SSHKeyDetails sshKeyDetails =
        secretUtils.getSshKey(BaseNGAccess.builder().accountIdentifier(accountIdentifier).build(), key);
    DecryptableEntity decryptableEntity =
        decryptionHelper.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
    SecretRefData keyData = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
    return keyData.getDecryptedValue();
  }
}
