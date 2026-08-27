/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.proxy.layout.resource.LayoutProxyV2ApiImpl;
import io.harness.rule.Owner;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class LayoutProxyV2ApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "TEST_ACCOUNT_IDENTIFIER";
  private Call<Object> call;
  AutoCloseable openMocks;
  @Mock BackstageResourceClient backstageResourceClient;
  @InjectMocks LayoutProxyV2ApiImpl layoutProxyApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAllLayoutsV2() throws IOException {
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getAllLayoutsV2(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getAllLayoutsV2(ACCOUNT_IDENTIFIER);
    assertEquals(200, actualResponse.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAllLayoutsError() throws IOException {
    Response<Object> response = Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Failed"));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getAllLayoutsV2(ACCOUNT_IDENTIFIER)).thenReturn(call);
    javax.ws.rs.core.Response actualResponse = layoutProxyApiImpl.getAllLayoutsV2(ACCOUNT_IDENTIFIER);
    assertEquals(500, actualResponse.getStatus());
  }
}
