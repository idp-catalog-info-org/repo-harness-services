/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.event;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL;
import static io.harness.beans.FeatureName.CI_DLC_SIGNED_URL;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.cache.api.CacheMetadataResponse;
import io.harness.ci.cacheserviceclient.CacheServiceUtils;
import io.harness.ci.config.CICacheIntelligenceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.event.account.CIAccountDataStatus;
import io.harness.ci.execution.execution.CIDockerLayerCachingConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.repositories.CIAccountDataStatusRepository;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIBuildInfoRepositoryCustomImpl;
import io.harness.repositories.CIExecutionConfigRepository;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.repositories.CITelemetryStatusRepositoryCustomImpl;
import io.harness.repositories.ExecutionQueueLimitRepository;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CI)
@Slf4j
public class CIDataDeletionService {
  // 12 * 3600 * 1000 - 10 * 60 * 1000
  private static final long TWELVE_HOURS_MINUS_TEN_MINUTES = 42600000;
  private static final String LOCK_NAME = "CI_DATA_DELETION_LOCK";
  private final String DEFAULT_SERVICE_KEY = "gcp_service_key";
  @Inject private CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject private CIBuildInfoRepositoryCustomImpl ciBuildInfoRepositoryCustom;
  @Inject private CIExecutionConfigRepository ciExecutionConfigRepository;
  @Inject private CITelemetryStatusRepositoryCustomImpl ciTelemetryStatusRepository;
  @Inject private ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Inject private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject private CILogServiceUtils ciLogServiceUtils;
  @Inject private TIServiceUtils tiServiceUtils;
  @Inject private CIAccountDataStatusRepository ciAccountDataStatusRepository;
  @Inject private PersistentLocker persistentLocker;
  @Inject(optional = true) @Nullable private CIDockerLayerCachingConfigService ciDockerLayerCachingConfigService;
  @Inject(optional = true) @Nullable private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject @Named("ciDataDeletionExecutor") private ExecutorService executorService;

  @Inject(optional = true) @Nullable private CacheServiceUtils cacheServiceUtils;

  @Inject private CIFeatureFlagService featureFlagService;

