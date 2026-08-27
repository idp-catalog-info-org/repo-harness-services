/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.onboarding.dto.KubeconfigContextDescriptorDTO;
import io.harness.ng.core.onboarding.dto.KubeconfigImportStatus;
import io.harness.ng.core.onboarding.mapper.model.NamedCluster;
import io.harness.ng.core.onboarding.mapper.model.NamedContext;
import io.harness.ng.core.onboarding.mapper.model.NamedUser;
import io.harness.ng.core.onboarding.mapper.model.ParsedKubeConfig;
import io.harness.rule.Owner;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for {@link KubeconfigAuthDetector}: the reverse-engineering logic that classifies each kubeconfig
 * context into a connector-creation descriptor and gates it COMPLETE vs UNSUPPORTED.
 */
public class KubeconfigAuthDetectorTest extends CategoryTest {
  private static final String MASTER_URL = "https://k8s.example.com";
  private static final String NAMESPACE = "prod";

  // ---- helpers ---------------------------------------------------------------------------------

  private static String b64(String raw) {
    return Base64.getEncoder().encodeToString(raw.getBytes());
  }

  private static NamedCluster cluster(String name, String server, String caData, String caFile) {
    NamedCluster.ClusterSpec spec = new NamedCluster.ClusterSpec();
    spec.setServer(server);
    spec.setCertificateAuthorityData(caData);
    spec.setCertificateAuthority(caFile);
    NamedCluster c = new NamedCluster();
    c.setName(name);
    c.setCluster(spec);
    return c;
  }

  private static NamedContext context(String name, String clusterName, String userName, String namespace) {
    NamedContext.ContextSpec spec = new NamedContext.ContextSpec();
    spec.setCluster(clusterName);
    spec.setUser(userName);
    spec.setNamespace(namespace);
    NamedContext ctx = new NamedContext();
    ctx.setName(name);
    ctx.setContext(spec);
    return ctx;
  }

  private static NamedUser user(String name, NamedUser.UserSpec spec) {
    NamedUser u = new NamedUser();
    u.setName(name);
    u.setUser(spec);
    return u;
  }

  private static ParsedKubeConfig config(List<NamedCluster> clusters, List<NamedUser> users, NamedContext ctx) {
    ParsedKubeConfig cfg = new ParsedKubeConfig();
    cfg.setKind("Config");
    cfg.setClusters(clusters);
    cfg.setUsers(users);
    cfg.setContexts(List.of(ctx));
    return cfg;
  }

  /** Builds a single-context config wiring the given user spec to a cluster with an inline CA. */
  private static ParsedKubeConfig singleContext(NamedUser.UserSpec userSpec) {
    NamedCluster c = cluster("c1", MASTER_URL, b64("ca-pem"), null);
    NamedUser u = user("u1", userSpec);
    NamedContext ctx = context("ctx1", "c1", "u1", NAMESPACE);
    return config(List.of(c), List.of(u), ctx);
  }

  private static KubeconfigContextDescriptorDTO only(ParsedKubeConfig cfg) {
    List<KubeconfigContextDescriptorDTO> out = KubeconfigAuthDetector.detect(cfg);
    assertThat(out).hasSize(1);
    return out.get(0);
  }

  // ---- empty / null ----------------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDetectNullConfigReturnsEmpty() {
    assertThat(KubeconfigAuthDetector.detect(null)).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDetectNoContextsReturnsEmpty() {
    ParsedKubeConfig cfg = new ParsedKubeConfig();
    assertThat(KubeconfigAuthDetector.detect(cfg)).isEmpty();
  }

  // ---- SERVICE_ACCOUNT (token) -----------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testServiceAccountToken() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setToken("sa-token-value");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("SERVICE_ACCOUNT");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(d.getName()).isEqualTo("ctx1");
    assertThat(d.getClusterName()).isEqualTo("c1");
    assertThat(d.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(d.getSpec()).containsEntry("serviceAccountToken", "sa-token-value");
    assertThat(d.getSpec()).containsEntry("masterUrl", MASTER_URL);
    assertThat(d.getSpec().get("caCertificate")).isEqualTo("ca-pem");
    assertThat(d.getSecrets()).containsExactly("serviceAccountToken");
    assertThat(d.getErrors()).isEmpty();
  }

