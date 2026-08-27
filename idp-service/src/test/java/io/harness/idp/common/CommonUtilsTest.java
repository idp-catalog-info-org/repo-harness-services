/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.User;

import com.mongodb.MongoException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.UncategorizedMongoDbException;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CommonUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRemoveAccountFromIdentifier() {
    assertThat(CommonUtils.removeAccountFromIdentifier("account.test")).isEqualTo("test");
    assertThat(CommonUtils.removeAccountFromIdentifier("test")).isEqualTo("test");
    assertThat(CommonUtils.removeAccountFromIdentifier("org.test")).isEqualTo("org");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRemoveScopeFromIdentifier() {
    assertThat(CommonUtils.removeScopeFromIdentifier("scope.identifier")).isEqualTo("identifier");
    assertThat(CommonUtils.removeScopeFromIdentifier("identifier")).isEqualTo("identifier");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testTruncateEntityName() {
    String shortName = "short";
    assertThat(CommonUtils.truncateEntityName(shortName)).isEqualTo(shortName);

    String longName = "a".repeat(70);
    String truncated = CommonUtils.truncateEntityName(longName);
    assertThat(truncated).hasSize(63);
    assertThat(truncated).endsWith("---");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadFileFromClassPath() {
    assertThatThrownBy(() -> CommonUtils.readFileFromClassPath("nonexistent-file.txt"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testAddGlobalAccountIdentifierAlong() {
    Set<String> result = CommonUtils.addGlobalAccountIdentifierAlong("test-account");
    assertThat(result).hasSize(2);
    assertThat(result).contains("test-account", Constants.GLOBAL_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRemoveTrailingAndLeadingSlash() {
    assertThat(CommonUtils.removeTrailingAndLeadingSlash("/path/to/resource/")).isEqualTo("path/to/resource");
    assertThat(CommonUtils.removeTrailingAndLeadingSlash("path/to/resource")).isEqualTo("path/to/resource");
    assertThat(CommonUtils.removeTrailingAndLeadingSlash("/path/")).isEqualTo("path");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRemoveTrailingSlash() {
    assertThat(CommonUtils.removeTrailingSlash("path/")).isEqualTo("path");
    assertThat(CommonUtils.removeTrailingSlash("path")).isEqualTo("path");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRemoveLeadingSlash() {
    assertThat(CommonUtils.removeLeadingSlash("/path")).isEqualTo("path");
    assertThat(CommonUtils.removeLeadingSlash("path")).isEqualTo("path");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testParseObjectToString() {
    assertThat(CommonUtils.parseObjectToString("test")).isEqualTo("test");
    assertThat(CommonUtils.parseObjectToString(null)).isEmpty();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testAddAccountScopeInIdentifier() {
    assertThat(CommonUtils.addAccountScopeInIdentifier("test")).isEqualTo("account.test");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReplaceAccountScopeFromIdentifier() {
    assertThat(CommonUtils.replaceAccountScopeFromIdentifier("account.test")).isEqualTo("test");
    assertThat(CommonUtils.replaceAccountScopeFromIdentifier("test")).isEqualTo("test");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReplaceOrgScopeFromIdentifier() {
    assertThat(CommonUtils.replaceOrgScopeFromIdentifier("org.test")).isEqualTo("test");
    assertThat(CommonUtils.replaceOrgScopeFromIdentifier("test")).isEqualTo("test");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetUserFromEmbeddedUser() {
    assertThat(CommonUtils.getUserFromEmbeddedUser(null)).isNull();

    EmbeddedUser embeddedUser =
        EmbeddedUser.builder().uuid("uuid123").name("Test User").email("test@example.com").build();
    User user = CommonUtils.getUserFromEmbeddedUser(embeddedUser);

    assertThat(user).isNotNull();
    assertThat(user.getUuid()).isEqualTo("uuid123");
    assertThat(user.getName()).isEqualTo("Test User");
    assertThat(user.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetParsedMessageFromSetOfStrings() {
    Set<String> strings = new HashSet<>();
    strings.add("$.field1");
    strings.add("$.field2");

    String result = CommonUtils.getParsedMessageFromSetOfStrings(strings);
    assertThat(result).isNotEmpty();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUrlObject() {
    URL url = CommonUtils.urlObject("https://example.com");
    assertThat(url).isNotNull();
    assertThat(url.getHost()).isEqualTo("example.com");

    assertThatThrownBy(() -> CommonUtils.urlObject("invalid-url"))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Invalid URL");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testExtractDuplicateValueFromDuplicateKeyException() {
    String errorMessage = "dup key: { : \"field1\", : \"duplicateValue\" }";
    String result = CommonUtils.extractDuplicateValueFromDuplicateKeyException(errorMessage);
    assertThat(result).isEqualTo("duplicateValue");

    assertThat(CommonUtils.extractDuplicateValueFromDuplicateKeyException("invalid message")).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromMethod() {
    Map<String, Object> object = new HashMap<>();
    object.put("field1", "value1");
    object.put("field2", 123);

    Map<String, Object> nested = new HashMap<>();
    nested.put("nestedField", "nestedValue");
    object.put("nested", nested);

    assertThat(CommonUtils.from(object, "field1", String.class)).isEqualTo("value1");
    assertThat(CommonUtils.from(object, "field2", Integer.class)).isEqualTo(123);
    assertThat(CommonUtils.from(object, "nested.nestedField", String.class)).isEqualTo("nestedValue");
    assertThat(CommonUtils.from(object, "nonexistent", String.class)).isNull();
    assertThat(CommonUtils.from(null, "field1", String.class)).isNull();
    assertThat(CommonUtils.from(object, null, String.class)).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCheckIfAccountLevelEvent() {
    assertThat(CommonUtils.checkIfAccountLevelEvent("account1", null, null)).isTrue();
    assertThat(CommonUtils.checkIfAccountLevelEvent("account1", "", "")).isTrue();
    assertThat(CommonUtils.checkIfAccountLevelEvent("account1", "org1", null)).isFalse();
    assertThat(CommonUtils.checkIfAccountLevelEvent("account1", "org1", "proj1")).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetDomainFromUrl() {
    assertThat(CommonUtils.getDomainFromUrl("https://www.example.com")).isEqualTo("example.com");
    assertThat(CommonUtils.getDomainFromUrl("https://example.com:8080")).isEqualTo("example.com:8080");
    assertThat(CommonUtils.getDomainFromUrl("https://subdomain.example.com")).isEqualTo("subdomain.example.com");

    assertThatThrownBy(() -> CommonUtils.getDomainFromUrl("invalid url"))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Error in extracting domain from url");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetScopedIdentifier() {
    assertThat(CommonUtils.getScopedIdentifier("acc1", null, null, "id1")).isEqualTo("account.id1");
    assertThat(CommonUtils.getScopedIdentifier("acc1", "", "", "id1")).isEqualTo("account.id1");
    assertThat(CommonUtils.getScopedIdentifier("acc1", "org1", null, "id1")).isEqualTo("org.id1");
    assertThat(CommonUtils.getScopedIdentifier("acc1", "org1", "", "id1")).isEqualTo("org.id1");
    assertThat(CommonUtils.getScopedIdentifier("acc1", "org1", "proj1", "id1")).isEqualTo("id1");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testEscapeRegexMetacharacters() {
    assertThat(CommonUtils.escapeRegexMetacharacters((String) null)).isNull();
    assertThat(CommonUtils.escapeRegexMetacharacters("simple")).isEqualTo("simple");
    assertThat(CommonUtils.escapeRegexMetacharacters("test.name")).contains("\\.");
    assertThat(CommonUtils.escapeRegexMetacharacters("test*name")).contains("\\*");
    assertThat(CommonUtils.escapeRegexMetacharacters("test[name]")).contains("\\[").contains("\\]");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testEscapeRegexMetacharactersTriple() {
    assertThat(CommonUtils.escapeRegexMetacharacters((Triple<String, String, String>) null)).isNull();

    Triple<String, String, String> input = Triple.of("test.left", "test*middle", "test[right]");
    Triple<String, String, String> result = CommonUtils.escapeRegexMetacharacters(input);

    assertThat(result).isNotNull();
    assertThat(result.getLeft()).contains("\\.");
    assertThat(result.getMiddle()).contains("\\*");
    assertThat(result.getRight()).contains("\\[").contains("\\]");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetFilePathForGitIntegrationsGitHub() {
    String result = CommonUtils.getFilePathForGitIntegrations(
        "Github", "https://github.com/user/repo", "main", "src/main", "file.txt");
    assertThat(result).contains("blob").contains("main").contains("src/main").contains("file.txt");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetFilePathForGitIntegrationsGitLab() {
    String result = CommonUtils.getFilePathForGitIntegrations(
        "Gitlab", "https://gitlab.com/user/repo", "main", "src/main", "file.txt");
    assertThat(result).contains("blob").contains("main").contains("src/main").contains("file.txt");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetDirectoryPathForSourceCodeGitHub() {
    String result =
        CommonUtils.getDirectoryPathForSourceCode("Github", "https://github.com/user/repo", "main", "src/main");
    assertThat(result).contains("tree").contains("main").contains("src/main");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetBranchOnlyUrlForSourceCodeGitHub() {
    String result = CommonUtils.getBranchOnlyUrlForSourceCode("Github", "https://github.com/user/repo", "main");
    assertThat(result).contains("tree").contains("main");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetBranchOnlyUrlForSourceCodeGitLab() {
    String result = CommonUtils.getBranchOnlyUrlForSourceCode("Gitlab", "https://gitlab.com/user/repo", "main");
    assertThat(result).contains("-/tree").contains("main");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testThrowIfMongoWriteConflictErrorWithWriteConflict() {
    MongoException writeConflictException = new MongoException(112, "WriteConflict error");

    assertThatThrownBy(() -> CommonUtils.throwIfMongoWriteConflictError(writeConflictException))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Your requested operation could not be completed as there is another request performing the same "
            + "operation. Please retry your request.");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testThrowIfMongoWriteConflictErrorWithWrappedException() {
    MongoException writeConflictException = new MongoException(112, "WriteConflict error");
    UncategorizedMongoDbException wrappedException =
        new UncategorizedMongoDbException("Wrapped WriteConflict", writeConflictException);

    assertThatThrownBy(() -> CommonUtils.throwIfMongoWriteConflictError(wrappedException))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Your requested operation could not be completed as there is another request performing the same "
            + "operation. Please retry your request.");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testThrowIfMongoWriteConflictErrorWithNonWriteConflictException() {
    MongoException nonWriteConflictException = new MongoException(11000, "Duplicate key error");

    assertThatCode(() -> CommonUtils.throwIfMongoWriteConflictError(nonWriteConflictException))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testThrowIfMongoWriteConflictErrorWithGenericException() {
    Exception genericException = new RuntimeException("Some other error");

    assertThatCode(() -> CommonUtils.throwIfMongoWriteConflictError(genericException)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEVESH)

  @Category(UnitTests.class)
  public void testThrowIfMongoWriteConflictErrorWithNullException() {
    assertThatCode(() -> CommonUtils.throwIfMongoWriteConflictError(null)).doesNotThrowAnyException();
  }
}
