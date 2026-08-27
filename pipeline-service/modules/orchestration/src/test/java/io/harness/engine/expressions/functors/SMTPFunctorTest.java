/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.network.SafeHttpCall;
import io.harness.notification.SmtpConfig;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.MapperUtils;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDP)
@PrepareForTest({SafeHttpCall.class})
public class SMTPFunctorTest extends CategoryTest {
  @Mock private SmtpConfigClient smtpConfigClient;
  @InjectMocks private SMTPFunctor smtpFunctor;
  private Ambiance ambiance = Ambiance.newBuilder().build();
  private Ambiance ambianceWithAccountId =
      Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccountId").build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithEmptyAccountId() {
    on(smtpFunctor).set("ambiance", ambiance);
    assertNull(smtpFunctor.bind());
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithNullSmtpConfigResponse() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    on(smtpFunctor).set("ambiance", ambianceWithAccountId);

    RestResponse<SmtpConfigResponse> restResponse = new RestResponse<>();
    restResponse.setResource(null);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(restResponse);

    assertNull(smtpFunctor.bind());
    aStatic.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithNullSmtpConfig() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    on(smtpFunctor).set("ambiance", ambianceWithAccountId);

    SmtpConfigResponse smtpConfigResponse = new SmtpConfigResponse();
    smtpConfigResponse.setSmtpConfig(null);
    RestResponse<SmtpConfigResponse> restResponse = new RestResponse<>();
    restResponse.setResource(smtpConfigResponse);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(restResponse);

    assertNull(smtpFunctor.bind());
    aStatic.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithValidSmtpConfig() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    on(smtpFunctor).set("ambiance", ambianceWithAccountId);

    SmtpConfig smtpConfig =
        SmtpConfig.builder().host("smtp.gmail.com").port(587).fromAddress("test@harness.io").useSSL(true).build();

    SmtpConfigResponse smtpConfigResponse = new SmtpConfigResponse();
    smtpConfigResponse.setSmtpConfig(smtpConfig);
    RestResponse<SmtpConfigResponse> restResponse = new RestResponse<>();
    restResponse.setResource(smtpConfigResponse);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(restResponse);

    Object result = smtpFunctor.bind();
    assertEquals(MapperUtils.toMapViaJsonString(smtpConfig), result);
    aStatic.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithException() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    on(smtpFunctor).set("ambiance", ambianceWithAccountId);

    when(smtpConfigClient.getSmtpConfig(anyString())).thenReturn(null);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenThrow(new RuntimeException("Network error"));

    assertThatThrownBy(() -> smtpFunctor.bind())
        .isInstanceOf(EngineFunctorException.class)
        .hasMessageContaining("Invalid account: testAccountId");

    aStatic.close();
  }
}
