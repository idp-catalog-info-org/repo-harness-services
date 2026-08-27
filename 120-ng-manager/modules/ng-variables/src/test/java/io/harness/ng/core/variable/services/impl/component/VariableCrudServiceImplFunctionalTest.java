/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl.component;
import static io.harness.ModuleType.CORE;
import static io.harness.beans.ScopeLevel.PROJECT;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_VARIABLES;
import static io.harness.enforcement.constants.RestrictionType.STATIC_LIMIT;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.ng.core.variable.VariableType.STRING;
import static io.harness.ng.core.variable.VariableValueType.FIXED;
import static io.harness.rule.OwnerRule.SAHIBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ScopeInfo;
import io.harness.category.element.FunctionalTests;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.servicedependencies.EnforcementClient;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.licensing.Edition;
import io.harness.ng.core.base.NgVariableTestBase;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class VariableCrudServiceImplFunctionalTest extends NgVariableTestBase {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String IDENTIFIER_1 = "identifier1";
  public static final String IDENTIFIER_2 = "identifier2";
  public static final String IDENTIFIER_3 = "identifier3";
  public static final String VALUE = "value";
  private AutoCloseable autoCloseable;
  @Inject private VariableService variableService;
  @Inject private EnforcementClient enforcementClient;
  @Inject private EnforcementClientConfiguration enforcementClientConfiguration;

  @Before
  public void setup() throws IOException {
    autoCloseable = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(FunctionalTests.class)
  public void shouldCreateVariableWhenLimitDoesNotReachForEnterpriseLicense() throws IOException {
    VariableDTO variableDTO1 = getVariableDTO(IDENTIFIER_1, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VALUE);
    ScopeInfo scopeInfo = getProjScopeInfo();

    variableService.create(scopeInfo, variableDTO1);
    long limit = 10L;

    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap = Map.of(
        ENTERPRISE, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_VARIABLES)
                                                                      .edition(ENTERPRISE)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_VARIABLES, ACCOUNT_IDENTIFIER)).thenReturn(call);
    VariableDTO variableDTO2 = getVariableDTO(IDENTIFIER_2, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VALUE);
    assertThat(variableDTO2).isNotNull();
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(FunctionalTests.class)
  public void shouldNotCreateVariableWhenLimitReachesForEnterpriseLicense() throws IOException {
    VariableDTO variableDTO1 = getVariableDTO(IDENTIFIER_1, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VALUE);
    VariableDTO variableDTO2 = getVariableDTO(IDENTIFIER_2, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VALUE);
    VariableDTO variableDTO3 = getVariableDTO(IDENTIFIER_3, ORG_IDENTIFIER, PROJECT_IDENTIFIER, VALUE);
    ScopeInfo scopeInfo = getProjScopeInfo();

    variableService.create(scopeInfo, variableDTO1);
    variableService.create(scopeInfo, variableDTO2);

    long limit = 2L;

    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap = Map.of(
        ENTERPRISE, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_VARIABLES)
                                                                      .edition(ENTERPRISE)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_VARIABLES, ACCOUNT_IDENTIFIER)).thenReturn(call);
    assertThatThrownBy(() -> variableService.create(scopeInfo, variableDTO3))
        .isInstanceOf(LimitExceededException.class);
  }

  private static ScopeInfo getProjScopeInfo() {
    return ScopeInfo.builder()
        .scopeType(PROJECT)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .uniqueId(PROJECT_IDENTIFIER)
        .build();
  }

  private VariableDTO getVariableDTO(String identifier, String orgIdentifier, String projectIdentifier, String value) {
    return VariableDTO.builder()
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED).fixedValue(value).build())
        .type(STRING)
        .description("Description")
        .build();
  }

  @After
  public void tearDown() throws Exception {
    autoCloseable.close();
  }
}