/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class UserBillingMetricJobTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks UserBillingMetricJob userBillingMetricJob;
  @Mock NamespaceService namespaceService;
  @Mock AccessControlAdminClient accessControlAdminClient;
  @Mock UserClient userClient;
  @Mock MetricService metricService;

  private static final String EXECUTION_DATE = "2025-12-16";
  private static final String ACCOUNT_ID = "account123";
  private static final String USER1_USERNAME = "user1";
  private static final String USER1_EMAIL = "user1@example.com";
  private static final String USER2_USERNAME = "user2";
  private static final String USER2_EMAIL = "user2@example.com";
  private static final String USER3_USERNAME = "user3";
  private static final String USER3_EMAIL = "user3@example.com";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCollectBillingEvents() {
    when(namespaceService.getAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(
            PageResponse.builder()
                .content(List.of(RoleAssignmentResponseDTO.builder()
                                     .roleAssignment(RoleAssignmentDTO.builder()
                                                         .principal(PrincipalDTO.builder().identifier("admin").build())
                                                         .build())
                                     .build()))
                .build());

    MockedStatic<CGRestUtils> mockCGRestUtils = mockStatic(CGRestUtils.class);
    mockCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(constructUsers());

    List<BillingEvent> billingEvents = userBillingMetricJob.collectBillingEvents(EXECUTION_DATE);
    assertThat(billingEvents).hasSize(3);
    BillingEvent event1 = billingEvents.get(0);
    assertThat(event1.getResourceParentUniqueIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(event1.getIdempotencyKey()).contains(ACCOUNT_ID);
    assertThat(event1.getIdempotencyKey()).contains(EXECUTION_DATE);
    assertThat(event1.getTagsMap()).hasSize(3);
    BillingEvent event2 = billingEvents.get(1);
    assertThat(event2.getResourceParentUniqueIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(event2.getIdempotencyKey()).contains(ACCOUNT_ID);
    assertThat(event2.getIdempotencyKey()).contains(EXECUTION_DATE);
    assertThat(event2.getTagsMap()).hasSize(3);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testCollectBillingEventsRecordsMetrics() {
    when(namespaceService.getAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(
            PageResponse.builder()
                .content(List.of(RoleAssignmentResponseDTO.builder()
                                     .roleAssignment(RoleAssignmentDTO.builder()
                                                         .principal(PrincipalDTO.builder().identifier("admin").build())
                                                         .build())
                                     .build()))
                .build());
    MockedStatic<CGRestUtils> mockCGRestUtils = mockStatic(CGRestUtils.class);
    mockCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(constructUsers());

    List<BillingEvent> billingEvents = userBillingMetricJob.collectBillingEvents(EXECUTION_DATE);
    assertThat(billingEvents).hasSize(3);
    verify(metricService, times(1)).recordMetric(eq("idp_user_billing_processed_total"), eq(3.0));
    mockRestStatic.close();
    mockCGRestUtils.close();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testCollectBillingEventsRecordsErrorMetricOnException() {
    when(namespaceService.getAccountIds()).thenThrow(new RuntimeException("service unavailable"));

    assertThatThrownBy(() -> userBillingMetricJob.collectBillingEvents(EXECUTION_DATE))
        .isInstanceOf(RuntimeException.class);
    verify(metricService, times(1)).incCounter("idp_user_billing_error_total");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testCollectBillingEventsWithNoUsers() {
    when(namespaceService.getAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(PageResponse.builder().content(new ArrayList<>()).totalPages(0).build());

    List<BillingEvent> billingEvents = userBillingMetricJob.collectBillingEvents(EXECUTION_DATE);
    assertThat(billingEvents).isEmpty();
    verify(metricService, times(0)).recordMetric(eq("idp_user_billing_processed_total"), anyDouble());
    verify(metricService, times(0)).recordMetric(eq("idp_user_billing_error_total"), anyDouble());
    mockRestStatic.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private List<UserInfo> constructUsers() {
    List<UserInfo> users = new ArrayList<>();

    UserInfo user1 = UserInfo.builder().email(USER1_EMAIL).name(USER1_USERNAME).uuid(USER1_USERNAME).build();
    UserInfo user2 = UserInfo.builder().email(USER2_EMAIL).name(USER2_USERNAME).uuid(USER2_USERNAME).build();
    UserInfo user3 = UserInfo.builder().email(USER3_EMAIL).name(USER3_USERNAME).uuid(USER3_USERNAME).build();
    users.add(user1);
    users.add(user2);
    users.add(user3);
    return users;
  }
}
