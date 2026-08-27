/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.platform.config.service.api.v1.ConfigReference;
import io.harness.platform.config.service.api.v1.ConfigServiceGrpc;
import io.harness.platform.config.service.api.v1.GetConfigRequest;
import io.harness.platform.config.service.api.v1.GetConfigResponse;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ConfigServiceUdpEventDerivationConfigReader implements UdpEventDerivationConfigReader {
  private static final Metadata.Key<String> X_TENANT_ID_KEY =
      Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> X_HARNESS_ACCOUNT_ID_KEY =
      Metadata.Key.of("x-harness-account-id", Metadata.ASCII_STRING_MARSHALLER);
  private static final long GRPC_CALL_DEADLINE_SECONDS = 15;

  private final ConfigServiceGrpc.ConfigServiceBlockingV2Stub configServiceBlockingV2Stub;

  @Inject
  public ConfigServiceUdpEventDerivationConfigReader(
      @Nullable ConfigServiceGrpc.ConfigServiceBlockingV2Stub configServiceBlockingV2Stub) {
    this.configServiceBlockingV2Stub = configServiceBlockingV2Stub;
  }

  @Override
  public Optional<EventDerivationConfig> getConfig(String accountIdentifier, String uuid) {
    String typeId = UdpEventDerivationConstants.EVENT_DERIVATION_CONFIG_TYPE_ID;
    if (isEmpty(uuid) || isEmpty(accountIdentifier) || configServiceBlockingV2Stub == null) {
      log.info("{} reader skip typeId={} uuid={} accountPresent={} stubPresent={}",
          UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid, !isEmpty(accountIdentifier),
          configServiceBlockingV2Stub != null);
      return Optional.empty();
    }
    log.info("{} reader fetch start typeId={} uuid={} account={}", UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid,
        accountIdentifier);
    try {
      ConfigServiceGrpc.ConfigServiceBlockingV2Stub stubWithHeaders =
          configServiceBlockingV2Stub
              .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(buildTenantMetadata(accountIdentifier)))
              .withDeadlineAfter(GRPC_CALL_DEADLINE_SECONDS, TimeUnit.SECONDS);

      GetConfigRequest grpcRequest =
          GetConfigRequest.newBuilder()
              .setConfigReference(ConfigReference.newBuilder().setTypeId(typeId).setUuid(uuid).build())
              .build();

      GetConfigResponse response = stubWithHeaders.getConfig(grpcRequest);

      if (!response.hasConfig() || !response.getConfig().hasPayload()) {
        log.info(
            "{} reader fetch empty payload typeId={} uuid={}", UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid);
        return Optional.empty();
      }
      String jsonValue = response.getConfig().getPayload().getJsonValue();
      if (isEmpty(jsonValue)) {
        log.info("{} reader fetch blank json typeId={} uuid={}", UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid);
        return Optional.empty();
      }
      EventDerivationConfig.Builder builder = EventDerivationConfig.newBuilder();
      JsonFormat.parser().ignoringUnknownFields().merge(jsonValue, builder);
      EventDerivationConfig parsed = builder.build();
      log.info("{} reader parsed config typeId={} uuid={} entities={} attributes={} variables={}",
          UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid, parsed.getEntitiesCount(), parsed.getAttributesCount(),
          parsed.getVariablesCount());
      return Optional.of(parsed);
    } catch (InvalidProtocolBufferException ex) {
      log.error("{} reader parse failure typeId={} uuid={}, aborting publish to avoid blind upsert",
          UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid, ex);
      throw new IllegalStateException("Failed to parse existing event derivation config " + uuid, ex);
    } catch (Exception ex) {
      if (Status.Code.NOT_FOUND.equals(Status.fromThrowable(ex).getCode())) {
        log.info("{} reader fetch not found typeId={} uuid={}", UdpEventDerivationConstants.LOG_PREFIX, typeId, uuid);
        return Optional.empty();
      }
      log.error("{} reader fetch failure typeId={} uuid={}, aborting publish", UdpEventDerivationConstants.LOG_PREFIX,
          typeId, uuid, ex);
      if (ex instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Failed to fetch existing event derivation config " + uuid, ex);
    }
  }

  private static Metadata buildTenantMetadata(String accountIdentifier) {
    Metadata metadata = new Metadata();
    metadata.put(X_TENANT_ID_KEY, accountIdentifier);
    metadata.put(X_HARNESS_ACCOUNT_ID_KEY, accountIdentifier);
    return metadata;
  }
}
