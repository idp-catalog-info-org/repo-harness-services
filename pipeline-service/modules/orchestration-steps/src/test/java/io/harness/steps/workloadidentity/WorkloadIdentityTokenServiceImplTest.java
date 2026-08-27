/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.workloadidentity;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionService;
import io.harness.harnessid.client.HarnessIdClientService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.IdentityEntry;
import io.harness.pms.contracts.ambiance.IdentityExecutionContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationRequest;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class WorkloadIdentityTokenServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EngineExpressionService engineExpressionService;
  @Mock private HarnessIdClientService workloadIdentityService;
  @Mock private MetricService metricService;
  @Mock Ambiance ambiance;
  @InjectMocks private WorkloadIdentityTokenServiceImpl workloadIdentityTokenService;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensReturnsTokenPerIdentity() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put("AWS_ID_TOKEN",
        IdentitySpec.builder()
            .audience(ParameterField.createValueField("sts.amazonaws.com"))
            .subjectTemplate("repo:<+pipeline.identifier>")
            .build());
    identities.put("GCP_ID_TOKEN", IdentitySpec.builder().audience(ParameterField.createValueField("gcp-aud")).build());

    // subjectTemplate must resolve to a concrete (non-expression) value: the shared resolve guard now skips an
    // identity whose subject is still a <+...> after rendering, so resolve it here for AWS_ID_TOKEN to register.
    doReturn("repo:my-pipeline")
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), eq("repo:<+pipeline.identifier>"));
    doReturn(WorkloadRegistrationResponse.newBuilder().setWorkloadToken("wt").build())
        .when(workloadIdentityService)
        .register(any());
    doReturn("aws-token").when(workloadIdentityService).generateIdToken("wt", "sts.amazonaws.com", null);
    doReturn("gcp-token").when(workloadIdentityService).generateIdToken("wt", "gcp-aud", null);

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokens(ambiance, identities);

    assertThat(result).containsEntry("AWS_ID_TOKEN", "aws-token").containsEntry("GCP_ID_TOKEN", "gcp-token");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensSkipsDisabledAndBlankAudienceAndForwardsTokenMode() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put("DISABLED",
        IdentitySpec.builder()
            .audience(ParameterField.createValueField("aud"))
            .disabled(ParameterField.createValueField(true))
            .build());
    identities.put("NO_AUD", IdentitySpec.builder().build());
    // CLIENT_ASSERTION is no longer skipped; the mode is forwarded to HarnessID.
    identities.put("CLIENT_ASSERT",
        IdentitySpec.builder()
            .audience(ParameterField.createValueField("aud"))
            .tokenMode(TokenMode.CLIENT_ASSERTION)
            .build());
    identities.put("VALID", IdentitySpec.builder().audience(ParameterField.createValueField("aud")).build());

    doReturn(WorkloadRegistrationResponse.newBuilder().setWorkloadToken("wt").build())
        .when(workloadIdentityService)
        .register(any());
    doReturn("ok-ca").when(workloadIdentityService).generateIdToken("wt", "aud", "CLIENT_ASSERTION");
    doReturn("ok").when(workloadIdentityService).generateIdToken("wt", "aud", null);

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokens(ambiance, identities);

    assertThat(result).containsOnlyKeys("CLIENT_ASSERT", "VALID");
    assertThat(result).containsEntry("CLIENT_ASSERT", "ok-ca").containsEntry("VALID", "ok");
    verify(workloadIdentityService, times(2)).register(any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensFromContextMultiAudienceUsesIndexKey() {
    // Multi-audience: keys are identityName__<SANITIZED_AUDIENCE> where non-[A-Za-z0-9] chars → '_', upper-cased.
    // e.g. "sts.amazonaws.com" → "STS_AMAZONAWS_COM", "//iam.googleapis.com/..." → "__IAM_GOOGLEAPIS_COM_..."
    // Makes keys stable (audience-derived, not position-derived) and always valid shell identifiers.
    IdentityEntry entry = IdentityEntry.newBuilder()
                              .setIdentityName("aws")
                              .setWorkloadToken("wt")
                              .addAudiences("sts.amazonaws.com")
                              .addAudiences("//iam.googleapis.com/projects/123")
                              .build();
    Ambiance ambianceWithCtx =
        Ambiance.newBuilder()
            .setIdentityExecutionContext(IdentityExecutionContext.newBuilder().putIdentities("aws", entry).build())
            .build();

    doReturn("token-0").when(workloadIdentityService).generateIdToken("wt", "sts.amazonaws.com", null);
    doReturn("token-1").when(workloadIdentityService).generateIdToken("wt", "//iam.googleapis.com/projects/123", null);

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokensFromContext(ambianceWithCtx);

    // Keys are identityName__<SANITIZED_AUDIENCE>: dots/slashes → '_', upper-cased
    assertThat(result).containsOnlyKeys("aws__STS_AMAZONAWS_COM", "aws____IAM_GOOGLEAPIS_COM_PROJECTS_123");
    assertThat(result).containsEntry("aws__STS_AMAZONAWS_COM", "token-0");
    assertThat(result).containsEntry("aws____IAM_GOOGLEAPIS_COM_PROJECTS_123", "token-1");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensFromContextSingleAudienceUsesPlainKey() {
    // Single-audience identity: env var key = identityName (no __audience suffix, backward compat)
    IdentityEntry entry =
        IdentityEntry.newBuilder().setIdentityName("gcp").setWorkloadToken("wt-gcp").addAudiences("gcp-aud").build();
    Ambiance ambianceWithCtx =
        Ambiance.newBuilder()
            .setIdentityExecutionContext(IdentityExecutionContext.newBuilder().putIdentities("gcp", entry).build())
            .build();

    doReturn("gcp-token").when(workloadIdentityService).generateIdToken("wt-gcp", "gcp-aud", null);

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokensFromContext(ambianceWithCtx);

    assertThat(result).containsOnlyKeys("gcp");
    assertThat(result).containsEntry("gcp", "gcp-token");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensFromContextSkipsDisabledAndEmptyToken() {
    // Disabled entry and missing workload token are both skipped
    IdentityEntry disabled = IdentityEntry.newBuilder()
                                 .setIdentityName("dis")
                                 .setWorkloadToken("wt")
                                 .addAudiences("aud")
                                 .setDisabled(true)
                                 .build();
    IdentityEntry noToken = IdentityEntry.newBuilder().setIdentityName("empty").addAudiences("aud").build();
    Ambiance ambianceWithCtx = Ambiance.newBuilder()
                                   .setIdentityExecutionContext(IdentityExecutionContext.newBuilder()
                                                                    .putIdentities("dis", disabled)
                                                                    .putIdentities("empty", noToken)
                                                                    .build())
                                   .build();

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokensFromContext(ambianceWithCtx);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateIdentityTokensSkipsOnFailure() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put("AWS_ID_TOKEN", IdentitySpec.builder().audience(ParameterField.createValueField("aud")).build());

    doThrow(new RuntimeException("register failed")).when(workloadIdentityService).register(any());

    Map<String, String> result = workloadIdentityTokenService.generateIdentityTokens(ambiance, identities);

    assertThat(result).isEmpty();
  }

  // ---- register-only primitive (CI Option B broker flow) ----

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testRegisterIdentitiesReturnsWorkloadTokenAndDoesNotMint() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put(
        "AWS_ID_TOKEN", IdentitySpec.builder().audience(ParameterField.createValueField("sts.amazonaws.com")).build());

    ArgumentCaptor<WorkloadRegistrationRequest> captor = ArgumentCaptor.forClass(WorkloadRegistrationRequest.class);
    doReturn(WorkloadRegistrationResponse.newBuilder().setWorkloadToken("wt").build())
        .when(workloadIdentityService)
        .register(captor.capture());

    Map<String, RegisteredWorkloadIdentity> result =
        workloadIdentityTokenService.registerIdentities(ambiance, identities, 3600);

    assertThat(result).containsKey("AWS_ID_TOKEN");
    assertThat(result.get("AWS_ID_TOKEN").getWorkloadToken()).isEqualTo("wt");
    assertThat(result.get("AWS_ID_TOKEN").getAudience()).isEqualTo("sts.amazonaws.com");
    // The requested TTL is forwarded to HarnessID on the register request (bounds the workload token).
    assertThat(captor.getValue().getTtlSeconds()).isEqualTo(3600);
    // Register-only: it hands back the workload token and must NOT mint the OIDC token (the broker does that).
    verify(workloadIdentityService, never()).generateIdToken(any(), any(), any());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testRegisterIdentitiesSkipsUnresolvedAudience() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put("AWS_ID_TOKEN",
        IdentitySpec.builder()
            .audience(ParameterField.createExpressionField(true, "<+pipeline.variables.aud>", null, true))
            .build());
    // renderExpression echoes the expression back -> audience stays a <+...> -> skip, never register.
    doAnswer(inv -> inv.getArgument(1)).when(engineExpressionService).renderExpression(eq(ambiance), any());

    Map<String, RegisteredWorkloadIdentity> result =
        workloadIdentityTokenService.registerIdentities(ambiance, identities, 3600);

    assertThat(result).isEmpty();
    verify(workloadIdentityService, never()).register(any());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testRegisterIdentitiesSkipsUnresolvedSubjectTemplate() {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    identities.put("AWS_ID_TOKEN",
        IdentitySpec.builder()
            .audience(ParameterField.createValueField("sts.amazonaws.com"))
            .subjectTemplate("<+pipeline.variables.sub>")
            .build());
    // subjectTemplate stays a <+...> after rendering -> skip so a raw expression is never stamped as sub.
    doAnswer(inv -> inv.getArgument(1)).when(engineExpressionService).renderExpression(eq(ambiance), any());

    Map<String, RegisteredWorkloadIdentity> result =
        workloadIdentityTokenService.registerIdentities(ambiance, identities, 3600);

    assertThat(result).isEmpty();
    verify(workloadIdentityService, never()).register(any());
  }
}
