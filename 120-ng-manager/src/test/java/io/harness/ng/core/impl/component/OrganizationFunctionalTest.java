/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl.component;

import static io.harness.ModuleType.CORE;
import static io.harness.beans.ScopeLevel.ACCOUNT;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_ORGANIZATIONS;
import static io.harness.enforcement.constants.RestrictionType.STATIC_LIMIT;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.rule.OwnerRule.SAHIBA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.FunctionalTests;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.servicedependencies.EnforcementClient;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.licensing.Edition;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class OrganizationFunctionalTest extends NgManagerTestBase {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String IDENTIFIER_1 = "identifier_1";
  public static final String IDENTIFIER_2 = "identifier_2";
  public static final String IDENTIFIER_3 = "identifier_3";
  public static final String USER_NAME = "user";
  public static final String EMAIL = "email";
  @Inject private OrganizationService organizationService;
  @Inject private EnforcementClient enforcementClient;
  @Inject private EnforcementClientConfiguration enforcementClientConfiguration;
  @Inject private AccessControlClient accessControlClient;
  private AutoCloseable autoCloseable;
  @Before
  public void setup() throws IOException {
    autoCloseable = MockitoAnnotations.openMocks(this);
    MockedStatic<SourcePrincipalContextBuilder> mockedStaticContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    mockedStaticContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(new UserPrincipal(USER_NAME, EMAIL, USER_NAME, ACCOUNT_IDENTIFIER));
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(FunctionalTests.class)
  public void shouldCreateOrgWhenLimitDoesNotReachForEnterpriseLicense() throws IOException {
    long limit = 10L;
    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap = Map.of(
        ENTERPRISE, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_ORGANIZATIONS)
                                                                      .edition(ENTERPRISE)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_ORGANIZATIONS, ACCOUNT_IDENTIFIER)).thenReturn(call);

    OrganizationDTO orgDTO_1 = createOrganizationDTO(IDENTIFIER_1);
    ScopeInfo scopeInfo = getAccountScopeInfo();
    organizationService.create(scopeInfo, orgDTO_1);

    OrganizationDTO orgDTO_2 = createOrganizationDTO(IDENTIFIER_2);
    Organization organization = organizationService.create(scopeInfo, orgDTO_2);
    assertThat(organization).isNotNull();
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(FunctionalTests.class)
  public void shouldNotCreateOrgWhenLimitDoesNotReachForEnterpriseLicense() throws IOException {
    OrganizationDTO orgDTO_1 = createOrganizationDTO(IDENTIFIER_1);
    OrganizationDTO orgDTO_2 = createOrganizationDTO(IDENTIFIER_2);

    ScopeInfo scopeInfo = getAccountScopeInfo();
    organizationService.create(scopeInfo, orgDTO_1);
    organizationService.create(scopeInfo, orgDTO_2);
    organizationService.get(scopeInfo);
    long limit = 2L;

    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap = Map.of(
        ENTERPRISE, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_ORGANIZATIONS)
                                                                      .edition(ENTERPRISE)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_ORGANIZATIONS, ACCOUNT_IDENTIFIER)).thenReturn(call);
    OrganizationDTO orgDTO_3 = createOrganizationDTO(IDENTIFIER_3);
    assertThatThrownBy(() -> organizationService.create(scopeInfo, orgDTO_3))
        .isInstanceOf(LimitExceededException.class);
  }

  private static ScopeInfo getAccountScopeInfo() {
    return ScopeInfo.builder()
        .scopeType(ACCOUNT)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .uniqueId(ACCOUNT_IDENTIFIER)
        .build();
  }

  private OrganizationDTO createOrganizationDTO(String identifier) {
    return OrganizationDTO.builder().identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @After
  public void tearDown() throws Exception {
    autoCloseable.close();
  }
}
