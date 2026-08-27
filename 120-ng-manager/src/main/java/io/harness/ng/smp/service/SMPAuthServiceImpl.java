/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.smp.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.config.SmpConfig;
import io.harness.ng.smp.dto.PublicKeyDTO;
import io.harness.ng.smp.entities.SMPAuthInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.ng.smp.SMPAuthInfoRepository;
import io.harness.rest.RestResponse;
import io.harness.rsa.RSAKeyPairPEM;
import io.harness.rsa.RSAKeysUtils;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.zendesk.remote.ZendeskManagerClient;

import software.wings.beans.ZendeskSsoLoginResponse;

import com.auth0.jwt.interfaces.Claim;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

/**
 * Service class for managing SMP authentication.
 */
@Slf4j
@OwnedBy(PL)
public class SMPAuthServiceImpl {
  // Constants
  private static final String TOKEN_ISSUER = "Harness SMP";
  private static final String JWT_TYPE = "JWT";
  private static final String JWT_ALG = "RS256";
  private static final long TOKEN_VALIDITY_MINUTES = 5;
  private static final String ACCOUNT_ID_CLAIM = "account_id";
  private static final String ISSUER_CLAIM = "iss";
  private static final String USER_EMAIL_CLAIM = "email";
  private static final String USER_NAME_CLAIM = "name";
  private static final String ZENDESK_SSO_PATH = "ng/api/smp/auth/zendesk/sso";

  // Repository and utilities
  private final SMPAuthInfoRepository smpAuthInfoRepository;
  private final RSAKeysUtils rsaKeysUtils;
  private final SmpConfig smpConfig;
  private final ZendeskManagerClient zendeskManagerClient;

  /**
   * Constructor for SMPAuthServiceImpl.
   *
   * @param smpAuthInfoRepository SMPAuthInfoRepository instance
   * @param rsaKeysUtils          RSAKeysUtils instance
   */
  @Inject
  public SMPAuthServiceImpl(SMPAuthInfoRepository smpAuthInfoRepository, RSAKeysUtils rsaKeysUtils, SmpConfig smpConfig,
      ZendeskManagerClient zendeskManagerClient) {
    this.smpAuthInfoRepository = smpAuthInfoRepository;
    this.rsaKeysUtils = rsaKeysUtils;
    this.smpConfig = smpConfig;
    this.zendeskManagerClient = zendeskManagerClient;
  }

  /**
   * Saves SMP authentication information for an account.
   *
   * @param accountIdentifier    The Harness account identifier
   * @param smpAccountIdentifier The SMP account identifier
   * @param publicKey            The public key in PEM format
   * @param privateKey           The private key in PEM format (may be empty for public key only entries)
   */
  private void save(String accountIdentifier, String smpAccountIdentifier, String publicKey, String privateKey) {
    log.info("Saving SMP auth info for account {} with SMP account {} and public key", accountIdentifier,
        smpAccountIdentifier);

    SMPAuthInfo smpAuthInfo = SMPAuthInfo.builder()
                                  .smpAccountIdentifier(smpAccountIdentifier)
                                  .accountIdentifier(accountIdentifier)
                                  .publicKey(publicKey)
                                  .privateKey(privateKey)
                                  .build();

    try {
      smpAuthInfoRepository.save(smpAuthInfo);
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(String.format(
          "SMP auth info with account identifier %s is already present or was deleted in scope", accountIdentifier));
    }
  }

  /**
   * Retrieves the public key for a given account.
   *
   * @param accountIdentifier The account identifier
   * @return The public key in PEM format, or null if not found
   */
  public String getPublicKey(String accountIdentifier) {
    SMPAuthInfo smpAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    return smpAuthInfo != null ? smpAuthInfo.getPublicKey() : null;
  }

