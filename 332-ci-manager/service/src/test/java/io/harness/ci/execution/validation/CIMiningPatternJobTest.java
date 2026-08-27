/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.MiningPatternConfig;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.rule.Owner;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;

public class CIMiningPatternJobTest extends CIExecutionTestBase {
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock Storage mockStorage;
  @Mock GoogleCredentials mockCredentials;
  @Mock StorageOptions mockStorageOptions;
  @Mock StorageOptions.Builder mockStorageOptionsBuilder;

  private static final String VALID_GCS_CREDS_BASE64 =
      java.util.Base64.getEncoder().encodeToString("{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8));

  private CIMiningPatternJob ciMiningPatternJob;

  @Before
  public void setup() throws Exception {
    ciMiningPatternJob = new CIMiningPatternJob();
    Field configField = CIMiningPatternJob.class.getDeclaredField("ciExecutionServiceConfig");
    configField.setAccessible(true);
    configField.set(ciMiningPatternJob, ciExecutionServiceConfig);
  }

  // ==============================
  // Helper to set up GCS mocking for happy path
  // ==============================

  private void setupGcsConfig() {
    MiningPatternConfig config = MiningPatternConfig.builder()
                                     .projectId("test-project")
                                     .bucketName("test-bucket")
                                     .gcsCreds(VALID_GCS_CREDS_BASE64)
                                     .build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);
  }

  private void setupGcsMocks() {
    when(mockStorageOptionsBuilder.setCredentials(any(Credentials.class))).thenReturn(mockStorageOptionsBuilder);
    when(mockStorageOptionsBuilder.setProjectId(eq("test-project"))).thenReturn(mockStorageOptionsBuilder);
    when(mockStorageOptionsBuilder.build()).thenReturn(mockStorageOptions);
    when(mockStorageOptions.getService()).thenReturn(mockStorage);
  }

  // ==============================
  // Tests for getMaliciousMiningPatterns() - null/blank config
  // ==============================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenConfigIsNull_shouldReturnEmptySet() {
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(null);

    Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

    assertThat(result).as("Should return empty set when mining pattern config is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenProjectIdIsBlank_shouldReturnEmptySet() {
    MiningPatternConfig config =
        MiningPatternConfig.builder().projectId("").bucketName("test-bucket").gcsCreds(VALID_GCS_CREDS_BASE64).build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

    assertThat(result).as("Should return empty set when projectId is blank").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenBucketNameIsBlank_shouldReturnEmptySet() {
    MiningPatternConfig config =
        MiningPatternConfig.builder().projectId("test-project").bucketName("").gcsCreds(VALID_GCS_CREDS_BASE64).build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

    assertThat(result).as("Should return empty set when bucketName is blank").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenGcsCredsIsBlank_shouldReturnEmptySet() {
    MiningPatternConfig config =
        MiningPatternConfig.builder().projectId("test-project").bucketName("test-bucket").gcsCreds("").build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

    assertThat(result).as("Should return empty set when gcsCreds is blank").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenAllConfigFieldsNull_shouldReturnEmptySet() {
    MiningPatternConfig config = MiningPatternConfig.builder().projectId(null).bucketName(null).gcsCreds(null).build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

    assertThat(result).as("Should return empty set when all config fields are null").isEmpty();
  }

  // ==============================
  // Tests for getValidDomains() - null/blank config
  // ==============================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetValidDomains_whenConfigIsNull_shouldReturnEmptySet() {
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(null);

    Set<String> result = ciMiningPatternJob.getValidDomains();

    assertThat(result).as("Should return empty set when mining pattern config is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetValidDomains_whenProjectIdIsBlank_shouldReturnEmptySet() {
    MiningPatternConfig config =
        MiningPatternConfig.builder().projectId("").bucketName("test-bucket").gcsCreds(VALID_GCS_CREDS_BASE64).build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getValidDomains();

    assertThat(result).as("Should return empty set when projectId is blank").isEmpty();
  }

  // ==============================
  // Tests for getWhiteListed() - null/blank config
  // ==============================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWhiteListed_whenConfigIsNull_shouldReturnEmptySet() {
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(null);

    Set<String> result = ciMiningPatternJob.getWhiteListed();

    assertThat(result).as("Should return empty set when mining pattern config is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWhiteListed_whenBucketNameIsBlank_shouldReturnEmptySet() {
    MiningPatternConfig config =
        MiningPatternConfig.builder().projectId("test-project").bucketName("").gcsCreds(VALID_GCS_CREDS_BASE64).build();
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(config);

    Set<String> result = ciMiningPatternJob.getWhiteListed();

    assertThat(result).as("Should return empty set when bucketName is blank").isEmpty();
  }

  // ==============================
  // Phase 3: Happy path tests with mocked GCS - covers initialize* parsing logic
  // ==============================

  // Phase 3: Covers lines 78, 103, 105, 107, 108, 111, 112, 113, 122, 123
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenGcsReturnsPatterns_shouldParseAndReturnSet() {
    setupGcsConfig();
    setupGcsMocks();

    String patternsContent = "xmrig\ncryptominer\n\nmalicious-pool\n";
    byte[] contentBytes = patternsContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "suspiciousMiningPatterns.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

      assertThat(result)
          .as("Should parse patterns from GCS file content, skipping empty lines")
          .containsExactlyInAnyOrder("xmrig", "cryptominer", "malicious-pool");
    }
  }

  // Phase 3: Covers lines 86, 130, 132, 134, 135, 138, 139, 140, 149, 150
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetValidDomains_whenGcsReturnsDomains_shouldParseAndReturnSet() {
    setupGcsConfig();
    setupGcsMocks();

    String domainsContent = "harness.io\ngoogle.com\ngithub.com\n";
    byte[] contentBytes = domainsContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "validDomains.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getValidDomains();

      assertThat(result)
          .as("Should parse domain names from GCS file content")
          .containsExactlyInAnyOrder("harness.io", "google.com", "github.com");
    }
  }

  // Phase 3: Covers lines 94, 157, 159, 161, 162, 165, 166, 167, 176, 177
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWhiteListed_whenGcsReturnsAccounts_shouldParseAndReturnSet() {
    setupGcsConfig();
    setupGcsMocks();

    String accountsContent = "account1\naccount2\naccount3\n";
    byte[] contentBytes = accountsContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "whiteListedAccounts.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getWhiteListed();

      assertThat(result)
          .as("Should parse account IDs from GCS file content")
          .containsExactlyInAnyOrder("account1", "account2", "account3");
    }
  }

