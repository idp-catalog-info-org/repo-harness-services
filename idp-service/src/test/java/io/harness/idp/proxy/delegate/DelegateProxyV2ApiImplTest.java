/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateProxyV2ApiImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks DelegateProxyV2ApiImpl delegateProxyV2Api;
  @Mock DelegateProxyApiImpl delegateProxyApi;
  @Mock HttpHeaders httpHeaders;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDelegateProxyV2() throws JsonProcessingException {
    MultivaluedMap headers = new MultivaluedHashMap<>();
    headers.put("idp-task-header-test", List.of("test"));
    when(httpHeaders.getRequestHeaders()).thenReturn(headers);
    when(delegateProxyApi.forwardProxy(eq(null), eq(httpHeaders), eq(null),
             eq("{\"url\":\"https://api.github.com/test/"
                 + "test\",\"method\":\"GET\",\"headers\":{\"test\":\"test\"},\"body\":null}")))
        .thenReturn(Response.status(200).build());

    Response actualResponse =
        delegateProxyV2Api.delegateProxyV2("GET", "https://api.github.com/test/test", httpHeaders, null);
    assertEquals(200, actualResponse.getStatus());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