  /**
   * Generates a new RSA key pair for the account only if one doesn't exist already.
   * Throws exception if a key pair already exists.
   *
   * @param accountIdentifier The account identifier
   * @return A PublicKeyDTO containing the public key and account identifier
   * @throws DuplicateFieldException if a key pair already exists for the account
   */
  public PublicKeyDTO generateKeyPair(String accountIdentifier) {
    // Check if key already exists
    SMPAuthInfo existingAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    if (existingAuthInfo != null) {
      throw new DuplicateFieldException(String.format(
          "Key pair already exists for account identifier %s. Use rotate to update existing key.", accountIdentifier));
    }

    RSAKeyPairPEM rsaKeyPairPEM = rsaKeysUtils.generateKeyPairPEM();
    save(accountIdentifier, accountIdentifier, rsaKeyPairPEM.getPublicKeyPem(), rsaKeyPairPEM.getPrivateKeyPem());

    return PublicKeyDTO.builder()
        .publicKey(rsaKeyPairPEM.getPublicKeyPem())
        .accountIdentifier(accountIdentifier)
        .build();
  }

  /**
   * Rotates (updates) an existing RSA key pair for the account.
   * Throws exception if no existing key pair is found.
   *
   * @param accountIdentifier The account identifier
   * @return A PublicKeyDTO containing the new public key and account identifier
   * @throws InvalidRequestException if no existing key pair is found for the account
   */
  public PublicKeyDTO rotateKeyPair(String accountIdentifier) {
    // Check if key exists
    SMPAuthInfo existingAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    if (existingAuthInfo == null) {
      throw new InvalidRequestException(String.format(
          "No key pair found for account identifier %s. Use generate to create a new key pair.", accountIdentifier));
    }

    // Generate new key pair
    RSAKeyPairPEM rsaKeyPairPEM = rsaKeysUtils.generateKeyPairPEM();

    // Update the existing entry
    existingAuthInfo.setPublicKey(rsaKeyPairPEM.getPublicKeyPem());
    existingAuthInfo.setPrivateKey(rsaKeyPairPEM.getPrivateKeyPem());

    try {
      smpAuthInfoRepository.save(existingAuthInfo);
      log.info("Rotated key pair for account {}", accountIdentifier);

      return PublicKeyDTO.builder()
          .publicKey(rsaKeyPairPEM.getPublicKeyPem())
          .accountIdentifier(accountIdentifier)
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Failed to rotate key pair for account identifier %s", accountIdentifier), e);
    }
  }

  /**
   * Creates a public key for an account. Throws exception if key already exists.
   *
   * @param accountIdentifier The account identifier
   * @param publicKeyDTO      The public key data transfer object
   * @return The created PublicKeyDTO
   * @throws DuplicateFieldException if a key already exists for the account
   */
  public PublicKeyDTO createPublicKey(String accountIdentifier, PublicKeyDTO publicKeyDTO) {
    // Check if key already exists
    SMPAuthInfo existingAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    if (existingAuthInfo != null) {
      throw new DuplicateFieldException(
          String.format("Public key already exists for account identifier %s", accountIdentifier));
    }

    save(accountIdentifier, publicKeyDTO.getAccountIdentifier(), publicKeyDTO.getPublicKey(), "");

    // Return the created PublicKeyDTO
    return PublicKeyDTO.builder().accountIdentifier(accountIdentifier).publicKey(publicKeyDTO.getPublicKey()).build();
  }

  /**
   * Updates a public key for an account. Throws exception if key doesn't exist.
   *
   * @param accountIdentifier The account identifier
   * @param publicKeyDTO      The public key data transfer object
   * @return The updated PublicKeyDTO
   * @throws InvalidRequestException if no key exists for the account
   */
  public PublicKeyDTO updatePublicKey(String accountIdentifier, PublicKeyDTO publicKeyDTO) {
    // Check if key exists
    SMPAuthInfo existingAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    if (existingAuthInfo == null) {
      throw new InvalidRequestException(String.format(
          "No public key found for account identifier %s. Use POST to create a new key.", accountIdentifier));
    }

    // Update the existing entry
    existingAuthInfo.setPublicKey(publicKeyDTO.getPublicKey());
    existingAuthInfo.setSmpAccountIdentifier(publicKeyDTO.getAccountIdentifier());

    try {
      smpAuthInfoRepository.save(existingAuthInfo);
      log.info("Updated SMP auth info for account {} with new public key", accountIdentifier);

      // Return the updated PublicKeyDTO
      return PublicKeyDTO.builder().accountIdentifier(accountIdentifier).publicKey(publicKeyDTO.getPublicKey()).build();
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Failed to update public key for account identifier %s", accountIdentifier), e);
    }
  }

