/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.category.element.UnitTests;
import io.harness.ci.states.V1.cd.HarnessConfigFileStoreFetcher;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class HarnessConfigFileStoreFetcherTest {
  private HarnessConfigFileStoreFetcher fetcher;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    fetcher = spy(new HarnessConfigFileStoreFetcher());
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", "acc", "orgIdentifier", "org", "projectIdentifier", "proj"))
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchFileStoreContents_whenEmptyPaths_returnsEmptyMap() {
    assertThat(fetcher.fetchFileStoreContents(buildAmbiance(), Collections.emptyList())).isEmpty();
    assertThat(fetcher.fetchFileStoreContents(buildAmbiance(), null)).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchFileStoreContents_resolvesContentInOrder() {
    Ambiance ambiance = buildAmbiance();
    doReturn("content-1").when(fetcher).getContent("acc", "org", "proj", "/account/file1.yaml");
    doReturn("content-2").when(fetcher).getContent("acc", "org", "proj", "/account/file2.yaml");

    List<String> paths = Arrays.asList("/account/file1.yaml", "/account/file2.yaml");
    Map<String, String> result = fetcher.fetchFileStoreContents(ambiance, paths);

    assertThat(result).containsEntry("/account/file1.yaml", "content-1");
    assertThat(result).containsEntry("/account/file2.yaml", "content-2");
    assertThat(result.keySet()).containsExactly("/account/file1.yaml", "/account/file2.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchFileStoreContents_skipsBlankPaths() {
    Ambiance ambiance = buildAmbiance();
    doReturn("content").when(fetcher).getContent(eq("acc"), eq("org"), eq("proj"), eq("/account/file.yaml"));

    List<String> paths = Arrays.asList("", "/account/file.yaml");
    Map<String, String> result = fetcher.fetchFileStoreContents(ambiance, paths);

    assertThat(result).hasSize(1).containsEntry("/account/file.yaml", "content");
    // Blank path should not trigger a fetch.
    org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.never()).getContent(any(), any(), any(), eq(""));
  }
}
