/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.beans.FeatureName.PL_OIDC_ENHANCED_SUBJECT_FIELD;
import static io.harness.rule.OwnerRule.MADHU;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.oidc.aws.dto.AwsOidcTokenRequestDto;
import io.harness.oidc.aws.utility.AwsOidcTokenUtility;
import io.harness.oidc.config.OidcConfigStructure;
import io.harness.oidc.config.OidcConfigurationUtility;
import io.harness.oidc.dto.CustomOidcIdTokenRequestDTO;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.gcp.dto.GcpOidcTokenRequestDTO;
import io.harness.oidc.gcp.utility.GcpOidcTokenUtility;
import io.harness.oidc.idtoken.OidcIdTokenConstants;
import io.harness.oidc.idtoken.OidcIdTokenCustomAttributesStructure;
import io.harness.oidc.idtoken.OidcIdTokenHeaderStructure;
import io.harness.oidc.idtoken.OidcIdTokenPayloadStructure;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.oidc.rsa.OidcRsaKeyService;
import io.harness.oidc.utility.CustomOidcTokenUtility;
import io.harness.oidc.vault.dto.VaultOidcTokenRequestDTO;
import io.harness.oidc.vault.utility.VaultOidcTokenUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.rsa.RSAKeysUtils;
import io.harness.rsa.RsaKeyPair;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Collections;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import retrofit2.Call;
import retrofit2.Response;

public class NgOidcIDTokenResourceTest extends CategoryTest {
  private static final String accountIdentifier = randomAlphabetic(10);
  private static final String orgIdentifier = randomAlphabetic(10);
  private static final String projectIdentifier = randomAlphabetic(10);
  private static final String pipelineIdentifier = randomAlphabetic(10);
  private static final String environmentIdentifier = randomAlphabetic(10);
  private static final String connectorIdentifier = randomAlphabetic(10);
  private static final String serviceIdentifier = randomAlphabetic(10);
  private static final String WorkloadPoolId = randomAlphabetic(10);
  private static final String ProviderId = randomAlphabetic(10);
  private static final String GcpProjectId = randomAlphabetic(10);
  private static final String sub = randomAlphabetic(10);
  private static final String aud = randomAlphabetic(10);
  private static final String oidc_sub = "account/" + accountIdentifier;
  private static final String oidc_aud = "https://iam.googleapis.com/projects/781732827384/locations/global/"
      + "workloadIdentityPools/oidc_test/providers/harness";
  private static final String oidc_iss = "https://token.oidc.harness.io/account/" + accountIdentifier;
  private static final String NULL = null;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock private AccessControlClient accessControlClient;
  @Mock private PipelineServiceClient pipelineServiceClient;

  @InjectMocks private CustomOidcTokenUtility customOidcTokenUtility;
  @InjectMocks private GcpOidcTokenUtility gcpOidcTokenUtility;
  @InjectMocks private AwsOidcTokenUtility awsOidcTokenUtility;
  @InjectMocks private VaultOidcTokenUtility vaultOidcTokenUtility;
  @Mock private OidcConfigurationUtility oidcConfigurationUtility;
  @Mock OidcRsaKeyService oidcRsaKeyService;
  @Mock OidcJwksUtility oidcJwksUtility;
  @Mock FeatureFlagService featureFlagService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private EnvironmentService environmentService;
  @Mock private ConnectorService connectorService;
  @Mock ServiceEntityService serviceEntityService;
  @Inject @InjectMocks NgOidcIDTokenResource resource;