  /**
   * Generates an authentication token for the specified account.
   *
   * @param accountIdentifier The account identifier
   * @return A JWT token string
   * @throws InvalidRequestException if the private key is not found or token generation fails
   */
  public String generateAuthToken(String accountIdentifier) {
    SMPAuthInfo smpAuthInfo = smpAuthInfoRepository.findByAccountIdentifier(accountIdentifier);
    validatePrivateKey(smpAuthInfo, accountIdentifier);

    try {
      // Convert PEM string to RSA private key
      RSAPrivateKey privateKey = (RSAPrivateKey) rsaKeysUtils.readPemFile(smpAuthInfo.getPrivateKey());

      String userEmail = getUserEmailId();
      // Create JWT claims
      Map<String, String> claims = new HashMap<>();
      claims.put(ACCOUNT_ID_CLAIM, accountIdentifier);
      claims.put(ISSUER_CLAIM, TOKEN_ISSUER);
      claims.put(USER_EMAIL_CLAIM, userEmail);
      claims.put(USER_NAME_CLAIM, userEmail);

      // Create JWT header
      Map<String, Object> jwtHeader = createJwtHeader();

      // Token validity
      long validityDurationInMillis = TimeUnit.MINUTES.toMillis(TOKEN_VALIDITY_MINUTES);

      // Generate JWT token using private key
      return JWTTokenServiceUtils.generateJWTToken(claims, jwtHeader, validityDurationInMillis, privateKey);
    } catch (Exception e) {
      log.error("Failed to generate auth token for account {}", accountIdentifier, e);
      throw new InvalidRequestException("Failed to generate auth token: " + e.getMessage());
    }
  }

  /**
   * Creates standard JWT header.
   *
   * @return Map containing JWT header fields
   */
  private Map<String, Object> createJwtHeader() {
    Map<String, Object> jwtHeader = new HashMap<>();
    jwtHeader.put("typ", JWT_TYPE);
    jwtHeader.put("alg", JWT_ALG);
    return jwtHeader;
  }

  /**
   * Validates that the SMP auth info exists and contains a private key.
   *
   * @param smpAuthInfo      The SMP auth info object
   * @param accountIdentifier The account identifier for error reporting
   * @throws InvalidRequestException if validation fails
   */
  private void validatePrivateKey(SMPAuthInfo smpAuthInfo, String accountIdentifier) {
    if (smpAuthInfo == null || smpAuthInfo.getPrivateKey() == null || smpAuthInfo.getPrivateKey().isEmpty()) {
      throw new InvalidRequestException("Private key not found for account " + accountIdentifier);
    }
  }

  /**
   * Verifies a JWT token and extracts its claims.
   *
   * @param authToken The JWT token to verify
   * @param accountIdentifier The account identifier
   * @return The verified claims from the token
   * @throws InvalidRequestException if verification fails
   */
  private Map<String, Claim> verifyAndExtractClaims(String authToken, String accountIdentifier) {
    try {
      SMPAuthInfo smpAuthInfo = smpAuthInfoRepository.findBySmpAccountIdentifier(accountIdentifier);
      validatePublicKey(smpAuthInfo, accountIdentifier);

      String publicKey = smpAuthInfo.getPublicKey();
      RSAKey rsaKey = rsaKeysUtils.readPemFile(publicKey);

      Map<String, Claim> verifiedClaims = JWTTokenServiceUtils.verifyJWTToken(authToken, rsaKey, TOKEN_ISSUER);

      // Check if the required account ID claim is present and matches the expected value
      if (!verifiedClaims.containsKey(ACCOUNT_ID_CLAIM)
          || !verifiedClaims.get(ACCOUNT_ID_CLAIM).asString().equals(smpAuthInfo.getSmpAccountIdentifier())) {
        throw new InvalidRequestException("Invalid token: account ID claim missing or mismatched");
      }

      return verifiedClaims;
    } catch (Exception e) {
      log.error("Failed to verify JWT token for account {}", accountIdentifier, e);
      throw new InvalidRequestException("Failed to verify JWT token: " + e.getMessage());
    }
  }

