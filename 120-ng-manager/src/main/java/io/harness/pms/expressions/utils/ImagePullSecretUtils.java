/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.k8s.model.ImageDetails.ImageDetailsBuilder;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.ID_TOKEN_CONTEXT.PIPELINE_EXECUTION;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GithubPackagesArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.artifactory.utils.ArtifactoryOidcHelperUtility;
import io.harness.cdng.aws.utils.AwsOidcConnectorUtility;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ModuleConstants;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.connector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureSecretType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConstant;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.source.ArtifactSourceConstants;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.DockerConfigJsonSecretFunctor;
import io.harness.expression.ImageSecretFunctor;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.BaseUrls;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.oidc.artifactory.ArtifactoryOidcTokenExchangeDetails;
import io.harness.oidc.artifactory.credential.ArtifactoryOidcCredentialUtility;
import io.harness.oidc.artifactory.dto.ArtifactoryOidcTokenResponseDTO;
import io.harness.oidc.aws.delegate.AwsOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SimpleEncryption;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.utils.FunctorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class ImagePullSecretUtils {
  @Inject private EcrImagePullSecretHelper ecrImagePullSecretHelper;
  @Inject private AzureHelperService azureHelperService;

  @Inject private HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;
  @Inject private ArtifactoryOidcHelperUtility artifactoryOidcHelperUtility;
  @Inject private ArtifactoryOidcCredentialUtility artifactoryOidcCredentialUtility;
  @Inject private GCPImagePullSecretHelper gcpImagePullSecretHelper;
  @Inject @Named(ModuleConstants.CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  @Inject private AwsOidcConnectorUtility awsOidcConnectorUtility;

  @Inject private NextGenConfiguration nextGenConfiguration;
  @Inject private BaseUrls baseUrls;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private ScopeInfoService scopeInfoService;

  private static final String ACR_DUMMY_DOCKER_USERNAME = "00000000-0000-0000-0000-000000000000";
  private static final String ECR_IMAGE_PULL_REQUEST_SWEEPING_OUTPUT_VAR_NAME =
      "ecr_image_pull_secret_sweepingOutput_var_name";

  public String getImagePullSecret(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetails imageDetails = getImageDetails(artifactOutcome, ambiance);
    boolean useSweepingOutSecretFunctor = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.CDS_USE_SWEEPING_OUTPUT_SECRET_FUNCTOR_FOR_IMAGE_PULL_SECRET.name());
    boolean useSingleQuotes =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name());
    if (isNotEmpty(imageDetails.getRegistryUrl()) && isNotBlank(imageDetails.getUsername())) {
      return getArtifactRegistryCredentials(imageDetails, useSingleQuotes, useSweepingOutSecretFunctor);
    } else if (isNotEmpty(imageDetails.getRegistryUrl()) && isNotBlank(imageDetails.getUsernameRef())) {
      return getArtifactRegistryCredentialsFromUsernameRef(imageDetails, useSingleQuotes, useSweepingOutSecretFunctor);
    }
    return "";
  }

  public String getDockerConfigJson(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetails imageDetails = getImageDetails(artifactOutcome, ambiance);
    boolean useSweepingOutSecretFunctor = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.CDS_USE_SWEEPING_OUTPUT_SECRET_FUNCTOR_FOR_IMAGE_PULL_SECRET.name());
    boolean useSingleQuotes =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name());
    if (StringUtils.isNoneBlank(imageDetails.getRegistryUrl(), imageDetails.getUsername())) {
      return getDockerConfigJson(imageDetails, useSingleQuotes, useSweepingOutSecretFunctor);
    } else if (StringUtils.isNoneBlank(imageDetails.getRegistryUrl(), imageDetails.getUsernameRef())) {
      return getDockerConfigJsonFromUsernameRef(imageDetails, useSingleQuotes, useSweepingOutSecretFunctor);
    }
    return "";
  }

  private ImageDetails getImageDetails(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetailsBuilder imageDetailsBuilder = ImageDetails.builder();
    switch (artifactOutcome.getArtifactType()) {
      case ArtifactSourceConstants.DOCKER_REGISTRY_NAME:
        getImageDetailsFromDocker((DockerArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AMAZON_S3_NAME:
        getImageDetailsFromS3((S3ArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GCR_NAME:
        getImageDetailsFromGcr((GcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ECR_NAME:
        getImageDetailsFromEcr((EcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.NEXUS3_REGISTRY_NAME:
        getImageDetailsFromNexus((NexusArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME:
        getImageDetailsFromArtifactory((ArtifactoryArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ACR_NAME:
        getImageDetailsFromAcr((AcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.JENKINS_NAME:
        getBuildDetailsFromJenkins((JenkinsArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GITHUB_PACKAGES_NAME:
        getImageDetailsFromGithubPackages(
            (GithubPackagesArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME:
        getImageDetailsFromGar((GarArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AZURE_ARTIFACTS_NAME:
        getImageDetailsFromAzureArtifacts((AzureArtifactsOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AMI_ARTIFACTS_NAME:
        getImageDetailsForAMI((AMIArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.HARNESS_ARTIFACT_REGISTRY_NAME:
        getImageDetailsForHarnessArtifactRegistry(imageDetailsBuilder, ambiance);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactOutcome.getArtifactType()));
    }
    return imageDetailsBuilder.build();
  }

  private void getImageDetailsForAMI(
      AMIArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    AwsConnectorDTO connectorConfig = (AwsConnectorDTO) connectorDTO.getConnectorConfig();

    if (connectorConfig.getCredential() != null && connectorConfig.getCredential().getConfig() != null
        && connectorConfig.getCredential().getAwsCredentialType() == MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO credentials = (AwsManualConfigSpecDTO) connectorConfig.getCredential().getConfig();

      String passwordRef = credentials.getSecretKeyRef().toSecretRefStringValue();

      if (credentials.getAccessKeyRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getAccessKeyRef().toSecretRefStringValue(), ambiance));
      }

      imageDetailsBuilder.username(credentials.getAccessKey());

      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private void getImageDetailsForHarnessArtifactRegistry(ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    Principal principal = harnessArtifactRegistryHelper.getPrincipal(ambiance);
    Map<String, String> claims = principal.getJWTClaims();
    final long THREE_DAYS = TimeUnit.MILLISECONDS.convert(3, TimeUnit.DAYS);
    claims.put("accountId", AmbianceUtils.getAccountId(ambiance));

    String token = JWTTokenServiceUtils.generateJWTToken(
        claims, THREE_DAYS, nextGenConfiguration.getNextGenConfig().getHarnessRegistryServiceSecret());

    imageDetailsBuilder.username(principal.getName());
    imageDetailsBuilder.password(token);
    AccountDTO accountDTO = harnessArtifactRegistryHelper.getAccountDTO(AmbianceUtils.getAccountId(ambiance));
    String subdomainURL = accountDTO.getSubdomainURL();
    if (StringUtils.isBlank(subdomainURL)
        || !harnessArtifactRegistryHelper.isVanityEnabledForHarnessRegistry(ambiance)) {
      imageDetailsBuilder.registryUrl(
          harnessArtifactRegistryHelper.getRegistryHost(baseUrls.getHarnessArtifactRegistryUrl()));
    } else {
      imageDetailsBuilder.registryUrl(harnessArtifactRegistryHelper.getRegistryHost(subdomainURL));
    }
  }

  private void getImageDetailsFromS3(
      S3ArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    AwsConnectorDTO connectorConfig = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getCredential() != null && connectorConfig.getCredential().getConfig() != null
        && connectorConfig.getCredential().getAwsCredentialType() == MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO credentials = (AwsManualConfigSpecDTO) connectorConfig.getCredential().getConfig();
      String passwordRef = credentials.getSecretKeyRef().toSecretRefStringValue();
      if (credentials.getAccessKeyRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getAccessKeyRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getAccessKey());
      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private void getImageDetailsFromAzureArtifacts(
      AzureArtifactsOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        (AzureArtifactsConnectorDTO) connectorDTO.getConnectorConfig();

    String password = "";

    if (azureArtifactsConnectorDTO.getAuth() != null && azureArtifactsConnectorDTO.getAuth().getCredentials() != null) {
      AzureArtifactsCredentialsDTO httpDTO = azureArtifactsConnectorDTO.getAuth().getCredentials();

      if (httpDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
        AzureArtifactsTokenDTO azureArtifactsHttpCredentialsSpecDTO = httpDTO.getCredentialsSpec();

        password = new String(azureArtifactsHttpCredentialsSpecDTO.getTokenRef().getDecryptedValue());

      } else {
        throw new InvalidRequestException("Please select the Auth type as Username-Token");
      }
    }

    if (password == null) {
      throw new InvalidRequestException("The token is null");
    }

    imageDetailsBuilder.passwordRef(password);

    imageDetailsBuilder.registryUrl(azureArtifactsConnectorDTO.getAzureArtifactsUrl());
  }

  private void getImageDetailsFromGithubPackages(
      GithubPackagesArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorDTO.getConnectorConfig();

    String username = "";
    String password = "";

    if (githubConnectorDTO.getAuthentication() != null
        && githubConnectorDTO.getAuthentication().getCredentials() != null) {
      if (githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
        GithubHttpCredentialsDTO httpDTO =
            (GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();

        if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
          GithubUsernamePasswordDTO githubUsernamePasswordDTO =
              (GithubUsernamePasswordDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernamePasswordDTO.getUsername(), githubUsernamePasswordDTO.getUsernameRef());

        } else if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
          GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());
        }
      }
    }

    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();

    if (githubApiAccessDTO == null) {
      throw new InvalidRequestException("Please enable the API Access for the Github Connector");
    }

    GithubApiAccessType githubApiAccessType = githubApiAccessDTO.getType();

    if (githubApiAccessType == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      if (githubTokenSpecDTO.getTokenRef() != null) {
        password = EmptyPredicate.isNotEmpty(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            ? new String(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            : getPasswordExpression(githubTokenSpecDTO.getTokenRef().toSecretRefStringValue() == null
                      ? ""
                      : githubTokenSpecDTO.getTokenRef().toSecretRefStringValue(),
                  ambiance);

      } else {
        throw new InvalidRequestException("The token reference for the Github Connector is null");
      }

    } else {
      throw new InvalidRequestException("Please select the API Access auth type to Token");
    }

    imageDetailsBuilder.username(username);
    imageDetailsBuilder.passwordRef(password);
    imageDetailsBuilder.registryUrl("https://ghcr.io");
  }

  public static String getArtifactRegistryCredentials(
      ImageDetails imageDetails, boolean useSingleQuotes, boolean useSweepingOutSecretFunctor) {
    if (StringUtils.isAllBlank(imageDetails.getPasswordRef(), imageDetails.getPassword())) {
      return "";
    }
    if (imageDetails.getPassword() != null && useSweepingOutSecretFunctor) {
      String imagePullSec = ImageSecretFunctor.createAndEncodeImageSecret(
          imageDetails.getRegistryUrl(), imageDetails.getUsername(), imageDetails.getPassword());
      return getSweepingOutPutSecret(imagePullSec, useSingleQuotes, new SimpleEncryption());
    }
    if (imageDetails.getPassword() != null) {
      return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", \"" + imageDetails.getUsername() + "\", "
          + format("\"%s\"", imageDetails.getPassword()) + ")}";
    }
    return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", \"" + imageDetails.getUsername() + "\", "
        + imageDetails.getPasswordRef() + ")}";
  }

  public static String getArtifactRegistryCredentialsFromUsernameRef(
      ImageDetails imageDetails, boolean useSingleQuotes, boolean useSweepingOutSecretFunctor) {
    if (imageDetails.getPasswordRef() == null && imageDetails.getPassword() == null) {
      return "";
    }
    if (imageDetails.getPassword() != null && useSweepingOutSecretFunctor) {
      String imagePullSec = ImageSecretFunctor.createAndEncodeImageSecret(
          imageDetails.getRegistryUrl(), imageDetails.getUsernameRef(), imageDetails.getPassword());
      return getSweepingOutPutSecret(imagePullSec, useSingleQuotes, new SimpleEncryption());
    }
    if (imageDetails.getPassword() != null) {
      return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", " + imageDetails.getUsernameRef() + ", "
          + format("\"%s\"", imageDetails.getPassword()) + ")}";
    }
    return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", " + imageDetails.getUsernameRef() + ", "
        + imageDetails.getPasswordRef() + ")}";
  }

  public static String getDockerConfigJson(
      ImageDetails imageDetails, boolean useSingleQuotes, boolean useSweepingOutSecretFunctor) {
    if (imageDetails.getPassword() != null && useSweepingOutSecretFunctor) {
      String imagePullSec = DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
          imageDetails.getRegistryUrl(), imageDetails.getUsername(), imageDetails.getPassword());
      return getSweepingOutPutSecret(imagePullSec, useSingleQuotes, new SimpleEncryption());
    } else if (imageDetails.getPassword() != null) {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", \""
          + imageDetails.getUsername() + "\", " + format("\"%s\"", imageDetails.getPassword()) + ")}";
    } else {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", \""
          + imageDetails.getUsername() + "\", " + imageDetails.getPasswordRef() + ")}";
    }
  }

  public static String getDockerConfigJsonFromUsernameRef(
      ImageDetails imageDetails, boolean useSingleQuotes, boolean useSweepingOutSecretFunctor) {
    if (imageDetails.getPassword() != null && useSweepingOutSecretFunctor) {
      String imagePullSec = DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
          imageDetails.getRegistryUrl(), imageDetails.getUsernameRef(), imageDetails.getPassword());
      return getSweepingOutPutSecret(imagePullSec, useSingleQuotes, new SimpleEncryption());
    } else if (imageDetails.getPassword() != null) {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", \""
          + imageDetails.getUsernameRef() + "\", " + format("\"%s\"", imageDetails.getPassword()) + ")}";
    } else {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", "
          + imageDetails.getUsernameRef() + ", " + imageDetails.getPasswordRef() + ")}";
    }
  }

  private void getImageDetailsFromDocker(
      DockerArtifactOutcome dockerArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = dockerArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    DockerConnectorDTO connectorConfig = (DockerConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == DockerAuthType.USER_PASSWORD) {
      DockerUserNamePasswordDTO credentials = (DockerUserNamePasswordDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
      imageDetailsBuilder.registryUrl(connectorConfig.getDockerRegistryUrl());
    }
  }

  private void getImageDetailsFromGcr(
      GcrArtifactOutcome gcrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = gcrArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    GcpConnectorDTO connectorConfig = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
    String imageName = gcrArtifactOutcome.getRegistryHostname() + "/" + gcrArtifactOutcome.getImagePath();
    imageDetailsBuilder.registryUrl(imageName);
    imageDetailsBuilder.username("_json_key");
    if (connectorConfig.getCredential() != null
        && connectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO config = (GcpManualDetailsDTO) connectorConfig.getCredential().getConfig();
      imageDetailsBuilder.passwordRef(
          getPasswordExpression(config.getSecretKeyRef().toSecretRefStringValue(), ambiance));
    } else if (connectorConfig.getCredential() != null
        && GcpCredentialType.OIDC_AUTHENTICATION == connectorConfig.getCredential().getGcpCredentialType()) {
      imageDetailsBuilder.username("oauth2accesstoken");
      BaseNGAccess baseNGAccess = getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      List<EncryptedDataDetail> encryptionDetails =
          gcpImagePullSecretHelper.getEncryptionDetailsGCP(connectorConfig, baseNGAccess);
      GcpOidcTokenExchangeDetailsForDelegate gcpOidcTokenExchangeDetailsForDelegate =
          (GcpOidcTokenExchangeDetailsForDelegate) awsOidcConnectorUtility.getOidcTokenForConnector(
              ambiance, connectorDTO, PIPELINE_EXECUTION.toString());
      GcrArtifactDelegateRequest gcrRequest = ArtifactDelegateRequestUtils.getGcrDelegateRequest(
          gcrArtifactOutcome.getImagePath(), null, null, null, gcrArtifactOutcome.getRegistryHostname(), null,
          connectorConfig, encryptionDetails, gcpOidcTokenExchangeDetailsForDelegate, ArtifactSourceType.GCR);
      ArtifactTaskExecutionResponse artifactTaskExecutionResponseForAuthToken =
          gcpImagePullSecretHelper.executeSyncTaskGCR(
              gcrRequest, ArtifactTaskType.GET_AUTH_TOKEN, baseNGAccess, "GCR Get Auth-token failure due to error");
      String authToken =
          ((GcrArtifactDelegateResponse) artifactTaskExecutionResponseForAuthToken.getArtifactDelegateResponses().get(
               0))
              .getAuthToken();
      setPasswordFromAuthTokenGCP(imageDetailsBuilder, authToken);
    }
  }

  private void getImageDetailsFromGar(
      GarArtifactOutcome garArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = garArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorInfoDTO = getConnector(connectorRef, ambiance);
    GcpConnectorDTO connectorConfig = (GcpConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String imageName = garArtifactOutcome.getRegistryHostname() + "/" + garArtifactOutcome.getProject() + "/"
        + garArtifactOutcome.getPkg();
    imageDetailsBuilder.registryUrl(imageName);
    imageDetailsBuilder.username("_json_key");
    if (connectorConfig.getCredential() != null
        && connectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO config = (GcpManualDetailsDTO) connectorConfig.getCredential().getConfig();
      imageDetailsBuilder.passwordRef(
          getPasswordExpression(config.getSecretKeyRef().toSecretRefStringValue(), ambiance));
    } else if (connectorConfig.getCredential() != null
        && GcpCredentialType.OIDC_AUTHENTICATION == connectorConfig.getCredential().getGcpCredentialType()) {
      imageDetailsBuilder.username("oauth2accesstoken");
      BaseNGAccess baseNGAccess = getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      List<EncryptedDataDetail> encryptionDetails =
          gcpImagePullSecretHelper.getEncryptionDetailsGCP(connectorConfig, baseNGAccess);
      GcpOidcTokenExchangeDetailsForDelegate gcpOidcTokenExchangeDetailsForDelegate =
          (GcpOidcTokenExchangeDetailsForDelegate) awsOidcConnectorUtility.getOidcTokenForConnector(
              ambiance, connectorInfoDTO, PIPELINE_EXECUTION.toString());
      GarDelegateRequest garRequest = ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(
          garArtifactOutcome.getRegion(), garArtifactOutcome.getRepositoryName(), garArtifactOutcome.getProject(),
          garArtifactOutcome.getPkg(), garArtifactOutcome.getVersion(), null, connectorRef, connectorConfig,
          encryptionDetails, ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, -1, gcpOidcTokenExchangeDetailsForDelegate);
      ArtifactTaskExecutionResponse artifactTaskExecutionResponseForAuthToken =
          gcpImagePullSecretHelper.executeSyncTaskGAR(
              garRequest, ArtifactTaskType.GET_AUTH_TOKEN, baseNGAccess, "Ecr Get Auth-token failure due to error");
      String authToken =
          ((GarDelegateResponse) artifactTaskExecutionResponseForAuthToken.getArtifactDelegateResponses().get(0))
              .getAuthToken();
      setPasswordFromAuthTokenGCP(imageDetailsBuilder, authToken);
    }
  }

  private void setPasswordFromAuthTokenGCP(ImageDetailsBuilder imageDetailsBuilder, String authToken) {
    String decoded = new String(Base64.getDecoder().decode(authToken));
    String password = decoded.split(" ")[1];
    imageDetailsBuilder.password(password);
  }

  private void getImageDetailsFromEcr(
      EcrArtifactOutcome ecrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = ecrArtifactOutcome.getConnectorRef();
    BaseNGAccess baseNGAccess = ecrImagePullSecretHelper.getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    ConnectorInfoDTO connectorInfoDTO = getConnector(connectorRef, ambiance);
    AwsConnectorDTO connectorDTO = (AwsConnectorDTO) connectorInfoDTO.getConnectorConfig();
    List<EncryptedDataDetail> encryptionDetails =
        ecrImagePullSecretHelper.getEncryptionDetails(connectorDTO, baseNGAccess);

    AwsOidcTokenExchangeDetailsForDelegate awsOidcTokenExchangeDetailsForDelegate =
        (AwsOidcTokenExchangeDetailsForDelegate) awsOidcConnectorUtility.getOidcTokenForConnector(
            ambiance, connectorInfoDTO, PIPELINE_EXECUTION.toString());
    String oidcToken =
        awsOidcTokenExchangeDetailsForDelegate != null ? awsOidcTokenExchangeDetailsForDelegate.getOidcIdToken() : null;

    EcrArtifactDelegateRequest ecrRequest =
        ArtifactDelegateRequestUtils.getEcrDelegateRequest(ecrArtifactOutcome.getRegistryId(),
            ecrArtifactOutcome.getImagePath(), ecrArtifactOutcome.getTag(), null, null, ecrArtifactOutcome.getRegion(),
            connectorRef, connectorDTO, encryptionDetails, ArtifactSourceType.ECR, oidcToken);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponseForImageUrl = ecrImagePullSecretHelper.executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_IMAGE_URL, baseNGAccess, "Ecr Get image URL failure due to error");
    String imageUrl =
        ((EcrArtifactDelegateResponse) artifactTaskExecutionResponseForImageUrl.getArtifactDelegateResponses().get(0))
            .getImageUrl();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponseForAuthToken = ecrImagePullSecretHelper.executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_AUTH_TOKEN, baseNGAccess, "Ecr Get Auth-token failure due to error");
    String authToken =
        ((EcrArtifactDelegateResponse) artifactTaskExecutionResponseForAuthToken.getArtifactDelegateResponses().get(0))
            .getAuthToken();
    String decoded = new String(Base64.getDecoder().decode(authToken));
    String password = decoded.split(":")[1];
    imageDetailsBuilder.name(imageUrl)
        .sourceName(ArtifactSourceType.ECR.getDisplayName())
        .registryUrl(imageUrlToRegistryUrl(imageUrl))
        .username("AWS");
    imageDetailsBuilder.password(password);
  }

  private void getImageDetailsFromNexus(
      NexusArtifactOutcome nexusArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = nexusArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    NexusConnectorDTO connectorConfig = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == NexusAuthType.USER_PASSWORD) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
      if (isNotEmpty(nexusArtifactOutcome.getRegistryHostname())) {
        imageDetailsBuilder.registryUrl(nexusArtifactOutcome.getRegistryHostname());
      } else {
        imageDetailsBuilder.registryUrl(connectorConfig.getNexusServerUrl());
      }
    }
  }

  private void getImageDetailsFromArtifactory(ArtifactoryArtifactOutcome artifactoryArtifactOutcome,
      ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactoryArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    ArtifactoryConnectorDTO connectorConfig = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
      if (isNotEmpty(artifactoryArtifactOutcome.getRegistryHostname())) {
        imageDetailsBuilder.registryUrl(artifactoryArtifactOutcome.getRegistryHostname());
      } else {
        imageDetailsBuilder.registryUrl(connectorConfig.getArtifactoryServerUrl());
      }
    } else if (connectorConfig.getAuth() != null
        && connectorConfig.getAuth().getAuthType() == ArtifactoryAuthType.OIDC) {
      ArtifactoryOidcTokenExchangeDetails oidcDetails =
          artifactoryOidcHelperUtility.getArtifactoryOidcTokenExchangeDetailsForPipelineExecution(
              ambiance, connectorDTO);
      if (oidcDetails != null) {
        ArtifactoryOidcTokenResponseDTO tokenResponse =
            artifactoryOidcCredentialUtility.exchangeOidcToken(connectorConfig.getArtifactoryServerUrl(), oidcDetails);
        imageDetailsBuilder.username(tokenResponse.getUsername());
        imageDetailsBuilder.password(tokenResponse.getAccessToken());
      }
      if (isNotEmpty(artifactoryArtifactOutcome.getRegistryHostname())) {
        imageDetailsBuilder.registryUrl(artifactoryArtifactOutcome.getRegistryHostname());
      } else {
        imageDetailsBuilder.registryUrl(connectorConfig.getArtifactoryServerUrl());
      }
    }
  }

  private void getImageDetailsFromAcr(
      AcrArtifactOutcome acrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    try {
      String connectorRef = acrArtifactOutcome.getConnectorRef();
      ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
      AzureConnectorDTO connectorConfig = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
      imageDetailsBuilder.registryUrl(acrArtifactOutcome.getRegistry());
      if (connectorConfig.getCredential() != null
          && connectorConfig.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
        AzureManualDetailsDTO config = (AzureManualDetailsDTO) connectorConfig.getCredential().getConfig();
        if (config.getAuthDTO().getAzureSecretType() == AzureSecretType.SECRET_KEY) {
          log.info("Generating image pull credentials for SP with secret");
          imageDetailsBuilder.username(config.getClientId());
          imageDetailsBuilder.passwordRef(getPasswordExpression(
              ((AzureClientSecretKeyDTO) config.getAuthDTO().getCredentials()).getSecretKey().toSecretRefStringValue(),
              ambiance));
        } else {
          log.info(format(
              "Generating image pull credentials for SP with certificate. Fetching access token for clientId: %s",
              ((AzureManualDetailsDTO) connectorConfig.getCredential().getConfig()).getClientId()));
          generateAcrImageDetailsBuilder(ambiance, connectorConfig, acrArtifactOutcome, imageDetailsBuilder);
        }
      } else if (connectorConfig.getCredential() != null
          && connectorConfig.getCredential().getAzureCredentialType() == AzureCredentialType.INHERIT_FROM_DELEGATE) {
        AzureInheritFromDelegateDetailsDTO config =
            (AzureInheritFromDelegateDetailsDTO) connectorConfig.getCredential().getConfig();
        if (config.getAuthDTO() instanceof AzureMSIAuthUADTO) {
          log.info(
              format("Generating image pull credentials for User-Assigned MSI. Fetching access token for clientId: %s",
                  ((AzureMSIAuthUADTO) config.getAuthDTO()).getCredentials().getClientId()));
        } else {
          log.info("Generating image pull credentials for System-Assigned MSI");
        }
        generateAcrImageDetailsBuilder(ambiance, connectorConfig, acrArtifactOutcome, imageDetailsBuilder);
      } else {
        if (connectorConfig.getCredential() == null) {
          throw new Exception(format("Connector credentials are missing. Can not generate Image details."));
        }

        throw new Exception(
            format("AzureCredentialType [%s] is invalid", connectorConfig.getCredential().getAzureCredentialType()));
      }
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void generateAcrImageDetailsBuilder(Ambiance ambiance, AzureConnectorDTO connectorConfig,
      AcrArtifactOutcome acrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder) {
    log.info("Generating ACR image details");
    BaseNGAccess baseNGAccess = azureHelperService.getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
      SecurityContextBuilder.setContext(principal);
    }
    log.info(format("SecurityContext is %s service", principal.getName()));

    List<EncryptedDataDetail> encryptionDetails =
        azureHelperService.getEncryptionDetails(connectorConfig, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.CONTAINER_REGISTRY, acrArtifactOutcome.getRegistry());

    AzureTaskParams azureTaskParams = AzureTaskParams.builder()
                                          .azureTaskType(AzureTaskType.GET_ACR_TOKEN)
                                          .azureConnector(connectorConfig)
                                          .encryptionDetails(encryptionDetails)
                                          .delegateSelectors(connectorConfig.getDelegateSelectors())
                                          .additionalParams(additionalParams)
                                          .build();

    AzureAcrTokenTaskResponse accessTokenResponse = (AzureAcrTokenTaskResponse) azureHelperService.executeSyncTask(
        ambiance, azureTaskParams, baseNGAccess, "Azure get ACR access token task failure due to error");

    imageDetailsBuilder.username(ACR_DUMMY_DOCKER_USERNAME);
    imageDetailsBuilder.password(accessTokenResponse.getToken());
  }

  private void getBuildDetailsFromJenkins(
      JenkinsArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    JenkinsConnectorDTO connectorConfig = (JenkinsConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType().getDisplayName() == JenkinsConstant.USERNAME_PASSWORD) {
      JenkinsUserNamePasswordDTO credentials = (JenkinsUserNamePasswordDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.passwordRef(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private String imageUrlToRegistryUrl(String imageUrl) {
    String fullImageUrl = "https://" + imageUrl + (imageUrl.endsWith("/") ? "" : "/");
    fullImageUrl = fullImageUrl.substring(0, fullImageUrl.length() - 1);
    int index = fullImageUrl.lastIndexOf('/');
    return fullImageUrl.substring(0, index + 1);
  }

  private ConnectorInfoDTO getConnector(String connectorIdentifierRef, Ambiance ambiance) {
    try {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      // Assues the value in Ambiance is always updated
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      ScopeInfo scopeInfo = pmsFeatureFlagHelper.isEnabled(connectorRef.getAccountIdentifier(),
                                FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
          ? scopeInfoService.getScopeInfo(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
                connectorRef.getProjectIdentifier())
          : null;
      Optional<ConnectorResponseDTO> connectorDTO = scopeInfo != null
          ? connectorService.get(scopeInfo, connectorRef.getIdentifier())
          : connectorService.get(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
                connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef), WingsException.USER);
      }
      return connectorDTO.get().getConnector();
    } catch (Exception e) {
      log.error(format("Unable to get connector information : [%s] ", connectorIdentifierRef), e);
      throw new InvalidRequestException(format("Unable to get connector information : [%s] ", connectorIdentifierRef));
    }
  }

  private String getPasswordExpression(String passwordRef, Ambiance ambiance) {
    return FunctorUtils.getSecretExpression(ambiance.getExpressionFunctorToken(), passwordRef,
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()));
  }

  protected static String getSweepingOutPutSecret(
      String secret, boolean withSingleQuotes, SimpleEncryption encryption) {
    String encodedValue = EncodingUtils.encodeBase64(encryption.encrypt(secret.getBytes(StandardCharsets.UTF_8)));
    if (withSingleQuotes) {
      return "${sweepingOutputSecrets.obtain('" + ECR_IMAGE_PULL_REQUEST_SWEEPING_OUTPUT_VAR_NAME + "', '"
          + encodedValue + "')}";
    } else {
      return "${sweepingOutputSecrets.obtain(\"" + ECR_IMAGE_PULL_REQUEST_SWEEPING_OUTPUT_VAR_NAME + "\",\""
          + encodedValue + "\")}";
    }
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
