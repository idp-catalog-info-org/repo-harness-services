/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.k8s.client;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ExplanationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.gcp.client.GcpClient;
import io.harness.idp.common.IdpAppConfig;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.k8s.exception.ClusterCredentialsNotFoundException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.GcpAccessTokenSupplier;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.retry.RetryHelper;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.services.container.Container;
import com.google.api.services.container.model.Cluster;
import com.google.api.services.container.model.MasterAuth;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.Certificate;
import software.amazon.awssdk.services.eks.model.DescribeClusterRequest;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class K8sApiClient implements K8sClient {
  private static final String RESTARTED_ANNOTATION = "harness.io/idp-restarted-at";
  private static final String LOCK_FORMAT = "%s/Deployment";
  private final IdpAppConfig idpAppConfig;
  private final KubernetesHelperService kubernetesHelperService;
  private final Retry retry = buildRetryAndRegisterListeners();
  private final ExecutorService executorService;
  private final DataStore<StoredCredential> store;
  private final Clock clock;
  private final GcpClient gcpClient;
  private final ApiClient primaryApiClient;
  private final ApiClient failoverApiClient;
  private final ResourceLocker resourceLocker;

  @Inject
  public K8sApiClient(@Named("idpAppConfig") IdpAppConfig idpAppConfig, KubernetesHelperService kubernetesHelperService,
      @Named("K8sFailoverSync") ExecutorService executorService, DataStore<StoredCredential> store, Clock clock,
      GcpClient gcpClient, ResourceLocker resourceLocker) {
    this.idpAppConfig = idpAppConfig;
    this.kubernetesHelperService = kubernetesHelperService;
    this.executorService = executorService;
    this.store = store;
    this.clock = clock;
    this.gcpClient = gcpClient;
    this.primaryApiClient = getApiClient(getPrimaryKubernetesConfigs(), idpAppConfig.getPrimary().getWorkloadIdentity(),
        idpAppConfig.getPrimary().getEksAuth());
    this.failoverApiClient = getApiClient(getFailoverKubernetesConfigs(),
        idpAppConfig.getFailover().getWorkloadIdentity(), idpAppConfig.getFailover().getEksAuth());
    this.resourceLocker = resourceLocker;
  }

  @Override
  public void updateSecretData(
      String accountIdentifier, String namespace, String secretName, Map<String, byte[]> data) {
    boolean idpApiRestartEnabled = idpAppConfig.isApiBasedRestart();
    log.info("apiBasedRestart config enabled: {}", idpApiRestartEnabled);
    if (idpApiRestartEnabled) {
      restartDeploymentPod(namespace);
    }
    // This we are doing as in provisioning if backstage-env will not be created idp-app will not start.
    // Here restart will happen using the api but we will parallelly update the secret this will not cause any issue.
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> updateSecretDataInternal(namespace, secretName, data, false));
      }
    } catch (RuntimeException e) {
      log.warn("Update secret data failover sync failed for namespace {}, secretName {}", namespace, secretName, e);
    }
    updateSecretDataInternal(namespace, secretName, data, true);
  }

  @Override
  public V1ConfigMap updateConfigMapData(
      String namespace, String configMapName, Map<String, String> data, boolean replace) {
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> updateConfigMapDataInternal(namespace, configMapName, data, replace, false));
      }
    } catch (RuntimeException e) {
      log.warn("Update config map data failover sync failed for namespace {}, configMapName {}", namespace,
          configMapName, e);
    }
    return updateConfigMapDataInternal(namespace, configMapName, data, replace, true);
  }

  @Override
  public V1PodList getBackstagePodList(String namespace) {
    CoreV1Api coreV1Api = new CoreV1Api(this.primaryApiClient);
    final Supplier<V1PodList> podListSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return coreV1Api.listNamespacedPod(
            namespace, null, null, null, null, idpAppConfig.getPodLabel(), null, null, null, null, null, false);
      } catch (ApiException e) {
        String err = format("Could not check for pod status. Code: %s, message: %s", e.getCode(), e.getMessage());
        log.error(err, e);
        throw new InvalidRequestException(err, e, USER);
      }
    });
    return podListSupplier.get();
  }

  @Override
  public void removeSecretData(String namespace, String secretName, List<String> envNames) {
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> removeSecretDataInternal(namespace, secretName, envNames, false));
      }
    } catch (RuntimeException e) {
      log.warn("Remove secret data failover sync failed for namespace {}, secretName {}", namespace, secretName, e);
    }
    removeSecretDataInternal(namespace, secretName, envNames, true);
  }

  @Override
  public void deleteConfigMap(String accountIdentifier, String namespace, String configMapName) {
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> deleteConfigMapInternal(namespace, configMapName, false));
      }
    } catch (RuntimeException e) {
      log.warn("Delete config map failover sync failed for namespace {}, config map {}", namespace, configMapName, e);
    }
    deleteConfigMapInternal(namespace, configMapName, true);
  }

  private void restartDeploymentPod(String namespace) {
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> restartDeploymentPodInternal(namespace, idpAppConfig.getPodLabel(), false));
      }
    } catch (RuntimeException e) {
      log.warn("Restart deployment pod for failover failed for namespace {}", namespace, e);
    }
    restartDeploymentPodInternal(namespace, idpAppConfig.getPodLabel(), true);
  }

  private void restartDeploymentPodInternal(String namespace, String podLabel, boolean isPrimary) {
    ApiClient apiClient = getApiClient(isPrimary);
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    try (AcquiredLock lock = resourceLocker.acquireLock(String.format(LOCK_FORMAT, namespace))) {
      if (lock == null) {
        log.info("IDP App in {} namespace is already being restarted by another operation. This restart can be ignored",
            namespace);
        return;
      }
      V1Deployment deployment = getDeployment(appsV1Api, namespace, podLabel);
      updateRestartAnnotation(appsV1Api, deployment);
    }
  }

  private V1Deployment getDeployment(AppsV1Api appsV1Api, String namespace, String podLabel) {
    V1DeploymentList deploymentList;
    try {
      deploymentList = appsV1Api.listNamespacedDeployment(
          namespace, null, null, null, null, podLabel, null, null, null, null, null, null);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage =
          format("Failed to list deployments in namespace: %s with labelSelector %s. Code: %s, message: %s", namespace,
              podLabel, ex.getCode(), ex.getMessage());
      throw new InvalidRequestException(errorMessage, ex, USER);
    }
    return deploymentList.getItems().stream().findFirst().orElseThrow(
        ()
            -> new UnexpectedException(
                String.format("No deployments found for namespace: %s and labelSelector: %s", namespace, podLabel)));
  }

  private void updateRestartAnnotation(AppsV1Api appsV1Api, V1Deployment deployment) {
    long currentTimeMillis = Instant.now().toEpochMilli();
    V1ObjectMeta metadata = deployment.getMetadata();
    Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
    annotations = annotations != null ? annotations : new HashMap<>();
    annotations.put(RESTARTED_ANNOTATION, String.valueOf(currentTimeMillis));

    try {
      appsV1Api.replaceNamespacedDeployment(
          metadata.getName(), metadata.getNamespace(), deployment, null, null, null, null);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = format("Failed to update %s/Deployment/%s. Code: %s, message: %s", metadata.getNamespace(),
          metadata.getName(), ex.getCode(), ex.getMessage());
      throw new InvalidRequestException(errorMessage, ex, USER);
    }

    log.info("Annotation {} updated in pod spec in namespace {} with: {}", RESTARTED_ANNOTATION,
        metadata.getNamespace(), currentTimeMillis);
  }

  private void deleteConfigMapInternal(String namespace, String configMapName, boolean isPrimary) {
    if (isBlank(configMapName)) {
      throw new InvalidRequestException("ConfigMap name is empty");
    }
    ApiClient apiClient = getApiClient(isPrimary);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    try {
      coreV1Api.deleteNamespacedConfigMap(configMapName, namespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = format("Failed to delete %s/ConfigMap/%s. Code: %s, message: %s", namespace, configMapName,
          ex.getCode(), ex.getMessage());
      throw new InvalidRequestException(errorMessage, ex, USER);
    }
  }

  private V1Secret getSecret(CoreV1Api coreV1Api, String namespace, String secretName) {
    if (isBlank(secretName)) {
      throw new InvalidRequestException("Secret name is empty");
    }
    final Supplier<V1Secret> secretSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return coreV1Api.readNamespacedSecret(secretName, namespace, null);
      } catch (ApiException e) {
        if (e.getCode() == 404) {
          return createSecret(coreV1Api, namespace, secretName);
        }
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage = format(
            "Failed to get %s/Secret/%s. Code: %s, message: %s", namespace, secretName, ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(errorMessage, ex, USER);
      }
    });
    return secretSupplier.get();
  }

  private V1Secret createSecret(CoreV1Api coreV1Api, String namespace, String secretName) {
    if (isBlank(secretName)) {
      throw new InvalidRequestException("Secret name is empty");
    }
    final Supplier<V1Secret> secretSupplier = Retry.decorateSupplier(retry, () -> {
      V1Secret secret = new V1Secret();
      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setNamespace(namespace);
      metadata.setName(secretName);
      secret.setMetadata(metadata);
      try {
        return coreV1Api.createNamespacedSecret(namespace, secret, null, null, null, null);
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage = format("Failed to create %s/Secret/%s. Code: %s, message: %s", namespace, secretName,
            ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(errorMessage, ex, USER);
      }
    });
    return secretSupplier.get();
  }

  private V1ConfigMap createConfigMap(CoreV1Api coreV1Api, String namespace, String configMapName) {
    if (isBlank(configMapName)) {
      throw new InvalidRequestException("ConfigMap name is empty");
    }
    final Supplier<V1ConfigMap> config = Retry.decorateSupplier(retry, () -> {
      V1ConfigMap configMap = new V1ConfigMap();
      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setNamespace(namespace);
      metadata.setName(configMapName);
      configMap.setMetadata(metadata);
      try {
        return coreV1Api.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage = format("Failed to create %s/ConfigMap/%s. Code: %s, message: %s", namespace,
            configMapName, ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(errorMessage, ex, USER);
      }
    });
    return config.get();
  }

  private void replaceSecret(CoreV1Api coreV1Api, V1Secret secret) {
    String secretName = Objects.requireNonNull(secret.getMetadata()).getName();
    String namespace = Objects.requireNonNull(secret.getMetadata()).getNamespace();
    final Supplier<V1Secret> secretSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return coreV1Api.replaceNamespacedSecret(secretName, namespace, secret, null, null, null, null);
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String secretDef = secret.getMetadata() != null && isNotEmpty(secret.getMetadata().getName())
            ? format("%s/Secret/%s", secret.getMetadata().getNamespace(), secret.getMetadata().getName())
            : "ConfigMap";
        String message =
            format("Failed to replace %s. Code: %s, message: %s", secretDef, ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(message, ex, USER);
      }
    });
    secretSupplier.get();
  }

  private V1ConfigMap getConfigMap(CoreV1Api coreV1Api, String namespace, String configMapName) {
    if (isBlank(configMapName)) {
      throw new InvalidRequestException("Config Map name is empty");
    }
    final Supplier<V1ConfigMap> configMapSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
      } catch (ApiException e) {
        if (e.getCode() == 404) {
          return createConfigMap(coreV1Api, namespace, configMapName);
        }
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String message = format("Failed to get %s/ConfigMap/%s. Code: %s, message: %s", namespace, configMapName,
            ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(message, ex, USER);
      }
    });
    return configMapSupplier.get();
  }

  private V1ConfigMap replaceConfigMap(CoreV1Api coreV1Api, V1ConfigMap configMap) {
    String configMapName = Objects.requireNonNull(configMap.getMetadata()).getName();
    String namespace = Objects.requireNonNull(configMap.getMetadata()).getNamespace();
    final Supplier<V1ConfigMap> configMapSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return coreV1Api.replaceNamespacedConfigMap(configMapName, namespace, configMap, null, null, null, null);
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String configMapDef = configMap.getMetadata() != null && isNotEmpty(configMap.getMetadata().getName())
            ? format("%s/ConfigMap/%s", configMap.getMetadata().getNamespace(), configMap.getMetadata().getName())
            : "ConfigMap";
        String message =
            format("Failed to replace %s. Code: %s, message: %s", configMapDef, ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(message, ex, USER);
      }
    });
    return configMapSupplier.get();
  }

  @Override
  public V1Namespace createNamespace(String namespace) {
    createNamespaceForFailoverCluster(namespace);
    return createNamespaceInternal(namespace, true);
  }

  @Override
  public void createNamespaceForFailoverCluster(String namespace) {
    try {
      if (idpAppConfig.getFailoverSync() != null && idpAppConfig.getFailoverSync().isEnabled()) {
        executorService.submit(() -> createNamespaceInternal(namespace, false));
      }
    } catch (RuntimeException e) {
      log.warn("Namespace creation in failover sync failed for namespace {}", namespace, e);
    }
  }

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }

  private Pair<KubernetesConfig, KubernetesConfig> getKubernetesConfigs() {
    // TODO: Once load balance setup is done, we can do DNS lookup to decide which is primary and which is failover.
    return new Pair<>(getPrimaryKubernetesConfigs(), getFailoverKubernetesConfigs());
  }

  private KubernetesConfig getPrimaryKubernetesConfigs() {
    return getKubernetesConfig(idpAppConfig.getPrimary().getMasterUrl(), idpAppConfig.getPrimary().getToken(),
        idpAppConfig.getPrimary().getCaCrt());
  }

  private KubernetesConfig getFailoverKubernetesConfigs() {
    return getKubernetesConfig(idpAppConfig.getFailover().getMasterUrl(), idpAppConfig.getFailover().getToken(),
        idpAppConfig.getFailover().getCaCrt());
  }

  private KubernetesConfig getKubernetesConfig(String masterUrl, String token, String caCrt) {
    if (StringUtils.isBlank(masterUrl)) {
      throw new ClusterCredentialsNotFoundException("Master URL not found");
    }
    if (StringUtils.isBlank(token)) {
      throw new ClusterCredentialsNotFoundException("Service Account Token not found");
    }
    KubernetesConfigBuilder builder = KubernetesConfig.builder();
    builder.masterUrl(masterUrl);
    builder.serviceAccountTokenSupplier(() -> token);

    if (StringUtils.isNotBlank(caCrt)) {
      builder.clientCert(caCrt.toCharArray());
    }
    return builder.build();
  }

  private void updateSecretDataInternal(
      String namespace, String secretName, Map<String, byte[]> data, boolean isPrimary) {
    ApiClient apiClient = getApiClient(isPrimary);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret = getSecret(coreV1Api, namespace, secretName);
    Map<String, byte[]> secretData = secret.getData();
    secretData = secretData == null ? new HashMap<>() : secretData;
    secretData.putAll(data);
    secret.setData(secretData);
    replaceSecret(coreV1Api, secret);
  }

  private V1ConfigMap updateConfigMapDataInternal(
      String namespace, String configMapName, Map<String, String> data, boolean replace, boolean isPrimary) {
    ApiClient apiClient = getApiClient(isPrimary);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1ConfigMap configMap = getConfigMap(coreV1Api, namespace, configMapName);
    Map<String, String> configMapData = configMap.getData();
    configMapData = configMapData == null ? new HashMap<>() : configMapData;
    if (replace) {
      configMapData.clear();
    }
    configMapData.putAll(data);
    configMap.setData(configMapData);
    return replaceConfigMap(coreV1Api, configMap);
  }

  private void removeSecretDataInternal(String namespace, String secretName, List<String> envNames, boolean isPrimary) {
    ApiClient apiClient = getApiClient(isPrimary);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret = getSecret(coreV1Api, namespace, secretName);
    Map<String, byte[]> secretData = secret.getData();
    if (secretData != null) {
      envNames.forEach(secretData::remove);
    }
    secret.setData(secretData);
    replaceSecret(coreV1Api, secret);
    log.info(
        "Successfully removed [{}] environment secrets from [{}/Secret/{}]", envNames.size(), namespace, secretName);
  }

  private V1Namespace createNamespaceInternal(String namespace, boolean isPrimary) {
    ApiClient apiClient = getApiClient(isPrimary);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    final Supplier<V1Namespace> namespaceSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        V1Namespace v1Namespace = new V1Namespace();
        V1ObjectMeta v1ObjectMeta = new V1ObjectMeta();
        v1ObjectMeta.setName(namespace);
        v1Namespace.setMetadata(v1ObjectMeta);
        return coreV1Api.createNamespace(v1Namespace, null, null, null, null);
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        if (ex.getCode() == 409) {
          log.info("Namespace {} already exists", namespace);
          return null;
        }
        String message =
            format("Failed to create namespace %s. Code: %s, message: %s", namespace, ex.getCode(), ex.getMessage());
        throw new InvalidRequestException(message, ex, USER);
      }
    });
    return namespaceSupplier.get();
  }

  private GcpAccessTokenSupplier createForDefaultAppCredentials() {
    Function<String, GoogleCredential> mapper = unused -> {
      try {
        return GoogleCredential.getApplicationDefault();
      } catch (IOException e) {
        throw new ExplanationException("Cannot instantiate deserialize google credentials.", e);
      }
    };
    return new GcpAccessTokenSupplier(null, mapper, store, clock, null);
  }

  KubernetesConfig getKubeConfig(KubernetesConfig kubernetesConfig,
      IdpAppConfig.WorkloadIdentity workloadIdentityConfig, IdpAppConfig.EksAuth eksAuthConfig) {
    if (eksAuthConfig != null && eksAuthConfig.isEnabled()) {
      log.info("Using EKS auth for cluster: {}", eksAuthConfig.getClusterName());
      return getKubeConfigFromEks(eksAuthConfig);
    }
    log.info("useWorkloadIdentity: {}", workloadIdentityConfig.isEnabled());
    if (!workloadIdentityConfig.isEnabled()) {
      return kubernetesConfig;
    }
    return getKubeConfigFromGcp(kubernetesConfig, workloadIdentityConfig);
  }

  private KubernetesConfig getKubeConfigFromGcp(
      KubernetesConfig kubernetesConfig, IdpAppConfig.WorkloadIdentity workloadIdentity) {
    KubernetesConfigBuilder kubernetesConfigBuilder = KubernetesConfig.builder();
    Cluster cluster = getCluster(workloadIdentity);
    if (cluster == null) {
      return kubernetesConfigBuilder.build();
    }
    kubernetesConfigBuilder.masterUrl("https://" + cluster.getEndpoint() + "/").namespace("default");
    MasterAuth masterAuth = cluster.getMasterAuth();
    if (isNotEmpty(kubernetesConfig.getMasterUrl())) {
      kubernetesConfigBuilder.masterUrl(kubernetesConfig.getMasterUrl());
    }
    if (masterAuth.getUsername() != null) {
      kubernetesConfigBuilder.username(masterAuth.getUsername().toCharArray());
    }
    if (masterAuth.getPassword() != null) {
      kubernetesConfigBuilder.password(masterAuth.getPassword().toCharArray());
    }
    if (masterAuth.getClusterCaCertificate() != null) {
      kubernetesConfigBuilder.caCert(masterAuth.getClusterCaCertificate().toCharArray());
    }
    if (masterAuth.getClientCertificate() != null) {
      kubernetesConfigBuilder.clientCert(masterAuth.getClientCertificate().toCharArray());
    }
    if (masterAuth.getClientKey() != null) {
      kubernetesConfigBuilder.clientKey(masterAuth.getClientKey().toCharArray());
    }
    kubernetesConfigBuilder.authType(KubernetesClusterAuthType.GCP_OAUTH);
    Supplier<String> tokenSupplier = createForDefaultAppCredentials();
    kubernetesConfigBuilder.serviceAccountTokenSupplier(tokenSupplier);
    return kubernetesConfigBuilder.build();
  }

  private Cluster getCluster(IdpAppConfig.WorkloadIdentity workloadIdentity) {
    try {
      Container gkeContainerService = gcpClient.getGkeContainerService();
      return gkeContainerService.projects()
          .locations()
          .clusters()
          .get("projects/" + workloadIdentity.getProject() + "/locations/" + workloadIdentity.getLocation()
              + "/clusters/" + workloadIdentity.getCluster())
          .execute();
    } catch (Exception e) {
      log.error("Could not get the cluster information. Project: {}, location: {}, cluster: {}",
          workloadIdentity.getProject(), workloadIdentity.getLocation(), workloadIdentity.getCluster(), e);
    }
    return null;
  }

  private KubernetesConfig getKubeConfigFromEks(IdpAppConfig.EksAuth eksAuth) {
    String region = eksAuth.getRegion();
    if (StringUtils.isBlank(region)) {
      region = System.getenv("AWS_REGION");
    }
    if (StringUtils.isBlank(region)) {
      region = System.getenv("AWS_DEFAULT_REGION");
    }
    if (StringUtils.isBlank(region)) {
      throw new ClusterCredentialsNotFoundException("EKS region not configured and AWS_REGION env var not set");
    }

    try (EksClient eksClient = EksClient.builder()
                                   .region(Region.of(region))
                                   .credentialsProvider(DefaultCredentialsProvider.create())
                                   .build()) {
      DescribeClusterResponse response =
          eksClient.describeCluster(DescribeClusterRequest.builder().name(eksAuth.getClusterName()).build());

      String endpoint = response.cluster().endpoint();
      Certificate ca = response.cluster().certificateAuthority();
      String caCertData = (ca != null) ? ca.data() : null;

      log.info("EKS cluster {} endpoint resolved: {}", eksAuth.getClusterName(), endpoint);

      EksTokenSupplier tokenSupplier = new EksTokenSupplier(eksAuth.getClusterName());

      KubernetesConfigBuilder builder = KubernetesConfig.builder();
      builder.masterUrl(endpoint);
      builder.namespace("default");
      if (StringUtils.isNotBlank(caCertData)) {
        builder.caCert(caCertData.toCharArray());
      }
      // GCP_OAUTH is used here despite this being EKS auth. The auth type only controls
      // routing in ApiClientFactoryImpl: GCP_OAUTH triggers GkeTokenAuthentication, which
      // is a per-request interceptor that calls supplier.get() on every K8s API call.
      // This is needed because EKS tokens expire in ~15 min and must be refreshed.
      // All other auth types use AccessTokenAuthentication (static, one-shot token).
      // Using GCP_OAUTH avoids changes to shared 960/970 modules.
      builder.authType(KubernetesClusterAuthType.GCP_OAUTH);
      builder.serviceAccountTokenSupplier(tokenSupplier);
      return builder.build();
    } catch (Exception e) {
      log.error("Failed to describe EKS cluster {}. Region: {}", eksAuth.getClusterName(), region, e);
      throw new ClusterCredentialsNotFoundException(
          "Could not get EKS cluster info for " + eksAuth.getClusterName(), e);
    }
  }

  private ApiClient getApiClient(KubernetesConfig kubernetesConfig,
      IdpAppConfig.WorkloadIdentity workloadIdentityConfig, IdpAppConfig.EksAuth eksAuthConfig) {
    KubernetesConfig kubeConfig = getKubeConfig(kubernetesConfig, workloadIdentityConfig, eksAuthConfig);
    log.info("Master URL: {}", kubeConfig.getMasterUrl());
    return kubernetesHelperService.getApiClient(kubeConfig);
  }

  private ApiClient getApiClient(boolean isPrimary) {
    return isPrimary ? this.primaryApiClient : this.failoverApiClient;
  }
}
