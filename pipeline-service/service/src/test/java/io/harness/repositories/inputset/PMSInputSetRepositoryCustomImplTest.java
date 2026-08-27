/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.inputset.InputSetRemoteRepoInfo;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeInfoHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;

@OwnedBy(PIPELINE)
public class PMSInputSetRepositoryCustomImplTest extends CategoryTest {
  PMSInputSetRepositoryCustomImpl inputSetRepository;
  @Mock GitAwarePersistence gitAwarePersistence;
  @Mock MongoTemplate mongoTemplate;
  @Mock OutboxService outboxService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock TransactionHelper transactionHelper;
  @Mock InputSetEntityReadHelper inputSetEntityReadHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ScopeInfoHelper scopeInfoHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    java.lang.reflect.Constructor<PMSInputSetRepositoryCustomImpl> ctor =
        PMSInputSetRepositoryCustomImpl.class.getDeclaredConstructor(GitAwarePersistence.class, MongoTemplate.class,
            OutboxService.class, GitSyncSdkService.class, GitAwareEntityHelper.class, TransactionHelper.class,
            InputSetEntityReadHelper.class, PmsFeatureFlagService.class, ScopeInfoHelper.class,
            ScopeResolutionHelper.class);
    ctor.setAccessible(true);
    inputSetRepository =
        ctor.newInstance(gitAwarePersistence, mongoTemplate, outboxService, gitSyncSdkService, gitAwareEntityHelper,
            transactionHelper, inputSetEntityReadHelper, pmsFeatureFlagService, scopeInfoHelper, scopeResolutionHelper);
    when(mongoTemplate.findDistinct(any(), eq(InputSetEntityKeys.repo),
             eq(io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.class), eq(String.class)))
        .thenReturn(Arrays.asList("harness-core", "test-repo"));
  }

  private Document extractMatchCriteria(Aggregation aggregation) {
    AggregationOperation matchOp = aggregation.getPipeline().getOperations().get(0);
    assertThat(matchOp).isInstanceOf(MatchOperation.class);
    return matchOp.toDocument(Aggregation.DEFAULT_CONTEXT);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeAccountScopeWithoutRepoNameDoesNotAddRepoCriteria() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    inputSetRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    assertThat(match.get(InputSetEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(match.get(InputSetEntityKeys.storeType)).isEqualTo(StoreType.REMOTE);
    assertThat(match.get(InputSetEntityKeys.deleted)).isEqualTo(false);
    assertThat(((Document) match.get(InputSetEntityKeys.repo)).containsKey("$in")).isTrue();
    assertThat(match.containsKey(InputSetEntityKeys.parentUniqueId)).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeWithRepoNameUsesCaseInsensitiveAnchoredRegex() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    inputSetRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, "Harness-Core", null, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    Object repoFilter = match.get(InputSetEntityKeys.repo);
    assertThat(repoFilter).isInstanceOf(java.util.regex.Pattern.class);
    java.util.regex.Pattern pattern = (java.util.regex.Pattern) repoFilter;
    assertThat(pattern.pattern()).isEqualTo("^\\QHarness-Core\\E$");
    assertThat(pattern.flags() & java.util.regex.Pattern.CASE_INSENSITIVE)
        .isEqualTo(java.util.regex.Pattern.CASE_INSENSITIVE);

    assertThat(pattern.matcher("Harness-Core").matches()).isTrue();
    assertThat(pattern.matcher("harness-core").matches()).isTrue();
    assertThat(pattern.matcher("HARNESS-CORE").matches()).isTrue();
    assertThat(pattern.matcher("harness-core-fork").matches()).isFalse();
    assertThat(pattern.matcher("not-harness-core").matches()).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeEscapesRegexMetaCharsInRepoName() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String repoWithRegexChars = "team.repo+.*";
    inputSetRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, null, null, repoWithRegexChars, null, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    java.util.regex.Pattern pattern = (java.util.regex.Pattern) match.get(InputSetEntityKeys.repo);
    assertThat(pattern.matcher("team.repo+.*").matches()).isTrue();
    assertThat(pattern.matcher("teamXrepo+.*").matches()).isFalse();
    assertThat(pattern.matcher("team.repoo.*").matches()).isFalse();
    assertThat(pattern.matcher("team.repo+abc").matches()).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeMapsAggregationResultToDtos() {
    PMSInputSetRepositoryCustomImpl.ConnectorTuple projectScopedTuple =
        new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    projectScopedTuple.setConnectorRef("githubMain");
    projectScopedTuple.setAccountId(accountIdentifier);
    projectScopedTuple.setOrgIdentifier("default");
    projectScopedTuple.setProjectIdentifier("cdProject");

    PMSInputSetRepositoryCustomImpl.ConnectorTuple orgScopedTuple =
        new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    orgScopedTuple.setConnectorRef("org.sharedGithub");
    orgScopedTuple.setAccountId(accountIdentifier);
    orgScopedTuple.setOrgIdentifier("default");
    orgScopedTuple.setProjectIdentifier(null);

    PMSInputSetRepositoryCustomImpl.ConnectorTuple accountScopedTuple =
        new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    accountScopedTuple.setConnectorRef("account.accountGithub");
    accountScopedTuple.setAccountId(accountIdentifier);
    accountScopedTuple.setOrgIdentifier(null);
    accountScopedTuple.setProjectIdentifier(null);

    Set<PMSInputSetRepositoryCustomImpl.ConnectorTuple> tuples = new HashSet<>();
    tuples.add(projectScopedTuple);
    tuples.add(orgScopedTuple);
    tuples.add(accountScopedTuple);

    PMSInputSetRepositoryCustomImpl.FilePathTuple inputOne = new PMSInputSetRepositoryCustomImpl.FilePathTuple();
    inputOne.setFilePath(".harness/input-1.yaml");
    inputOne.setAccountId(accountIdentifier);
    inputOne.setOrgIdentifier("default");
    inputOne.setProjectIdentifier("cdProject");
    inputOne.setParentUniqueId("uid-cdProject");

    PMSInputSetRepositoryCustomImpl.FilePathTuple inputTwo = new PMSInputSetRepositoryCustomImpl.FilePathTuple();
    inputTwo.setFilePath(".harness/input-2.yaml");
    inputTwo.setAccountId(accountIdentifier);
    inputTwo.setOrgIdentifier("default");
    inputTwo.setProjectIdentifier("cdProject");
    inputTwo.setParentUniqueId("uid-cdProject");

    List<PMSInputSetRepositoryCustomImpl.FilePathTuple> filePathTuples = new ArrayList<>();
    filePathTuples.add(inputOne);
    filePathTuples.add(inputTwo);

    PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("harness-core");
    result.setRepoURL("https://github.com/wings-software/harness-core");
    result.setCount(5L);
    result.setFilePathTuples(filePathTuples);
    result.setConnectorTuples(tuples);

    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<InputSetRemoteRepoInfo> infos =
        inputSetRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    InputSetRemoteRepoInfo info = infos.get(0);
    assertThat(info.getRepoName()).isEqualTo("harness-core");
    assertThat(info.getRepoURL()).isEqualTo("https://github.com/wings-software/harness-core");
    assertThat(info.getCount()).isEqualTo(5L);
    assertThat(info.getFilePathsByOwningScope().keySet())
        .containsExactlyInAnyOrder(".harness/input-1.yaml", ".harness/input-2.yaml");
    // Per-file owning scope carries parentUniqueId so the GitX webhook health endpoint can decide
    // which per-scope webhook governs each file.
    assertThat(info.getFilePathsByOwningScope().get(".harness/input-1.yaml").getParentUniqueId())
        .isEqualTo("uid-cdProject");
    assertThat(info.getConnectorRefs())
        .containsExactlyInAnyOrder(accountIdentifier + "/default/cdProject/githubMain",
            accountIdentifier + "/default/sharedGithub", accountIdentifier + "/accountGithub");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeProjectScopeUsesScopeInfoUniqueIdDirectly() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String projectUniqueId = "projUid-123";
    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .uniqueId(projectUniqueId)
                                     .build();

    inputSetRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, null, projectScopeInfo, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    assertThat(match.get(InputSetEntityKeys.parentUniqueId)).isEqualTo(projectUniqueId);
    verify(scopeResolutionHelper, never()).getProjectUniqueIds(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeProjectScopeFallsBackToHelperWhenScopeInfoMissing() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String resolvedProjUid = "resolvedProjUid";
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(resolvedProjUid)
                        .build());

    inputSetRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    assertThat(match.get(InputSetEntityKeys.parentUniqueId)).isEqualTo(resolvedProjUid);
    verify(scopeResolutionHelper, times(2)).getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeOrgScopeIncludesOrgAndAllProjectUniqueIds() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String orgUniqueId = "orgUid-1";
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId(orgUniqueId)
                                 .build();
    when(scopeResolutionHelper.getProjectUniqueIds(accountIdentifier, orgUniqueId))
        .thenReturn(Arrays.asList("p1", "p2", "p3"));

    inputSetRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, null, null, orgScopeInfo, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    Object parentUniqueIdFilter = match.get(InputSetEntityKeys.parentUniqueId);
    assertThat(parentUniqueIdFilter).isInstanceOf(Document.class);
    @SuppressWarnings("unchecked") List<String> inList = (List<String>) ((Document) parentUniqueIdFilter).get("$in");
    assertThat(inList).containsExactlyInAnyOrder(orgUniqueId, "p1", "p2", "p3");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeOrgScopeFallsBackToHelperWhenScopeInfoMissing() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String resolvedOrgUid = "resolvedOrgUid";
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, null))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .scopeType(ScopeLevel.ORGANIZATION)
                        .uniqueId(resolvedOrgUid)
                        .build());
    when(scopeResolutionHelper.getProjectUniqueIds(accountIdentifier, resolvedOrgUid)).thenReturn(null);

    inputSetRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, orgIdentifier, null, null, null, 0, 1000);

    verify(inputSetEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    Object parentUniqueIdFilter = match.get(InputSetEntityKeys.parentUniqueId);
    assertThat(parentUniqueIdFilter).isInstanceOf(Document.class);
    @SuppressWarnings("unchecked") List<String> inList = (List<String>) ((Document) parentUniqueIdFilter).get("$in");
    assertThat(inList).containsExactly(resolvedOrgUid);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeSkipsConnectorFqnWhenConnectorRefIsEmpty() {
    PMSInputSetRepositoryCustomImpl.ConnectorTuple validTuple = new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    validTuple.setConnectorRef("conn1");
    validTuple.setAccountId(accountIdentifier);
    validTuple.setOrgIdentifier("o");
    validTuple.setProjectIdentifier("p");

    PMSInputSetRepositoryCustomImpl.ConnectorTuple emptyRefTuple = new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    emptyRefTuple.setConnectorRef("");
    emptyRefTuple.setAccountId(accountIdentifier);

    PMSInputSetRepositoryCustomImpl.ConnectorTuple nullRefTuple = new PMSInputSetRepositoryCustomImpl.ConnectorTuple();
    nullRefTuple.setConnectorRef(null);
    nullRefTuple.setAccountId(accountIdentifier);

    Set<PMSInputSetRepositoryCustomImpl.ConnectorTuple> tuples = new HashSet<>();
    tuples.add(validTuple);
    tuples.add(emptyRefTuple);
    tuples.add(nullRefTuple);

    PMSInputSetRepositoryCustomImpl.FilePathTuple xTuple = new PMSInputSetRepositoryCustomImpl.FilePathTuple();
    xTuple.setFilePath(".harness/x.yaml");
    xTuple.setAccountId(accountIdentifier);
    xTuple.setOrgIdentifier("o");
    xTuple.setProjectIdentifier("p");
    xTuple.setParentUniqueId("uid-p");

    PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(1L);
    result.setFilePathTuples(new ArrayList<>(Arrays.asList(xTuple)));
    result.setConnectorTuples(tuples);

    when(inputSetEntityReadHelper.aggregate(
             any(), eq(PMSInputSetRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<InputSetRemoteRepoInfo> infos =
        inputSetRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    assertThat(infos.get(0).getConnectorRefs()).containsExactly(accountIdentifier + "/o/p/conn1");
  }
}