  // Phase 3: Covers lines 105, 107, 111, 112, 113 - patterns with whitespace trimming
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenPatternsHaveWhitespace_shouldTrimEntries() {
    setupGcsConfig();
    setupGcsMocks();

    String patternsContent = "  xmrig  \n  pool.miner.com  \n";
    byte[] contentBytes = patternsContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "suspiciousMiningPatterns.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

      assertThat(result)
          .as("Should trim whitespace from parsed patterns")
          .containsExactlyInAnyOrder("xmrig", "pool.miner.com");
    }
  }

  // Phase 3: Covers lines 202, 204, 206 via storage.readAllBytes success path
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenGcsReturnsSinglePattern_shouldReturnSingleElementSet() {
    setupGcsConfig();
    setupGcsMocks();

    String patternsContent = "single-pattern";
    byte[] contentBytes = patternsContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "suspiciousMiningPatterns.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

      assertThat(result)
          .as("Should return set with single pattern when GCS file has one entry")
          .hasSize(1)
          .containsExactly("single-pattern");
    }
  }

  // Phase 3: Covers lines 117-119 - exception during GCS download returns empty set from initialize
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenStorageThrowsException_shouldReturnEmptySet() {
    setupGcsConfig();
    setupGcsMocks();

    when(mockStorage.readAllBytes("test-bucket", "suspiciousMiningPatterns.txt"))
        .thenThrow(new RuntimeException("GCS read failed"));

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

      assertThat(result).as("Should return empty set when GCS storage.readAllBytes throws exception").isEmpty();
    }
  }

  // Phase 3: Covers lines 144-146 - exception during valid domains GCS download
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetValidDomains_whenStorageThrowsException_shouldReturnEmptySet() {
    setupGcsConfig();
    setupGcsMocks();

    when(mockStorage.readAllBytes("test-bucket", "validDomains.txt"))
        .thenThrow(new RuntimeException("GCS read failed"));

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getValidDomains();

      assertThat(result).as("Should return empty set when GCS storage throws during valid domains fetch").isEmpty();
    }
  }

  // Phase 3: Covers lines 171-173 - exception during white listed accounts GCS download
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetWhiteListed_whenStorageThrowsException_shouldReturnEmptySet() {
    setupGcsConfig();
    setupGcsMocks();

    when(mockStorage.readAllBytes("test-bucket", "whiteListedAccounts.txt"))
        .thenThrow(new RuntimeException("GCS read failed"));

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getWhiteListed();

      assertThat(result)
          .as("Should return empty set when GCS storage throws during white listed accounts fetch")
          .isEmpty();
    }
  }

  // Phase 3: Covers lines 208-210 - exception in downloadFromGCS try block (credential creation)
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_whenCredentialCreationFails_shouldReturnEmptySet() {
    setupGcsConfig();

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenThrow(new RuntimeException("Invalid credentials"));

      Set<String> result = ciMiningPatternJob.getMaliciousMiningPatterns();

      assertThat(result).as("Should return empty set when GoogleCredentials creation fails").isEmpty();
    }
  }

  // Phase 3: Covers lines for getValidDomains with empty content (only newlines)
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetValidDomains_whenGcsReturnsOnlyNewlines_shouldReturnEmptySet() {
    setupGcsConfig();
    setupGcsMocks();

    String emptyContent = "\n\n\n";
    byte[] contentBytes = emptyContent.getBytes(StandardCharsets.UTF_8);
    when(mockStorage.readAllBytes("test-bucket", "validDomains.txt")).thenReturn(contentBytes);

    try (MockedStatic<GoogleCredentials> gcMock = mockStatic(GoogleCredentials.class);
         MockedStatic<StorageOptions> soMock = mockStatic(StorageOptions.class)) {
      gcMock.when(() -> GoogleCredentials.fromStream(any())).thenReturn(mockCredentials);
      soMock.when(StorageOptions::newBuilder).thenReturn(mockStorageOptionsBuilder);

      Set<String> result = ciMiningPatternJob.getValidDomains();

      assertThat(result).as("Should return empty set when GCS file contains only newlines").isEmpty();
    }
  }

  // Phase 3: Covers cache behavior -- second call uses cached result
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaliciousMiningPatterns_repeatedCalls_shouldReturnCachedResult() {
    when(ciExecutionServiceConfig.getMiningPatternConfig()).thenReturn(null);

    ciMiningPatternJob.getMaliciousMiningPatterns();
    ciMiningPatternJob.getMaliciousMiningPatterns();

    verify(ciExecutionServiceConfig, times(1)).getMiningPatternConfig();
  }
}
