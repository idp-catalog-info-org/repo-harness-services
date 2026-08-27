/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.rule.OwnerRule.KARAN_GARG;
import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.config.ScopedPermissionsBackfillConfig;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.entities.Token;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ScopedPermissionsBackfillJobTest extends NgManagerTestBase {
  private static final int BATCH_SIZE = 100;

  @Mock private TokenRepository tokenRepository;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AcquiredLock<?> acquiredLock;

  private ScopedPermissionsBackfillJob job;

  @Before
  public void setUp() {
    ScopedPermissionsBackfillConfig config = new ScopedPermissionsBackfillConfig();
    config.setDelayInSeconds(3600);
    config.setInitialDelayInSeconds(0);
    config.setBatchSize(BATCH_SIZE);
    job = new ScopedPermissionsBackfillJob(tokenRepository, persistentLocker, config);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void processToken_backfillsLegacyOnlyEntry() {
    Token token = tokenWithEntries(legacy("p1"));

    boolean modified = job.processToken(token);

    assertThat(modified).isTrue();
    assertThat(token.getScopedResourcePermissions().get(0).getPermission()).isEqualTo("p1");
    assertThat(token.getScopedResourcePermissions().get(0).getPermissions()).containsExactly("p1");
    verify(tokenRepository, times(1)).save(token);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void processToken_skipsEntryWithBothFieldsSet() {
    Token token = tokenWithEntries(both("p1", List.of("p1", "p2")));

    boolean modified = job.processToken(token);

    assertThat(modified).isFalse();
    assertThat(token.getScopedResourcePermissions().get(0).getPermissions()).containsExactly("p1", "p2");
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void processToken_skipsEntryWithOnlyNewList() {
    Token token = tokenWithEntries(newOnly(List.of("p1")));

    boolean modified = job.processToken(token);

    assertThat(modified).isFalse();
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void processToken_mixedEntries_normalizesOnlyLegacyOnes() {
    Token token = tokenWithEntries(legacy("p1"), newOnly(List.of("q1", "q2")), both("r1", List.of("r1")));

    boolean modified = job.processToken(token);

    assertThat(modified).isTrue();
    assertThat(token.getScopedResourcePermissions().get(0).getPermissions()).containsExactly("p1");
    assertThat(token.getScopedResourcePermissions().get(1).getPermissions()).containsExactly("q1", "q2");
    assertThat(token.getScopedResourcePermissions().get(2).getPermissions()).containsExactly("r1");
    verify(tokenRepository, times(1)).save(token);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void processToken_emptyScopedPermissionsList_isNoOp() {
    Token token = Token.builder().uuid("u1").scopedResourcePermissions(Collections.emptyList()).build();

    boolean modified = job.processToken(token);

    assertThat(modified).isFalse();
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void run_lockNotAcquired_doesNotQueryRepository() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class))).thenReturn(null);

    job.run();

    verify(tokenRepository, never()).findScopedTokensNeedingPermissionsBackfill(anyInt());
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void run_emptyFirstBatch_breaksImmediately() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class)))
        .thenAnswer(invocation -> acquiredLock);
    when(tokenRepository.findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE)).thenReturn(Collections.emptyList());

    job.run();

    verify(tokenRepository, times(1)).findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE);
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void run_partialBatch_stopsAfterOneFetch() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class)))
        .thenAnswer(invocation -> acquiredLock);
    Token t1 = tokenWithEntries(legacy("p1"));
    Token t2 = tokenWithEntries(legacy("p2"));
    when(tokenRepository.findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE)).thenReturn(List.of(t1, t2));

    job.run();

    // Returned 2 < batchSize, so we expect exactly one fetch then exit.
    verify(tokenRepository, times(1)).findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE);
    verify(tokenRepository, times(1)).save(t1);
    verify(tokenRepository, times(1)).save(t2);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void run_perTokenExceptionDoesNotAbortLoop() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class)))
        .thenAnswer(invocation -> acquiredLock);
    Token t1 = tokenWithEntries(legacy("p1"));
    Token t2 = tokenWithEntries(legacy("p2"));
    when(tokenRepository.findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE)).thenReturn(List.of(t1, t2));

    ScopedPermissionsBackfillJob spied = spy(job);
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(spied).processToken(t1);
    org.mockito.Mockito.doReturn(true).when(spied).processToken(t2);

    spied.run();

    verify(spied).processToken(t1);
    verify(spied).processToken(t2);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void run_drainsAcrossMultipleFullBatchesUntilEmpty() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class)))
        .thenAnswer(invocation -> acquiredLock);
    // Two full batches then empty: verify the while-loop keeps fetching until the query reports no more.
    List<Token> firstFull = fullBatchOfLegacyTokens(BATCH_SIZE, "a");
    List<Token> secondFull = fullBatchOfLegacyTokens(BATCH_SIZE, "b");
    when(tokenRepository.findScopedTokensNeedingPermissionsBackfill(eq(BATCH_SIZE)))
        .thenReturn(firstFull, secondFull, Collections.emptyList());

    job.run();

    verify(tokenRepository, times(3)).findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE);
    // Every token in both batches is legacy-only, so every one should have been saved.
    verify(tokenRepository, times(2 * BATCH_SIZE)).save(any(Token.class));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void run_fullBatchWithZeroModifications_stopsAfterOneFetch() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any(Duration.class)))
        .thenAnswer(invocation -> acquiredLock);
    List<Token> fullFailingBatch = fullBatchOfLegacyTokens(BATCH_SIZE, "fail");
    when(tokenRepository.findScopedTokensNeedingPermissionsBackfill(eq(BATCH_SIZE))).thenReturn(fullFailingBatch);

    ScopedPermissionsBackfillJob spied = spy(job);
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(spied).processToken(any(Token.class));

    spied.run();

    // Full batch, every processToken failed → batchModified == 0 → progress guard ends the run.
    verify(tokenRepository, times(1)).findScopedTokensNeedingPermissionsBackfill(BATCH_SIZE);
    verify(tokenRepository, never()).save(any());
  }

  private static List<Token> fullBatchOfLegacyTokens(int size, String prefix) {
    List<Token> batch = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      batch.add(tokenWithEntries(legacy(prefix + i)));
    }
    return batch;
  }

  // --- helpers ---

  private static Token tokenWithEntries(ScopedResourcePermission... entries) {
    return Token.builder().uuid("u-" + java.util.UUID.randomUUID()).scopedResourcePermissions(List.of(entries)).build();
  }

  private static ScopedResourcePermission legacy(String permission) {
    return ScopedResourcePermission.builder().resourceType("CONNECTOR").permission(permission).build();
  }

  private static ScopedResourcePermission newOnly(List<String> permissions) {
    return ScopedResourcePermission.builder().resourceType("CONNECTOR").permissions(permissions).build();
  }

  private static ScopedResourcePermission both(String permission, List<String> permissions) {
    return ScopedResourcePermission.builder()
        .resourceType("CONNECTOR")
        .permission(permission)
        .permissions(permissions)
        .build();
  }
}