  /**
   * Verifies an authentication token for the specified account.
   *
   * @param authToken        The JWT token to verify
   * @param accountIdentifier The account identifier
   * @return true if the token is valid, false otherwise
   * @throws InvalidRequestException if verification fails
   */
  public boolean verifyAuthToken(String authToken, String accountIdentifier) {
    try {
      verifyAndExtractClaims(authToken, accountIdentifier);
      return true;
    } catch (Exception e) {
      log.error("Failed to verify auth token for account {}", accountIdentifier, e);
      return false;
    }
  }

  /**
   * Validates that the SMP auth info exists and contains a public key.
   *
   * @param smpAuthInfo      The SMP auth info object
   * @param accountIdentifier The account identifier for error reporting
   * @throws InvalidRequestException if validation fails
   */
  private void validatePublicKey(SMPAuthInfo smpAuthInfo, String accountIdentifier) {
    if (smpAuthInfo == null || smpAuthInfo.getPublicKey() == null || smpAuthInfo.getPublicKey().isEmpty()) {
      throw new InvalidRequestException("Public key not found for SMP account " + accountIdentifier);
    }
  }

  /**
   * Generates a URL with SSO authentication for SaaS services.
   *
   * @param accountIdentifier The account identifier
   * @param baseUrl          The base URL of the SaaS service
   * @param returnTo          The return URL
   * @return The complete redirect URL with authentication token
   */
  private String generateSaasSSORedirectUrl(String accountIdentifier, String baseUrl, String returnTo) {
    String jwtToken = generateAuthToken(accountIdentifier);
    StringBuilder redirectUrl = new StringBuilder(baseUrl);

    // Ensure we have a proper URL path structure
    if (!baseUrl.endsWith("/")) {
      redirectUrl.append('/');
    }

    redirectUrl.append(ZENDESK_SSO_PATH)
        .append("?token=")
        .append(jwtToken)
        .append("&accountId=")
        .append(accountIdentifier)
        .append("&returnTo=")
        .append(returnTo);

    return redirectUrl.toString();
  }

  /**
   * Generates a URL for SMP Zendesk authentication.
   *
   * @param accountIdentifier The account identifier
   * @return The Zendesk redirect URL with authentication token
   */
  public String generateSMPZendeskRedirectUrl(String accountIdentifier, String returnTo) {
    return generateSaasSSORedirectUrl(accountIdentifier, smpConfig.getSaasBaseUrl(), returnTo);
  }

  public String generateSMPZendeskSsoUrl(String returnTo, String jwtToken, String accountIdentifier) {
    try {
      Map<String, Claim> claims = verifyAndExtractClaims(jwtToken, accountIdentifier);
      Call<RestResponse<ZendeskSsoLoginResponse>> request = zendeskManagerClient.getZendeskRedirect(
          returnTo, claims.get(USER_NAME_CLAIM).asString(), claims.get(USER_EMAIL_CLAIM).asString());
      ZendeskSsoLoginResponse response = CGRestUtils.getResponse(request);
      return response.getRedirectUrl();
    } catch (InvalidRequestException e) {
      log.error("Failed to generate zendesk sso url for smp account {}", accountIdentifier, e);
      throw new InvalidRequestException("Failed to generate zendesk sso url. Please try again.");
    }
  }

  private String getUserEmailId() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
      return userPrincipal.getEmail();
    }
    throw new InvalidRequestException("Only user accounts are allowed to create Zendesk tickets");
  }
}
