/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter;

import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.PipelineServiceAccountAccessFilter;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineServiceAccountAccessFilterTest extends CategoryTest {
  private static final String ACCOUNT_A = "accountA";
  private static final String ACCOUNT_B = "accountB";

  private final PipelineServiceAccountAccessFilter filter = new PipelineServiceAccountAccessFilter(true);

  @After
  public void tearDown() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  // --- UserPrincipal (UI session JWT + PAT via X-Api-Key) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyUserPrincipalWhenAccountMismatch() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAllowUserPrincipalWhenAccountMatches() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_A));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyUserPrincipalWithNullAccount() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", null));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyUserPrincipalWithBlankAccount() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ""));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  // --- ServiceAccountPrincipal (service account PAT via X-Api-Key) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAllowServiceAccountPrincipalWhenAccountMatches() {
    SecurityContextBuilder.setContext(new ServiceAccountPrincipal("sa", "sa@harness.io", "sa", ACCOUNT_A));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyServiceAccountPrincipalWhenAccountMismatch() {
    SecurityContextBuilder.setContext(new ServiceAccountPrincipal("sa", "sa@harness.io", "sa", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyServiceAccountPrincipalWithNullAccount() {
    SecurityContextBuilder.setContext(new ServiceAccountPrincipal("sa", "sa@harness.io", "sa", null));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  // --- ServicePrincipal (internal service-to-service calls — must be skipped) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAllowServicePrincipalRegardlessOfAccount() {
    SecurityContextBuilder.setContext(new ServicePrincipal("pipeline-service"));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_B)))
        .doesNotThrowAnyException();
  }

  // --- ApiKeyPrincipal (legacy DTO, no accountId — must be skipped, not blocked) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipApiKeyPrincipalAsNonAccountScoped() {
    // In practice NextGenAuthenticationFilter never sets ApiKeyPrincipal for pipeline endpoints;
    // X-Api-Key resolves to UserPrincipal or ServiceAccountPrincipal. This confirms the filter
    // doesn't accidentally block the legacy principal type.
    SecurityContextBuilder.setContext(new ApiKeyPrincipal("somekey"));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  // --- No principal (auth disabled / public endpoint) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAllowWhenNoPrincipalPresent() {
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  // --- No account param in request ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAllowWhenNoAccountParamPresent() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_A));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, null)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldTreatBlankAccountParamAsNoAccount() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, "  ")))
        .doesNotThrowAnyException();
  }

  // --- All three accountId param key variants ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldEnforceAccountIdParamKey() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_ID, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldEnforceLegacyAccountParamKey() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  // --- Path param vs query param ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldEnforceAccountInPathParam() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithPathParam(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyWhenQueryAndPathParamsDiffer() {
    // Both params are non-blank and differ → attacker smuggles a second account; must be denied.
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_A));
    ContainerRequestContext req =
        requestWith(params(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A), // query = ACCOUNT_A
            params(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_B)); // path  = ACCOUNT_B
    assertThatThrownBy(() -> filter.filter(req)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldDenyWhenAccountIdentifierAndAccountIdDiffer() {
    // Cross-key exploit: accountIdentifier=A passes filter while resource reads accountId=B.
    // Both keys are present with different values → must be denied regardless of which key the
    // resource binds to.
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_A));
    MultivaluedMap<String, String> query = new MultivaluedHashMap<>();
    query.putSingle(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_A); // accountIdentifier=A
    query.putSingle(NGCommonEntityConstants.ACCOUNT_ID, ACCOUNT_B); // accountId=B
    ContainerRequestContext req = requestWith(query, new MultivaluedHashMap<>());
    assertThatThrownBy(() -> filter.filter(req)).isInstanceOf(AccessDeniedException.class);
  }

  // --- Exempt paths (support-user / ServicePrincipal gated, must not be blocked) ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipEnforcementForAdminPath() {
    SecurityContextBuilder.setContext(new UserPrincipal("support", "support@harness.io", "support", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithPath("/admin/block-execution", ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipEnforcementForPipelineInlineHcMigrationsPath() {
    SecurityContextBuilder.setContext(new UserPrincipal("support", "support@harness.io", "support", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithPath("/pipeline/inline-hc-migrations/rollback", ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipEnforcementForInputSetInlineHcMigrationsPath() {
    SecurityContextBuilder.setContext(new UserPrincipal("support", "support@harness.io", "support", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithPath("/input-set/inline-hc-migrations/rollback", ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipEnforcementForInlineHcMigrationPath() {
    SecurityContextBuilder.setContext(new UserPrincipal("support", "support@harness.io", "support", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithPath("/inline-hc-migration/rollback", ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldSkipEnforcementForRetentionPath() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatCode(() -> filter.filter(requestWithPath("/pipelines/retention/config", ACCOUNT_A)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldStillEnforceForNonExemptPathWithMismatch() {
    // Ensure the exemption list doesn't accidentally widen — a normal pipeline path is still blocked.
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", ACCOUNT_B));
    assertThatThrownBy(() -> filter.filter(requestWithPath("/pipelines", ACCOUNT_A)))
        .isInstanceOf(AccessDeniedException.class);
  }

  // --- Case sensitivity ---

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldMatchAccountIdCaseSensitively() {
    SecurityContextBuilder.setContext(new UserPrincipal("user", "user@harness.io", "user", "AccountA"));
    assertThatThrownBy(() -> filter.filter(requestWithQuery(NGCommonEntityConstants.ACCOUNT_KEY, "accounta")))
        .isInstanceOf(AccessDeniedException.class);
  }

  // --- Helpers ---

  private static ContainerRequestContext requestWithPath(String path, String accountId) {
    // Strip leading slash to match real Jersey UriInfo.getPath() behaviour.
    String jerseyPath = path.startsWith("/") ? path.substring(1) : path;
    MultivaluedMap<String, String> queryParams = params(NGCommonEntityConstants.ACCOUNT_KEY, accountId);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getPath()).thenReturn(jerseyPath);
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(ctx.getMethod()).thenReturn("GET");
    return ctx;
  }

  private static ContainerRequestContext requestWithQuery(String key, String value) {
    return requestWith(params(key, value), new MultivaluedHashMap<>());
  }

  private static ContainerRequestContext requestWithPathParam(String key, String value) {
    return requestWith(new MultivaluedHashMap<>(), params(key, value));
  }

  private static ContainerRequestContext requestWith(
      MultivaluedMap<String, String> queryParams, MultivaluedMap<String, String> pathParams) {
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    when(uriInfo.getPathParameters()).thenReturn(pathParams);
    when(uriInfo.getPath()).thenReturn("pipelines");
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(ctx.getMethod()).thenReturn("GET");
    return ctx;
  }

  private static MultivaluedMap<String, String> params(String key, String value) {
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    if (value != null) {
      map.putSingle(key, value);
    }
    return map;
  }
}
