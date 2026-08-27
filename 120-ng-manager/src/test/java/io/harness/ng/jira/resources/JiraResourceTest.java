/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.jira.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.ACCESS_CONNECTOR_PERMISSION;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.jira.resources.service.JiraResourceService;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class JiraResourceTest extends CategoryTest {
  @Mock JiraResourceService jiraResourceService;
  @Mock AccessControlClient accessControlClient;
  @InjectMocks JiraResource jiraResource;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String CONNECTOR_REF = "connId";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateCredentialsChecksConnectorAccess() {
    when(jiraResourceService.validateCredentials(any(), any(), any())).thenReturn(true);

    jiraResource.validateCredentials(CONNECTOR_REF, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null);

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(ResourceTypes.CONNECTOR, CONNECTOR_REF), ACCESS_CONNECTOR_PERMISSION);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetProjectsThrowsWhenNoConnectorAccess() {
    doThrow(new InvalidRequestException("User does not have access to connector"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    assertThatThrownBy(() -> jiraResource.getProjects(CONNECTOR_REF, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class);

    verify(jiraResourceService, times(0)).getProjects(any(), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSearchUserThrowsWhenNoConnectorAccess() {
    doThrow(new InvalidRequestException("User does not have access to connector"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    assertThatThrownBy(
        () -> jiraResource.getUserSearch(PROJ_IDENTIFIER, ORG_IDENTIFIER, ACCOUNT_ID, CONNECTOR_REF, "query", "0"))
        .isInstanceOf(InvalidRequestException.class);
  }
}
