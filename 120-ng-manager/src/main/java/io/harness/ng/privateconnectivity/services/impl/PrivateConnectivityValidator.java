/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityRouterType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;

import com.google.common.net.InternetDomainName;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.ws.rs.BadRequestException;
import lombok.experimental.UtilityClass;

/**
 * Validates PrivateConnectivitySetupRequestDTO.
 *
 * Validation rules:
 *  - advertiseRoutes: private IPv4 CIDRs only; reject public, default-route, CGNAT, and control-plane ranges
 *  - domains: exact FQDNs or wildcard FQDN patterns; no bare "*" without a dot-separated suffix
 *  - mode is derived from routes/domains; the customer never supplies an implementation role
 *  - splitDnsDomains: subnet/BOTH only; every resolver must be within an advertised private route
 *  - APP_CONNECTOR: dns.splitDnsDomains forbidden (App Connector DNS is automatic via ACL)
 */
@OwnedBy(CI)
@UtilityClass
public class PrivateConnectivityValidator {
  private static final int MAX_DNS_NAME_LENGTH = 253;
  private static final int MAX_DNS_LABEL_LENGTH = 63;
  private static final int MAX_APP_CONNECTOR_DOMAINS = 250;
  private static final Pattern DOMAIN_GLOB_PATTERN = Pattern.compile("^(\\*\\.)?[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?"
      + "(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$");
  private static final Pattern DNS_SUFFIX_PATTERN =
      Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$");
  private static final Pattern CIDR_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}/\\d{1,2}$");

  // Rejected CIDR ranges (public, default, CGNAT, Tailscale control-plane)
  private static final List<String> REJECTED_PREFIXES = List.of("0.0.0.0/0", // default route
      "100.64.0.0/10", // Tailscale/CGNAT range — overlaps with Tailscale device IPs
      "100.100.100.0/24", // Quad100 DNS
      "169.254.0.0/16" // link-local
  );

  private static void validate(PrivateConnectivitySetupRequestDTO request) {
    if (request == null) {
      throw new BadRequestException("Private Connectivity configuration is required");
    }
    List<String> routes = request.getAdvertiseRoutes() == null ? List.of() : request.getAdvertiseRoutes();
    List<String> domains = request.getDomains() == null ? List.of() : request.getDomains();

    final PrivateConnectivityRouterType routerType;
    try {
      routerType = PrivateConnectivityRouterType.fromConfiguration(routes, domains);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage());
    }
    if (domains.size() > MAX_APP_CONNECTOR_DOMAINS) {
      throw new BadRequestException(
          "At most " + MAX_APP_CONNECTOR_DOMAINS + " application domains are supported per tailnet");
    }

    for (String cidr : routes) {
      if (cidr == null) {
        throw new BadRequestException("advertiseRoutes entries must not be null");
      }
      validateCidr(cidr);
    }
    for (String domain : domains) {
      if (domain == null) {
        throw new BadRequestException("domains entries must not be null");
      }
      validateDomainGlob(domain);
    }

    if (request.getDns() != null && request.getDns().getSplitDnsDomains() != null
        && !request.getDns().getSplitDnsDomains().isEmpty()) {
      // App Connector DNS is driven by ACL nodeAttrs; split-DNS API is for subnet-router only.
      if (routerType == PrivateConnectivityRouterType.APP_CONNECTOR) {
        throw new BadRequestException("dns.splitDnsDomains requires an advertised private route to every resolver; "
            + "App Connector domain discovery itself is automatic");
      }
      validateSplitDns(request.getDns().getSplitDnsDomains(), routes);
    }
  }

  /**
   * Produces the canonical full desired configuration used for idempotency comparison, persistence,
   * and provider writes. Validation is separate so callers cannot compare one shape and apply another.
   */
  public static PrivateConnectivitySetupRequestDTO normalize(PrivateConnectivitySetupRequestDTO request) {
    if (request == null) {
      throw new BadRequestException("Private Connectivity configuration is required");
    }
    List<String> routes = normalizeList(request.getAdvertiseRoutes(), false, "advertiseRoutes");
    List<String> domains = normalizeList(request.getDomains(), true, "domains");
    Map<String, List<String>> splitDns = new TreeMap<>();
    if (request.getDns() != null && request.getDns().getSplitDnsDomains() != null) {
      for (Map.Entry<String, List<String>> entry : request.getDns().getSplitDnsDomains().entrySet()) {
        if (entry.getKey() == null) {
          throw new BadRequestException("dns.splitDnsDomains suffix must not be null");
        }
        String suffix = entry.getKey().trim().toLowerCase(Locale.ROOT);
        if (splitDns.containsKey(suffix)) {
          throw new BadRequestException("Duplicate split-DNS suffix after normalization: " + suffix);
        }
        splitDns.put(suffix, normalizeList(entry.getValue(), false, "dns resolver"));
      }
    }
    PrivateConnectivitySetupRequestDTO normalized =
        PrivateConnectivitySetupRequestDTO.builder()
            .advertiseRoutes(routes)
            .domains(domains)
            .dns(splitDns.isEmpty()
                    ? null
                    : PrivateConnectivitySetupRequestDTO.DnsConfig.builder().splitDnsDomains(splitDns).build())
            .build();
    validate(normalized);
    return normalized;
  }

  private static List<String> normalizeList(List<String> values, boolean lowercase, String fieldName) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(value -> {
          if (value == null) {
            throw new BadRequestException(fieldName + " entries must not be null");
          }
          String normalized = value.trim();
          return lowercase ? normalized.toLowerCase(Locale.ROOT) : normalized;
        })
        .distinct()
        .sorted()
        .toList();
  }

  private static void validateCidr(String cidr) {
    if (!CIDR_PATTERN.matcher(cidr).matches()) {
      throw new BadRequestException("Invalid CIDR format: " + cidr);
    }
    for (String rejected : REJECTED_PREFIXES) {
      if (cidr.equals(rejected)) {
        throw new BadRequestException("CIDR not allowed (overlaps with control-plane or public range): " + cidr);
      }
    }
    String[] parts = cidr.split("/");
    int prefix = Integer.parseInt(parts[1]);
    if (!Integer.toString(prefix).equals(parts[1])) {
      throw new BadRequestException("CIDR prefix must use canonical decimal notation: " + cidr);
    }
    if (prefix < 8 || prefix > 32) {
      throw new BadRequestException("CIDR prefix must be between /8 and /32: " + cidr);
    }
    long address = parseIpv4(parts[0], "CIDR");
    long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    if ((address & mask) != address) {
      throw new BadRequestException("CIDR must use the network address with host bits cleared: " + cidr);
    }
    // Require the entire prefix ⊆ RFC-1918 (not just that the network address looks private).
    // e.g. 192.168.0.0/15 includes public 192.169.0.0/16 and must be rejected.
    if (!isContainedInRfc1918(address, prefix)) {
      throw new BadRequestException(
          "Only private IPv4 CIDRs fully contained in RFC-1918 (10/8, 172.16/12, 192.168/16) are allowed: " + cidr);
    }
  }

  /** True when [network, network+|~mask|] is a subset of 10/8, 172.16/12, or 192.168/16. */
  private static boolean isContainedInRfc1918(long network, int prefix) {
    return isCidrSubset(network, prefix, 0x0A000000L, 8) // 10.0.0.0/8
        || isCidrSubset(network, prefix, 0xAC100000L, 12) // 172.16.0.0/12
        || isCidrSubset(network, prefix, 0xC0A80000L, 16); // 192.168.0.0/16
  }

  private static boolean isCidrSubset(long network, int prefix, long parentNetwork, int parentPrefix) {
    if (prefix < parentPrefix) {
      return false;
    }
    long parentMask = (0xFFFFFFFFL << (32 - parentPrefix)) & 0xFFFFFFFFL;
    return (network & parentMask) == (parentNetwork & parentMask);
  }

  private static void validateDomainGlob(String domain) {
    if (!DOMAIN_GLOB_PATTERN.matcher(domain).matches()) {
      throw new BadRequestException(
          "Invalid domain (must be an FQDN or *.subdomain.tld glob, with no bare *): " + domain);
    }
    validateDnsNameLengths(domain, "domain");
    String suffix = domain.startsWith("*.") ? domain.substring(2) : domain;
    try {
      if (InternetDomainName.from(suffix).isPublicSuffix()) {
        throw new BadRequestException("Domain must be scoped below a public suffix: " + domain);
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid domain: " + domain);
    }
  }

  private static void validateSplitDns(Map<String, List<String>> splitDns, List<String> routes) {
    for (Map.Entry<String, List<String>> entry : splitDns.entrySet()) {
      if (entry.getKey() == null || !DNS_SUFFIX_PATTERN.matcher(entry.getKey()).matches()) {
        throw new BadRequestException("Invalid split-DNS suffix: " + entry.getKey());
      }
      validateDnsNameLengths(entry.getKey(), "split-DNS suffix");
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        throw new BadRequestException("At least one resolver is required for split-DNS suffix " + entry.getKey());
      }
      for (String resolver : entry.getValue()) {
        if (!isResolverInRoutes(resolver, routes)) {
          throw new BadRequestException(
              "Split-DNS resolver " + resolver + " must be within an advertised private route");
        }
      }
    }
  }

  private static void validateDnsNameLengths(String name, String fieldName) {
    if (name.length() > MAX_DNS_NAME_LENGTH) {
      throw new BadRequestException(fieldName + " exceeds the 253-character DNS name limit: " + name);
    }
    for (String label : name.split("\\.")) {
      if (label.length() > MAX_DNS_LABEL_LENGTH) {
        throw new BadRequestException(fieldName + " contains a DNS label longer than 63 characters: " + name);
      }
    }
  }

  private static boolean isResolverInRoutes(String resolverIp, List<String> routes) {
    if (routes == null || routes.isEmpty()) {
      return false;
    }
    long resolver = parseIpv4(resolverIp, "resolver IP address");
    for (String cidr : routes) {
      if (isInCidr(resolver, cidr)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInCidr(long address, String cidr) {
    String[] parts = cidr.split("/");
    long network = parseIpv4(parts[0], "CIDR");
    int prefix = Integer.parseInt(parts[1]);
    long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    return (address & mask) == (network & mask);
  }

  private static long parseIpv4(String value, String fieldName) {
    if (value == null) {
      throw new BadRequestException("Invalid " + fieldName + ": null");
    }
    String[] octets = value.split("\\.", -1);
    if (octets.length != 4) {
      throw new BadRequestException("Invalid " + fieldName + ": " + value);
    }
    long result = 0;
    for (String octet : octets) {
      if (octet.isEmpty() || !octet.chars().allMatch(character -> character >= '0' && character <= '9')) {
        throw new BadRequestException("Invalid " + fieldName + ": " + value);
      }
      // Reject ambiguous octal-looking input instead of persisting a value that the provider may
      // canonicalize differently (for example 010.0.0.0 versus 10.0.0.0). Desired-state
      // idempotency and provider policy generation both use the same canonical values.
      if (octet.length() > 1 && octet.charAt(0) == '0') {
        throw new BadRequestException(
            "Invalid " + fieldName + " (IPv4 octets must use canonical decimal notation): " + value);
      }
      int parsed;
      try {
        parsed = Integer.parseInt(octet);
      } catch (NumberFormatException e) {
        throw new BadRequestException("Invalid " + fieldName + ": " + value);
      }
      if (parsed < 0 || parsed > 255) {
        throw new BadRequestException("Invalid " + fieldName + ": " + value);
      }
      result = (result << 8) | parsed;
    }
    return result;
  }
}
