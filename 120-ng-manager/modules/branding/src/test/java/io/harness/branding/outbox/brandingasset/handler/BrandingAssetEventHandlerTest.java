/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.brandingasset.handler;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetDeleteEvent;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetEvent;
import io.harness.branding.outbox.brandingasset.events.BrandingAssetUploadEvent;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class BrandingAssetEventHandlerTest extends CategoryTest {
  @Mock private AuditClientService auditClientService;
  @Mock private BrandingMapper brandingMapper;

  @InjectMocks private BrandingAssetEventHandler brandingAssetEventHandler;

  private static final String ACCOUNT_ID = "test-account";
  private static final String ASSET_ID = "test-asset-id";
  private ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleBrandingAssetUploadEvent() throws Exception {
    BrandingAsset brandingAsset = BrandingAsset.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .assetId(ASSET_ID)
                                      .assetType("LARGE_LOGO_LIGHT")
                                      .mimeType("image/png")
                                      .build();

    BrandingAssetUploadEvent uploadEvent = new BrandingAssetUploadEvent(ACCOUNT_ID, brandingAsset);
    BrandingAssetsDTO assetsDTO = new BrandingAssetsDTO().assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT");

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_UPLOADED)
                                  .eventData(objectMapper.writeValueAsString(uploadEvent))
                                  .resourceScope(uploadEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingAssetsDTO(brandingAsset)).thenReturn(assetsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(brandingMapper, times(1)).toBrandingAssetsDTO(brandingAsset);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleBrandingAssetDeleteEvent() throws Exception {
    BrandingAsset brandingAsset = BrandingAsset.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .assetId(ASSET_ID)
                                      .assetType("LARGE_LOGO_LIGHT")
                                      .mimeType("image/png")
                                      .build();

    BrandingAssetDeleteEvent deleteEvent = new BrandingAssetDeleteEvent(ACCOUNT_ID, brandingAsset);
    BrandingAssetsDTO assetsDTO = new BrandingAssetsDTO().assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT");

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingAssetsDTO(brandingAsset)).thenReturn(assetsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(brandingMapper, times(1)).toBrandingAssetsDTO(brandingAsset);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleUnsupportedEventType() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("UNSUPPORTED_EVENT")
                                  .eventData("{}")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    assertThatThrownBy(() -> brandingAssetEventHandler.handle(outboxEvent))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Not supported event type UNSUPPORTED_EVENT");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleUploadEventWithInvalidJson() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_UPLOADED)
                                  .eventData("invalid-json")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventWithInvalidJson() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_DELETED)
                                  .eventData("invalid-json")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleUploadEventAuditFailure() throws Exception {
    BrandingAsset brandingAsset = BrandingAsset.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .assetId(ASSET_ID)
                                      .assetType("LARGE_LOGO_LIGHT")
                                      .mimeType("image/png")
                                      .build();

    BrandingAssetUploadEvent uploadEvent = new BrandingAssetUploadEvent(ACCOUNT_ID, brandingAsset);
    BrandingAssetsDTO assetsDTO = new BrandingAssetsDTO().assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT");

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_UPLOADED)
                                  .eventData(objectMapper.writeValueAsString(uploadEvent))
                                  .resourceScope(uploadEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingAssetsDTO(brandingAsset)).thenReturn(assetsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(false);

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
    verify(brandingMapper, times(1)).toBrandingAssetsDTO(brandingAsset);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventAuditFailure() throws Exception {
    BrandingAsset brandingAsset = BrandingAsset.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .assetId(ASSET_ID)
                                      .assetType("LARGE_LOGO_LIGHT")
                                      .mimeType("image/png")
                                      .build();

    BrandingAssetDeleteEvent deleteEvent = new BrandingAssetDeleteEvent(ACCOUNT_ID, brandingAsset);
    BrandingAssetsDTO assetsDTO = new BrandingAssetsDTO().assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT");

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingAssetEvent.BRANDING_ASSET_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingAssetsDTO(brandingAsset)).thenReturn(assetsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(false);

    boolean result = brandingAssetEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
    verify(brandingMapper, times(1)).toBrandingAssetsDTO(brandingAsset);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }
}