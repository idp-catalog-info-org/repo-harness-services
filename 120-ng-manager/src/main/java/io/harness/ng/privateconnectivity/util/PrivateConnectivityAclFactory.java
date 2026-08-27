/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityRouterType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Builds vendor ACL JSON for private connectivity networks.
 */
@OwnedBy(CI)
@UtilityClass
public class PrivateConnectivityAclFactory {
  private static final ObjectMapper CANONICAL_MAPPER =
      new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  private static final List<String> APP_CONNECTOR_DESTINATIONS =
      List.of("autogroup:internet", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

  /**
   * Builds the complete server-owned Tailscale policy for an account's dedicated tailnet.
   * Deny-by-default: no runner→runner grant; no inbound grants to tag:ci-runner.
   */
  public static String buildAclJson(PrivateConnectivitySetupRequestDTO request) {
    try {
      List<String> routes =
          request.getAdvertiseRoutes() == null ? List.of() : List.copyOf(request.getAdvertiseRoutes());
      List<String> domains = request.getDomains() == null ? List.of() : List.copyOf(request.getDomains());
      PrivateConnectivityRouterType routerType = PrivateConnectivityRouterType.fromConfiguration(routes, domains);

      boolean subnet =
          routerType == PrivateConnectivityRouterType.SUBNET_ROUTER || routerType == PrivateConnectivityRouterType.BOTH;
      boolean app =
          routerType == PrivateConnectivityRouterType.APP_CONNECTOR || routerType == PrivateConnectivityRouterType.BOTH;

      ObjectNode root = CANONICAL_MAPPER.createObjectNode();

      ObjectNode tagOwners = root.putObject("tagOwners");
      tagOwners.putArray(PrivateConnectivityHelpers.CI_RUNNER_TAG).add("autogroup:admin");
      // Customer appliances share one server-owned identity. Tailscale derives each appliance's
      // behavior from --advertise-routes and/or --advertise-connector, not from Harness role tags.
      tagOwners.putArray(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG).add("autogroup:admin");
      // Phase 1 supports an SRE-applied helper deployment; deployment automation is intentionally separate.
      tagOwners.putArray(PrivateConnectivityHelpers.HELPER_TAG).add("autogroup:admin");

      ArrayNode grants = root.putArray("grants");
      List<String> estateDst = new ArrayList<>();
      if (subnet) {
        estateDst.addAll(routes);
      }
      if (!estateDst.isEmpty()) {
        grants.add(grant(List.of(PrivateConnectivityHelpers.CI_RUNNER_TAG, PrivateConnectivityHelpers.HELPER_TAG),
            estateDst, List.of("*")));
      }
      if (app) {
        // App Connector backends are dynamic routes, so grants cover their possible resolved
        // addresses while nodeAttrs limits discovery to the configured domains.
        grants.add(grant(List.of(PrivateConnectivityHelpers.CI_RUNNER_TAG, PrivateConnectivityHelpers.HELPER_TAG),
            APP_CONNECTOR_DESTINATIONS, List.of("*")));
        // Clients need DNS access to the connector for App Connector route discovery, but no other
        // direct access to the customer appliance is required.
        grants.add(grant(List.of(PrivateConnectivityHelpers.CI_RUNNER_TAG, PrivateConnectivityHelpers.HELPER_TAG),
            List.of(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG), List.of("tcp:53", "udp:53")));
      }

      ObjectNode autoApprovers = root.putObject("autoApprovers");
      ObjectNode autoRoutes = autoApprovers.putObject("routes");
      if (subnet) {
        for (String cidr : routes) {
          autoRoutes.putArray(cidr).add(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
        }
      }
      if (app) {
        // App Connector discovery advertises dynamic routes, so its appliance needs broad route approval.
        autoRoutes.putArray("0.0.0.0/0").add(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
        autoRoutes.putArray("::/0").add(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
      }

      if (app && !domains.isEmpty()) {
        ArrayNode nodeAttrs = root.putArray("nodeAttrs");
        ObjectNode attr = nodeAttrs.addObject();
        // Tailscale App Connector discovery attributes are evaluated globally; tag ownership and
        // the connector's "connectors" list constrain which nodes may advertise discovered /32s.
        attr.putArray("target").add("*");
        ObjectNode appNode = attr.putObject("app");
        ArrayNode connectors = appNode.putArray("tailscale.com/app-connectors");
        ObjectNode connector = connectors.addObject();
        connector.put("name", "harness-app-connector");
        connector.putArray("connectors").add(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
        ArrayNode domainArr = connector.putArray("domains");
        for (String domain : domains) {
          domainArr.add(domain);
        }
      }

      // Policy test: assert lateral-movement denial.
      ArrayNode tests = root.putArray("tests");
      // Tailscale ACL test destinations are users, tags, or host addresses—not CIDR routes.
      // A route such as 10.10.0.0/16 is valid in grants/autoApprovers but is rejected as an ACL
      // test host. Route reachability is verified by the live subnet-router acceptance tests.
      ObjectNode denyPeer = tests.addObject();
      denyPeer.put("src", PrivateConnectivityHelpers.CI_RUNNER_TAG);
      denyPeer.putArray("deny").add(PrivateConnectivityHelpers.CI_RUNNER_TAG + ":22");

      return CANONICAL_MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to build private connectivity ACL JSON", e);
    }
  }

  private static ObjectNode grant(List<String> src, List<String> dst, List<String> ip) {
    ObjectNode grant = CANONICAL_MAPPER.createObjectNode();
    ArrayNode srcArr = grant.putArray("src");
    src.forEach(srcArr::add);
    ArrayNode dstArr = grant.putArray("dst");
    dst.forEach(dstArr::add);
    ArrayNode ipArr = grant.putArray("ip");
    ip.forEach(ipArr::add);
    return grant;
  }
}
