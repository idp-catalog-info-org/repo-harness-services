/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static junit.framework.TestCase.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.InternalServerErrorException;
import io.harness.provider.ProviderTaskResponse;
import io.harness.provider.entity.ProviderType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedRecord;

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OauthSecretServiceTest extends NgManagerTestBase {
  @InjectMocks private OauthSecretService oauthSecretService;

  public static class TestCase {
    private String name;
    private ProviderType providerType;
    private boolean hasRefreshToken;
    private boolean shouldPass;

    public TestCase(String name, ProviderType providerType, boolean hasRefreshToken, boolean shouldPass) {
      this.name = name;
      this.providerType = providerType;
      this.hasRefreshToken = hasRefreshToken;
      this.shouldPass = shouldPass;
    }

    public String getName() {
      return name;
    }

    public ProviderType getProviderType() {
      return providerType;
    }

    public boolean isHasRefreshToken() {
      return hasRefreshToken;
    }

    public boolean isShouldPass() {
      return shouldPass;
    }
  }

  private List<TestCase> getTestCases() {
    return Arrays.asList(new TestCase("Expected and received refresh token",
                             ProviderType.BITBUCKET_SERVER, // Supports refresh token
                             true, // Has refresh token
                             true // Should pass
                             ),
        new TestCase("Not expected and not received refresh token",
            ProviderType.GITHUB_ENTERPRISE, // Does not support refresh token
            false, // No refresh token
            true // Should pass
            ),
        new TestCase("Expected but not received refresh token",
            ProviderType.BITBUCKET_SERVER, // Supports refresh token
            false, // No refresh token
            false // Should fail
            ),
        new TestCase("Not expected but received refresh token",
            ProviderType.GITHUB_ENTERPRISE, // Does not support refresh token
            true, // Has refresh token
            true // Should pass - only logs error but doesn't throw exception
            ));
  }

  @Test
  @Owner(developers = OwnerRule.BHUMIJ)
  @Category(UnitTests.class)
  public void testVerifyRefreshTokenPresence() {
    for (TestCase testCase : getTestCases()) {
      log.info("Running test case: {}", testCase.getName());

      // Setup
      ProviderTaskResponse providerTaskResponse = new ProviderTaskResponse();
      providerTaskResponse.setAccessToken(mock(EncryptedRecord.class));

      // Explicitly set refresh token to avoid ambiguity
      if (testCase.isHasRefreshToken()) {
        // Create a specific mock instance for EncryptedRecord to avoid ambiguity
        EncryptedRecord mockRefreshToken = mock(EncryptedRecord.class);
        providerTaskResponse.setRefreshToken(mockRefreshToken);
      } else {
        // Explicitly set to null to make it clear
        providerTaskResponse.setRefreshToken(null);
      }

      // Execute & Verify
      if (testCase.isShouldPass()) {
        try {
          oauthSecretService.verifyRefreshTokenPresence(providerTaskResponse, testCase.getProviderType());
        } catch (Exception e) {
          log.error("Test case '{}' failed unexpectedly: {}", testCase.getName(), e.getMessage());
          fail("Test case '" + testCase.getName() + "' should have passed but failed with: " + e.getMessage());
        }
      } else {
        try {
          oauthSecretService.verifyRefreshTokenPresence(providerTaskResponse, testCase.getProviderType());
          log.error("Test case '{}' passed unexpectedly when it should have failed", testCase.getName());
          fail("Test case '" + testCase.getName() + "' should have failed but passed");
        } catch (InternalServerErrorException e) {
          // Verify exception message
          String expectedMessage =
              String.format("Error in getting refresh token for provider type %s", testCase.getProviderType());
          assertEquals("Test case '" + testCase.getName() + "' failed with incorrect exception message",
              expectedMessage, e.getMessage());
          log.info("Test case '{}' failed with expected exception message: {}", testCase.getName(), e.getMessage());
        } catch (Exception e) {
          log.error(
              "Test case '{}' failed with unexpected exception type: {}", testCase.getName(), e.getClass().getName());
          fail("Test case '" + testCase.getName()
              + "' failed with unexpected exception type: " + e.getClass().getName());
        }
      }
    }
  }
}
