/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.GONZALO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.fme.FeatureFlag;
import io.harness.fme.FmeResponse;
import io.harness.fme.governance.FmeGovernanceResult;
import io.harness.fme.governance.GovernanceStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.rule.Owner;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeFeatureFlagNotFoundException;
import io.harness.steps.fme.exception.FmeInternalServerErrorException;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeApiExecutorTest extends CategoryTest {
  @Mock private NGLogCallback logCallback;

  private ExecutionContext context;

  @Before
  public void setup() {
    context = ExecutionContext.builder()
                  .logCallback(logCallback)
                  .flagName("test-flag")
                  .environment("production")
                  .operationName("create")
                  .build();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_success() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    FeatureFlag featureFlag = FeatureFlag.builder().name("test-flag").build();
    Response<FeatureFlag> response = Response.success(featureFlag);
    when(call.execute()).thenReturn(response);

    FeatureFlag result = FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("test-flag");
    verify(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_successWithGovernanceWarning() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    FmeGovernanceResult governance = FmeGovernanceResult.builder().status(GovernanceStatus.WARNING).id("gov-1").build();
    FeatureFlag featureFlag = FeatureFlag.builder().name("test-flag").governance(governance).build();
    Response<FeatureFlag> response = Response.success(featureFlag);
    when(call.execute()).thenReturn(response);

    FeatureFlag result = FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND);

    assertThat(result).isNotNull();
    assertThat(result.getGovernance()).isNotNull();
    // Verify governance warning is logged
    verify(logCallback).saveExecutionLog("=== Governance Warning ===", LogLevel.WARN);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_notFound_throwFlagNotFound() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    Response<FeatureFlag> response =
        Response.error(404, ResponseBody.create(MediaType.parse("application/json"), "Not found"));
    when(call.execute()).thenReturn(response);

    assertThatThrownBy(() -> FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND))
        .isInstanceOf(FmeFeatureFlagNotFoundException.class);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_notFound_returnNull() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    Response<FeatureFlag> response =
        Response.error(404, ResponseBody.create(MediaType.parse("application/json"), "Not found"));
    when(call.execute()).thenReturn(response);

    FeatureFlag result = FmeApiExecutor.execute(call, context, NotFoundBehavior.RETURN_NULL);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_policyDenied_499() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    String errorBody = "{"
        + "\"code\": 499,"
        + "\"message\": \"policy denied\","
        + "\"details\": {"
        + "  \"id\": \"gov-1\","
        + "  \"status\": \"error\","
        + "  \"action\": \"onstep\","
        + "  \"type\": \"featureFlag\","
        + "  \"details\": [{"
        + "    \"id\": \"ps-1\","
        + "    \"name\": \"Test Policy Set\","
        + "    \"status\": \"error\","
        + "    \"details\": [{"
        + "      \"id\": \"p-1\","
        + "      \"name\": \"Naming Policy\","
        + "      \"status\": \"error\","
        + "      \"severity\": \"high\","
        + "      \"denyMessages\": [\"flag name must start with ff_\"]"
        + "    }]"
        + "  }]"
        + "}"
        + "}";
    Response<FeatureFlag> response =
        Response.error(499, ResponseBody.create(MediaType.parse("application/json"), errorBody));
    when(call.execute()).thenReturn(response);

    assertThatThrownBy(() -> FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Review the policy requirements");

    verify(logCallback).saveExecutionLog("=== Policy Denied ===", LogLevel.ERROR);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_otherError() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    Response<FeatureFlag> response =
        Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Internal server error"));
    when(call.execute()).thenReturn(response);

    assertThatThrownBy(() -> FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND))
        .isInstanceOf(FmeInternalServerErrorException.class);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecute_ioException() throws IOException {
    Call<FeatureFlag> call = mock(Call.class);
    when(call.execute()).thenThrow(new IOException("Network error"));

    assertThatThrownBy(() -> FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND))
        .isInstanceOf(FmeInternalServerErrorException.class)
        .hasMessageContaining("Failed to communicate with FME API");
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteWrapped_success() throws IOException {
    Call<FmeResponse<Boolean>> call = mock(Call.class);
    FmeResponse<Boolean> fmeResponse = FmeResponse.<Boolean>builder().entity(true).build();
    Response<FmeResponse<Boolean>> response = Response.success(fmeResponse);
    when(call.execute()).thenReturn(response);

    Boolean result = FmeApiExecutor.executeWrapped(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND);

    assertThat(result).isTrue();
    verify(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteWrapped_successWithGovernanceWarning() throws IOException {
    Call<FmeResponse<Boolean>> call = mock(Call.class);
    FmeGovernanceResult governance = FmeGovernanceResult.builder().status(GovernanceStatus.WARNING).id("gov-1").build();
    FmeResponse<Boolean> fmeResponse = FmeResponse.<Boolean>builder().entity(true).governance(governance).build();
    Response<FmeResponse<Boolean>> response = Response.success(fmeResponse);
    when(call.execute()).thenReturn(response);

    Boolean result = FmeApiExecutor.executeWrapped(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND);

    assertThat(result).isTrue();
    verify(logCallback).saveExecutionLog("=== Governance Warning ===", LogLevel.WARN);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteWrapped_policyDenied_499() throws IOException {
    Call<FmeResponse<Boolean>> call = mock(Call.class);
    String errorBody = "{"
        + "\"code\": 499,"
        + "\"message\": \"policy denied\","
        + "\"details\": {"
        + "  \"id\": \"gov-1\","
        + "  \"status\": \"error\","
        + "  \"action\": \"onstep\","
        + "  \"type\": \"featureFlag\","
        + "  \"details\": [{"
        + "    \"id\": \"ps-1\","
        + "    \"name\": \"Test Policy Set\","
        + "    \"status\": \"error\","
        + "    \"details\": [{"
        + "      \"id\": \"p-1\","
        + "      \"name\": \"Naming Policy\","
        + "      \"status\": \"error\","
        + "      \"severity\": \"high\","
        + "      \"denyMessages\": [\"flag name must start with ff_\"]"
        + "    }]"
        + "  }]"
        + "}"
        + "}";
    Response<FmeResponse<Boolean>> response =
        Response.error(499, ResponseBody.create(MediaType.parse("application/json"), errorBody));
    when(call.execute()).thenReturn(response);

    assertThatThrownBy(() -> FmeApiExecutor.executeWrapped(call, context, NotFoundBehavior.THROW_FLAG_NOT_FOUND))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Review the policy requirements");

    verify(logCallback).saveExecutionLog("=== Policy Denied ===", LogLevel.ERROR);
  }
}
