/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JATIN;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.opa.ConnectorOpaEvaluationContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.opa.OpaEvaluationContext;
import io.harness.opa.OpaServiceImpl;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.rule.Owner;
import io.harness.secret.opa.SecretOpaEvaluationContext;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.SourcePrincipalHelper;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PL)
@Slf4j
public class OpaServiceImplTest extends NgManagerTestBase {
  @Mock private OpaServiceClientHelper opaServiceClientHelper;
  @Mock private NextGenConfiguration nextGenConfiguration;
  @Mock SourcePrincipalContextBuilder sourcePrincipalContextBuilder;
  @Mock UserPrincipal principal;

  private OpaServiceImpl opaService;

  @Before
  public void setup() throws IllegalAccessException {
    opaService = new OpaServiceImpl(opaServiceClientHelper, true);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluateSecret() throws IOException {
    try {
      SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().identifier("id").name("name").build();
      OpaEvaluationContext opaEvaluationContext = SecretOpaEvaluationContext.builder().secret(secretDTOV2).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("pass").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      when(principal.getType()).thenReturn(PrincipalType.USER);
      when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn("userId");
      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");

      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluatSecret2() {
    try {
      SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().identifier("id").name("name").build();
      OpaEvaluationContext opaEvaluationContext = SecretOpaEvaluationContext.builder().secret(secretDTOV2).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      when(principal.getType()).thenReturn(PrincipalType.USER);
      when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn("userId");

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluateSecret3() {
    try {
      SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().identifier("id").name("name").build();
      OpaEvaluationContext opaEvaluationContext = SecretOpaEvaluationContext.builder().secret(secretDTOV2).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluateSecret4() {
    try {
      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata =
          opaService.evaluate(null, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");
    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluateSecret5() {
    try {
      SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().identifier("id").name("name").build();
      OpaEvaluationContext opaEvaluationContext = SecretOpaEvaluationContext.builder().secret(secretDTOV2).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("pass").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("pass");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());
      assertThat(evaluationResponse.getId()).isEqualTo(secretDTOV2.getIdentifier());

    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluate() throws IOException {
    try {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().name("name").identifier("id").orgIdentifier("orgId").build();
      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      OpaEvaluationContext opaEvaluationContext = ConnectorOpaEvaluationContext.builder().entity(connectorDTO).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("pass").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      when(principal.getType()).thenReturn(PrincipalType.USER);
      when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn("userId");

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");

      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluate2() {
    try {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().name("name").identifier("id").orgIdentifier("orgId").build();
      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      OpaEvaluationContext opaEvaluationContext = ConnectorOpaEvaluationContext.builder().entity(connectorDTO).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      when(principal.getType()).thenReturn(PrincipalType.USER);
      when(SourcePrincipalHelper.getPrincipalIdentifier()).thenReturn("userId");

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluate3() {
    try {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().name("name").identifier("id").orgIdentifier("orgId").build();
      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      OpaEvaluationContext opaEvaluationContext = ConnectorOpaEvaluationContext.builder().entity(connectorDTO).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluate4() {
    try {
      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("error").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata =
          opaService.evaluate(null, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("error");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluate5() {
    try {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().name("name").identifier("id").orgIdentifier("orgId").build();
      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      OpaEvaluationContext opaEvaluationContext = ConnectorOpaEvaluationContext.builder().entity(connectorDTO).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("pass").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");
      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getStatus()).isEqualTo("pass");

      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());
      assertThat(evaluationResponse.getId()).isEqualTo(connectorInfoDTO.getIdentifier());

    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = JATIN)
  @Category(UnitTests.class)
  public void testEvaluateNullSourcePrincipal() throws IOException {
    try {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().name("name").identifier("id").orgIdentifier("orgId").build();
      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      OpaEvaluationContext opaEvaluationContext = ConnectorOpaEvaluationContext.builder().entity(connectorDTO).build();

      OpaEvaluationResponseHolder evaluationResponse =
          OpaEvaluationResponseHolder.builder().status("pass").id("id").build();
      when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(),
               anyString(), any(), anyString(), anyString(), any(), any()))
          .thenReturn(evaluationResponse);

      GovernanceMetadata governanceMetadata = opaService.evaluate(
          opaEvaluationContext, "accountId", "orgIdentifier", "projectIdentifier", "identifier", "onsave", "key");

      assertThat(governanceMetadata.getStatus()).isNotBlank();
      assertThat(governanceMetadata.getStatus()).isEqualTo(evaluationResponse.getStatus());
      assertThat(governanceMetadata.getId()).isEqualTo(evaluationResponse.getId());

    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }
}
