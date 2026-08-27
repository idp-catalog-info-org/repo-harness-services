/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.DockerRunnerResourceImpl;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.utils.system.SystemWrapper;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DockerRunnerResourceTest extends CategoryTest {
  @InjectMocks DockerRunnerResourceImpl dockerRunnerResource;
  private MockedStatic<CGRestUtils> aStatic;
  @Mock AccountClient accountClient;
  final String free_command = "docker run  --cpus=1 --memory=2g \\\n"
      + "  -e DELEGATE_NAME=docker-delegate \\\n"
      + "  -e NEXT_GEN=\"true\" \\\n"
      + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
      + "  -e ACCOUNT_ID=9UuUfLwaQ-6ZowvbG7qtLQ \\\n"
      + "  -e DELEGATE_TOKEN=mytoken= \\\n"
      + "  -e DELEGATE_TAGS=\"\" \\\n"
      + "  -e MANAGER_HOST_AND_PORT=https://app.harness.io/gratis harness/delegate:23.12.81604";
  final String qa_command = "docker run  --cpus=1 --memory=2g \\\n"
      + "  -e DELEGATE_NAME=docker-delegate \\\n"
      + "  -e NEXT_GEN=\"true\" \\\n"
      + "  -e DELEGATE_TYPE=\"DOCKER\" \\\n"
      + "  -e ACCOUNT_ID=h61p38AZSV6MzEkpWWBtew \\\n"
      + "  -e DELEGATE_TOKEN=mytoken= \\\n"
      + "  -e DELEGATE_TAGS=\"\" \\\n"
      + "  -e MANAGER_HOST_AND_PORT=https://qa.harness.io us.gcr.io/gcr-play/delegate:23.12.81806";

  @Before
  public void setup() {
    aStatic = mockStatic(CGRestUtils.class);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGet_FREE() throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("command", free_command);
    try (MockedStatic<SystemWrapper> mockStatic = mockStatic(SystemWrapper.class)) {
      mockStatic.when(() -> SystemWrapper.getenv(any())).thenReturn("free");
      when(CGRestUtils.getResponse(any())).thenReturn(map);
      RestResponse<String> res = dockerRunnerResource.get("abcde", "os", "arch");
      assertThat(res.getResource())
          .isEqualTo(
              "wget https://raw.githubusercontent.com/harness/harness-docker-runner/master/scripts/script-free.sh -O "
              + "script.sh\n"
              + "sh script.sh abcde mytoken= 23.12.81604");
    }
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGet_QA() throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("command", qa_command);
    try (MockedStatic<SystemWrapper> mockStatic = mockStatic(SystemWrapper.class)) {
      mockStatic.when(() -> SystemWrapper.getenv(any())).thenReturn("qa");
      when(CGRestUtils.getResponse(any())).thenReturn(map);
      RestResponse<String> res = dockerRunnerResource.get("abcde", "os", "arch");
      assertThat(res.getResource())
          .isEqualTo("wget https://raw.githubusercontent.com/harness/harness-docker-runner/master/scripts/script-qa.sh "
              + "-O script.sh\n"
              + "sh script.sh abcde mytoken= 23.12.81806");
    }
  }
}