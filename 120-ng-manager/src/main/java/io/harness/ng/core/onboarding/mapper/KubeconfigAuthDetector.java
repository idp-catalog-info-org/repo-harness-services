/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.onboarding.dto.KubeconfigContextDescriptorDTO;
import io.harness.ng.core.onboarding.dto.KubeconfigImportStatus;
import io.harness.ng.core.onboarding.mapper.model.NamedCluster;
import io.harness.ng.core.onboarding.mapper.model.NamedContext;
import io.harness.ng.core.onboarding.mapper.model.NamedUser;
import io.harness.ng.core.onboarding.mapper.model.ParsedKubeConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Reverse-engineers each context in a parsed kubeconfig into a connector-creation descriptor.
 *
 * <p>This is the inverse of the forward flow (connector DTO -> kubeconfig YAML in
 * {@code KubeConfigGenerateUtility} / {@code KUBE_CONFIG_TEMPLATE}). It classifies the manual auth type purely from the
 * fields present on the resolved {@code user} block and reports a completeness importStatus.
 */
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class KubeconfigAuthDetector {
  // type vocabulary aligned to the connector KubernetesAuthType enum (feeds connector-create)
  private static final String TYPE_SERVICE_ACCOUNT = "SERVICE_ACCOUNT";
  private static final String TYPE_USER_PASSWORD = "USER_PASSWORD";
  private static final String TYPE_CLIENT_KEY_CERT = "CLIENT_KEY_CERT";
  private static final String TYPE_OPEN_ID_CONNECT = "OPEN_ID_CONNECT";
  private static final String TYPE_UNKNOWN = "UNKNOWN";

  private static final String AUTH_PROVIDER_OIDC = "oidc";
  private static final String AUTH_PROVIDER_GCP = "gcp";
  private static final String AUTH_PROVIDER_AZURE = "azure";

  public static List<KubeconfigContextDescriptorDTO> detect(ParsedKubeConfig kubeconfig) {
    List<KubeconfigContextDescriptorDTO> descriptors = new ArrayList<>();
    if (kubeconfig == null || isEmpty(kubeconfig.getContexts())) {
      return descriptors;
    }

    Map<String, NamedCluster> clusterByName = indexByName(kubeconfig.getClusters(), NamedCluster::getName);
    Map<String, NamedUser> userByName = indexByName(kubeconfig.getUsers(), NamedUser::getName);

    for (NamedContext namedContext : kubeconfig.getContexts()) {
      descriptors.add(describeContext(namedContext, clusterByName, userByName));
    }
    return descriptors;
  }

  private static KubeconfigContextDescriptorDTO describeContext(
      NamedContext namedContext, Map<String, NamedCluster> clusterByName, Map<String, NamedUser> userByName) {
    NamedContext.ContextSpec ctx = namedContext.getContext();

    List<String> secrets = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Map<String, Object> spec = new LinkedHashMap<>();

    String clusterName = ctx != null ? ctx.getCluster() : null;
    NamedCluster cluster = clusterName != null ? clusterByName.get(clusterName) : null;
    NamedUser user = ctx != null && ctx.getUser() != null ? userByName.get(ctx.getUser()) : null;

    // Dangling references: a context that points at a missing cluster or user cannot be used.
    if (cluster == null || user == null || user.getUser() == null) {
      if (cluster == null) {
        errors.add(String.format("Referenced cluster '%s' was not found in the kubeconfig", clusterName));
      }
      if (user == null || user.getUser() == null) {
        errors.add(
            String.format("Referenced user '%s' was not found in the kubeconfig", ctx != null ? ctx.getUser() : null));
      }
      return unsupported(namedContext.getName(), clusterName, errors);
    }

    NamedUser.UserSpec u = user.getUser();
    String type = classifyAuthType(u);

    // Unsupported auth (gcp/azure/exec/none): no manual-credential connector can be created, so we point the
    // user at the right connector kind via a blocking error.
    if (TYPE_UNKNOWN.equals(type)) {
      errors.add(unsupportedReason(u));
      return unsupported(namedContext.getName(), clusterName, errors);
    }

    // Supported type: populate cluster/connection details plus the auth-specific fields.
    String namespace = StringUtils.trimToNull(ctx.getNamespace());
    populateClusterSpec(spec, cluster, warnings);
    KubeconfigImportStatus importStatus;

    switch (type) {
      case TYPE_SERVICE_ACCOUNT:
        spec.put("serviceAccountToken", u.getToken());
        secrets.add("serviceAccountToken");
        importStatus = KubeconfigImportStatus.COMPLETE;
        break;

      case TYPE_USER_PASSWORD:
        // The connector requires BOTH a username (KubernetesUserNamePasswordDTO @OneOfField(username, usernameRef))
        // and a @NotNull passwordRef. A block missing either side cannot create a connector as-is, so it is not
        // importable -- reject it symmetrically.
        List<String> missingUserPass = new ArrayList<>();
        if (StringUtils.isBlank(u.getUsername())) {
          missingUserPass.add("username");
        }
        if (StringUtils.isBlank(u.getPassword())) {
          missingUserPass.add("password");
        }
        if (!missingUserPass.isEmpty()) {
          errors.add(String.format("username/password auth requires %s, which the kubeconfig does not provide for "
                  + "this context; this context cannot be imported.",
              String.join(" + ", missingUserPass)));
          return unsupported(namedContext.getName(), clusterName, errors);
        }
        spec.put("username", u.getUsername());
        spec.put("password", u.getPassword());
        secrets.add("password");
        importStatus = KubeconfigImportStatus.COMPLETE;
        break;

      case TYPE_CLIENT_KEY_CERT:
        importStatus = describeClientKeyCert(u, spec, secrets, warnings);
        break;

      case TYPE_OPEN_ID_CONNECT:
        importStatus = describeOidc(u, spec, secrets, errors, warnings);
        break;

      default:
        // Unreachable: classifyAuthType returns only the constants handled above or TYPE_UNKNOWN (handled earlier).
        return unsupported(namedContext.getName(), clusterName, errors);
    }

    return descriptor(
        namedContext.getName(), clusterName, namespace, type, importStatus, spec, secrets, errors, warnings);
  }

  /**
   * Classifies the auth type from field presence, in priority order:
   * auth-provider (oidc/gcp/azure) -> exec -> token -> username+password -> client cert/key.
   */
  private static String classifyAuthType(NamedUser.UserSpec u) {
    if (u.getAuthProvider() != null && StringUtils.isNotBlank(u.getAuthProvider().getName())) {
      String providerName = u.getAuthProvider().getName().toLowerCase(Locale.ROOT);
      if (AUTH_PROVIDER_OIDC.equals(providerName)) {
        return TYPE_OPEN_ID_CONNECT;
      }
      // gcp / azure / any other auth-provider -> unsupported manual credential
      return TYPE_UNKNOWN;
    }
    if (u.getExec() != null) {
      return TYPE_UNKNOWN;
    }
    if (StringUtils.isNotBlank(u.getToken())) {
      return TYPE_SERVICE_ACCOUNT;
    }
    if (StringUtils.isNotBlank(u.getPassword()) || StringUtils.isNotBlank(u.getUsername())) {
      return TYPE_USER_PASSWORD;
    }
    // CLIENT_KEY_CERT is supported only when BOTH cert and key are inline base64 (*-data). File-path refs
    // (or only one side present) mean the credential material lives on disk and is not in the uploaded file,
    // so it is not importable -> fall through to UNKNOWN/UNSUPPORTED (see unsupportedReason).
    if (hasInlineClientKeyCert(u)) {
      return TYPE_CLIENT_KEY_CERT;
    }
    return TYPE_UNKNOWN;
  }

  private static boolean hasInlineClientKeyCert(NamedUser.UserSpec u) {
    return StringUtils.isNotBlank(u.getClientCertificateData()) && StringUtils.isNotBlank(u.getClientKeyData());
  }

  private static boolean hasAnyClientKeyCertReference(NamedUser.UserSpec u) {
    return StringUtils.isNotBlank(u.getClientCertificateData()) || StringUtils.isNotBlank(u.getClientKeyData())
        || StringUtils.isNotBlank(u.getClientCertificate()) || StringUtils.isNotBlank(u.getClientKey());
  }

  private static KubeconfigImportStatus describeClientKeyCert(
      NamedUser.UserSpec u, Map<String, Object> spec, List<String> secrets, List<String> warnings) {
    // Reached only for inline cert + key (see classifyAuthType). Decode base64 PEM -> raw PEM (what the
    // connector stores).
    spec.put("clientCertificate", decodeToPem(u.getClientCertificateData(), "client-certificate-data", warnings));
    spec.put("clientKey", decodeToPem(u.getClientKeyData(), "client-key-data", warnings));
    secrets.add("clientCertificate");
    secrets.add("clientKey");
    return KubeconfigImportStatus.COMPLETE;
  }

  private static KubeconfigImportStatus describeOidc(NamedUser.UserSpec u, Map<String, Object> spec,
      List<String> secrets, List<String> errors, List<String> warnings) {
    Map<String, String> config = u.getAuthProvider().getConfig();
    if (config == null) {
      config = new LinkedHashMap<>();
    }

    String clientId = config.get("client-id");
    String clientSecret = config.get("client-secret");
    String issuerUrl = config.get("idp-issuer-url");
    String scopes = config.get("extra-scopes");

    // We only support the client_credentials grant for imported OIDC contexts. It is the only grant Harness can
    // run headless from a kubeconfig: it needs client-id + client-secret + issuer-url, whereas the 'password'
    // grant would need a username + password a kubeconfig never stores. All three are therefore REQUIRED -- if
    // any is missing (e.g. a public/PKCE client has no client-secret) the context is not importable.
    List<String> absent = new ArrayList<>();
    if (StringUtils.isBlank(clientId)) {
      absent.add("client-id");
    }
    if (StringUtils.isBlank(clientSecret)) {
      absent.add("client-secret");
    }
    if (StringUtils.isBlank(issuerUrl)) {
      absent.add("idp-issuer-url");
    }
    if (!absent.isEmpty()) {
      // UNSUPPORTED: not actionable. Follow the empty-spec convention and explain the blocking reason via errors.
      spec.clear();
      secrets.clear();
      errors.add(String.format("The client_credentials grant requires %s, which the kubeconfig does not provide "
              + "(e.g. a public/PKCE OIDC client has no client-secret); this context cannot be imported.",
          String.join(" + ", absent)));
      return KubeconfigImportStatus.UNSUPPORTED;
    }

    // All three inputs present: pre-fill the grant type so the connector authenticates as the OIDC client.
    spec.put("oidcClientId", clientId);
    secrets.add("oidcClientId");
    spec.put("oidcSecret", clientSecret);
    secrets.add("oidcSecret");
    spec.put("oidcIssuerUrl", issuerUrl);
    if (StringUtils.isNotBlank(scopes)) {
      spec.put("oidcScopes", scopes);
    }
    spec.put("oidcGrantType", "client_credentials");

    // client_credentials authenticates as the OIDC client, not the human user the kubeconfig id-token was minted
    // for -- the resulting identity (and its cluster RBAC) may differ. We can't detect that from the file.
    warnings.add("Using the client_credentials grant: the connector authenticates as the OIDC client, not the "
        + "kubeconfig user, so its cluster permissions may differ. Verify RBAC after import.");
    // A kubeconfig never records the grant type; if the IdP client only supports the password grant,
    // client_credentials will fail at token-mint time. Tell the user the exact fix.
    warnings.add("If your IdP client does not support the client_credentials grant, switch the connector's grant "
        + "type to password and supply oidcUsername + oidcPassword.");
    warnings.add("id-token / refresh-token in kubeconfig are ignored; Harness mints its own token at deploy time");
    if (StringUtils.isNotBlank(config.get("idp-certificate-authority-data"))
        || StringUtils.isNotBlank(config.get("idp-certificate-authority"))) {
      warnings.add("idp-certificate-authority-data has no dedicated Harness field");
    }

    // Grant type is pre-filled and all inputs are present, so the connector can be created with no user input.
    return KubeconfigImportStatus.COMPLETE;
  }

  private static void populateClusterSpec(Map<String, Object> spec, NamedCluster cluster, List<String> warnings) {
    NamedCluster.ClusterSpec c = cluster != null ? cluster.getCluster() : null;

    spec.put("masterUrl", c != null ? c.getServer() : null);

    String caData = c != null ? c.getCertificateAuthorityData() : null;
    if (StringUtils.isNotBlank(caData)) {
      spec.put("caCertificate", decodeToPem(caData, "certificate-authority-data", warnings));
    } else {
      spec.put("caCertificate", null);
      if (c != null && StringUtils.isNotBlank(c.getCertificateAuthority())) {
        warnings.add(
            String.format("certificate-authority is a file path (%s); CA material is not present in the kubeconfig",
                c.getCertificateAuthority()));
      }
      // The K8s connector has no skip-TLS-verify field; it derives skip = (no CA cert) at kubeconfig-generation
      // time. So we don't emit skipTlsVerify in spec -- a null caCertificate is the signal. Warn so the UI can
      // surface that this cluster will connect without TLS verification.
      warnings.add("No CA cert present; the connector will skip TLS verification for this cluster");
    }
  }

  /** kubeconfig *-data fields are base64-encoded PEM; decode to raw PEM. Pass through + warn if not valid base64. */
  private static String decodeToPem(String base64Data, String fieldName, List<String> warnings) {
    try {
      return new String(Base64.getDecoder().decode(base64Data.trim()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      warnings.add(String.format("%s is not valid base64; value returned unchanged", fieldName));
      return base64Data;
    }
  }

  private static String unsupportedReason(NamedUser.UserSpec u) {
    if (u.getExec() != null) {
      String cmd = u.getExec().getCommand();
      return String.format("exec auth-provider (%s) is not a manual-credential type; create the matching cloud "
              + "connector (GCP/Azure/AWS) instead",
          StringUtils.defaultIfBlank(cmd, "exec plugin"));
    }
    if (u.getAuthProvider() != null && StringUtils.isNotBlank(u.getAuthProvider().getName())) {
      String name = u.getAuthProvider().getName().toLowerCase(Locale.ROOT);
      if (AUTH_PROVIDER_GCP.equals(name)) {
        return "gcp auth-provider is not a manual-credential type; create a GCP connector instead";
      }
      if (AUTH_PROVIDER_AZURE.equals(name)) {
        return "azure auth-provider is not a manual-credential type; create an Azure connector instead";
      }
      return String.format("auth-provider '%s' is not a supported manual-credential type", name);
    }
    // Client cert/key referenced by file path (or only one side inline): the material lives on the uploader's
    // disk and is not present in the uploaded kubeconfig, so it cannot be imported.
    if (hasAnyClientKeyCertReference(u)) {
      return "client certificate/key are referenced by file path (or incomplete); the credential material is "
          + "not present in the kubeconfig, so it cannot be imported. Re-run with inline "
          + "client-certificate-data + client-key-data, or create the connector manually.";
    }
    return "Could not detect a supported manual-credential auth type for this user";
  }

  private static <T> Map<String, T> indexByName(List<T> items, Function<T, String> nameFn) {
    if (isEmpty(items)) {
      return new LinkedHashMap<>();
    }
    // Keep the first occurrence on duplicate names.
    return items.stream()
        .filter(item -> nameFn.apply(item) != null)
        .collect(Collectors.toMap(nameFn, Function.identity(), (first, dup) -> first, LinkedHashMap::new));
  }

  private static boolean isEmpty(List<?> list) {
    return list == null || list.isEmpty();
  }

  /**
   * Builds an UNSUPPORTED descriptor: not actionable, so spec/secrets/warnings are empty and only the errors
   * explain why it is blocked. A non-empty spec is the signal that a context can drive connector creation.
   */
  private static KubeconfigContextDescriptorDTO unsupported(String name, String clusterName, List<String> errors) {
    return descriptor(name, clusterName, null, TYPE_UNKNOWN, KubeconfigImportStatus.UNSUPPORTED, new LinkedHashMap<>(),
        new ArrayList<>(), errors, new ArrayList<>());
  }

  private static KubeconfigContextDescriptorDTO descriptor(String name, String clusterName, String namespace,
      String type, KubeconfigImportStatus importStatus, Map<String, Object> spec, List<String> secrets,
      List<String> errors, List<String> warnings) {
    return KubeconfigContextDescriptorDTO.builder()
        .name(name)
        .clusterName(clusterName)
        .namespace(namespace)
        .type(type)
        .importStatus(importStatus)
        .spec(spec)
        .secrets(secrets)
        .errors(errors)
        .warnings(warnings)
        .build();
  }
}
