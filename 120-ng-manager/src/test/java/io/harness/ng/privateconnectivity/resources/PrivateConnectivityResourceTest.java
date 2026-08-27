/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.resources;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityHelperCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityPublicStatus;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupResponseDTO;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityService;
import io.harness.rule.Owner;
import io.harness.security.annotations.AdminPortalAuth;
import io.harness.security.annotations.NextGenManagerAuth;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrivateConnectivityResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldKeepOperatorOnlyLifecycleMethodsBehindAdminPortalAuth() throws Exception {
    assertThat(PrivateConnectivityResource.class.isAnnotationPresent(NextGenManagerAuth.class)).isTrue();
    for (String methodName :
        List.of("updateConfigAsAdmin", "reconcile", "getHelperCredential", "release", "getAdmin")) {
      Method method = findMethod(methodName);
      assertThat(method.isAnnotationPresent(AdminPortalAuth.class))
          .as("%s must remain restricted to Harness operators", methodName)
          .isTrue();
    }
    for (String methodName : List.of("setup", "get", "updateConfig", "getCredential")) {
      assertThat(findMethod(methodName).isAnnotationPresent(AdminPortalAuth.class))
          .as("%s must remain on the customer-authenticated API surface", methodName)
          .isFalse();
    }
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldPreventCachingEveryResponseThatDisclosesAJoinCredential() {
    PrivateConnectivityService service = mock(PrivateConnectivityService.class);
    PrivateConnectivityCredentialDTO customerCredential =
        PrivateConnectivityCredentialDTO.builder().authKey("customer-secret").expiresAt(100L).build();
    when(service.setup(ACCOUNT_ID, PrivateConnectivitySetupRequestDTO.builder().build()))
        .thenReturn(PrivateConnectivitySetupResponseDTO.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .status(PrivateConnectivityPublicStatus.READY)
                        .credential(customerCredential)
                        .build());
    when(service.getCredential(ACCOUNT_ID)).thenReturn(customerCredential);
    when(service.getHelperCredential(ACCOUNT_ID))
        .thenReturn(PrivateConnectivityHelperCredentialDTO.builder()
                        .type("AUTH_KEY")
                        .value("helper-secret")
                        .expiresAt(200L)
                        .build());
    PrivateConnectivityResource resource = new PrivateConnectivityResource(service);

    Response setup = resource.setup(ACCOUNT_ID, PrivateConnectivitySetupRequestDTO.builder().build());
    Response customer = resource.getCredential(ACCOUNT_ID);
    Response helper = resource.getHelperCredential(ACCOUNT_ID);

    assertThat(setup.getHeaderString("Cache-Control")).isEqualTo("no-store");
    assertThat(customer.getHeaderString("Cache-Control")).isEqualTo("no-store");
    assertThat(helper.getHeaderString("Cache-Control")).isEqualTo("no-store");
  }

  private static Method findMethod(String methodName) {
    return Arrays.stream(PrivateConnectivityResource.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .findFirst()
        .orElseThrow();
  }
}
