/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.sql.SQLException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTasksChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-id";
  static final String TEST_VALUE = "{\n"
      + "    \"accountIdentifier\" : \"kmpySmUISimoRrJL6NL73w\",\n"
      + "    \"identifier\" : \"49c40d3f-20e1-4b55-ba6d-cf8999592cee\",\n"
      + "    \"spec\" : "
      + "\"{\\\"apiVersion\\\":\\\"scaffolder.backstage.io/"
      + "v1beta3\\\",\\\"steps\\\":[{\\\"id\\\":\\\"template\\\",\\\"name\\\":\\\"Fetch Skeleton + "
      + "Template\\\",\\\"action\\\":\\\"fetch:template\\\",\\\"input\\\":{\\\"url\\\":\\\"./"
      + "skeleton\\\",\\\"copyWithoutRender\\\":[\\\".github/workflows/"
      + "*\\\"],\\\"values\\\":{\\\"component_id\\\":\\\"${{ parameters.component_id "
      + "}}\\\",\\\"description\\\":\\\"${{ parameters.description }}\\\",\\\"artifact_id\\\":\\\"${{ "
      + "parameters.component_id }}\\\",\\\"java_package_name\\\":\\\"${{ parameters.java_package_name "
      + "}}\\\",\\\"owner\\\":\\\"${{ parameters.owner }}\\\",\\\"destination\\\":\\\"${{ parameters.repoUrl | "
      + "parseRepoUrl "
      + "}}\\\",\\\"http_port\\\":8080}}},{\\\"id\\\":\\\"publish\\\",\\\"name\\\":\\\"Publish\\\",\\\"action\\\":"
      + "\\\"publish:github\\\",\\\"input\\\":{\\\"allowedHosts\\\":[\\\"github.com\\\"],\\\"description\\\":"
      + "\\\"This is ${{ parameters.component_id }}\\\",\\\"repoUrl\\\":\\\"${{ parameters.repoUrl "
      + "}}\\\"}},{\\\"id\\\":\\\"register\\\",\\\"name\\\":\\\"Register\\\",\\\"action\\\":\\\"catalog:register\\\","
      + "\\\"input\\\":{\\\"repoContentsUrl\\\":\\\"${{ steps.publish.output.repoContentsUrl "
      + "}}\\\",\\\"catalogInfoPath\\\":\\\"/"
      + "catalog-info.yaml\\\"}}],\\\"output\\\":{\\\"links\\\":[{\\\"title\\\":\\\"Repository\\\",\\\"url\\\":\\\"${"
      + "{ steps.publish.output.remoteUrl }}\\\"},{\\\"title\\\":\\\"Open in "
      + "catalog\\\",\\\"icon\\\":\\\"catalog\\\",\\\"entityRef\\\":\\\"${{ steps.register.output.entityRef "
      + "}}\\\"}]},\\\"parameters\\\":{\\\"component_id\\\":\\\"Sathish\\\",\\\"java_package_name\\\":\\\"io."
      + "sathish\\\",\\\"description\\\":\\\"Sathish\\\",\\\"owner\\\":\\\"group:default/"
      + "harness_account_all_users\\\",\\\"repoUrl\\\":\\\"github.com?owner=sathish-soundarapandian&repo=onboarding-"
      + "test\\\"},\\\"user\\\":{\\\"entity\\\":{\\\"metadata\\\":{\\\"namespace\\\":\\\"default\\\","
      + "\\\"annotations\\\":{\\\"backstage.io/managed-by-location\\\":\\\"user-https://app.harness.io/"
      + "\\\",\\\"backstage.io/managed-by-origin-location\\\":\\\"user-https://app.harness.io/\\\",\\\"harness.io/"
      + "entity-uuid\\\":\\\"lv0euRhKRCyiXWzS7pOg6g\\\"},\\\"name\\\":\\\"admin\\\",\\\"title\\\":\\\"Admin\\\","
      + "\\\"uid\\\":\\\"73db712f-589f-4da5-867b-ba3a59e6c7fc\\\",\\\"etag\\\":"
      + "\\\"d16a8ac798bc02d5336004b35a20cf7915ec8e97\\\"},\\\"kind\\\":\\\"User\\\",\\\"apiVersion\\\":"
      + "\\\"backstage.io/"
      + "v1alpha1\\\",\\\"spec\\\":{\\\"profile\\\":{\\\"displayName\\\":\\\"Admin\\\",\\\"email\\\":\\\"admin@"
      + "harness.io\\\"},\\\"memberOf\\\":[]},\\\"relations\\\":[{\\\"type\\\":\\\"memberOf\\\",\\\"targetRef\\\":"
      + "\\\"group:default/"
      + "harness_account_all_users\\\",\\\"target\\\":{\\\"kind\\\":\\\"group\\\",\\\"namespace\\\":\\\"default\\\","
      + "\\\"name\\\":\\\"harness_account_all_users\\\"}}]},\\\"ref\\\":\\\"user:default/"
      + "admin\\\"},\\\"templateInfo\\\":{\\\"entityRef\\\":\\\"template:default/"
      + "springboot-template\\\",\\\"baseUrl\\\":\\\"https://github.com/backstage/software-templates/tree/main/"
      + "scaffolder-templates/springboot-grpc-template/"
      + "\\\",\\\"entity\\\":{\\\"metadata\\\":{\\\"namespace\\\":\\\"default\\\",\\\"annotations\\\":{\\\"backstage."
      + "io/managed-by-location\\\":\\\"url:https://github.com/backstage/software-templates/tree/main/"
      + "scaffolder-templates/springboot-grpc-template/template.yaml\\\",\\\"backstage.io/"
      + "managed-by-origin-location\\\":\\\"url:https://github.com/backstage/software-templates/blob/main/"
      + "scaffolder-templates/springboot-grpc-template/template.yaml\\\",\\\"backstage.io/view-url\\\":\\\"https://"
      + "github.com/backstage/software-templates/tree/main/scaffolder-templates/springboot-grpc-template/"
      + "template.yaml\\\",\\\"backstage.io/edit-url\\\":\\\"https://github.com/backstage/software-templates/edit/"
      + "main/scaffolder-templates/springboot-grpc-template/template.yaml\\\",\\\"backstage.io/"
      + "source-location\\\":\\\"url:https://github.com/backstage/software-templates/tree/main/scaffolder-templates/"
      + "springboot-grpc-template/\\\"},\\\"name\\\":\\\"springboot-template\\\",\\\"title\\\":\\\"Spring Boot gRPC "
      + "Service\\\",\\\"description\\\":\\\"Create a simple microservice using gRPC and Spring Boot "
      + "Java\\\",\\\"tags\\\":[\\\"recommended\\\",\\\"java\\\",\\\"grpc\\\"],\\\"uid\\\":\\\"131a08c8-8297-46a5-"
      + "9c26-68d26661ccea\\\",\\\"etag\\\":\\\"00602d99404712d536349443d36515e21b139191\\\"}}}}\",\n"
      + "    \"status\" : \"failed\",\n"
      + "    \"taskCreatedAt\" : 1701129561002,\n"
      + "    \"lastHeartbeatAt\" : 1701129563199,\n"
      + "    \"taskCreatedBy\" : \"user:default/admin\"\n"
      + "}";

  AutoCloseable openMocks;
  private MockDataProvider provider;
  @InjectMocks BackstageScaffolderTasksChangeEventHandler backstageScaffolderTasksChangeEventHandler;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    provider = mock(MockDataProvider.class);

    final MockConnection connection = new MockConnection(provider);
    final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    FieldUtils.writeField(backstageScaffolderTasksChangeEventHandler, "dsl", dslContext, true);

    final MockResult[] mockResults = {new MockResult(1)};
    when(provider.execute(any())).thenReturn(mockResults);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    boolean result = backstageScaffolderTasksChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageScaffolderTasksChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = backstageScaffolderTasksChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageScaffolderTasksChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = backstageScaffolderTasksChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageScaffolderTasksChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