  OidcConfigStructure.OidcTokenStructure oidcTokenStructure;
  OidcConfigStructure.OidcTokenStructure vaultOidcStructure;
  OidcIdTokenHeaderStructure oidcIdTokenHeaderStructure;
  OidcIdTokenPayloadStructure oidcIdTokenPayloadStructure;
  OidcIdTokenPayloadStructure vaultOidcIdTokenPayloadStructure;
  OidcIdTokenCustomAttributesStructure oidcIdTokenCustomAttributesStructure =
      new OidcIdTokenCustomAttributesStructure();
  RSAKeysUtils rsaKeysUtils;
  KeyPair keyPair;
  private static final String ACCOUNT_ID = randomAlphabetic(10);
  private static final String JWT_AUTH_PATH = "harness/jwt";
  private static final String vault_oidc_sub = "account/" + ACCOUNT_ID;
  private static final String vault_oidc_aud = JWT_AUTH_PATH;
  private static final String vault_oidc_iss = "https://cbx59xz44mn5.share.zrok.io/oidc/account/" + ACCOUNT_ID + "/";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    resource.vaultOidcTokenUtility = vaultOidcTokenUtility;
    Long base = currentTimeMillis();
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    vaultOidcIdTokenPayloadStructure = OidcIdTokenPayloadStructure.builder()
                                           .sub(vault_oidc_sub)
                                           .aud(vault_oidc_aud)
                                           .iss(vault_oidc_iss)
                                           .iat(base)
                                           .exp(base + 3599)
                                           .build();
    oidcIdTokenPayloadStructure = OidcIdTokenPayloadStructure.builder()
                                      .sub(oidc_sub)
                                      .aud(oidc_aud)
                                      .iss(oidc_iss)
                                      .iat(base)
                                      .exp(base + 3599)
                                      .build();
    oidcTokenStructure = new OidcConfigStructure.OidcTokenStructure();
    oidcTokenStructure.setOidcIdTokenHeaderStructure(oidcIdTokenHeaderStructure);
    oidcTokenStructure.setOidcIdTokenPayloadStructure(oidcIdTokenPayloadStructure);
    vaultOidcStructure = new OidcConfigStructure.OidcTokenStructure();
    vaultOidcStructure.setOidcIdTokenHeaderStructure(oidcIdTokenHeaderStructure);
    vaultOidcStructure.setOidcIdTokenPayloadStructure(vaultOidcIdTokenPayloadStructure);

