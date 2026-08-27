/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.provision.jobs;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.Constants.GLOBAL_DELEGATE_ACCOUNT_ID;
import static io.harness.idp.common.Constants.SMP_DEPLOYMENT_TYPE;

import io.harness.ModuleType;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class DefaultProvisioningForDevSpaces implements Managed {
  @Inject @Named("env") private String env;
  @Inject @Named("devSpaceDefaultBackstageNamespace") private String devSpaceDefaultBackstageNamespace;

  @Inject @Named("devSpaceDefaultAccountId") private String devSpaceDefaultAccountId;
  @Inject @Named("deploymentType") private String deploymentType;
  @Inject @Named("deploymentNamespace") private String deploymentNamespace;
  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject private AccountUtils accountUtils;
  @Inject @Named("dynamicConfigResolution") boolean dynamicConfigResolution;

  private ExecutorService executorService;
  private final NamespaceService namespaceService;
  private final ProvisionService provisionService;

  private static final String DEV_SPACE_ENV_TYPE = "dev-spaces";

  @Inject
  public DefaultProvisioningForDevSpaces(@Named("DefaultDevSpaceEnvProvisioner") ExecutorService executorService,
      NamespaceService namespaceService, ProvisionService provisionService) {
    this.executorService = executorService;
    this.namespaceService = namespaceService;
    this.provisionService = provisionService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("default-entry-creator-for-dev-space-env").build());
    executorService.submit(this::run);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    log.info("Starting default provisioning");
    log.info("Env: {} DevSpaceDefaultBackstageNamespace: {} DevSpaceDefaultAccountId: {} DeploymentType: {} "
            + "DeploymentNamespace: {}",
        env, devSpaceDefaultBackstageNamespace, devSpaceDefaultAccountId, deploymentType, deploymentNamespace);
    try {
      if (env.equals(DEV_SPACE_ENV_TYPE) && !devSpaceDefaultBackstageNamespace.isEmpty()
          && !devSpaceDefaultAccountId.isEmpty()) {
        log.info("Creating default provisioning for dev spaces");
        provision(devSpaceDefaultAccountId, devSpaceDefaultBackstageNamespace);
      } else if (deploymentType.equals(SMP_DEPLOYMENT_TYPE) && !isEmpty(deploymentNamespace)) {
        List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();
        log.info("Fetched total of {} NG Accounts", accountIdentifiers.size());
        accountIdentifiers.stream()
            .filter(accountIdentifier
                -> !accountIdentifier.equals(GLOBAL_ACCOUNT_ID) && !accountIdentifier.equals(GLOBAL_DELEGATE_ACCOUNT_ID)
                    && isLicenseConfigured(accountIdentifier))
            .forEach(accountIdentifier -> {
              log.info("Creating default provisioning for SMP in namespace: {} for account: {}", deploymentNamespace,
                  accountIdentifier);
              try {
                provision(accountIdentifier, deploymentNamespace);
              } catch (Exception e) {
                log.error("Default provisioning is unsuccessful for SMP in namespace: {} for account: {}",
                    deploymentNamespace, accountIdentifier, e);
              }
            });
      }
    } catch (Exception e) {
      log.error("Default provisioning is unsuccessful", e);
    }
    executorService.shutdownNow();
  }

  private void provision(String accountIdentifier, String namespace) {
    NamespaceEntity namespaceEntity =
        namespaceService.createDevSpaceEnvDefaultMappingEntry(accountIdentifier, namespace);
    if (deploymentType.equals(SMP_DEPLOYMENT_TYPE)) {
      namespaceService.updateIdpV2MigrationInfoAndSave(namespaceEntity, true);
    }
    provisionService.createBackstageBackendSecret(accountIdentifier);
    provisionService.createDefaultPermissions(accountIdentifier);
    if (!dynamicConfigResolution) {
      provisionService.createBackstageOverrideConfig(accountIdentifier);
    }
  }

  private boolean isLicenseConfigured(String accountIdentifier) {
    try {
      Response<ResponseDTO<LicensesWithSummaryDTO>> response =
          ngLicenseHttpClient.getLicenseSummary(accountIdentifier, ModuleType.IDP.toString()).execute();
      if (response.isSuccessful()) {
        ResponseDTO<LicensesWithSummaryDTO> responseDTO = response.body();
        return responseDTO != null && responseDTO.getData() != null;
      }
    } catch (Exception e) {
      log.warn(String.format("Error getting license summary for account %s", accountIdentifier), e);
    }
    return false;
  }
}
