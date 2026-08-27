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
import io.harness.platform.schema.service.api.v1.GetTypeRequest;
import io.harness.platform.schema.service.api.v1.GetTypeResponse;
import io.harness.platform.schema.service.api.v1.ObjectKind;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.schema.service.api.v1.SchemaServiceGrpc;

import com.google.inject.Inject;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class SchemaServiceUdpEntityTypeReader implements UdpEntityTypeReader {
  private static final Metadata.Key<String> X_TENANT_ID_KEY =
      Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> X_HARNESS_ACCOUNT_ID_KEY =
      Metadata.Key.of("x-harness-account-id", Metadata.ASCII_STRING_MARSHALLER);
  private static final long GRPC_CALL_DEADLINE_SECONDS = 15;

  @Nullable private final SchemaServiceGrpc.SchemaServiceBlockingV2Stub schemaServiceBlockingV2Stub;

  @Inject
  public SchemaServiceUdpEntityTypeReader(
      @Nullable SchemaServiceGrpc.SchemaServiceBlockingV2Stub schemaServiceBlockingV2Stub) {
    this.schemaServiceBlockingV2Stub = schemaServiceBlockingV2Stub;
  }

  @Override
  public Optional<ObjectType> getEntityType(String accountIdentifier, String typeId) {
    if (isEmpty(typeId) || isEmpty(accountIdentifier) || schemaServiceBlockingV2Stub == null) {
      log.info("{} schema reader skip typeId={} accountPresent={} stubPresent={}",
          UdpEventDerivationConstants.LOG_PREFIX, typeId, !isEmpty(accountIdentifier),
          schemaServiceBlockingV2Stub != null);
      return Optional.empty();
    }

    log.info("{} schema reader fetch start typeId={} account={}", UdpEventDerivationConstants.LOG_PREFIX, typeId,
        accountIdentifier);
    try {
      SchemaServiceGrpc.SchemaServiceBlockingV2Stub stubWithHeaders =
          schemaServiceBlockingV2Stub
              .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(buildTenantMetadata(accountIdentifier)))
              .withDeadlineAfter(GRPC_CALL_DEADLINE_SECONDS, TimeUnit.SECONDS);

      GetTypeRequest grpcRequest =
          GetTypeRequest.newBuilder().setKind(ObjectKind.OBJECT_KIND_ENTITY).setId(typeId).build();

      GetTypeResponse response = stubWithHeaders.getType(grpcRequest);

      if (!response.hasType()) {
        log.info("{} schema reader fetch empty typeId={}", UdpEventDerivationConstants.LOG_PREFIX, typeId);
        return Optional.empty();
      }
      ObjectType fetchedType = response.getType();
      log.info("{} schema reader fetch success typeId={} fields={}", UdpEventDerivationConstants.LOG_PREFIX, typeId,
          fetchedType.hasEntityType() ? fetchedType.getEntityType().getFieldsCount() : 0);
      return Optional.of(fetchedType);
    } catch (StatusRuntimeException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatus().getCode())) {
        log.info("{} schema reader fetch not found typeId={}", UdpEventDerivationConstants.LOG_PREFIX, typeId);
        return Optional.empty();
      }
      log.error("{} schema reader fetch failure typeId={}, aborting publish", UdpEventDerivationConstants.LOG_PREFIX,
          typeId, ex);
      throw ex;
    } catch (Exception ex) {
      log.error("{} schema reader fetch failure typeId={}, aborting publish", UdpEventDerivationConstants.LOG_PREFIX,
          typeId, ex);
      throw new IllegalStateException("Failed to fetch existing ObjectType " + typeId + " from schema service", ex);
    }
  }

  private static Metadata buildTenantMetadata(String accountIdentifier) {
    Metadata metadata = new Metadata();
    metadata.put(X_TENANT_ID_KEY, accountIdentifier);
    metadata.put(X_HARNESS_ACCOUNT_ID_KEY, accountIdentifier);
    return metadata;
  }
}