  public void deleteJob() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.info("Could not acquire lock");
        return;
      }

      List<CIAccountDataStatus> deletionList = ciAccountDataStatusRepository.findAllByDeleted(false);
      if (deletionList.isEmpty()) {
        log.info("No account data pending for deletion");
        return;
      }

      for (CIAccountDataStatus account : deletionList) {
        String accountId = account.getAccountId();

        // Have observed from logs that some entries contains only white spaces are also getting entered.
        if (StringUtils.isBlank(accountId)) {
          account.setDeleted(true);
          ciAccountDataStatusRepository.save(account);
          continue;
        }
        if (account.lastSent == null
            || account.lastSent < System.currentTimeMillis() - TWELVE_HOURS_MINUS_TEN_MINUTES) {
          saveSentTimeStamp(account);

          executorService.submit(() -> {
            boolean entitiesDeleted = delete(accountId);
            if (entitiesDeleted) {
              account.setDeleted(true);
              ciAccountDataStatusRepository.save(account);
              log.info("Successfully deleted all the CI data for accountId " + accountId);
            }
          });
        } else {
          log.info(format("Deletion request for accountId %s was already sent at %d", accountId, account.lastSent));
        }
      }
    }
  }

  private void saveSentTimeStamp(CIAccountDataStatus account) {
    account.setLastSent(System.currentTimeMillis());
    ciAccountDataStatusRepository.save(account);
  }

  private boolean delete(String accountId) {
    log.info("Starting CI data deletion for accountId " + accountId);
    boolean deletedAll = true;

    deletedAll = deleteData(() -> ciTaskDetailsRepository.deleteAllByAccountId(accountId), "CITaskDetails", accountId)
        && deletedAll;
    deletedAll = deleteData(() -> ciBuildInfoRepositoryCustom.deleteAllByAccountId(accountId), "CIBuildInfo", accountId)
        && deletedAll;
    deletedAll =
        deleteData(
            () -> ciExecutionConfigRepository.deleteAllByAccountIdentifier(accountId), "CIExecutionConfig", accountId)
        && deletedAll;
    deletedAll =
        deleteData(() -> ciTelemetryStatusRepository.deleteAllByAccountId(accountId), "CITelemetryStatus", accountId)
        && deletedAll;
    deletedAll = deleteData(()
                                -> executionQueueLimitRepository.deleteAllByAccountIdentifier(accountId),
                     "ExecutionQueueLimit", accountId)
        && deletedAll;
    deletedAll = deleteData(()
                                -> ciAccountExecutionMetadataRepository.deleteAllByAccountId(accountId),
                     "CIAccountExecutionMetadata", accountId)
        && deletedAll;
    deletedAll =
        deleteData(() -> ciExecutionRepository.deleteAllByAccountId(accountId), "CIExecution", accountId) && deletedAll;
    deletedAll = deleteLogs(accountId) && deletedAll;
    deletedAll = deleteTIData(accountId) && deletedAll;
    if (featureFlagService.isEnabled(CI_DLC_SIGNED_URL, accountId)
        || featureFlagService.isEnabled(CI_CACHE_INTELLIGENCE_SIGNED_URL, accountId)) {
      deletedAll = deleteCacheFromS3(accountId) && deletedAll;
    } else {
      deletedAll = deleteCacheFromCacheIntelligence(accountId) && deletedAll;
      deletedAll = deleteCacheFromDlc(accountId) && deletedAll;
    }
    return deletedAll;
  }

  private boolean deleteData(Runnable deleteAction, String dataType, String accountId) {
    try {
      deleteAction.run();
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting %s data for accountId %s", dataType, accountId), e);
      return false;
    }
    return true;
  }

  private boolean deleteTIData(String accountId) {
    try {
      tiServiceUtils.clean(accountId);
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting ti data for accountId %s", accountId), e);
      return false;
    }
    return true;
  }

  private boolean deleteLogs(String accountId) {
    String key = "accountId:" + accountId;
    boolean logsExist = ciLogServiceUtils.checkIfLogsExist(accountId, key);
    if (logsExist == false) {
      return true;
    }
    try {
      ciLogServiceUtils.deleteLogs(accountId, key);
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting step logs for accountId %s", accountId), e);
    }
    return false;
  }

  private boolean deleteCacheFromDlc(String accountId) {
    if (!ciDockerLayerCachingConfigService.isCacheBlobPresent(accountId)) {
      return true;
    }

    // Executing in different thread as it may be time consuming so that main thread can be free.
    executorService.submit(() -> executeDeletionOfCacheFromDLC(accountId));
    return false; // Returning the status right away and deletion task can be executed in background.
  }
  private boolean deleteCacheFromCacheIntelligence(String accountId) {
    Storage storage = getStorageForCacheIntelligence(ciExecutionServiceConfig);
    CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
    String bucketName = cacheIntelligenceConfig.getBucket();

    if (!isCacheBlobPresentForCacheIntelligence(accountId, bucketName, storage)) {
      return true;
    }

    // Executing in different thread as it may be time consuming so that main thread can be free.
    executorService.submit(() -> executeDeletionOfCacheFromCacheIntelligence(accountId, storage, bucketName));
    return false; // Returning the status right away and deletion task can be executed in background.
  }
  private boolean deleteCacheFromS3(String accountId) {
    if (!isCacheBlobPresentForAccount(accountId)) {
      return true;
    }
    executorService.submit(() -> executeDeletionOfCacheFromS3(accountId));
    return false;
  }

  private void executeDeletionOfCacheFromDLC(String accountId) {
    try {
      ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deletion cache blobs from DLC for accountId %s", accountId), e);
    }
  }

  private void executeDeletionOfCacheFromCacheIntelligence(String accountId, Storage storage, String bucketName) {
    int blobSize = 0;
    try {
      Page<Blob> blobs;
      log.info("Deleting Cache Intelligence blobs for account {}", accountId);
      try {
        blobs = storage.list(bucketName, Storage.BlobListOption.prefix(accountId));
      } catch (Exception e) {
        log.error(String.format("Exception occurred while fetching the blob list for account id %s", accountId), e);
        return;
      }
      for (Blob blob : blobs.iterateAll()) {
        blobSize++;
        blob.delete(BlobSourceOption.generationMatch());
      }
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deletion cache blobs from Cache Intelligence for accountId %s",
                    accountId),
          e);
      return;
    }
    log.info("Successfully deleted {} Cache Intelligence blobs for account {}", blobSize, accountId);
  }

  private void executeDeletionOfCacheFromS3(String accountId) {
    cacheServiceUtils.getDeletedPaths(accountId, "", "");
    log.info("Successfully deleted S3 cache for account {}", accountId);
  }

  private Storage getStorageForCacheIntelligence(CIExecutionServiceConfig ciExecutionServiceConfig) {
    CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
    if (cacheIntelligenceConfig.getServiceKey().equals(DEFAULT_SERVICE_KEY)) {
      return StorageOptions.getDefaultInstance().getService();
    } else {
      File credentialsFile = new File(cacheIntelligenceConfig.getServiceKey());
      ServiceAccountCredentials credentials = null;
      try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
        credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
      } catch (FileNotFoundException e) {
        log.error("Failed to find Google credential file for the GCS service account in the specified path.", e);
      } catch (IOException e) {
        log.error("Failed to get Google credential file for the GCS service account.", e);
      }
      return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
    }
  }

  private boolean isCacheBlobPresentForCacheIntelligence(String accountId, String bucketName, Storage storage) {
    try {
      Page<Blob> blob =
          storage.list(bucketName, Storage.BlobListOption.prefix(accountId), Storage.BlobListOption.pageSize(1));
      return blob.getValues().iterator().hasNext();
    } catch (Exception e) {
      log.error(String.format("Failed to retrieve blob for account id %s", accountId), e);
      return true;
    }
  }
  private boolean isCacheBlobPresentForAccount(String accountId) {
    CacheMetadataResponse cacheMetadataResponse = cacheServiceUtils.getCacheMetadata(accountId, "");
    return cacheMetadataResponse.getDetails().size() > 0;
  }
}
