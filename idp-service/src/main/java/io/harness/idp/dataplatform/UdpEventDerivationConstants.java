/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class UdpEventDerivationConstants {
  public static final String LOG_PREFIX = "[IDP_UDP_DERIVATION_DEBUG]";
  public static final String IDP_CONFIG_PREFIX = "idp:";
  public static final String EVENT_DERIVATION_CONFIG_TYPE_ID = "event_derivation_config";
  public static final String EVENT_DERIVATION_SERDE_PROTO_CLASS =
      "io.harness.config_models.derivation_config.v1.EventDerivationConfig";
  public static final String CONNECTOR_MAPPING_CONFIG_TYPE_ID = "connector_mapping_config";
  public static final String CONNECTOR_MAPPING_SERDE_PROTO_CLASS =
      "io.harness.config_models.connector_mapping_config.v1.ConnectorMappingConfig";
  public static final String CONNECTOR_NAME = "starrocks_harness";
  public static final String CONNECTOR_CONFIG_TYPE_ID = "connector_config";
  public static final String TABLE_FQN = "udp.harness_entities";

  public static String derivationConfigUuid(String accountIdentifier, String kindIdentifier) {
    String name = accountIdentifier + ":" + IDP_CONFIG_PREFIX + normalizeKindIdentifier(kindIdentifier)
        + "_entity_event_derivation_config";
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
  }

  public static String derivationConfigName(String accountIdentifier, String kindIdentifier) {
    return accountIdentifier + ":" + IDP_CONFIG_PREFIX + normalizeKindIdentifier(kindIdentifier)
        + "_entity_event_derivation_config";
  }

  public static String connectorMappingConfigUuid(String accountIdentifier, String kindIdentifier) {
    String name = accountIdentifier + ":" + IDP_CONFIG_PREFIX + normalizeKindIdentifier(kindIdentifier)
        + "_connector_mapping_config";
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
  }

  public static String connectorMappingConfigName(String accountIdentifier, String kindIdentifier) {
    return accountIdentifier + ":" + IDP_CONFIG_PREFIX + normalizeKindIdentifier(kindIdentifier)
        + "_connector_mapping_config";
  }

  static String normalizeKindIdentifier(String kindIdentifier) {
    if (kindIdentifier == null || kindIdentifier.isBlank()) {
      return "";
    }
    StringBuilder normalized = new StringBuilder();
    for (int i = 0; i < kindIdentifier.length(); i++) {
      char ch = kindIdentifier.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        if (Character.isUpperCase(ch) && i > 0 && Character.isLowerCase(kindIdentifier.charAt(i - 1))
            && normalized.charAt(normalized.length() - 1) != '_') {
          normalized.append('_');
        }
        normalized.append(Character.toLowerCase(ch));
      } else if (normalized.length() > 0 && normalized.charAt(normalized.length() - 1) != '_') {
        normalized.append('_');
      }
    }
    while (normalized.length() > 0 && normalized.charAt(normalized.length() - 1) == '_') {
      normalized.deleteCharAt(normalized.length() - 1);
    }
    return normalized.toString().toLowerCase(Locale.ROOT);
  }
}
