/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Top-level authorization gate for the Pipeline Service REST surface.
 *
 * <p>The {@code accountIdentifier}/{@code accountId} is a client-supplied request parameter; the token/routingId only
 * authenticate <i>who</i> the caller is, not <i>which account</i> they belong to. Without this check an authenticated
 * user of one account could read another account's pipelines, executions, input-sets, and approvals by changing that
 * parameter. This filter runs after authentication ({@link Priorities#AUTHORIZATION}) and rejects any request whose
 * account-scoped principal does not match the requested account with a 403.
 *
 * <p>Internal service principals (no account) are not account-scoped and are left to the per-resource RBAC checks.
 *
 * <p><b>Exempted path prefixes</b> — these have their own tighter auth guards that already handle cross-account
 * legitimately and must not be blocked by this filter:
 * <ul>
 *   <li>{@code admin/*} — requires {@code ServicePrincipal} (CG-manager only) or Harness support-user identity;
 *       both are intentional cross-account operations handled by {@code checkAccessPermissionsForAccountOverrides}
 *       and {@code checkUserAuthorization} in {@code PipelineAdminResourceImpl}.</li>
 *   <li>{@code pipeline/inline-hc-migrations/*}, {@code input-set/inline-hc-migrations/*},
 *       {@code inline-hc-migration/*} — Harness support-user-gated migration/rollback tooling that must operate
 *       on arbitrary accounts.</li>
 *   <li>{@code pipelines/retention/*} — retention configuration endpoints with no auth guard; exempt until
 *       proper auth is wired up (tracked separately).</li>
 * </ul>
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
@OwnedBy(PIPELINE)
public class PipelineServiceAccountAccessFilter implements ContainerRequestFilter {
  private static final String ACCESS_DENIED_MESSAGE = "Not authorized to access the requested account";

  private static final List<String> ACCOUNT_PARAM_KEYS =
      List.of(NGCommonEntityConstants.ACCOUNT_KEY, NGCommonEntityConstants.ACCOUNT_ID, NGCommonEntityConstants.ACCOUNT);

  // Paths with their own cross-account auth guards — exempt from tenant-isolation enforcement here.
  private static final List<String> EXEMPT_PATH_PREFIXES = List.of("admin/", "pipeline/inline-hc-migrations/",
      "input-set/inline-hc-migrations/", "inline-hc-migration/", "pipelines/retention/");

  private final boolean enableTenantIsolationFilter;

  public PipelineServiceAccountAccessFilter(boolean enableTenantIsolationFilter) {
    this.enableTenantIsolationFilter = enableTenantIsolationFilter;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!enableTenantIsolationFilter || isExemptPath(requestContext.getUriInfo().getPath())) {
      return;
    }
    List<String> requestedAccounts = resolveAllProvidedAccounts(requestContext.getUriInfo());
    if (!requestedAccounts.isEmpty()) {
      enforceAccountAccess(requestContext, requestedAccounts);
    }
  }

  private static boolean isExemptPath(String path) {
    // Jersey's UriInfo.getPath() returns paths without a leading slash; strip it defensively.
    String normalised = path.startsWith("/") ? path.substring(1) : path;
    return EXEMPT_PATH_PREFIXES.stream().anyMatch(normalised::startsWith);
  }

  private void enforceAccountAccess(ContainerRequestContext requestContext, List<String> requestedAccounts) {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (!isAccountScoped(principal)) {
      return;
    }
    String principalAccount = getPrincipalAccountId(principal);
    boolean denied =
        !isNotBlank(principalAccount) || requestedAccounts.stream().anyMatch(a -> !a.equals(principalAccount));
    if (denied) {
      log.warn("Account access denied: principal account [{}] does not match requested accounts {} for {} /{}",
          principalAccount, requestedAccounts, requestContext.getMethod(), requestContext.getUriInfo().getPath());
      throw new AccessDeniedException(ACCESS_DENIED_MESSAGE, WingsException.USER);
    }
  }

  private static List<String> resolveAllProvidedAccounts(UriInfo uriInfo) {
    MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
    MultivaluedMap<String, String> pathParams = uriInfo.getPathParameters();
    return Stream
        .concat(ACCOUNT_PARAM_KEYS.stream().map(queryParams::getFirst),
            ACCOUNT_PARAM_KEYS.stream().map(pathParams::getFirst))
        .filter(PipelineServiceAccountAccessFilter::isNotBlank)
        .distinct()
        .toList();
  }

  private static boolean isAccountScoped(Principal principal) {
    return principal instanceof UserPrincipal || principal instanceof ServiceAccountPrincipal;
  }

  private static String getPrincipalAccountId(Principal principal) {
    if (principal instanceof UserPrincipal userPrincipal) {
      return userPrincipal.getAccountId();
    }
    if (principal instanceof ServiceAccountPrincipal serviceAccountPrincipal) {
      return serviceAccountPrincipal.getAccountId();
    }
    return null;
  }

  private static boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }
}
