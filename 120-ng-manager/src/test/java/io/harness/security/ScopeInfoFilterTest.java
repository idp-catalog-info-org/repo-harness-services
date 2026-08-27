/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.beans.ScopeLevel.ACCOUNT;
import static io.harness.beans.ScopeLevel.ORGANIZATION;
import static io.harness.beans.ScopeLevel.PROJECT;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NGCommonEntityConstants;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriInfo;
import lombok.Builder;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ScopeInfoFilterTest {
  public static final String ACCOUNT_QUERY_PARAM_VALUE = "account-query-param-value";
  public static final String ORG_QUERY_PARAM_VALUE = "org-query-param-value";
  public static final String PROJECT_QUERY_PARAM_VALUE = "project-query-param-value";
  public static final String ORG_PATH_PARAM_VALUE = "org-value";
  public static final String PROJECT_PATH_PARAM_VALUE = "project-value";
  private final ScopeInfoClient scopeInfoClient = mock(ScopeInfoClient.class);
  private final ContainerRequestContext containerRequestContext = mock(ContainerRequestContext.class);
  private final UriInfo uriInfo = mock(UriInfo.class);
  private final ScopeInfoFilter filter = new ScopeInfoFilter(scopeInfoClient);

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInvalidCaseWhereExtraQueryParamProvided() {
    TestPath requestPath = TestPath.builder()
                               .url("/organizations")
                               .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                               .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                               .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/organizations/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/organizations/all-organizations")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder().url("/aggregate/projects").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/aggregate/projects/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/aggregate/projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/aggregate/organizations/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/aggregate/organizations")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .projectQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .projectQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/all-projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .projectQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/project-count")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .projectQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/orgs")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .orgQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects/{project}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .orgQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects/{project}/secrets")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .orgQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isEqualTo(PROJECT_PATH_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(PROJECT);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects/{project}/secrets/{secret}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .orgQueryParam(PROJECT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isEqualTo(PROJECT_PATH_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInvalidCaseWhereSupportedQueryParamProvided() {
    TestPath requestPath =
        TestPath.builder().url("/organizations").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath =
        TestPath.builder().url("/organizations/{identifier}").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath =
        TestPath.builder().url("/organizations/all-organizations").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/aggregate/projects/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/aggregate/projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/aggregate/organizations/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath =
        TestPath.builder().url("/aggregate/organizations").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder()
                      .url("/projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/{identifier}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/{identifier}/move")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/all-projects")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/projects/project-count")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .orgQueryParam(ORG_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_QUERY_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder().url("/orgs").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder().url("/orgs/{org}").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ACCOUNT);

    requestPath = TestPath.builder().url("/orgs/{org}/projects").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath =
        TestPath.builder().url("/orgs/{org}/projects/{project}").accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE).build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isNull();
    assertThat(getScopeLevel(requestPath)).isEqualTo(ORGANIZATION);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects/{project}/secrets")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isEqualTo(PROJECT_PATH_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(PROJECT);

    requestPath = TestPath.builder()
                      .url("/orgs/{org}/projects/{project}/secrets/{secret}")
                      .accountQueryParam(ACCOUNT_QUERY_PARAM_VALUE)
                      .build();
    assertThat(getOrg(requestPath)).isEqualTo(ORG_PATH_PARAM_VALUE);
    assertThat(getProject(requestPath)).isEqualTo(PROJECT_PATH_PARAM_VALUE);
    assertThat(getScopeLevel(requestPath)).isEqualTo(PROJECT);
  }

  private ScopeLevel getScopeLevel(TestPath testPath) {
    String org = getOrg(testPath);
    String project = EmptyPredicate.isNotEmpty(org) ? getProject(testPath) : null;
    if (EmptyPredicate.isNotEmpty(project)) {
      return PROJECT;
    }
    if (EmptyPredicate.isNotEmpty(org)) {
      return ORGANIZATION;
    }
    return ACCOUNT;
  }

  private String getOrg(TestPath path) {
    mockContainerRequest(
        path.getUrl(), path.getAccountQueryParam(), path.getOrgQueryParam(), path.getProjectQueryParam());
    return filter.getOrgIdentifierFrom(containerRequestContext).orElse(null);
  }

  private String getProject(TestPath path) {
    mockContainerRequest(
        path.getUrl(), path.getAccountQueryParam(), path.getOrgQueryParam(), path.getProjectQueryParam());
    return filter.getProjectIdentifierFrom(containerRequestContext).orElse(null);
  }

  private void mockContainerRequest(
      String url, String accountQueryParam, String orgQueryParam, String projectQueryParam) {
    when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);

    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.addFirst(NGCommonEntityConstants.ACCOUNT_KEY, accountQueryParam);
    queryParameters.addFirst(NGCommonEntityConstants.ORG_KEY, orgQueryParam);
    queryParameters.addFirst(NGCommonEntityConstants.PROJECT_KEY, projectQueryParam);

    MultivaluedMap<String, String> pathParameters = new MultivaluedHashMap<>();
    String patternString = "\\{([^\\}]+)\\}";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(url);
    while (matcher.find()) {
      String group = matcher.group();
      String key = group.substring(1, group.length() - 1);
      pathParameters.addFirst(key, key + "-value");
    }

    String[] segments = url.startsWith("/") ? url.substring(1).split("/") : url.split("/");
    List<PathSegment> pathSegments = new ArrayList<>();
    Arrays.stream(segments).forEach(segment -> pathSegments.add(new TestPathSegment(segment)));

    when(uriInfo.getQueryParameters()).thenReturn(queryParameters);
    when(uriInfo.getPathParameters()).thenReturn(pathParameters);
    when(uriInfo.getPathSegments()).thenReturn(pathSegments);
  }

  static class TestPathSegment implements PathSegment {
    private String path;

    TestPathSegment(String path) {
      this.path = path;
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public MultivaluedMap<String, String> getMatrixParameters() {
      return new MultivaluedHashMap<>();
    }
  }

  @Data
  @Builder
  static class TestPath {
    String url;
    String accountQueryParam;
    String orgQueryParam;
    String projectQueryParam;
  }
}
