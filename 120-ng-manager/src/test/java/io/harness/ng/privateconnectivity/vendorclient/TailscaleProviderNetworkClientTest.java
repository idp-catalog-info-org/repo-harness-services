/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.vendorclient;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.CreateOutcome;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderCreateException;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderNetworkException;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TailscaleProviderNetworkClientTest extends CategoryTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ORGANIZATION_ID = "organization";
  private static final String NETWORK_REF = "tailnet";
  private static final MediaType JSON = MediaType.get("application/json");

  private PrivateConnectivityOrgConfig orgConfig;
  private PrivateConnectivityConfigRepository repository;
  private PrivateConnectivityChildCredentialService childCredentialService;

  @Before
  public void setUp() {
    orgConfig = PrivateConnectivityOrgConfig.builder()
                    .orgOAuthClientId("org-client")
                    .orgOAuthClientSecret("org-secret")
                    .organizationIdentity(ORGANIZATION_ID)
                    .build();
    repository = mock(PrivateConnectivityConfigRepository.class);
    childCredentialService = mock(PrivateConnectivityChildCredentialService.class);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldCaptureChildOauthCredentialFromNetworkCreation() {
    RecordingInterceptor interceptor = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      if ("GET".equals(request.method())) {
        return json(request, 200, inventory());
      }
      return json(request, 200,
          "{\"id\":\"tailnet\",\"orgId\":\"organization\",\"dnsName\":\"tailnet.ts.net\","
              + "\"oauthClient\":{\"id\":\"child-client\",\"secret\":\"child-secret\"}}");
    });

    ProviderNetworkClient.NetworkCreateResult result = client(interceptor).createNetwork("pc-account-12345");

    assertThat(result.providerNetworkRef()).isEqualTo(NETWORK_REF);
    assertThat(result.adminCredential().clientId()).isEqualTo("child-client");
    assertThat(result.adminCredential().clientSecret()).isEqualTo("child-secret");
    assertThat(result.toString()).doesNotContain("child-secret");
    assertThat(interceptor.requestBodies()).anyMatch(body -> body.contains("pc-account-12345"));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldClassifyLostCreateResponseAsOutcomeUnknown() {
    RecordingInterceptor interceptor = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      if ("GET".equals(request.method())) {
        return json(request, 200, inventory());
      }
      throw new IOException("response lost");
    });

    Throwable failure = catchThrowable(() -> client(interceptor).createNetwork("pc-account-12345"));

    assertThat(failure).isInstanceOf(ProviderCreateException.class);
    assertThat(((ProviderCreateException) failure).getOutcome()).isEqualTo(CreateOutcome.OUTCOME_UNKNOWN);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldClassifyProviderValidationFailureAsDefiniteRejection() {
    RecordingInterceptor interceptor = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      if ("GET".equals(request.method())) {
        return json(request, 200, inventory());
      }
      return json(request, 400, "{\"message\":\"invalid display name\"}");
    });

    Throwable failure = catchThrowable(() -> client(interceptor).createNetwork("pc-account-12345"));

    assertThat(failure).isInstanceOf(ProviderCreateException.class);
    assertThat(((ProviderCreateException) failure).getOutcome()).isEqualTo(CreateOutcome.DEFINITELY_NOT_CREATED);
    assertThat(failure.getMessage()).doesNotContain("invalid display name");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldSendCompleteDnsReplacementAndClearDocuments() throws Exception {
    bindChildCredential();
    RecordingInterceptor interceptor = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      return json(request, 200, "{}");
    });
    TailscaleProviderNetworkClient client = client(interceptor);

    client.configureDns(
        NETWORK_REF, new ProviderNetworkClient.DnsConfig(Map.of("corp.example.com", List.of("10.20.0.10"))));
    client.configureDns(NETWORK_REF, ProviderNetworkClient.DnsConfig.cleared());

    List<String> dnsBodies = interceptor.requestBodies().stream().filter(body -> body.contains("splitDNS")).toList();
    assertThat(dnsBodies).hasSize(2);
    JsonNode enabled = MAPPER.readTree(dnsBodies.get(0));
    assertThat(enabled.path("nameservers").size()).isEqualTo(2);
    assertThat(enabled.path("splitDNS").path("corp.example.com").get(0).path("address").asText())
        .isEqualTo("10.20.0.10");
    assertThat(enabled.path("preferences").path("magicDNS").asBoolean()).isTrue();
    JsonNode cleared = MAPPER.readTree(dnsBodies.get(1));
    assertThat(cleared.path("nameservers").size()).isZero();
    assertThat(cleared.path("splitDNS").size()).isZero();
    assertThat(cleared.path("searchPaths").size()).isZero();
    assertThat(cleared.path("preferences").path("magicDNS").asBoolean()).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldDeduplicateValidDeviceIdsAndRejectMalformedInventory() {
    bindChildCredential();
    RecordingInterceptor valid = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      return json(request, 200, "{\"devices\":[{\"id\":\"one\"},{\"nodeId\":\"two\"},{\"id\":\"one\"}]}");
    });
    assertThat(client(valid).listDeviceIds(NETWORK_REF)).containsExactly("one", "two");

    RecordingInterceptor malformed = new RecordingInterceptor(request -> {
      if (request.url().encodedPath().endsWith("/oauth/token")) {
        return json(request, 200, accessToken());
      }
      return json(request, 200, "{\"devices\":[{}]}");
    });
    assertThatThrownBy(() -> client(malformed).listDeviceIds(NETWORK_REF))
        .isInstanceOf(ProviderNetworkException.class)
        .hasMessageContaining("without an id");
  }

  private void bindChildCredential() {
    PrivateConnectivityConfig config = PrivateConnectivityConfig.builder()
                                           .accountIdentifier("account")
                                           .providerNetworkRef(NETWORK_REF)
                                           .providerConfigurationFingerprint(orgConfig.configurationFingerprint())
                                           .providerTailnetOAuthClientId("child-client")
                                           .providerTailnetOAuthSecretRef("secret-ref")
                                           .build();
    when(repository.findByProviderNetworkRef(NETWORK_REF)).thenReturn(Optional.of(config));
    when(childCredentialService.load("account", "secret-ref"))
        .thenReturn(new PrivateConnectivityChildCredentialService.ChildCredential("child-client", "child-secret"));
  }

  private TailscaleProviderNetworkClient client(Interceptor interceptor) {
    return new TailscaleProviderNetworkClient(
        orgConfig, repository, childCredentialService, new OkHttpClient.Builder().addInterceptor(interceptor).build());
  }

  private static String accessToken() {
    return "{\"access_token\":\"access-token\",\"expires_in\":3600}";
  }

  private static String inventory() {
    return "{\"tailnets\":[{\"id\":\"existing\",\"displayName\":\"existing\","
        + "\"orgId\":\"organization\"}],\"totalCount\":1}";
  }

  private static Response json(Request request, int status, String body) {
    return new Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message(status < 400 ? "OK" : "Error")
        .body(ResponseBody.create(JSON, body.getBytes(StandardCharsets.UTF_8)))
        .build();
  }

  @FunctionalInterface
  private interface Responder {
    Response respond(Request request) throws IOException;
  }

  private static class RecordingInterceptor implements Interceptor {
    private final Responder responder;
    private final List<String> requestBodies = new ArrayList<>();

    private RecordingInterceptor(Responder responder) {
      this.responder = responder;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      if (request.body() != null) {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        requestBodies.add(buffer.readUtf8());
      }
      return responder.respond(request);
    }

    private List<String> requestBodies() {
      return List.copyOf(requestBodies);
    }
  }
}
