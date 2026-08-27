/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.utils.ModuleConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_OIDC_ID_TOKEN_PERMISSION;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.entities.Connector;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.oidc.aws.dto.AwsOidcTokenRequestDto;
import io.harness.oidc.aws.utility.AwsOidcTokenUtility;
import io.harness.oidc.azure.dto.AzureOidcTokenRequestDTO;
import io.harness.oidc.azure.utility.AzureOidcTokenUtility;
import io.harness.oidc.dto.CustomOidcIdTokenRequestDTO;
import io.harness.oidc.gcp.dto.GcpOidcTokenRequestDTO;
import io.harness.oidc.gcp.utility.GcpOidcTokenUtility;
import io.harness.oidc.idtoken.OidcIdTokenCustomAttributesStructure;
import io.harness.oidc.utility.CustomOidcTokenUtility;
import io.harness.oidc.vault.dto.VaultOidcTokenRequestDTO;
import io.harness.oidc.vault.utility.VaultOidcTokenUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.NGFeatureFlagHelperService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Path("/oidc/id-token")
@Api("/oidc/id-token")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "Oidc-ID-Token",
    description = "This contains APIs related to OIDC ID Token generation as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@Slf4j
public class NgOidcIDTokenResource {
  @Inject GcpOidcTokenUtility gcpOidcTokenUtility;
  @Inject AwsOidcTokenUtility awsOidcTokenUtility;
  @Inject CustomOidcTokenUtility customOidcTokenUtility;
  @Inject AccessControlClient accessControlClient;
  @Inject AzureOidcTokenUtility azureOidcTokenUtility;
  @Inject VaultOidcTokenUtility vaultOidcTokenUtility;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject EnvironmentService environmentService;
  @Inject @Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService;
  @Inject @Named("PRIVILEGED") PipelineServiceClient pipelineServiceClient;
  @Inject ServiceEntityService serviceEntityService;

  private static final String RESOURCE_TYPE_PIPELINE = "PIPELINE";
  private static final String ACCOUNT_ID_KEY = "accountId";
  private static final String ORG_ID_KEY = "orgIdentifier";
  private static final String PROJECT_ID_KEY = "projectIdentifier";
  private static final String IDENTIFIER_KEY = "identifier";
  private static final String DELETED_KEY = "deleted";

