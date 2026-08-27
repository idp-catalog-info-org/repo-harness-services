/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service;

import static io.harness.idp.common.Constants.IDP_PREFIX;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GIT_INTEGRATION_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_BRANCH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_PATH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_REPO;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.ReadValidationDetails;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IntegrationServiceImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks IntegrationServiceImpl integrationService;

  @Mock GitIntegrationServiceImpl gitIntegrationService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSave() {
    AbstractIntegrationRequest abstractIntegrationRequest = abstractIntegrationRequest();
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(gitIntegrationService.save(eq(TEST_ACCOUNT_IDENTIFIER),
             eq((GitIntegrationRequest) abstractIntegrationRequest.getRequest()), anyBoolean(), anyBoolean()))
        .thenReturn((GitIntegrationResponse) baseIntegrationResponse);

    BaseIntegrationResponse baseIntegrationResponseFunc =
        integrationService.save(TEST_ACCOUNT_IDENTIFIER, "git", abstractIntegrationRequest, true, false);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);

    baseIntegrationResponseFunc =
        integrationService.save(TEST_ACCOUNT_IDENTIFIER, "git", abstractIntegrationRequest, true, true);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);

    baseIntegrationResponseFunc =
        integrationService.save(TEST_ACCOUNT_IDENTIFIER, "git", abstractIntegrationRequest, false, false);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdate() {
    AbstractIntegrationRequest abstractIntegrationRequest = abstractIntegrationRequest();
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(gitIntegrationService.update(eq(TEST_ACCOUNT_IDENTIFIER), anyString(),
             eq((GitIntegrationRequest) abstractIntegrationRequest.getRequest()), anyBoolean()))
        .thenReturn((GitIntegrationResponse) baseIntegrationResponse);

    BaseIntegrationResponse baseIntegrationResponseFunc = integrationService.update(
        TEST_ACCOUNT_IDENTIFIER, "git", TEST_GIT_INTEGRATION_IDENTIFIER, abstractIntegrationRequest, true);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);

    baseIntegrationResponseFunc = integrationService.update(
        TEST_ACCOUNT_IDENTIFIER, "git", TEST_GIT_INTEGRATION_IDENTIFIER, abstractIntegrationRequest, false);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAll() {
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();
    List<GitIntegrationResponse> baseIntegrationResponses = new ArrayList<>();
    baseIntegrationResponses.add((GitIntegrationResponse) baseIntegrationResponse);

    when(gitIntegrationService.get(eq(TEST_ACCOUNT_IDENTIFIER), any(Pageable.class), any()))
        .thenReturn(baseIntegrationResponses);

    List<BaseIntegrationResponse> baseIntegrationResponsesFunc =
        integrationService.get(TEST_ACCOUNT_IDENTIFIER, "git", PageRequest.of(0, 10), null);
    assertEquals(baseIntegrationResponses, baseIntegrationResponsesFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGet() {
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER))
        .thenReturn((GitIntegrationResponse) baseIntegrationResponse);

    BaseIntegrationResponse baseIntegrationResponseFunc =
        integrationService.get(TEST_ACCOUNT_IDENTIFIER, "git", TEST_GIT_INTEGRATION_IDENTIFIER);
    assertEquals(baseIntegrationResponse, baseIntegrationResponseFunc);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testInvalidIntegration() {
    integrationService.get(TEST_ACCOUNT_IDENTIFIER, "cloud", TEST_GIT_INTEGRATION_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDelete() {
    doNothing().when(gitIntegrationService).delete(eq(TEST_ACCOUNT_IDENTIFIER), anyString(), anyBoolean());
    integrationService.delete(TEST_ACCOUNT_IDENTIFIER, "git");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private AbstractIntegrationRequest abstractIntegrationRequest() {
    return new AbstractIntegrationRequest().request(
        new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .readValidationDetails(new ReadValidationDetails().fileUrl(TEST_READ_VALIDATION_FILE_URL))
            .writeValidationDetails(new WriteValidationDetails()
                                        .repository(TEST_WRITE_VALIDATION_REPO)
                                        .branch(TEST_WRITE_VALIDATION_BRANCH)
                                        .path(TEST_WRITE_VALIDATION_PATH))
            .type(BaseIntegrationRequest.TypeEnum.GIT));
  }

  private BaseIntegrationResponse baseIntegrationResponse() {
    return new GitIntegrationResponse()
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .name(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .type(BaseIntegrationResponse.TypeEnum.GIT);
  }
}
