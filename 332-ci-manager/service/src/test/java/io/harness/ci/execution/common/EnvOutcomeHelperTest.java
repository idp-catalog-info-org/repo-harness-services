/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.envgroup.unified.UnifiedEnvGroupResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class EnvOutcomeHelperTest {
  private static final String ACCOUNT_ID = "acct";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";
  private static final String ENV_GROUP_REF = "eg1";

  @Mock private EnvironmentGroupService environmentGroupService;
  @Mock private EnvironmentGroupResourceClient environmentGroupResourceClient;
  @InjectMocks private EnvOutcomeHelper envOutcomeHelper;

  private EnvironmentEntity baseEnvironmentEntity() {
    return EnvironmentEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .identifier("env1")
        .name("Environment 1")
        .description("Test environment")
        .type(EnvironmentType.Production)
        .tags(Collections.emptyList())
        .build();
  }

  private IdentifierRef envGroupIdentifierRef() {
    return IdentifierRef.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .identifier(ENV_GROUP_REF)
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeWithoutEnvGroup() {
    EnvironmentEntity entity = EnvironmentEntity.builder()
                                   .identifier("env1")
                                   .name("Environment 1")
                                   .description("Test environment")
                                   .type(EnvironmentType.Production)
                                   .tags(Collections.emptyList())
                                   .build();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcome("env1", entity, null);
    assertThat(outcome.getIdentifier()).isEqualTo("env1");
    assertThat(outcome.getName()).isEqualTo("Environment 1");
    assertThat(outcome.getRef()).isEqualTo("env1");
    assertThat(outcome.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(outcome.getGroup()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeWithEmptyEnvGroupRef() {
    EnvironmentEntity entity = EnvironmentEntity.builder()
                                   .identifier("env1")
                                   .name("Env")
                                   .type(EnvironmentType.PreProduction)
                                   .tags(Collections.emptyList())
                                   .build();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcome("env1", entity, "");
    assertThat(outcome.getIdentifier()).isEqualTo("env1");
    assertThat(outcome.getGroup()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeWithEnvGroupFound() {
    EnvironmentEntity entity = baseEnvironmentEntity();
    EnvironmentGroupEntity envGroup = EnvironmentGroupEntity.builder()
                                          .id("mongo-eg-id")
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .identifier(ENV_GROUP_REF)
                                          .name("Env Group Name")
                                          .description("Env group desc")
                                          .tags(Collections.emptyList())
                                          .build();
    when(environmentGroupService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_REF)).thenReturn(Optional.of(envGroup));

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcome("env1", entity, ENV_GROUP_REF);

    assertThat(outcome.getGroup()).isNotNull();
    assertThat(outcome.getGroup().getId()).isEqualTo("mongo-eg-id");
    assertThat(outcome.getGroup().getRef()).isEqualTo(ENV_GROUP_REF);
    assertThat(outcome.getGroup().getName()).isEqualTo("Env Group Name");
    assertThat(outcome.getGroup().getDescription()).isEqualTo("Env group desc");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeWithEnvGroupMissing() {
    EnvironmentEntity entity = baseEnvironmentEntity();
    when(environmentGroupService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_REF)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> envOutcomeHelper.getEnvironmentOutcome("env1", entity, ENV_GROUP_REF))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(ENV_GROUP_REF)
        .hasMessageContaining(PROJECT_ID)
        .hasMessageContaining(ORG_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGInfraResponseWithNullResponse() {
    UnifiedInfraConvertorResponse response = UnifiedInfraConvertorResponse.builder().build();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse("env1", response, null);
    assertThat(outcome.getRef()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGInfraResponseFullEnvironmentNoGroup() {
    UnifiedEnvironmentConverterResponseDTO envDto = UnifiedEnvironmentConverterResponseDTO.builder()
                                                        .identifier("ng-env-id")
                                                        .name("NG Env")
                                                        .description("NG desc")
                                                        .tags(Map.of("t", "v"))
                                                        .type(EnvironmentType.PreProduction)
                                                        .build();
    UnifiedInfraConverterResponseDTO responseDto =
        UnifiedInfraConverterResponseDTO.builder().environmentResponse(envDto).build();
    UnifiedInfraConvertorResponse response = UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse("refVal", response, null);

    assertThat(outcome.getRef()).isEqualTo("refVal");
    assertThat(outcome.getIdentifier()).isEqualTo("ng-env-id");
    assertThat(outcome.getName()).isEqualTo("NG Env");
    assertThat(outcome.getDescription()).isEqualTo("NG desc");
    assertThat(outcome.getTags()).containsEntry("t", "v");
    assertThat(outcome.getType()).isEqualTo(EnvironmentType.PreProduction);
    assertThat(outcome.getGroup()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGInfraResponsePartialDtoFallsBackToRefOnly() {
    UnifiedInfraConverterResponseDTO responseDto = UnifiedInfraConverterResponseDTO.builder()
                                                       .identifier("ignored")
                                                       .name("ignored")
                                                       .environmentResponse(null)
                                                       .build();
    UnifiedInfraConvertorResponse response = UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse("onlyRef", response, null);

    assertThat(outcome.getRef()).isEqualTo("onlyRef");
    assertThat(outcome.getIdentifier()).isNull();
    assertThat(outcome.getName()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGInfraResponseWithEnvGroup() {
    UnifiedEnvironmentConverterResponseDTO envDto =
        UnifiedEnvironmentConverterResponseDTO.builder().identifier("e").name("n").build();
    UnifiedInfraConverterResponseDTO responseDto =
        UnifiedInfraConverterResponseDTO.builder().environmentResponse(envDto).build();
    UnifiedInfraConvertorResponse response = UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvGroupResponseDTO>> envGroupCall = mock(Call.class);
    when(environmentGroupResourceClient.getUnifiedEnvironmentGroup(ENV_GROUP_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .thenReturn(envGroupCall);
    UnifiedEnvGroupResponseDTO groupDto = UnifiedEnvGroupResponseDTO.builder()
                                              .id("unified-eg-id")
                                              .name("Unified EG")
                                              .description("d")
                                              .tags(Map.of("a", "b"))
                                              .build();

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(envGroupCall)).thenReturn(groupDto);

      EnvironmentOutcome outcome =
          envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse("r", response, envGroupIdentifierRef());

      assertThat(outcome.getGroup()).isNotNull();
      assertThat(outcome.getGroup().getRef()).isEqualTo(ENV_GROUP_REF);
      assertThat(outcome.getGroup().getId()).isEqualTo("unified-eg-id");
      assertThat(outcome.getGroup().getName()).isEqualTo("Unified EG");
      assertThat(outcome.getGroup().getTags()).containsEntry("a", "b");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGInfraResponseEnvGroupNotFound() {
    UnifiedEnvironmentConverterResponseDTO envDto =
        UnifiedEnvironmentConverterResponseDTO.builder().identifier("e").name("n").build();
    UnifiedInfraConverterResponseDTO responseDto =
        UnifiedInfraConverterResponseDTO.builder().environmentResponse(envDto).build();
    UnifiedInfraConvertorResponse response = UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvGroupResponseDTO>> envGroupCall = mock(Call.class);
    when(environmentGroupResourceClient.getUnifiedEnvironmentGroup(ENV_GROUP_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .thenReturn(envGroupCall);

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(envGroupCall)).thenReturn(null);

      assertThatThrownBy(
          () -> envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse("r", response, envGroupIdentifierRef()))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining(ENV_GROUP_REF);
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGEnvWithoutEnvGroup() {
    EnvironmentEntity entity = baseEnvironmentEntity();

    EnvironmentOutcome outcome = envOutcomeHelper.getEnvironmentOutcomeFromNGEnv("ref1", entity, null);

    assertThat(outcome.getRef()).isEqualTo("ref1");
    assertThat(outcome.getIdentifier()).isEqualTo("env1");
    assertThat(outcome.getName()).isEqualTo("Environment 1");
    assertThat(outcome.getGroup()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGEnvWithEnvGroup() {
    EnvironmentEntity entity = baseEnvironmentEntity();
    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvGroupResponseDTO>> envGroupCall = mock(Call.class);
    when(environmentGroupResourceClient.getUnifiedEnvironmentGroup(ENV_GROUP_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .thenReturn(envGroupCall);
    UnifiedEnvGroupResponseDTO groupDto =
        UnifiedEnvGroupResponseDTO.builder().id("id-ng").name("ng-name").description("ng-d").build();

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(envGroupCall)).thenReturn(groupDto);

      EnvironmentOutcome outcome =
          envOutcomeHelper.getEnvironmentOutcomeFromNGEnv("ref1", entity, envGroupIdentifierRef());

      assertThat(outcome.getGroup()).isNotNull();
      assertThat(outcome.getGroup().getRef()).isEqualTo(ENV_GROUP_REF);
      assertThat(outcome.getGroup().getId()).isEqualTo("id-ng");
      assertThat(outcome.getGroup().getName()).isEqualTo("ng-name");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentOutcomeFromNGEnvEnvGroupNotFound() {
    EnvironmentEntity entity = baseEnvironmentEntity();
    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvGroupResponseDTO>> envGroupCall = mock(Call.class);
    when(environmentGroupResourceClient.getUnifiedEnvironmentGroup(ENV_GROUP_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .thenReturn(envGroupCall);

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(envGroupCall)).thenReturn(null);

      assertThatThrownBy(() -> envOutcomeHelper.getEnvironmentOutcomeFromNGEnv("ref1", entity, envGroupIdentifierRef()))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining(ENV_GROUP_REF);
    }
  }
}