  @POST
  @Path("gcp")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate an OIDC ID Token for GCP", nickname = "generateOidcIdTokenForGcp")
  @Operation(operationId = "generateOidcIdTokenForGcp", summary = "Generates an OIDC ID Token for GCP",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  generateGcpOidcIdToken(@RequestBody(required = true,
      description = "Details of GCP Workload Identity") @Valid GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", gcpOidcTokenRequestDTO);
    }
    checkForIdTokenAccess(
        gcpOidcTokenRequestDTO.getAccountId(), gcpOidcTokenRequestDTO.getOidcIdTokenCustomAttributesStructure());
    String idToken = gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcTokenRequestDTO);
    return ResponseDTO.newResponse(idToken);
  }

  @POST
  @Path("gcp-v2")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(
      value = "Generate an OIDC ID Token for GCP with custom attributes", nickname = "generateOidcIdTokenForGcp2")
  @Operation(operationId = "generateOidcIdTokenForGcp", summary = "Generates an OIDC ID Token for GCP",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  public ResponseDTO<String>
  generateGcpOidcIdTokenV2(@RequestBody(required = true,
      description = "Details of GCP Workload Identity") @Valid GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", gcpOidcTokenRequestDTO);
    }
    checkForIdTokenAccess(
        gcpOidcTokenRequestDTO.getAccountId(), gcpOidcTokenRequestDTO.getOidcIdTokenCustomAttributesStructure());
    String idToken = gcpOidcTokenUtility.generateGcpOidcIdTokenV2(gcpOidcTokenRequestDTO);
    return ResponseDTO.newResponse(idToken);
  }

  @POST
  @Path("aws")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate an OIDC ID Token for AWS", nickname = "generateOidcIdTokenForAws")
  @Operation(operationId = "generateOidcIdTokenForAws", summary = "Generates an OIDC ID Token for AWS",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  getOidcIdTokenForAws(@RequestBody(required = true,
      description = "contains oidc fields for aws") @Valid AwsOidcTokenRequestDto awsOidcTokenRequestDto) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", awsOidcTokenRequestDto);
    }
    checkForIdTokenAccess(
        awsOidcTokenRequestDto.getAccountId(), awsOidcTokenRequestDto.getOidcIdTokenCustomAttributesStructure());
    String idToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcTokenRequestDto);
    return ResponseDTO.newResponse(idToken);
  }

  @POST
  @Path("custom")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate a custom OIDC ID Token", nickname = "generateCustomOidcIdToken")
  @Operation(operationId = "generateCustomOidcIdToken", summary = "Generate a custom OIDC ID Token",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  getCustomOidcIdToken(@RequestBody(required = true, description = "contains oidc fields for custom token")
      @Valid CustomOidcIdTokenRequestDTO customOidcIdTokenRequestDTO) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", customOidcIdTokenRequestDTO);
    }
    checkForIdTokenAccess(customOidcIdTokenRequestDTO.getAccountId(),
        customOidcIdTokenRequestDTO.getOidcIdTokenCustomAttributesStructure());
    String idToken = customOidcTokenUtility.getCustomOidcIdTokenWithCustomAttributes(customOidcIdTokenRequestDTO);
    return ResponseDTO.newResponse(idToken);
  }

  @POST
  @Path("vault")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate an OIDC ID Token for Vault", nickname = "generateOidcIdTokenForVault")
  @Operation(operationId = "generateOidcIdTokenForVault", summary = "Generates an OIDC ID Token for Vault",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  @Timed
  @ResponseMetered
  @InternalApi
  @Hidden
  public ResponseDTO<String>
  generateVaultOidcIdToken(@RequestBody(required = true,
      description = "contains oidc field for vault") @Valid VaultOidcTokenRequestDTO vaultOidcTokenRequestDTO) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", vaultOidcTokenRequestDTO);
    }
    checkForIdTokenAccess(
        vaultOidcTokenRequestDTO.getAccountId(), vaultOidcTokenRequestDTO.getOidcIdTokenCustomAttributesStructure());

    String idToken = vaultOidcTokenUtility.generateVaultOidcIdToken(vaultOidcTokenRequestDTO);
    return ResponseDTO.newResponse(idToken);
  }

  @POST
  @Path("azure")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Generate an OIDC ID Token for Azure", nickname = "generateOidcIdTokenForAzure")
  @Operation(operationId = "generateOidcIdTokenForAzure", summary = "Generates an OIDC ID Token for Azure",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC ID Token as a JWT")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  generateAzureOidcIdToken(@RequestBody(required = true,
      description = "contains oidc field for azure") @Valid AzureOidcTokenRequestDTO azureOidcTokenRequestDTO) {
    if (!isServicePrincipal()) {
      log.warn("OIDC ID token create request received for non-service principal {}", azureOidcTokenRequestDTO);
    }
    checkForIdTokenAccess(
        azureOidcTokenRequestDTO.getAccountId(), azureOidcTokenRequestDTO.getOidcIdTokenCustomAttributesStructure());

    String idToken = azureOidcTokenUtility.generateAzureOidcIdToken(azureOidcTokenRequestDTO);
    return ResponseDTO.newResponse(idToken);
  }
  @VisibleForTesting
  protected void checkForIdTokenAccess(
      String accountId, OidcIdTokenCustomAttributesStructure oidcIdTokenCustomAttributesStructure) {
    String finalAccountId = validateAndGetAccountId(accountId, oidcIdTokenCustomAttributesStructure);

    if (shouldApplyCheck(finalAccountId)
        && ngFeatureFlagHelperService.isEnabled(finalAccountId, FeatureName.PL_ENABLE_OIDC_ID_TOKEN_ACCESS_CHECK)) {
      String orgId = null;
      String projectId = null;

      if (oidcIdTokenCustomAttributesStructure != null) {
        orgId = oidcIdTokenCustomAttributesStructure.getOrganizationId();
        projectId = oidcIdTokenCustomAttributesStructure.getProjectIdentifier();
      }

      ScopeInfo scopeInfo = validateScopeInfo(finalAccountId, orgId, projectId);

      if (oidcIdTokenCustomAttributesStructure != null) {
        checkIfAdditionAttributeIsAValidResource(scopeInfo, oidcIdTokenCustomAttributesStructure);
        if (haveAnyAdditionalAttributes(oidcIdTokenCustomAttributesStructure)) {
          applyAdditionalOIDCAccessChecks(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier(), oidcIdTokenCustomAttributesStructure);
        } else {
          doAccessCheckOnScope(scopeInfo);
        }
      } else {
        doAccessCheckOnScope(scopeInfo);
      }
    }
  }

  private boolean shouldApplyCheck(String finalAccountId) {
    boolean isAPIInternal =
        ngFeatureFlagHelperService.isEnabled(finalAccountId, FeatureName.PL_INTERNALIZE_OIDC_TOKEN_ENDPOINTS);

    boolean isServicePrincipal = isServicePrincipal();
    if (isServicePrincipal) {
      return false;
    }

    if (isAPIInternal) {
      throw new NotFoundException();
    }
    return true;
  }

  private boolean isServicePrincipal() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal != null) {
      return PrincipalType.SERVICE.equals(principal.getType());
    }
    return false;
  }

  private void doAccessCheckOnScope(ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of("OIDC_ID_TOKEN", null), CREATE_OIDC_ID_TOKEN_PERMISSION);
  }

  private String validateAndGetAccountId(
      String accountId, OidcIdTokenCustomAttributesStructure oidcIdTokenCustomAttributesStructure) {
    if (isNotEmpty(accountId) && oidcIdTokenCustomAttributesStructure != null
        && isNotEmpty(oidcIdTokenCustomAttributesStructure.getAccountId())) {
      if (!accountId.equals(oidcIdTokenCustomAttributesStructure.getAccountId())) {
        throw new InvalidRequestException("Mismatch in accountId in payload");
      }
    } else if (oidcIdTokenCustomAttributesStructure != null
        && isNotEmpty(oidcIdTokenCustomAttributesStructure.getAccountId())) {
      return oidcIdTokenCustomAttributesStructure.getAccountId();
    }
    if (isEmpty(accountId)) {
      throw new InvalidRequestException("Account Id not present in request");
    }
    return accountId;
  }

  private void checkIfAdditionAttributeIsAValidResource(
      ScopeInfo scopeInfo, OidcIdTokenCustomAttributesStructure oidcIdTokenCustomAttributesStructure) {
    String pipelineIdentifier = oidcIdTokenCustomAttributesStructure.getPipelineIdentifier();
    String serviceIdentifier = oidcIdTokenCustomAttributesStructure.getServiceIdentifier();
    String environmentIdentifier = oidcIdTokenCustomAttributesStructure.getEnvironmentIdentifier();
    String connectorIdentifier = oidcIdTokenCustomAttributesStructure.getConnectorIdentifier();

    if (isNotEmpty(pipelineIdentifier)) {
      PMSPipelineResponseDTO response = NGRestUtils.getResponse(
          pipelineServiceClient.getPipelineByIdentifier(pipelineIdentifier, scopeInfo.getAccountIdentifier(),
              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), null, null, null));
      if (response == null) {
        throw new InvalidRequestException(
            String.format("Pipeline with identifier [%s] does not exist", pipelineIdentifier));
      }
    }
    if (isNotEmpty(serviceIdentifier)) {
      Criteria criteria = createCriteria(scopeInfo, serviceIdentifier);
      Page<ServiceEntity> serviceEntity = serviceEntityService.list(criteria, Pageable.unpaged());
      if (serviceEntity.isEmpty() || isEmpty(serviceEntity.getContent())) {
        throw new InvalidRequestException(
            String.format("Service with identifier [%s] does not exist", serviceIdentifier));
      }
    }
    if (isNotEmpty(environmentIdentifier)) {
      Criteria criteria = createCriteria(scopeInfo, environmentIdentifier);
      Page<Environment> environment = environmentService.list(criteria, Pageable.unpaged());
      if (environment.isEmpty() || !environment.hasContent()) {
        throw new InvalidRequestException(
            String.format("Environment with identifier [%s] does not exist", environmentIdentifier));
      }
    }
    if (isNotEmpty(connectorIdentifier)) {
      ConnectorFilterPropertiesDTO filterDTO =
          ConnectorFilterPropertiesDTO.builder().connectorIdentifiers(List.of(connectorIdentifier)).build();
      Page<Connector> connectorResponseDTO =
          connectorService.listAll(scopeInfo, filterDTO, null, null, true, Pageable.unpaged(), null);
      if (connectorResponseDTO.isEmpty() || isEmpty(connectorResponseDTO.getContent())) {
        throw new InvalidRequestException(
            String.format("Connector with identifier [%s] does not exist", connectorIdentifier));
      }
    }
  }

  private Criteria createCriteria(ScopeInfo scopeInfo, String identifier) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria accountCriteria = getCriteriaForScope(scopeInfo.getAccountIdentifier(), null, null, identifier);
    criteriaList.add(accountCriteria);
    if (isNotEmpty(scopeInfo.getOrgIdentifier())) {
      Criteria orgCriteria =
          getCriteriaForScope(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null, identifier);
      criteriaList.add(orgCriteria);
    }

    if (isNotEmpty(scopeInfo.getOrgIdentifier()) && isNotEmpty(scopeInfo.getProjectIdentifier())) {
      Criteria projectCriteria = getCriteriaForScope(
          scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), identifier);
      criteriaList.add(projectCriteria);
    }

    return new Criteria().orOperator(criteriaList);
  }

  private Criteria getCriteriaForScope(
      @NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return Criteria.where(ACCOUNT_ID_KEY)
        .is(accountIdentifier)
        .and(ORG_ID_KEY)
        .is(orgIdentifier)
        .and(PROJECT_ID_KEY)
        .is(projectIdentifier)
        .and(IDENTIFIER_KEY)
        .is(identifier)
        .and(DELETED_KEY)
        .is(false);
  }

  private boolean haveAnyAdditionalAttributes(
      OidcIdTokenCustomAttributesStructure oidcIdTokenCustomAttributesStructure) {
    return isNotEmpty(oidcIdTokenCustomAttributesStructure.getPipelineIdentifier());
  }

  private ScopeInfo validateScopeInfo(String accountId, String orgId, String projectId) {
    try {
      return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountId, orgId, projectId));
    } catch (Exception e) {
      throw new InvalidRequestException("Scope is not valid");
    }
  }

  private void applyAdditionalOIDCAccessChecks(
      String accountId, String orgId, String projectId, OidcIdTokenCustomAttributesStructure attributes) {
    checkAccessIfPresent(accountId, orgId, projectId, attributes.getPipelineIdentifier(), RESOURCE_TYPE_PIPELINE);
    // todo handle access check for other attributes if needed later
  }

  private void checkAccessIfPresent(
      String accountId, String orgId, String projectId, String resourceIdentifier, String resourceType) {
    if (isNotEmpty(resourceIdentifier)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of(resourceType, resourceIdentifier), CREATE_OIDC_ID_TOKEN_PERMISSION);
    }
  }
}
