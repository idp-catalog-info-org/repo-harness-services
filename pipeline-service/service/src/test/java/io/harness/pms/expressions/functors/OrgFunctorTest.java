/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.serializer.MapperUtils;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrgFunctorTest extends CategoryTest {
  @Mock private OrganizationClient organizationClient;
  private Ambiance ambiance = Ambiance.newBuilder().build();
  private Ambiance ambiance1 =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "accountId")
          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
          .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
          .build();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBindReturnsNullForEmptyAmbiance() {
    OrgFunctor orgFunctor = new OrgFunctor(organizationClient, ambiance);
    assertNull(orgFunctor.bind());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBindThrowsExceptionOnError() {
    try (MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = Mockito.mockStatic(NGRestUtils.class)) {
      ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any()))
          .thenThrow(new RuntimeException("Test exception"));

      OrgFunctor orgFunctor = new OrgFunctor(organizationClient, ambiance1);
      assertThatThrownBy(() -> orgFunctor.bind()).isInstanceOf(EngineFunctorException.class);
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBindReturnsOrgData() {
    try (MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Optional<OrganizationResponse> resData =
          Optional.of(OrganizationResponse.builder().organization(OrganizationDTO.builder().build()).build());

      ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(resData);

      OrgFunctor orgFunctor = new OrgFunctor(organizationClient, ambiance1);
      assertEquals(orgFunctor.bind(), MapperUtils.toMapViaJsonString(resData.get().getOrganization()));
    }
  }
}