    oidcIdTokenHeaderStructure = OidcIdTokenHeaderStructure.builder().typ("JWT").alg("RS256").kid("1234567").build();
    oidcIdTokenPayloadStructure = OidcIdTokenPayloadStructure.builder()
                                      .sub(oidc_sub)
                                      .aud(oidc_aud)
                                      .iss(oidc_iss)
                                      .iat(base)
                                      .exp(base + 3599)
                                      .build();
    oidcTokenStructure = new OidcConfigStructure.OidcTokenStructure();
    oidcTokenStructure.setOidcIdTokenHeaderStructure(oidcIdTokenHeaderStructure);
    oidcTokenStructure.setOidcIdTokenPayloadStructure(oidcIdTokenPayloadStructure);

    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfoCall);
    when(oidcConfigurationUtility.getCustomOidcTokenStructure()).thenReturn(oidcTokenStructure);
    when(oidcConfigurationUtility.getAwsOidcTokenStructure()).thenReturn(oidcTokenStructure);
    when(oidcConfigurationUtility.getGcpOidcTokenStructure()).thenReturn(oidcTokenStructure);
    when(oidcConfigurationUtility.getVaultOidcTokenStructure()).thenReturn(vaultOidcStructure);

    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_INTERNALIZE_OIDC_TOKEN_ENDPOINTS))
        .thenReturn(false);
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OIDC_ID_TOKEN_ACCESS_CHECK))
        .thenReturn(true);
    when(featureFlagService.isEnabled(any(FeatureName.class), any())).thenReturn(true);
    rsaKeysUtils = new RSAKeysUtils();
    keyPair = rsaKeysUtils.generateKeyPair();
    String privateKeyPEM = rsaKeysUtils.convertToPem(rsaKeysUtils.PRIVATE_KEY, keyPair.getPrivate());
    OidcJwks oidcJwks =
        OidcJwks.builder().accountId(accountIdentifier).keyId("asdcx").rsaKeyPair(RsaKeyPair.builder().build()).build();
    when(oidcRsaKeyService.getDecryptedJwksPrivateKeyPem(any(), any())).thenReturn(privateKeyPEM);
    when(oidcJwksUtility.getJwksKeys(any())).thenReturn(oidcJwks);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    oidcIdTokenCustomAttributesStructure.setAccountId(accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_AccountIdentifierMismatch() {
    oidcIdTokenCustomAttributesStructure.setAccountId(accountIdentifier + "MISMATCH");
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_AccountIdentifierNULL() {
    oidcIdTokenCustomAttributesStructure.setAccountId(NULL);
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(NULL, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_InvalidScope() {
    when(scopeInfoClient.getScopeInfo(any(), any(), any())).thenThrow(new InvalidRequestException("Scope is invalid"));
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_CheckFFDisabled() {
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OIDC_ID_TOKEN_ACCESS_CHECK))
        .thenReturn(false);
    resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure);
    verify(scopeInfoClient, times(0)).getScopeInfo(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_CheckFFEnabled() {
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OIDC_ID_TOKEN_ACCESS_CHECK))
        .thenReturn(true);
    resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure);
    verify(scopeInfoClient, times(1)).getScopeInfo(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccessAccount_FFDisabled() {
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_INTERNALIZE_OIDC_TOKEN_ENDPOINTS))
        .thenReturn(true);
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_InvalidpipelineIdentifier() throws IOException {
    oidcIdTokenCustomAttributesStructure.setPipelineIdentifier(pipelineIdentifier);
    Call pipelineCall = mock(Call.class);
    when(pipelineServiceClient.getPipelineByIdentifier(
             pipelineIdentifier, accountIdentifier, null, null, null, null, null))
        .thenReturn(pipelineCall);
    when(pipelineCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(null)));

    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_EnvironmentIdentifier() {
    oidcIdTokenCustomAttributesStructure.setEnvironmentIdentifier(environmentIdentifier);
    Pageable page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
    Page<Environment> environment = new PageImpl<>(
        Collections.singletonList(
            Environment.builder().accountId(accountIdentifier).identifier(environmentIdentifier).build()),
        page, 1);
    when(environmentService.list(any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_ServiceIdentifier() {
    oidcIdTokenCustomAttributesStructure.setServiceIdentifier(serviceIdentifier);
    when(serviceEntityService.list(any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_ConnectorIdentifier() {
    oidcIdTokenCustomAttributesStructure.setConnectorIdentifier(connectorIdentifier);
    when(connectorService.listAll(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_applyAdditionalOIDCAccessChecks() throws IOException {
    oidcIdTokenCustomAttributesStructure.setPipelineIdentifier(pipelineIdentifier);
    Call pipelineCall = mock(Call.class);
    when(pipelineServiceClient.getPipelineByIdentifier(
             pipelineIdentifier, accountIdentifier, null, null, null, null, null))
        .thenReturn(pipelineCall);
    when(pipelineCall.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(PMSPipelineResponseDTO.builder().yamlPipeline(pipelineIdentifier).build())));
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_noAccess() {
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, oidcIdTokenCustomAttributesStructure))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void checkForIdTokenAccess_NulloidcIdTokenCustomAttributesStructureNoAccess() {
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(() -> resource.checkForIdTokenAccess(accountIdentifier, null))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  AwsOidcTokenRequestDto getAwsOidcTokenRequestDto() {
    return AwsOidcTokenRequestDto.builder()
        .accountId(accountIdentifier)
        .oidcIdTokenCustomAttributesStructure(
            OidcIdTokenCustomAttributesStructure.builder().accountId(accountIdentifier).build())
        .build();
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateAwsOidcIdToken_FFon() {
    String expectedSubject = "account/" + accountIdentifier + ":org/:project/";
    AwsOidcTokenRequestDto awsOidcTokenRequestDto = getAwsOidcTokenRequestDto();
    String idToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcTokenRequestDto);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(expectedSubject);
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateAwsOidcIdTokenOrg_FFon() {
    String expectedSubject = "account/" + accountIdentifier + ":org/" + orgIdentifier + ":project/";
    AwsOidcTokenRequestDto awsOidcTokenRequestDto = getAwsOidcTokenRequestDto();
    awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure().setOrganizationId(orgIdentifier);
    String idToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcTokenRequestDto);
    Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    assertThat(claims.getBody().getSubject()).isEqualTo(expectedSubject);
    assertThat(claims.getBody().get(OidcIdTokenConstants.ACCOUNT_ID)).isEqualTo(accountIdentifier);
    assertThat(claims.getBody().get(OidcIdTokenConstants.ORGANIZATION_ID)).isEqualTo(orgIdentifier);
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateAwsOidcIdTokenProject_FFon() {
    String expectedSubject = "account/" + accountIdentifier + ":org/" + orgIdentifier + ":project/" + projectIdentifier;
    AwsOidcTokenRequestDto awsOidcTokenRequestDto = getAwsOidcTokenRequestDto();
    awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure().setOrganizationId(orgIdentifier);
    awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure().setProjectIdentifier(projectIdentifier);

    String idToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcTokenRequestDto);
    Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    assertThat(claims.getBody().getSubject()).isEqualTo(expectedSubject);
    assertThat(claims.getBody().get(OidcIdTokenConstants.ACCOUNT_ID)).isEqualTo(accountIdentifier);
    assertThat(claims.getBody().get(OidcIdTokenConstants.ORGANIZATION_ID)).isEqualTo(orgIdentifier);
    assertThat(claims.getBody().get(OidcIdTokenConstants.PROJECT_ID)).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateAwsOidcIdToken_FFoff() {
    AwsOidcTokenRequestDto awsOidcTokenRequestDto = getAwsOidcTokenRequestDto();
    awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure().setOrganizationId(orgIdentifier);
    awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure().setProjectIdentifier(projectIdentifier);
    when(featureFlagService.isEnabled(PL_OIDC_ENHANCED_SUBJECT_FIELD, accountIdentifier)).thenReturn(false);
    String idToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcTokenRequestDto);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(oidc_sub);
  }

  CustomOidcIdTokenRequestDTO getCustomOidcIdTokenRequestDTO() {
    return CustomOidcIdTokenRequestDTO.builder()
        .accountId(accountIdentifier)
        .sub(sub)
        .aud(aud)
        .oidcIdTokenCustomAttributesStructure(oidcIdTokenCustomAttributesStructure)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void getCustomOidcIdToken_FFon() {
    String expectedSubject = sub;
    CustomOidcIdTokenRequestDTO customOidcIdTokenRequestDTO = getCustomOidcIdTokenRequestDTO();
    String idToken = customOidcTokenUtility.getCustomOidcIdTokenWithCustomAttributes(customOidcIdTokenRequestDTO);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(expectedSubject);
  }

  @Test
  @Owner(developers = OwnerRule.MADHU)
  @Category(UnitTests.class)
  public void getCustomOidcIdToken_FFoff() {
    String expectedSubject = sub;
    when(featureFlagService.isEnabled(PL_OIDC_ENHANCED_SUBJECT_FIELD, accountIdentifier)).thenReturn(false);
    CustomOidcIdTokenRequestDTO customOidcIdTokenRequestDTO = getCustomOidcIdTokenRequestDTO();
    String idToken = customOidcTokenUtility.getCustomOidcIdTokenWithCustomAttributes(customOidcIdTokenRequestDTO);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(expectedSubject);
  }

  GcpOidcTokenRequestDTO getGcpOidcTokenRequestDTO() {
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = new GcpOidcTokenRequestDTO();
    gcpOidcTokenRequestDTO.setAccountId(accountIdentifier);
    gcpOidcTokenRequestDTO.setWorkloadPoolId(WorkloadPoolId);
    gcpOidcTokenRequestDTO.setProviderId(ProviderId);
    gcpOidcTokenRequestDTO.setGcpProjectId(GcpProjectId);
    gcpOidcTokenRequestDTO.setOidcIdTokenCustomAttributesStructure(oidcIdTokenCustomAttributesStructure);
    return gcpOidcTokenRequestDTO;
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateGCPOidcIdToken_FFon() {
    String expectedSubject = "account/" + accountIdentifier;
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = getGcpOidcTokenRequestDTO();
    String idToken = gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcTokenRequestDTO);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(expectedSubject);
  }

  @Test
  @Owner(developers = MADHU)
  @Category(UnitTests.class)
  public void generateGCPOidcIdToken_FFoff() {
    when(featureFlagService.isEnabled(PL_OIDC_ENHANCED_SUBJECT_FIELD, accountIdentifier)).thenReturn(false);
    String expectedSubject = "account/" + accountIdentifier;
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = getGcpOidcTokenRequestDTO();
    String idToken = gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcTokenRequestDTO);
    Jws<Claims> jwksClaims = Jwts.parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(idToken);
    Claims claims = jwksClaims.getBody();
    assertThat(claims.getSubject()).isEqualTo(expectedSubject);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testGenerateVaultOidcIdToken_Success() {
    VaultOidcTokenRequestDTO requestDTO = getVaultOidcTokenRequestDTO();
    ResponseDTO<String> response = resource.generateVaultOidcIdToken(requestDTO);
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testGenerateVaultOidcIdToken_AccountIdIsDifferentInPayload() {
    VaultOidcTokenRequestDTO requestDTO = getVaultOidcTokenRequestDTO();
    requestDTO.getOidcIdTokenCustomAttributesStructure().setAccountId(accountIdentifier + "MISMATCH");
    assertThatThrownBy(() -> resource.generateVaultOidcIdToken(requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Mismatch in accountId in payload");
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testGenerateVaultOidcIdToken_NullAccountId() {
    VaultOidcTokenRequestDTO requestDTO =
        VaultOidcTokenRequestDTO.builder()
            .accountId(NULL)
            .jwtAuthPath("/jwt")
            .oidcIdTokenCustomAttributesStructure(
                OidcIdTokenCustomAttributesStructure.builder().accountId(NULL).build())
            .build();

    assertThatThrownBy(() -> resource.generateVaultOidcIdToken(requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Account Id not present");
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testGenerateVaultOidcIdToken_FFDisabled_NoAccessCheck() {
    VaultOidcTokenRequestDTO requestDTO = getVaultOidcTokenRequestDTO();
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_OIDC_ID_TOKEN_ACCESS_CHECK))
        .thenReturn(false);
    ResponseDTO<String> response = resource.generateVaultOidcIdToken(requestDTO);
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    verify(scopeInfoClient, times(0)).getScopeInfo(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testGenerateVaultOidcIdToken_InternalizeFFEnabled() {
    VaultOidcTokenRequestDTO requestDTO = getVaultOidcTokenRequestDTO();
    when(ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_INTERNALIZE_OIDC_TOKEN_ENDPOINTS))
        .thenReturn(true);

    assertThatThrownBy(() -> resource.generateVaultOidcIdToken(requestDTO)).isInstanceOf(NotFoundException.class);
  }

  private VaultOidcTokenRequestDTO getVaultOidcTokenRequestDTO() {
    return VaultOidcTokenRequestDTO.builder()
        .accountId(accountIdentifier)
        .jwtAuthPath("/jwt")
        .oidcIdTokenCustomAttributesStructure(
            OidcIdTokenCustomAttributesStructure.builder().accountId(accountIdentifier).build())
        .build();
  }
}
