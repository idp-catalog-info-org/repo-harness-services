/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification.stagemetadata;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.approval.notification.stagemetadata.summary.CDStageSummary;
import io.harness.pms.approval.notification.stagemetadata.summary.GenericStageSummary;
import io.harness.pms.approval.notification.stagemetadata.summary.StageSummary;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class StageMetadataNotificationHelperImplTest extends CategoryTest {
  @Mock private CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;
  @Mock private Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO> > > mockExecutionCall;
  @InjectMocks StageMetadataNotificationHelperImpl stageMetadataNotificationHelper;
  private static final String accountId = "accountId";

  private static final String planExIdentifier = "planExIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final Scope scope = Scope.builder()
                                         .accountIdentifier(accountId)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .build();

  private static CDStageSummary cdStageSummaryUpcoming1;
  private static CDStageSummary cdStageSummaryUpcoming2;

  private static CDStageSummaryResponseDTO cdStageSummaryResponseUpcoming1;
  private static CDStageSummaryResponseDTO cdStageSummaryResponseUpcoming2;
  private static CDStageSummary cdStageSummaryFinished1;
  private static CDStageSummary cdStageSummaryFinished2;
  private static CDStageSummaryResponseDTO cdStageSummaryResponseFinished1;
  private static CDStageSummaryResponseDTO cdStageSummaryResponseFinished2;
  private static CDStageSummaryResponseDTO cdStageSummaryResponseFinished3;
  private static CDStageSummary cdMultiStageSummaryUpcoming1;
  private static CDStageSummaryResponseDTO cdMultiStageSummaryResponseUpcoming1;
  private static GenericStageSummary genericStageSummaryUpcoming;
  private static GenericStageSummary genericStageSummaryFinished;
  private static TestStageSummary testStageSummary;

  @Before
  public void setup() throws Exception {
    // fallback to identifiers when stage names not present
    cdStageSummaryUpcoming1 = CDStageSummary.builder().build();
    cdStageSummaryUpcoming1.setStageName("s3 name");
    cdStageSummaryUpcoming1.setStageIdentifier("s3_id");

    cdStageSummaryResponseUpcoming1 =
        CDStageSummaryResponseDTO.builder().service("s3 s name").environment("s3 e name").infra("s3 i name").build();

    cdStageSummaryUpcoming2 = CDStageSummary.builder().build();
    cdStageSummaryUpcoming2.setStageIdentifier("s4_id");

    cdStageSummaryResponseUpcoming2 =
        CDStageSummaryResponseDTO.builder().service("s4 s name").environment("s4 e name").infra("s4 i name").build();

    cdMultiStageSummaryUpcoming1 = CDStageSummary.builder().build();
    cdMultiStageSummaryUpcoming1.setStageIdentifier("multiService_id");
    cdMultiStageSummaryUpcoming1.setStageName("multiService name");

    cdMultiStageSummaryResponseUpcoming1 = CDStageSummaryResponseDTO.builder()
                                               .services("[<+pipeline.variables.ser1>, <+pipeline.variables.ser2>]")
                                               .environments("[env name]")
                                               .infras("[infra name]")
                                               .envGroup("[env grp]")
                                               .build();

    genericStageSummaryUpcoming = GenericStageSummary.builder().build();
    genericStageSummaryUpcoming.setStageIdentifier("g1_id");
    genericStageSummaryUpcoming.setStageName("g1 name");

    // finished
    cdStageSummaryFinished1 = CDStageSummary.builder().build();
    cdStageSummaryFinished1.setStageName("s1 name");
    cdStageSummaryFinished1.setStageIdentifier("s1_id");
    cdStageSummaryFinished1.setStageExecutionIdentifier("s1_ex_id");

    cdStageSummaryResponseFinished1 =
        CDStageSummaryResponseDTO.builder().service("s1 s name").environment("s1 e name").infra("s1 i name").build();

    cdStageSummaryFinished2 = CDStageSummary.builder().build();
    cdStageSummaryFinished2.setStageIdentifier("s2_id");
    cdStageSummaryFinished2.setStageExecutionIdentifier("s2_ex_id");

    cdStageSummaryResponseFinished2 =
        CDStageSummaryResponseDTO.builder().service("s2 s name").environment("s2 e name").infra("s2 i name").build();
    cdStageSummaryResponseFinished3 = CDStageSummaryResponseDTO.builder()
                                          .service("s2 s name")
                                          .environment("s2 e name")
                                          .infra("s2 i name")
                                          .artifactDisplayName("s2 a name")
                                          .build();

    genericStageSummaryFinished = GenericStageSummary.builder().build();
    genericStageSummaryFinished.setStageIdentifier("g2_id");
    genericStageSummaryFinished.setStageExecutionIdentifier("g2_ex_id");

    testStageSummary = TestStageSummary.builder().build();
    testStageSummary.setStageExecutionIdentifier("test_ex_id");
    testStageSummary.setStageIdentifier("test_id");

    mockExecutionCall = Mockito.mock(Call.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSetFormattedSummaryOfFinishedStages() throws IOException {
    // upcoming added to finished set to check cases where finished stages doesn't have execution identifiers
    Set<StageSummary> finishedStages = new LinkedHashSet<>(List.of(
        cdStageSummaryFinished1, cdStageSummaryFinished2, genericStageSummaryFinished, genericStageSummaryUpcoming));

    // A: edge cases
    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(new HashSet<>(), new HashSet<>(), scope);

    assertThatThrownBy(
        () -> stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(finishedStages, null, scope))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted finished stages and scope is required");

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(
                               finishedStages, new HashSet<>(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted finished stages and scope is required");

    Set<String> formattedFinishedStages = new LinkedHashSet<>();
    when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, List.of("s1_ex_id", "s2_ex_id"), true))
        .thenReturn(mockExecutionCall);
    when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, List.of("s2_ex_id", "s1_ex_id"), true))
        .thenReturn(mockExecutionCall);
    // B: when get complete CD response

    Map<String, CDStageSummaryResponseDTO> completeResponse = new HashMap<>();
    completeResponse.put("s1_ex_id", cdStageSummaryResponseFinished1);
    completeResponse.put("s2_ex_id", cdStageSummaryResponseFinished2);
    doReturn(getWrappedExecutionResponse(completeResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(finishedStages, formattedFinishedStages, scope);

    assertThat(formattedFinishedStages)
        .hasSize(4)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id : \n"
                + "     Service  :  s2 s name\n"
                + "     Environment  :  s2 e name\n"
                + "     Infrastructure Definition  :  s2 i name",
            "g2_id", "g1 name");

    // original set shouldn't be modified
    assertThat(finishedStages).hasSize(4);
    formattedFinishedStages.clear();

    // C: when get partial CD response
    Map<String, CDStageSummaryResponseDTO> partialResponse = new HashMap<>();
    partialResponse.put("s1_ex_id", cdStageSummaryResponseFinished1);
    doReturn(getWrappedExecutionResponse(partialResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(finishedStages, formattedFinishedStages, scope);

    assertThat(formattedFinishedStages)
        .hasSize(4)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id", "g2_id", "g1 name");

    // original set shouldn't be modified
    assertThat(finishedStages).hasSize(4);
    formattedFinishedStages.clear();

    // D: when no CD stages
    Set<StageSummary> finishedStagesWithoutCDStages =
        new LinkedHashSet<>(List.of(genericStageSummaryFinished, genericStageSummaryUpcoming));

    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(
        finishedStagesWithoutCDStages, formattedFinishedStages, scope);

    assertThat(formattedFinishedStages).hasSize(2).containsExactly("g2_id", "g1 name");
    // original set shouldn't be modified
    assertThat(finishedStagesWithoutCDStages).hasSize(2);
    formattedFinishedStages.clear();

    // E: when only CD stages
    Set<StageSummary> finishedStagesWithOnlyCDStages =
        new LinkedHashSet<>(List.of(cdStageSummaryFinished1, cdStageSummaryFinished2));

    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(
        finishedStagesWithOnlyCDStages, formattedFinishedStages, scope);

    assertThat(formattedFinishedStages)
        .hasSize(2)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id");
    // original set shouldn't be modified
    assertThat(finishedStagesWithOnlyCDStages).hasSize(2);
    formattedFinishedStages.clear();

    // F: unprocessed stages left
    Set<StageSummary> finishedStagesWithUnknownStages = new LinkedHashSet<>(List.of(testStageSummary));

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(
                               finishedStagesWithUnknownStages, formattedFinishedStages, scope))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error while formatting finished stages, unable to process [[TestStageSummary()]] stages");

    // original set shouldn't be modified
    assertThat(finishedStagesWithUnknownStages).hasSize(1);
    formattedFinishedStages.clear();

    // G: when failed CD response
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, List.of("s1_ex_id", "s2_ex_id"), true);
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, List.of("s2_ex_id", "s1_ex_id"), true);

    stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(finishedStages, formattedFinishedStages, scope);

    assertThat(formattedFinishedStages).hasSize(4).containsExactly("s1 name", "s2_id", "g2_id", "g1 name");
    // original set shouldn't be modified
    assertThat(finishedStages).hasSize(4);
    formattedFinishedStages.clear();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSetFormattedSummaryOfUpcomingStages() throws IOException {
    Set<StageSummary> upcomingStages =
        new LinkedHashSet<>(List.of(cdStageSummaryUpcoming1, cdStageSummaryUpcoming2, genericStageSummaryUpcoming));

    // A: edge cases
    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        new HashSet<>(), new HashSet<>(), scope, planExIdentifier);

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
                               upcomingStages, null, scope, planExIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted Upcoming stages and scope and plan id is required");

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
                               upcomingStages, new HashSet<>(), null, planExIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted Upcoming stages and scope and plan id is required");

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
                               upcomingStages, new HashSet<>(), scope, "  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted Upcoming stages and scope and plan id is required");

    Set<String> formattedUpcomingStages = new LinkedHashSet<>();
    when(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, planExIdentifier, List.of("s3_id", "s4_id"), true))
        .thenReturn(mockExecutionCall);
    when(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, planExIdentifier, List.of("s4_id", "s3_id"), true))
        .thenReturn(mockExecutionCall);
    // B: when get complete CD response

    Map<String, CDStageSummaryResponseDTO> completeResponse = new HashMap<>();
    completeResponse.put("s3_id", cdStageSummaryResponseUpcoming1);
    completeResponse.put("s4_id", cdStageSummaryResponseUpcoming2);
    doReturn(getWrappedExecutionResponse(completeResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        upcomingStages, formattedUpcomingStages, scope, planExIdentifier);

    assertThat(formattedUpcomingStages)
        .hasSize(3)
        .containsExactly("s3 name : \n"
                + "     Service  :  s3 s name\n"
                + "     Environment  :  s3 e name\n"
                + "     Infrastructure Definition  :  s3 i name",
            "s4_id : \n"
                + "     Service  :  s4 s name\n"
                + "     Environment  :  s4 e name\n"
                + "     Infrastructure Definition  :  s4 i name",
            "g1 name");
    // original set shouldn't be modified
    assertThat(upcomingStages).hasSize(3);
    formattedUpcomingStages.clear();

    // C: when get partial CD response
    Map<String, CDStageSummaryResponseDTO> partialResponse = new HashMap<>();
    partialResponse.put("s3_id", cdStageSummaryResponseUpcoming1);
    doReturn(getWrappedExecutionResponse(partialResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        upcomingStages, formattedUpcomingStages, scope, planExIdentifier);

    assertThat(formattedUpcomingStages)
        .hasSize(3)
        .containsExactly("s3 name : \n"
                + "     Service  :  s3 s name\n"
                + "     Environment  :  s3 e name\n"
                + "     Infrastructure Definition  :  s3 i name",
            "s4_id", "g1 name");
    // original set shouldn't be modified
    assertThat(upcomingStages).hasSize(3);
    formattedUpcomingStages.clear();

    // D: when no CD stages
    Set<StageSummary> upcomingStagesWithoutCDStages = new LinkedHashSet<>(List.of(genericStageSummaryUpcoming));

    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        upcomingStagesWithoutCDStages, formattedUpcomingStages, scope, planExIdentifier);

    assertThat(formattedUpcomingStages).hasSize(1).containsExactly("g1 name");
    // original set shouldn't be modified
    assertThat(upcomingStagesWithoutCDStages).hasSize(1);
    formattedUpcomingStages.clear();

    // E: when only CD stages
    Set<StageSummary> upcomingStagesWithOnlyCDStages =
        new LinkedHashSet<>(List.of(cdStageSummaryUpcoming1, cdStageSummaryUpcoming2));

    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        upcomingStagesWithOnlyCDStages, formattedUpcomingStages, scope, planExIdentifier);

    assertThat(formattedUpcomingStages)
        .hasSize(2)
        .containsExactly("s3 name : \n"
                + "     Service  :  s3 s name\n"
                + "     Environment  :  s3 e name\n"
                + "     Infrastructure Definition  :  s3 i name",
            "s4_id");
    // original set shouldn't be modified
    assertThat(upcomingStagesWithOnlyCDStages).hasSize(2);
    formattedUpcomingStages.clear();

    // F: unprocessed stages left
    Set<StageSummary> upcomingStagesWithUnknownStages = new LinkedHashSet<>(List.of(testStageSummary));

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
                               upcomingStagesWithUnknownStages, formattedUpcomingStages, scope, planExIdentifier))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error while formatting upcoming stages, unable to process [[TestStageSummary()]] stages");

    // original set shouldn't be modified
    assertThat(upcomingStagesWithUnknownStages).hasSize(1);
    formattedUpcomingStages.clear();

    // G: when failed CD response
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStagePlanCreationFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, planExIdentifier, List.of("s3_id", "s4_id"), true);
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStagePlanCreationFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, planExIdentifier, List.of("s4_id", "s3_id"), true);

    stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(
        upcomingStages, formattedUpcomingStages, scope, planExIdentifier);

    assertThat(formattedUpcomingStages).hasSize(3).containsExactly("s3 name", "s4_id", "g1 name");
    // original set shouldn't be modified
    assertThat(upcomingStages).hasSize(3);
    formattedUpcomingStages.clear();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSetFormattedSummaryOfRunningStages() throws IOException {
    // upcoming added to running set to check cases where running stages doesn't have execution identifiers
    Set<StageSummary> runningStages = new LinkedHashSet<>(List.of(
        cdStageSummaryFinished1, cdStageSummaryFinished2, genericStageSummaryFinished, genericStageSummaryUpcoming));

    // A: edge cases
    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        new HashSet<>(), new HashSet<>(), scope, planExIdentifier);

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
                               runningStages, null, scope, planExIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted running stages and scope and plan id is required");

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
                               runningStages, new HashSet<>(), null, planExIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted running stages and scope and plan id is required");

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
                               runningStages, new HashSet<>(), scope, "  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Formatted running stages and scope and plan id is required");

    Set<String> formattedRunningStages = new LinkedHashSet<>();
    when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, List.of("s1_ex_id", "s2_ex_id"), true))
        .thenReturn(mockExecutionCall);
    when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
             accountId, orgIdentifier, projectIdentifier, List.of("s2_ex_id", "s1_ex_id"), true))
        .thenReturn(mockExecutionCall);
    // B: when get complete CD response

    Map<String, CDStageSummaryResponseDTO> completeResponse = new HashMap<>();
    completeResponse.put("s1_ex_id", cdStageSummaryResponseFinished1);
    completeResponse.put("s2_ex_id", cdStageSummaryResponseFinished2);
    doReturn(getWrappedExecutionResponse(completeResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        runningStages, formattedRunningStages, scope, planExIdentifier);

    assertThat(formattedRunningStages)
        .hasSize(4)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id : \n"
                + "     Service  :  s2 s name\n"
                + "     Environment  :  s2 e name\n"
                + "     Infrastructure Definition  :  s2 i name",
            "g2_id", "g1 name");

    // original set shouldn't be modified
    assertThat(runningStages).hasSize(4);
    formattedRunningStages.clear();

    // C: when get partial CD response
    Map<String, CDStageSummaryResponseDTO> partialResponse = new HashMap<>();
    partialResponse.put("s1_ex_id", cdStageSummaryResponseFinished1);
    doReturn(getWrappedExecutionResponse(partialResponse)).when(mockExecutionCall).execute();

    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        runningStages, formattedRunningStages, scope, planExIdentifier);

    assertThat(formattedRunningStages)
        .hasSize(4)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id", "g2_id", "g1 name");

    // original set shouldn't be modified
    assertThat(runningStages).hasSize(4);
    formattedRunningStages.clear();

    // D: when no CD stages
    Set<StageSummary> runningStagesWithoutCDStages =
        new LinkedHashSet<>(List.of(genericStageSummaryFinished, genericStageSummaryUpcoming));

    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        runningStagesWithoutCDStages, formattedRunningStages, scope, planExIdentifier);

    assertThat(formattedRunningStages).hasSize(2).containsExactly("g2_id", "g1 name");
    // original set shouldn't be modified
    assertThat(runningStagesWithoutCDStages).hasSize(2);
    formattedRunningStages.clear();

    // E: when only CD stages
    Set<StageSummary> runningStagesWithOnlyCDStages =
        new LinkedHashSet<>(List.of(cdStageSummaryFinished1, cdStageSummaryFinished2));

    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        runningStagesWithOnlyCDStages, formattedRunningStages, scope, planExIdentifier);

    assertThat(formattedRunningStages)
        .hasSize(2)
        .containsExactly("s1 name : \n"
                + "     Service  :  s1 s name\n"
                + "     Environment  :  s1 e name\n"
                + "     Infrastructure Definition  :  s1 i name",
            "s2_id");
    // original set shouldn't be modified
    assertThat(runningStagesWithOnlyCDStages).hasSize(2);
    formattedRunningStages.clear();

    // F: unprocessed stages left
    Set<StageSummary> runningStagesWithUnknownStages = new LinkedHashSet<>(List.of(testStageSummary));

    assertThatThrownBy(()
                           -> stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
                               runningStagesWithUnknownStages, formattedRunningStages, scope, planExIdentifier))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error while formatting running stages, unable to process [[TestStageSummary()]] stages");

    // original set shouldn't be modified
    assertThat(runningStagesWithUnknownStages).hasSize(1);
    formattedRunningStages.clear();

    // G: when failed CD response
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, List.of("s1_ex_id", "s2_ex_id"), true);
    doThrow(new InvalidRequestException("dummy"))
        .when(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(
            accountId, orgIdentifier, projectIdentifier, List.of("s2_ex_id", "s1_ex_id"), true);

    stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(
        runningStages, formattedRunningStages, scope, planExIdentifier);

    assertThat(formattedRunningStages).hasSize(4).containsExactly("s1 name", "s2_id", "g2_id", "g1 name");
    // original set shouldn't be modified
    assertThat(runningStages).hasSize(4);
    formattedRunningStages.clear();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testIsGraphNodeOfCDDeploymentStageType() {
    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutNodeDTO.builder().build();
    assertThat(StageMetadataNotificationHelper.isGraphNodeOfCDDeploymentStageType(graphLayoutNodeDTO)).isFalse();

    graphLayoutNodeDTO.setNodeGroup("STAGE");
    assertThat(StageMetadataNotificationHelper.isGraphNodeOfCDDeploymentStageType(graphLayoutNodeDTO)).isFalse();

    graphLayoutNodeDTO.setNodeType("Deployment");
    assertThat(StageMetadataNotificationHelper.isGraphNodeOfCDDeploymentStageType(graphLayoutNodeDTO)).isTrue();

    graphLayoutNodeDTO.setModule("cd");
    assertThat(StageMetadataNotificationHelper.isGraphNodeOfCDDeploymentStageType(graphLayoutNodeDTO)).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testAddStageNodeToStagesSummary() {
    GraphLayoutNodeDTO customNode = GraphLayoutNodeDTO.builder()
                                        .name("custom stage name")
                                        .nodeIdentifier("custom stage id")
                                        .nodeExecutionId("custom stage ex id")
                                        .nodeGroup("STAGE")
                                        .build();
    GraphLayoutNodeDTO customLoopingNode = GraphLayoutNodeDTO.builder()
                                               .name("custom stage<+strategy.identifierPostFix>")
                                               .nodeIdentifier("custom_stage<+strategy.identifierPostFix>")
                                               .nodeGroup("STAGE")
                                               .build();
    assertThatThrownBy(() -> StageMetadataNotificationHelper.addStageNodeToStagesSummary(null, customNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input stage node and stages set is required for adding details of stage node to a stages set");
    assertThatThrownBy(() -> StageMetadataNotificationHelper.addStageNodeToStagesSummary(new HashSet<>(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input stage node and stages set is required for adding details of stage node to a stages set");

    GraphLayoutNodeDTO cdNode = GraphLayoutNodeDTO.builder()
                                    .name("cd stage name")
                                    .nodeIdentifier("cd stage id")
                                    .nodeExecutionId("cd stage ex id")
                                    .nodeGroup("STAGE")
                                    .nodeType("Deployment")
                                    .module("cd")
                                    .build();
    CDStageSummary cdStageSummary = CDStageSummary.builder().build();
    cdStageSummary.setStageIdentifier("cd stage id");
    cdStageSummary.setStageExecutionIdentifier("cd stage ex id");
    cdStageSummary.setStageName("cd stage name");

    GraphLayoutNodeDTO cdMultiNode = GraphLayoutNodeDTO.builder()
                                         .name("cd multi<+strategy.identifierPostFix>")
                                         .nodeIdentifier("cd_multi<+strategy.identifierPostFix>")
                                         .nodeGroup("STAGE")
                                         .nodeType("Deployment")
                                         .module(null)
                                         .build();
    CDStageSummary cdMultiStageSummary = CDStageSummary.builder().build();
    cdMultiStageSummary.setStageIdentifier("cd_multi");
    cdMultiStageSummary.setStageName("cd multi");

    GenericStageSummary genericStageSummary = GenericStageSummary.builder().build();
    genericStageSummary.setStageIdentifier("custom stage id");
    genericStageSummary.setStageExecutionIdentifier("custom stage ex id");
    genericStageSummary.setStageName("custom stage name");

    GenericStageSummary genericLoopingStageSummary = GenericStageSummary.builder().build();
    genericLoopingStageSummary.setStageIdentifier("custom_stage");
    genericLoopingStageSummary.setStageName("custom stage");

    Set<StageSummary> stages = new LinkedHashSet<>();
    StageMetadataNotificationHelper.addStageNodeToStagesSummary(stages, customNode);
    StageMetadataNotificationHelper.addStageNodeToStagesSummary(stages, cdNode);
    StageMetadataNotificationHelper.addStageNodeToStagesSummary(stages, cdMultiNode);
    StageMetadataNotificationHelper.addStageNodeToStagesSummary(stages, customLoopingNode);
    assertThat(stages).hasSize(4).containsExactly(
        genericStageSummary, cdStageSummary, cdMultiStageSummary, genericLoopingStageSummary);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFormatCDStageMetadata() {
    // edge case
    assertThatThrownBy(() -> StageMetadataNotificationHelper.formatCDStageMetadata(null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("CD stage details required for getting formatted stage summary");

    // when responseDTO null
    assertThat(StageMetadataNotificationHelper.formatCDStageMetadata(null, cdStageSummaryFinished1))
        .isEqualTo("s1 name");
    // defaulting to ids
    assertThat(StageMetadataNotificationHelper.formatCDStageMetadata(null, cdStageSummaryFinished2)).isEqualTo("s2_id");

    // when responseDTO empty
    CDStageSummaryResponseDTO cdStageSummaryResponseDTO = CDStageSummaryResponseDTO.builder().build();
    assertThat(
        StageMetadataNotificationHelper.formatCDStageMetadata(cdStageSummaryResponseDTO, cdStageSummaryFinished1))
        .isEqualTo("s1 name");
    assertThat(
        StageMetadataNotificationHelper.formatCDStageMetadata(cdStageSummaryResponseDTO, cdStageSummaryFinished2))
        .isEqualTo("s2_id");

    // normal cases
    assertThat(
        StageMetadataNotificationHelper.formatCDStageMetadata(cdStageSummaryResponseFinished1, cdStageSummaryFinished1))
        .isEqualTo("s1 name : \n"
            + "     Service  :  s1 s name\n"
            + "     Environment  :  s1 e name\n"
            + "     Infrastructure Definition  :  s1 i name");
    assertThat(
        StageMetadataNotificationHelper.formatCDStageMetadata(cdStageSummaryResponseFinished2, cdStageSummaryFinished2))
        .isEqualTo("s2_id : \n"
            + "     Service  :  s2 s name\n"
            + "     Environment  :  s2 e name\n"
            + "     Infrastructure Definition  :  s2 i name");
    assertThat(StageMetadataNotificationHelper.formatCDStageMetadata(
                   cdMultiStageSummaryResponseUpcoming1, cdMultiStageSummaryUpcoming1))
        .isEqualTo("multiService name : \n"
            + "     Services  :  [<+pipeline.variables.ser1>, <+pipeline.variables.ser2>]\n"
            + "     Environments  :  [env name]\n"
            + "     Environment Group  :  [env grp]\n"
            + "     Infrastructure Definitions  :  [infra name]");
    assertThat(
        StageMetadataNotificationHelper.formatCDStageMetadata(cdStageSummaryResponseFinished3, cdStageSummaryFinished2))
        .isEqualTo("s2_id : \n"
            + "     Service  :  s2 s name\n"
            + "     Environment  :  s2 e name\n"
            + "     Infrastructure Definition  :  s2 i name\n"
            + "     Artifact Display Name  :  s2 a name");
  }

  private Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO> > > getWrappedExecutionResponse(
      Map<String, CDStageSummaryResponseDTO> response) {
    ResponseDTO<Map<String, CDStageSummaryResponseDTO> > wrappedResponseDTO = ResponseDTO.newResponse(response);
    return Response.success(wrappedResponseDTO);
  }
}