  // ---- USER_PASSWORD ---------------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testUserPasswordComplete() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setUsername("admin");
    spec.setPassword("s3cret");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("USER_PASSWORD");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(d.getSpec()).containsEntry("username", "admin");
    assertThat(d.getSpec()).containsEntry("password", "s3cret");
    assertThat(d.getSecrets()).containsExactly("password");
    assertThat(d.getErrors()).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testUserPasswordMissingPasswordIsUnsupported() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setUsername("admin");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getSpec()).isEmpty();
    assertThat(d.getSecrets()).isEmpty();
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("password"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testUserPasswordMissingUsernameIsUnsupported() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setPassword("s3cret");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("username"));
  }

  // ---- CLIENT_KEY_CERT -------------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testClientKeyCertInlineComplete() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setClientCertificateData(b64("cert-pem"));
    spec.setClientKeyData(b64("key-pem"));

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("CLIENT_KEY_CERT");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(d.getSpec().get("clientCertificate")).isEqualTo("cert-pem");
    assertThat(d.getSpec().get("clientKey")).isEqualTo("key-pem");
    assertThat(d.getSecrets()).containsExactly("clientCertificate", "clientKey");
    assertThat(d.getErrors()).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testClientCertByFilePathIsUnsupported() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setClientCertificate("/path/to/cert.pem");
    spec.setClientKey("/path/to/key.pem");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("file path"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testClientCertMissingKeyIsUnsupported() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setClientCertificateData(b64("cert-pem"));
    // no client-key-data

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).isNotEmpty();
  }

  // ---- OPEN_ID_CONNECT -------------------------------------------------------------------------

  private static NamedUser.UserSpec oidcUser(Map<String, String> config) {
    NamedUser.AuthProvider provider = new NamedUser.AuthProvider();
    provider.setName("oidc");
    provider.setConfig(config);
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setAuthProvider(provider);
    return spec;
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOidcComplete() {
    Map<String, String> config = new LinkedHashMap<>();
    config.put("client-id", "my-client");
    config.put("client-secret", "my-secret");
    config.put("idp-issuer-url", "https://idp.example.com");
    config.put("extra-scopes", "groups");

    KubeconfigContextDescriptorDTO d = only(singleContext(oidcUser(config)));

    assertThat(d.getType()).isEqualTo("OPEN_ID_CONNECT");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(d.getSpec()).containsEntry("oidcClientId", "my-client");
    assertThat(d.getSpec()).containsEntry("oidcSecret", "my-secret");
    assertThat(d.getSpec()).containsEntry("oidcIssuerUrl", "https://idp.example.com");
    assertThat(d.getSpec()).containsEntry("oidcScopes", "groups");
    assertThat(d.getSpec()).containsEntry("oidcGrantType", "client_credentials");
    assertThat(d.getSecrets()).contains("oidcClientId", "oidcSecret");
    assertThat(d.getWarnings()).isNotEmpty();
    assertThat(d.getErrors()).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOidcMissingClientSecretIsUnsupported() {
    Map<String, String> config = new LinkedHashMap<>();
    config.put("client-id", "my-client");
    config.put("idp-issuer-url", "https://idp.example.com");

    KubeconfigContextDescriptorDTO d = only(singleContext(oidcUser(config)));

    assertThat(d.getType()).isEqualTo("OPEN_ID_CONNECT");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getSpec()).isEmpty();
    assertThat(d.getSecrets()).isEmpty();
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("client-secret"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOidcMissingAllGrantFieldsListsEachAbsent() {
    KubeconfigContextDescriptorDTO d = only(singleContext(oidcUser(new LinkedHashMap<>())));

    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> {
      assertThat(e).contains("client-id");
      assertThat(e).contains("client-secret");
      assertThat(e).contains("idp-issuer-url");
    });
  }

  // ---- unsupported auth providers / exec -------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGcpAuthProviderIsUnsupported() {
    NamedUser.AuthProvider provider = new NamedUser.AuthProvider();
    provider.setName("gcp");
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setAuthProvider(provider);

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("GCP"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testAzureAuthProviderIsUnsupported() {
    NamedUser.AuthProvider provider = new NamedUser.AuthProvider();
    provider.setName("azure");
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setAuthProvider(provider);

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("Azure"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecPluginIsUnsupported() {
    NamedUser.ExecConfig exec = new NamedUser.ExecConfig();
    exec.setCommand("gke-gcloud-auth-plugin");
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setExec(exec);

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("gke-gcloud-auth-plugin"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testNoRecognizableCredentialIsUnsupported() {
    KubeconfigContextDescriptorDTO d = only(singleContext(new NamedUser.UserSpec()));

    assertThat(d.getType()).isEqualTo("UNKNOWN");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).isNotEmpty();
  }

  // ---- dangling references ---------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDanglingClusterReferenceIsUnsupported() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setToken("t");
    NamedUser u = user("u1", spec);
    NamedContext ctx = context("ctx1", "missing-cluster", "u1", NAMESPACE);
    ParsedKubeConfig cfg = config(List.of(), List.of(u), ctx);

    KubeconfigContextDescriptorDTO d = only(cfg);

    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("missing-cluster"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDanglingUserReferenceIsUnsupported() {
    NamedCluster c = cluster("c1", MASTER_URL, b64("ca"), null);
    NamedContext ctx = context("ctx1", "c1", "missing-user", NAMESPACE);
    ParsedKubeConfig cfg = config(List.of(c), List.of(), ctx);

    KubeconfigContextDescriptorDTO d = only(cfg);

    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
    assertThat(d.getErrors()).anySatisfy(e -> assertThat(e).contains("missing-user"));
  }

  // ---- base64 / CA handling --------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testInvalidBase64CertPassesThroughWithWarning() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    // Contains characters illegal in base64 so decode throws -> value returned unchanged + warning.
    spec.setClientCertificateData("!!!not-base64!!!");
    spec.setClientKeyData("@@@also-not-base64@@@");

    KubeconfigContextDescriptorDTO d = only(singleContext(spec));

    assertThat(d.getType()).isEqualTo("CLIENT_KEY_CERT");
    assertThat(d.getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(d.getSpec().get("clientCertificate")).isEqualTo("!!!not-base64!!!");
    assertThat(d.getSpec().get("clientKey")).isEqualTo("@@@also-not-base64@@@");
    assertThat(d.getWarnings()).anySatisfy(w -> assertThat(w).contains("not valid base64"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testNoCaCertWarnsAboutSkippedTlsVerification() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setToken("t");
    NamedCluster c = cluster("c1", MASTER_URL, null, null);
    NamedUser u = user("u1", spec);
    NamedContext ctx = context("ctx1", "c1", "u1", NAMESPACE);
    ParsedKubeConfig cfg = config(List.of(c), List.of(u), ctx);

    KubeconfigContextDescriptorDTO d = only(cfg);

    assertThat(d.getSpec().get("caCertificate")).isNull();
    assertThat(d.getWarnings()).anySatisfy(w -> assertThat(w).contains("skip TLS verification"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCaCertByFilePathWarns() {
    NamedUser.UserSpec spec = new NamedUser.UserSpec();
    spec.setToken("t");
    NamedCluster c = cluster("c1", MASTER_URL, null, "/etc/ca.crt");
    NamedUser u = user("u1", spec);
    NamedContext ctx = context("ctx1", "c1", "u1", NAMESPACE);
    ParsedKubeConfig cfg = config(List.of(c), List.of(u), ctx);

    KubeconfigContextDescriptorDTO d = only(cfg);

    assertThat(d.getWarnings()).anySatisfy(w -> assertThat(w).contains("/etc/ca.crt"));
  }

  // ---- multiple contexts -----------------------------------------------------------------------

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMultipleContextsClassifiedIndependently() {
    NamedUser.UserSpec saSpec = new NamedUser.UserSpec();
    saSpec.setToken("t");
    NamedUser.UserSpec gcpSpec = new NamedUser.UserSpec();
    NamedUser.AuthProvider gcp = new NamedUser.AuthProvider();
    gcp.setName("gcp");
    gcpSpec.setAuthProvider(gcp);

    NamedCluster c = cluster("c1", MASTER_URL, b64("ca"), null);
    NamedUser saUser = user("sa", saSpec);
    NamedUser gcpUser = user("gcp", gcpSpec);

    ParsedKubeConfig cfg = new ParsedKubeConfig();
    cfg.setKind("Config");
    cfg.setClusters(List.of(c));
    cfg.setUsers(List.of(saUser, gcpUser));
    cfg.setContexts(List.of(context("saCtx", "c1", "sa", NAMESPACE), context("gcpCtx", "c1", "gcp", NAMESPACE)));

    List<KubeconfigContextDescriptorDTO> out = KubeconfigAuthDetector.detect(cfg);

    assertThat(out).hasSize(2);
    assertThat(out.get(0).getType()).isEqualTo("SERVICE_ACCOUNT");
    assertThat(out.get(0).getImportStatus()).isEqualTo(KubeconfigImportStatus.COMPLETE);
    assertThat(out.get(1).getType()).isEqualTo("UNKNOWN");
    assertThat(out.get(1).getImportStatus()).isEqualTo(KubeconfigImportStatus.UNSUPPORTED);
  }
}
