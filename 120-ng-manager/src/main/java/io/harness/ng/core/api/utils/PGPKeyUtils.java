/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.PGPKeyIdentity;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.dto.PGPPublicKeyDTOInternal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public final class PGPKeyUtils {
  private PGPKeyUtils() {}

  private static final List<String> ALLOWED_ALGORITHMS =
      Arrays.asList("RSA", "ElGamal", "DSA", "ECDH", "ECDSA", "EdDSA", "X25519", "X448", "Ed25519", "Ed448");

  public static PGPPublicKeyDTOInternal validateAndExtractKey(String keyContent) {
    return validateAndExtractKey(keyContent, null);
  }

  public static PGPPublicKeyDTOInternal validateAndExtractKey(String keyContent, String userEmail) {
    if (StringUtils.isEmpty(keyContent)) {
      throw new InvalidRequestException("PGP keyContent cannot be empty");
    }

    try {
      PGPPublicKeyRing keyRing = parseKeyRing(keyContent);
      org.bouncycastle.openpgp.PGPPublicKey primaryKey = extractAndValidatePrimaryKey(keyRing);
      IdentityProcessingResult identityResult = processIdentities(primaryKey, userEmail);
      PGPSignature primarySelfSignature = findPrimarySelfSignature(primaryKey);
      validateAlgorithm(primaryKey);

      String primaryFingerprint = Hex.toHexString(primaryKey.getFingerprint()).toUpperCase();
      String primaryKeyId = formatKeyId(primaryKey.getKeyID());
      List<PGPPublicKeyDTOInternal> subKeys = extractSubKeys(keyRing, primaryKeyId, keyContent);

      Long validFrom = primaryKey.getCreationTime() != null ? primaryKey.getCreationTime().getTime() : null;
      Long validTo = extractExpirationFromSignature(primarySelfSignature, validFrom);

      return PGPPublicKeyDTOInternal.builder()
          .content(keyContent)
          .fingerprint(primaryFingerprint)
          .keyId(primaryKeyId)
          .algorithm(getAlgorithmString(primaryKey.getAlgorithm()))
          .bitLength(calculateBitLength(primaryKey))
          .subKeys(subKeys)
          .usage(determineKeyUsage(primaryKey))
          .timestamp(primaryKey.getCreationTime().getTime())
          .validFrom(validFrom)
          .validTo(validTo)
          .comment(identityResult.comment)
          .identities(identityResult.identities)
          .primaryIdentity(identityResult.primaryIdentity)
          .parentKeyId(null)
          .isSubKey(false)
          .build();

    } catch (InvalidRequestException e) {
      throw e;
    } catch (IOException | PGPException e) {
      throw new InvalidRequestException("Failed to parse PGP key: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new InvalidRequestException("Invalid PGP key format: " + e.getMessage(), e);
    }
  }

  private static PGPPublicKeyRing parseKeyRing(String keyContent) throws IOException, PGPException {
    ByteArrayInputStream keyInputStream = new ByteArrayInputStream(keyContent.getBytes());
    InputStream decodedInputStream = PGPUtil.getDecoderStream(keyInputStream);

    PGPPublicKeyRingCollection pgpPublicKeyRings =
        new PGPPublicKeyRingCollection(decodedInputStream, new JcaKeyFingerprintCalculator());
    Iterator<PGPPublicKeyRing> keyRingIterator = pgpPublicKeyRings.getKeyRings();

    if (!keyRingIterator.hasNext()) {
      throw new InvalidRequestException("PGP key ring contains no keys");
    }

    PGPPublicKeyRing keyRing = keyRingIterator.next();

    if (keyRingIterator.hasNext()) {
      throw new InvalidRequestException("can't accept a PGP key ring with multiple primary keys");
    }

    return keyRing;
  }

  private static org.bouncycastle.openpgp.PGPPublicKey extractAndValidatePrimaryKey(PGPPublicKeyRing keyRing) {
    Iterator<org.bouncycastle.openpgp.PGPPublicKey> keyIterator = keyRing.getPublicKeys();
    if (!keyIterator.hasNext()) {
      throw new InvalidRequestException("PGP key ring entity is nil");
    }

    org.bouncycastle.openpgp.PGPPublicKey primaryKey = keyIterator.next();

    if (!primaryKey.isMasterKey()) {
      throw new InvalidRequestException("Invalid key structure: expected primary key first");
    }

    if (isKeyRevoked(primaryKey)) {
      throw new InvalidRequestException(
          "Cannot add a revoked PGP key. The primary key has been revoked and should not be used.");
    }

    return primaryKey;
  }

  private static class IdentityProcessingResult {
    String comment;
    List<PGPKeyIdentity> identities;
    PGPKeyIdentity primaryIdentity;
  }

  private static IdentityProcessingResult processIdentities(
      org.bouncycastle.openpgp.PGPPublicKey primaryKey, String userEmail) {
    IdentityProcessingResult result = new IdentityProcessingResult();
    result.identities = new ArrayList<>();
    boolean foundPrincipal = userEmail == null;
    boolean isFirst = true;

    Iterator<String> userIds = primaryKey.getUserIDs();
    while (userIds.hasNext()) {
      String userId = userIds.next();
      PGPKeyIdentity identity = parseUserIdToIdentity(userId);
      result.identities.add(identity);

      if (isFirst) {
        result.primaryIdentity = identity;
        result.comment = extractComment(userId);
        isFirst = false;
      }

      if (userEmail != null && !foundPrincipal) {
        foundPrincipal = checkEmailMatch(userId, userEmail);
      }
    }

    if (userEmail != null && !foundPrincipal) {
      throw new InvalidRequestException("PGP key identities don't contain the user's email address: " + userEmail);
    }

    return result;
  }

  private static String extractComment(String userId) {
    int start = userId.indexOf('(');
    int end = userId.indexOf(')', start);
    if (start >= 0 && end > start) {
      return userId.substring(start + 1, end).trim();
    }
    return null;
  }

  private static boolean checkEmailMatch(String userId, String userEmail) {
    String identityEmail = extractEmailFromIdentity(userId);
    return identityEmail != null && identityEmail.equalsIgnoreCase(userEmail);
  }

  private static PGPSignature findPrimarySelfSignature(org.bouncycastle.openpgp.PGPPublicKey primaryKey) {
    Iterator<PGPSignature> signatures = primaryKey.getSignatures();
    while (signatures.hasNext()) {
      PGPSignature sig = signatures.next();
      if (sig.getKeyID() == primaryKey.getKeyID()) {
        return sig;
      }
    }
    return null;
  }

  private static void validateAlgorithm(org.bouncycastle.openpgp.PGPPublicKey primaryKey) {
    String algorithm = getAlgorithmString(primaryKey.getAlgorithm());
    if (!ALLOWED_ALGORITHMS.contains(algorithm)) {
      throw new InvalidRequestException(String.format("Algorithm [%s] is not supported. Allowed algorithms are: %s",
          algorithm, String.join(", ", ALLOWED_ALGORITHMS)));
    }
  }

  private static List<PGPPublicKeyDTOInternal> extractSubKeys(
      PGPPublicKeyRing keyRing, String primaryKeyId, String keyContent) {
    List<PGPPublicKeyDTOInternal> subKeys = new ArrayList<>();
    Iterator<org.bouncycastle.openpgp.PGPPublicKey> keyIterator = keyRing.getPublicKeys();

    // Skip the primary key
    if (keyIterator.hasNext()) {
      keyIterator.next();
    }

    while (keyIterator.hasNext()) {
      org.bouncycastle.openpgp.PGPPublicKey subKey = keyIterator.next();
      if (isKeyRevoked(subKey)) {
        continue;
      }
      PGPPublicKeyDTOInternal subKeyDTO = extractSubKeyInfo(subKey, primaryKeyId, keyContent);
      if (subKeyDTO != null) {
        subKeys.add(subKeyDTO);
      }
    }

    return subKeys;
  }

  private static Long extractExpirationFromSignature(PGPSignature signature, Long validFrom) {
    if (signature == null) {
      return null;
    }
    try {
      if (signature.getHashedSubPackets() != null) {
        long validSeconds = signature.getHashedSubPackets().getKeyExpirationTime();
        if (validSeconds > 0 && validFrom != null) {
          return validFrom + (validSeconds * 1000);
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract key expiration from signature", e);
    }
    return null;
  }

  public static String convertToKeyContent(io.harness.ng.core.common.beans.PGPPublicKey pgpPublicKey) {
    return pgpPublicKey.getPgpKeyContent();
  }

  /**
   * Extracts full information for a subkey.
   */
  private static PGPPublicKeyDTOInternal extractSubKeyInfo(
      org.bouncycastle.openpgp.PGPPublicKey subKey, String parentKeyId, String keyContent) {
    try {
      String subKeyFingerprint = Hex.toHexString(subKey.getFingerprint()).toUpperCase();
      String subKeyId = formatKeyId(subKey.getKeyID());
      String algorithm = getAlgorithmString(subKey.getAlgorithm());
      int bitLength = calculateBitLength(subKey);
      List<PGPKeyUsage> usage = determineKeyUsage(subKey);

      // Extract validity period from subkey binding signature
      Long validFrom = subKey.getCreationTime() != null ? subKey.getCreationTime().getTime() : null;
      Long validTo = extractKeyExpiration(subKey, validFrom);

      return PGPPublicKeyDTOInternal.builder()
          .content(keyContent)
          .fingerprint(subKeyFingerprint)
          .keyId(subKeyId)
          .algorithm(algorithm)
          .bitLength(bitLength)
          .usage(usage)
          .timestamp(subKey.getCreationTime() != null ? subKey.getCreationTime().getTime() : null)
          .validFrom(validFrom)
          .validTo(validTo)
          .parentKeyId(parentKeyId)
          .isSubKey(true)
          .subKeys(null)
          .identities(null)
          .primaryIdentity(null)
          .build();
    } catch (Exception e) {
      log.debug("Could not extract subkey info, skipping subkey", e);
      return null;
    }
  }

  /**
   * Determines the usage capabilities of a PGP key based on key flags.
   */
  private static List<PGPKeyUsage> determineKeyUsage(org.bouncycastle.openpgp.PGPPublicKey publicKey) {
    List<PGPKeyUsage> usages = extractUsageFromSignatures(publicKey);
    if (!usages.isEmpty()) {
      return usages;
    }
    return determineUsageFromAlgorithm(publicKey.getAlgorithm());
  }

  private static List<PGPKeyUsage> extractUsageFromSignatures(org.bouncycastle.openpgp.PGPPublicKey publicKey) {
    try {
      Iterator<PGPSignature> signatures = publicKey.getSignatures();
      while (signatures.hasNext()) {
        PGPSignature sig = signatures.next();
        if (sig.getHashedSubPackets() != null) {
          List<PGPKeyUsage> usages = extractUsageFromKeyFlags(sig.getHashedSubPackets().getKeyFlags());
          if (!usages.isEmpty()) {
            return usages;
          }
        }
      }
    } catch (Exception e) {
      log.debug("Could not determine key usage from signatures, using algorithm fallback", e);
    }
    return new ArrayList<>();
  }

  private static List<PGPKeyUsage> extractUsageFromKeyFlags(int keyFlags) {
    List<PGPKeyUsage> usages = new ArrayList<>();
    // Key flags from RFC 4880:
    // 0x01 = certify, 0x02 = sign, 0x04 = encrypt comms, 0x08 = encrypt storage, 0x20 = auth
    if ((keyFlags & 0x01) != 0) {
      usages.add(PGPKeyUsage.CERTIFY);
    }
    if ((keyFlags & 0x02) != 0) {
      usages.add(PGPKeyUsage.SIGN);
    }
    if ((keyFlags & 0x04) != 0 || (keyFlags & 0x08) != 0) {
      usages.add(PGPKeyUsage.ENCRYPT);
    }
    if ((keyFlags & 0x20) != 0) {
      usages.add(PGPKeyUsage.AUTH);
    }
    return usages;
  }

  private static List<PGPKeyUsage> determineUsageFromAlgorithm(int algorithm) {
    List<PGPKeyUsage> usages = new ArrayList<>();
    switch (algorithm) {
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_GENERAL:
        usages.add(PGPKeyUsage.SIGN);
        usages.add(PGPKeyUsage.ENCRYPT);
        break;
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_SIGN:
      case org.bouncycastle.openpgp.PGPPublicKey.DSA:
      case org.bouncycastle.openpgp.PGPPublicKey.EDDSA:
        usages.add(PGPKeyUsage.SIGN);
        break;
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.ELGAMAL_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.ECDH:
        usages.add(PGPKeyUsage.ENCRYPT);
        break;
      default:
        usages.add(PGPKeyUsage.SIGN);
        break;
    }
    return usages;
  }

  /**
   * Extracts key expiration time from signatures.
   */
  private static Long extractKeyExpiration(org.bouncycastle.openpgp.PGPPublicKey publicKey, Long validFrom) {
    try {
      Iterator<PGPSignature> signatures = publicKey.getSignatures();
      while (signatures.hasNext()) {
        PGPSignature sig = signatures.next();
        if (sig.getHashedSubPackets() != null) {
          long validSeconds = sig.getHashedSubPackets().getKeyExpirationTime();
          if (validSeconds > 0 && validFrom != null) {
            return validFrom + (validSeconds * 1000);
          }
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract key expiration from signatures", e);
    }
    return null;
  }

  /**
   * Checks if a PGP key has been revoked.
   * Examines signatures for key revocation (0x20) or subkey revocation (0x28) signatures.
   *
   * @param publicKey the PGP public key to check
   * @return true if the key is revoked, false otherwise
   */
  private static boolean isKeyRevoked(org.bouncycastle.openpgp.PGPPublicKey publicKey) {
    if (publicKey == null) {
      return false;
    }

    try {
      Iterator<PGPSignature> signatures = publicKey.getSignatures();
      while (signatures.hasNext()) {
        PGPSignature sig = signatures.next();
        int signatureType = sig.getSignatureType();

        if (signatureType == PGPSignature.KEY_REVOCATION) {
          return true;
        }

        if (signatureType == PGPSignature.SUBKEY_REVOCATION) {
          return true;
        }
      }

      if (publicKey.hasRevocation()) {
        return true;
      }
    } catch (Exception e) {
      log.debug("Could not determine revocation status, treating as not revoked", e);
    }

    return false;
  }

  private static int calculateBitLength(org.bouncycastle.openpgp.PGPPublicKey publicKey) {
    try {
      PublicKey javaPublicKey = new JcaPGPKeyConverter().getPublicKey(publicKey);
      int bitLength = calculateBitLengthFromJavaKey(publicKey.getAlgorithm(), javaPublicKey);
      if (bitLength > 0) {
        return bitLength;
      }
    } catch (Exception e) {
      log.debug("Could not calculate bit length from key, using default", e);
    }
    return getDefaultBitLengthForAlgorithm(publicKey.getAlgorithm());
  }

  private static int calculateBitLengthFromJavaKey(int algorithm, PublicKey javaPublicKey) {
    switch (algorithm) {
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_GENERAL:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_SIGN:
        return calculateRsaBitLength(javaPublicKey);
      case org.bouncycastle.openpgp.PGPPublicKey.DSA:
        return calculateDsaBitLength(javaPublicKey);
      case org.bouncycastle.openpgp.PGPPublicKey.ELGAMAL_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.ELGAMAL_GENERAL:
        return 2048;
      case org.bouncycastle.openpgp.PGPPublicKey.ECDSA:
      case org.bouncycastle.openpgp.PGPPublicKey.ECDH:
        return calculateEcBitLength(javaPublicKey);
      case org.bouncycastle.openpgp.PGPPublicKey.EDDSA:
        return calculateEdDsaBitLength(javaPublicKey);
      default:
        return 0;
    }
  }

  private static int calculateRsaBitLength(PublicKey javaPublicKey) {
    if (javaPublicKey instanceof RSAPublicKey) {
      return ((RSAPublicKey) javaPublicKey).getModulus().bitLength();
    }
    return 0;
  }

  private static int calculateDsaBitLength(PublicKey javaPublicKey) {
    if (javaPublicKey instanceof java.security.interfaces.DSAPublicKey) {
      return ((java.security.interfaces.DSAPublicKey) javaPublicKey).getParams().getP().bitLength();
    }
    return 0;
  }

  private static int calculateEcBitLength(PublicKey javaPublicKey) {
    if (javaPublicKey instanceof java.security.interfaces.ECPublicKey) {
      return ((java.security.interfaces.ECPublicKey) javaPublicKey).getParams().getOrder().bitLength();
    }
    return 256;
  }

  private static int calculateEdDsaBitLength(PublicKey javaPublicKey) {
    if (javaPublicKey != null) {
      byte[] encoded = javaPublicKey.getEncoded();
      if (encoded != null && encoded.length > 50) {
        return 456; // Ed448
      }
    }
    return 256; // Ed25519 default
  }

  private static int getDefaultBitLengthForAlgorithm(int algorithm) {
    switch (algorithm) {
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_GENERAL:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_SIGN:
        return 2048;
      case org.bouncycastle.openpgp.PGPPublicKey.DSA:
        return 1024;
      case org.bouncycastle.openpgp.PGPPublicKey.ECDSA:
      case org.bouncycastle.openpgp.PGPPublicKey.ECDH:
      case org.bouncycastle.openpgp.PGPPublicKey.EDDSA:
        return 256;
      default:
        return 0;
    }
  }

  private static String formatKeyId(long keyId) {
    // Format key ID as 16-character uppercase hex string
    return String.format("%016X", keyId);
  }

  public static List<String> extractIdentities(String keyContent) {
    List<String> identities = new ArrayList<>();
    try {
      ByteArrayInputStream keyInputStream = new ByteArrayInputStream(keyContent.getBytes());
      InputStream decodedInputStream = PGPUtil.getDecoderStream(keyInputStream);

      PGPPublicKeyRingCollection pgpPublicKeyRings =
          new PGPPublicKeyRingCollection(decodedInputStream, new JcaKeyFingerprintCalculator());
      Iterator<PGPPublicKeyRing> keyRingIterator = pgpPublicKeyRings.getKeyRings();

      if (keyRingIterator.hasNext()) {
        PGPPublicKeyRing keyRing = keyRingIterator.next();
        Iterator<org.bouncycastle.openpgp.PGPPublicKey> keyIterator = keyRing.getPublicKeys();
        if (keyIterator.hasNext()) {
          org.bouncycastle.openpgp.PGPPublicKey primaryKey = keyIterator.next();
          Iterator<String> userIds = primaryKey.getUserIDs();
          while (userIds.hasNext()) {
            identities.add(userIds.next());
          }
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract identities from PGP key", e);
    }
    return identities;
  }

  private static String extractEmailFromIdentity(String identity) {
    if (StringUtils.isEmpty(identity)) {
      return null;
    }

    int start = identity.indexOf('<');
    int end = identity.indexOf('>', start);
    if (start != -1 && end != -1 && end > start) {
      return identity.substring(start + 1, end).trim();
    }

    String trimmed = identity.trim();
    if (trimmed.contains("@") && trimmed.contains(".")
        && trimmed.matches("^[^\\s<>()]++@[^\\s<>()]++\\.[^\\s<>()]++$")) {
      return trimmed;
    }

    return null;
  }

  private static String extractNameFromIdentity(String identity) {
    if (StringUtils.isEmpty(identity)) {
      return null;
    }

    String trimmed = identity.trim();

    int emailStart = trimmed.indexOf('<');
    if (emailStart > 0) {
      String namePart = trimmed.substring(0, emailStart).trim();

      int commentStart = namePart.indexOf('(');
      if (commentStart > 0) {
        namePart = namePart.substring(0, commentStart).trim();
      }
      return namePart.isEmpty() ? null : namePart;
    }

    if (trimmed.contains("@") && trimmed.contains(".")) {
      if (trimmed.matches("^[^\\s<>()]++@[^\\s<>()]++\\.[^\\s<>()]++$")) {
        return null;
      }
    }

    return trimmed;
  }

  private static PGPKeyIdentity parseUserIdToIdentity(String userId) {
    String name = extractNameFromIdentity(userId);
    String email = extractEmailFromIdentity(userId);
    return PGPKeyIdentity.builder().name(name).email(email).build();
  }

  private static String getAlgorithmString(int algorithm) {
    switch (algorithm) {
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_GENERAL:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.RSA_SIGN:
        return "RSA";
      case org.bouncycastle.openpgp.PGPPublicKey.ELGAMAL_ENCRYPT:
      case org.bouncycastle.openpgp.PGPPublicKey.ELGAMAL_GENERAL:
        return "ElGamal";
      case org.bouncycastle.openpgp.PGPPublicKey.DSA:
        return "DSA";
      case org.bouncycastle.openpgp.PGPPublicKey.ECDH:
        return "ECDH";
      case org.bouncycastle.openpgp.PGPPublicKey.ECDSA:
        return "ECDSA";
      case org.bouncycastle.openpgp.PGPPublicKey.EDDSA:
        return "EdDSA";
      default:
        return "Unknown(" + algorithm + ")";
    }
  }
}