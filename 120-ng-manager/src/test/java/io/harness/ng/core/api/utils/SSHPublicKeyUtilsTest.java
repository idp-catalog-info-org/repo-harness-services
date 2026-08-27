/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import static io.harness.rule.OwnerRule.AKHIL_PANDEY;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.SSHPublicKeyDTOInternal;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CODE)
public class SSHPublicKeyUtilsTest {
  private final String rsaKeyContent =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDOi884RoTpTtFeYNWIJYOIZVHus8VJyL6S2RZxPCmZoMbDZpJGq3em9bWrjcuNij6mE5/8z239dSA6Rl+fVpCKbqV2bNZ96xJTYgzNjtdaO2mkQxCKr1RoIF/clggds9DuIU7EXTYuq8B6cil9CgHfD43zt96O3t+Ytd8W3bfRLl4h/etw1QCqtBJ/25JOyqkCi4rdLen27Qu19fYiZ0N/XxBDf0ZpBAmO/1fn3kkx/3t2JrFwYFQ03wBPjuhSY0PewFIJVT/H5f9y6jx9exE7/0hb0LVw+SQbpeo0XTPUNW8qFeprPk2hab9N4Qk7ZGKmJo8gh0+6vUVwfbc+ky5z9WKsej/75jndJHs9JwkXqA8LaiRobGVfpiD0Wmkof7/EFYFscV2yYVSP8cyHTUxNdntzbUJq9TMbeq9988jTpNRGRiKBdNC/mVWyQDTkhQdJrA2dOTdOj7Y2pxrlzzSV0OirbUPQGEhmxnpI2pJJPkDKvbjgSYuZpsn/YRT+VxMF8p8OJSmHisJXFVobJIl3ONuuCI/QrtVgInwVOQtqjcN47687SIPSfa0eU1blHGAuJaVQdrVTu5YkNyGQWYlZTSRdYf3b4UjgXp3wYfEps551z+BpHaOmkx+nMsHa5dl4MDP0nqjxSVaYI8XJpjBngkJBxr3wkU2cjzsgsAmYAw== ak_gcp";
  private final String rsaExpectedAlgorithm = "ssh-rsa";
  private final String rsaExpectedKey =
      "AAAAB3NzaC1yc2EAAAADAQABAAACAQDOi884RoTpTtFeYNWIJYOIZVHus8VJyL6S2RZxPCmZoMbDZpJGq3em9bWrjcuNij6mE5/8z239dSA6Rl+fVpCKbqV2bNZ96xJTYgzNjtdaO2mkQxCKr1RoIF/clggds9DuIU7EXTYuq8B6cil9CgHfD43zt96O3t+Ytd8W3bfRLl4h/etw1QCqtBJ/25JOyqkCi4rdLen27Qu19fYiZ0N/XxBDf0ZpBAmO/1fn3kkx/3t2JrFwYFQ03wBPjuhSY0PewFIJVT/H5f9y6jx9exE7/0hb0LVw+SQbpeo0XTPUNW8qFeprPk2hab9N4Qk7ZGKmJo8gh0+6vUVwfbc+ky5z9WKsej/75jndJHs9JwkXqA8LaiRobGVfpiD0Wmkof7/EFYFscV2yYVSP8cyHTUxNdntzbUJq9TMbeq9988jTpNRGRiKBdNC/mVWyQDTkhQdJrA2dOTdOj7Y2pxrlzzSV0OirbUPQGEhmxnpI2pJJPkDKvbjgSYuZpsn/YRT+VxMF8p8OJSmHisJXFVobJIl3ONuuCI/QrtVgInwVOQtqjcN47687SIPSfa0eU1blHGAuJaVQdrVTu5YkNyGQWYlZTSRdYf3b4UjgXp3wYfEps551z+BpHaOmkx+nMsHa5dl4MDP0nqjxSVaYI8XJpjBngkJBxr3wkU2cjzsgsAmYAw==";
  private final String rsaExpectedComment = "ak_gcp";
  private final String rsaExpectedFingerprint = "SHA256:s50ylXvH5WTcASOK3hTsRevl5qC4/cpNdPhujfQpyao";

  private final String ecKeyContent =
      "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGp366DS3VQHtq4BSY4bFCyRG4HRrO1EPnWOkzG9PuVz akhileshpandey@Akhilesh Pandey";
  private final String ed25519xpectedAlgorithm = "ssh-ed25519";
  private final String ed25519xpectedKey = "AAAAC3NzaC1lZDI1NTE5AAAAIGp366DS3VQHtq4BSY4bFCyRG4HRrO1EPnWOkzG9PuVz";

  private final String ed25519xpectedComment = "akhileshpandey@Akhilesh Pandey";
  private final String ed25519xpectedFingerprint = "SHA256:7WRKzKK7byppoVBl2j8FvZndL3j9rdrEBoOE7S8PzwQ";

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void TestRSA_SSHKeyParse() {
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(rsaKeyContent);
    assertEquals(rsaExpectedKey, sshPublicKeyDTOInternal.getSshKey());
    assertEquals(rsaExpectedAlgorithm, sshPublicKeyDTOInternal.getAlgorithm());
    assertEquals(rsaExpectedComment, sshPublicKeyDTOInternal.getComment());
    assertEquals(rsaExpectedFingerprint, sshPublicKeyDTOInternal.getFingerPrint());
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testEC_SSHKeyParse() {
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(ecKeyContent);
    assertEquals(ed25519xpectedKey, sshPublicKeyDTOInternal.getSshKey());
    assertEquals(ed25519xpectedAlgorithm, sshPublicKeyDTOInternal.getAlgorithm());
    assertEquals(ed25519xpectedComment, sshPublicKeyDTOInternal.getComment());
    assertEquals(ed25519xpectedFingerprint, sshPublicKeyDTOInternal.getFingerPrint());
  }
}