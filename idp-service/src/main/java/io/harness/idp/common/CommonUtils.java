/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.AZURE_REPO;
import static io.harness.idp.common.Constants.BITBUCKET_CLOUD;
import static io.harness.idp.common.Constants.BITBUCKET_SERVER;
import static io.harness.idp.common.Constants.ENTITIES_SUPPORTING_SYSTEM;
import static io.harness.idp.common.Constants.GITHUB;
import static io.harness.idp.common.Constants.GITLAB;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.Constants.HARNESS;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;
import static io.harness.idp.common.Constants.SOURCE_FORMAT_BLOB;
import static io.harness.idp.common.Constants.SOURCE_FORMAT_SRC;
import static io.harness.idp.common.Constants.SOURCE_FORMAT_TREE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.git.GitClientHelper;
import io.harness.retry.RetryHelper;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.User;
import io.harness.springdata.PersistenceUtils;

import com.google.common.io.Resources;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CommonUtils {
  private static final Pattern pattern = Pattern.compile("\\$\\.(\\w+)");
  private static final Pattern duplicateKeyExceptionPattern =
      Pattern.compile("dup key: \\{ : \"[^\"]*\", : \"([^\"]*)\" \\}");
  public static String removeAccountFromIdentifier(String identifier) {
    String[] arrOfStr = identifier.split("[.]");
    if (arrOfStr.length == 2 && arrOfStr[0].equals("account")) {
      return arrOfStr[1];
    }
    return arrOfStr[0];
  }

  public static String removeScopeFromIdentifier(String identifier) {
    String[] arrOfStr = identifier.split("[.]");
    if (arrOfStr.length == 2) {
      return arrOfStr[1];
    }
    return arrOfStr[0];
  }

  public static String truncateEntityName(String harnessEntityName) {
    if (harnessEntityName.length() > 63) {
      return StringUtils.truncate(harnessEntityName, 60) + "---";
    }
    return harnessEntityName;
  }

  public static String readFileFromClassPath(String filename) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename, e);
    }
  }

  public static Set<String> addGlobalAccountIdentifierAlong(String accountIdentifier) {
    return new HashSet<>(Arrays.asList(accountIdentifier, GLOBAL_ACCOUNT_ID));
  }

  public Object findObjectByName(Map<String, Object> map, String targetName) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (entry.getKey().equals(targetName)) {
        return entry.getValue();
      }

      if (entry.getValue() instanceof Map) {
        Object nestedResult = findObjectByName((Map<String, Object>) entry.getValue(), targetName);
        if (nestedResult != null) {
          return nestedResult;
        }
      }
    }
    return null;
  }

  public static String removeTrailingAndLeadingSlash(String str) {
    str = removeTrailingSlash(str);
    str = removeLeadingSlash(str);
    return str;
  }

  public static String removeTrailingSlash(String str) {
    if (str.endsWith("/")) {
      str = str.substring(0, str.length() - 1);
    }
    return str;
  }

  public static String removeLeadingSlash(String str) {
    if (str.startsWith("/")) {
      str = str.substring(1);
    }
    return str;
  }

  public static String parseObjectToString(Object value) {
    return value != null ? (String) value : StringUtils.EMPTY;
  }

  public static String addAccountScopeInIdentifier(String identifier) {
    return Constants.ACCOUNT_SCOPED + identifier;
  }

  public static String replaceAccountScopeFromIdentifier(String identifier) {
    return identifier.replace(Constants.ACCOUNT_SCOPED, "");
  }

  public static String replaceOrgScopeFromIdentifier(String identifier) {
    return identifier.replace(Constants.ORG_SCOPED, "");
  }

  public static UserPrincipal getUserPrincipalFromPrincipal() {
    UserPrincipal userPrincipal = null;
    if (SourcePrincipalContextBuilder.getSourcePrincipal() instanceof UserPrincipal) {
      userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
    }
    return userPrincipal;
  }

  public static String getUserIdentifierFromUserPrincipal(UserPrincipal userPrincipal) {
    String userIdentifier = null;
    if (userPrincipal != null) {
      userIdentifier = userPrincipal.getName();
    }
    return userIdentifier;
  }

  public static User getUserFromEmbeddedUser(EmbeddedUser createdBy) {
    if (createdBy == null) {
      return null;
    }
    User startedByUser = new User();
    startedByUser.setUuid(createdBy.getUuid());
    startedByUser.setName(createdBy.getName());
    startedByUser.setEmail(createdBy.getEmail());
    return startedByUser;
  }

  public static Retry buildRetryAndRegisterListeners(String className) {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(className,
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }

  public static String getParsedMessageFromSetOfStrings(Set<String> strings) {
    StringBuilder parsedMessage = new StringBuilder();
    for (String patternString : strings) {
      Matcher matcher = pattern.matcher(patternString);
      while (matcher.find()) {
        matcher.appendReplacement(parsedMessage, matcher.group(1));
      }
      matcher.appendTail(parsedMessage);
      parsedMessage.append(" | ");
    }
    return parsedMessage.toString();
  }

  public static URL urlObject(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new UnexpectedException("Invalid URL - " + url);
    }
  }

  public static String extractDuplicateValueFromDuplicateKeyException(String errorMessage) {
    // Define the regex pattern to capture the second value in the dup key

    Matcher matcher = duplicateKeyExceptionPattern.matcher(errorMessage);

    // Find the value and return it
    if (matcher.find()) {
      return matcher.group(1);
    }
    // Return null or an appropriate value if no match is found
    return null;
  }

  @SuppressWarnings("unchecked")
  public static <T> T from(Map<String, Object> object, String field, Class<T> clazz) {
    if (isEmpty(object) || isEmpty(field)) {
      return null;
    }

    String[] keys = field.split("(?<!\\\\)\\.");
    for (int i = 0; i < keys.length; i++) {
      keys[i] = keys[i].replace("\\.", ".");
    }

    Map<String, Object> currentMap = object;
    Object value = null;

    for (int i = 0; i < keys.length; i++) {
      value = currentMap.get(keys[i]);
      if (i < keys.length - 1) {
        if (value instanceof Map) {
          currentMap = (Map<String, Object>) value;
        } else {
          return null;
        }
      }
    }

    if (clazz != null) {
      try {
        if (clazz == Set.class && value instanceof List<?>) {
          // Convert List to Set if necessary
          return (T) new HashSet<>((List<Object>) value);
        }
        return clazz.cast(value);
      } catch (ClassCastException e) {
        throw new UnexpectedException("Unable to retrieve value from object by field", e);
      }
    }
    return (T) value;
  }

  public boolean checkIfAccountLevelEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return (!isEmpty(accountIdentifier) && isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) ? true : false;
  }

  public String getDomainFromUrl(String url) {
    try {
      URI uri = new URI(url);
      String host = uri.getHost();
      int port = uri.getPort();
      host = host.startsWith("www.") ? host.substring(4) : host;
      return port != -1 ? (host + ":" + port) : host;
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Error in extracting domain from url - " + url);
    }
  }

  public void normalizeSystemField(Map<String, Object> entity) {
    if (entity == null) {
      return;
    }
    String kind = (String) entity.get("kind");
    if (kind != null && ENTITIES_SUPPORTING_SYSTEM.contains(kind)) {
      Map<String, Object> spec = (Map<String, Object>) entity.get("spec");
      if (spec != null && spec.get("system") instanceof String) {
        String systemValue = (String) spec.get("system");
        spec.put("system", Collections.singletonList(systemValue));
      }
    }
  }

  public String getFilePathForGitIntegrations(
      String gitIntegrationType, String repositoryUrl, String branch, String directory, String filePath) {
    String finalUrl = null;
    if (gitIntegrationType.equals(AZURE_REPO)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + "items?api-version=6.0&path="
          + removeTrailingAndLeadingSlash(directory) + SLASH_DELIMITER + filePath + "&version=GB" + branch;
    }

    if (gitIntegrationType.equals(BITBUCKET_CLOUD)) {
      repositoryUrl = GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(repositoryUrl);

      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_SRC + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory) + SLASH_DELIMITER + filePath;
    }

    if (gitIntegrationType.equals(BITBUCKET_SERVER)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + "browse" + SLASH_DELIMITER + removeTrailingAndLeadingSlash(directory)
          + SLASH_DELIMITER + filePath + "?at=refs/heads/" + branch;
    }

    if (gitIntegrationType.equals(GITHUB)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_BLOB + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory) + SLASH_DELIMITER + filePath;
    }

    if (gitIntegrationType.equals(GITLAB)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_BLOB + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory) + SLASH_DELIMITER + filePath;
    }

    if (gitIntegrationType.equals(HARNESS)) {
      if (repositoryUrl.endsWith(".git")) {
        repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 4);
      }
      String[] repositoryUrlSplit = repositoryUrl.split("/");

      String organizationId;
      String projectId;
      String slug;
      if (repositoryUrlSplit.length == 7) {
        organizationId = repositoryUrlSplit[4];
        projectId = repositoryUrlSplit[5];
        slug = repositoryUrlSplit[6];

        finalUrl = repositoryUrlSplit[0] + "//" + repositoryUrlSplit[2] + "/ng/account/" + repositoryUrlSplit[3]
            + "/module/code/orgs/" + organizationId + "/projects/" + projectId + "/repos/" + slug + "/files/" + branch
            + "/~/" + directory + "/" + filePath;
      }
      if (repositoryUrlSplit.length == 6) {
        organizationId = repositoryUrlSplit[4];
        slug = repositoryUrlSplit[5];

        finalUrl = repositoryUrlSplit[0] + "//" + repositoryUrlSplit[2] + "/ng/account/" + repositoryUrlSplit[3]
            + "/module/code/orgs/" + organizationId + "/repos/" + slug + "/files/" + branch + "/~/" + directory + "/"
            + filePath;
      }
      if (repositoryUrlSplit.length == 5) {
        slug = repositoryUrlSplit[4];

        finalUrl = repositoryUrlSplit[0] + "//" + repositoryUrlSplit[2] + "/ng/account/" + repositoryUrlSplit[3]
            + "/module/code/repos/" + slug + "/files/" + branch + "/~/" + removeTrailingAndLeadingSlash(directory)
            + "/" + filePath;
      }
    }

    return finalUrl;
  }

  public String getDirectoryPathForSourceCode(
      String gitIntegrationType, String repositoryUrl, String branch, String directory) {
    String finalUrl = null;
    if (gitIntegrationType.equals(AZURE_REPO)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + "?path=/" + removeTrailingAndLeadingSlash(directory) + "&version=GB"
          + branch;
    }

    if (gitIntegrationType.equals(BITBUCKET_CLOUD)) {
      repositoryUrl = GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(repositoryUrl);

      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_SRC + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory);
    }

    if (gitIntegrationType.equals(BITBUCKET_SERVER)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + "browse" + SLASH_DELIMITER + removeTrailingAndLeadingSlash(directory)
          + "?at=refs/heads/" + branch;
    }

    if (gitIntegrationType.equals(GITHUB)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_TREE + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory);
    }

    if (gitIntegrationType.equals(GITLAB)) {
      finalUrl = repositoryUrl + SLASH_DELIMITER + SOURCE_FORMAT_TREE + SLASH_DELIMITER + branch + SLASH_DELIMITER
          + removeTrailingAndLeadingSlash(directory);
    }

    if (gitIntegrationType.equals(HARNESS)) {
      if (repositoryUrl.endsWith(".git")) {
        repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 4);
      }
      finalUrl = repositoryUrl + "/files/" + branch + "/~/" + removeTrailingAndLeadingSlash(directory);
    }

    return finalUrl;
  }

  public String getBranchOnlyUrlForSourceCode(String gitIntegrationType, String repositoryUrl, String branch) {
    String finalUrl = null;

    if (gitIntegrationType.equals(AZURE_REPO)) {
      finalUrl = repositoryUrl + "?path=/&version=GB" + branch;
    }

    if (gitIntegrationType.equals(BITBUCKET_CLOUD)) {
      repositoryUrl = GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(repositoryUrl);
      finalUrl = repositoryUrl + "/src/" + branch;
    }

    if (gitIntegrationType.equals(BITBUCKET_SERVER)) {
      finalUrl = repositoryUrl + "/browse?at=refs/heads/" + branch;
    }

    if (gitIntegrationType.equals(GITHUB)) {
      finalUrl = repositoryUrl + "/tree/" + branch;
    }

    if (gitIntegrationType.equals(GITLAB)) {
      finalUrl = repositoryUrl + "/-/tree/" + branch;
    }

    if (gitIntegrationType.equals(HARNESS)) {
      if (repositoryUrl.endsWith(".git")) {
        repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 4);
      }
      finalUrl = repositoryUrl + "/files/" + branch + "/~/";
    }

    return finalUrl;
  }

  public static String[] resolveScopeFromIdentifier(
      String identifier, String callerOrgIdentifier, String callerProjectIdentifier) {
    if (identifier == null) {
      return new String[] {null, null};
    }
    if (identifier.startsWith("account.")) {
      return new String[] {null, null};
    }
    if (identifier.startsWith("org.")) {
      return new String[] {callerOrgIdentifier, null};
    }
    // No prefix → project scope (default)
    return new String[] {callerOrgIdentifier, callerProjectIdentifier};
  }

  public String getScopedIdentifier(String accountId, String orgId, String projectId, String identifier) {
    if (!isEmpty(accountId) && isEmpty(orgId) && isEmpty(projectId)) {
      return "account." + identifier;
    } else if (!isEmpty(accountId) && !isEmpty(orgId) && isEmpty(projectId)) {
      return "org." + identifier;
    } else {
      return identifier;
    }
  }

  public static String escapeRegexMetacharacters(String input) {
    if (input == null) {
      return null;
    }
    final String regex = "([.\\?+*|{}\\[\\]()<>\"\\\\@#])";
    final String replacement = "\\\\$1";

    return input.replaceAll(regex, replacement);
  }

  public static Triple<String, String, String> escapeRegexMetacharacters(Triple<String, String, String> input) {
    if (input == null) {
      return null;
    }

    String escapedLeft = escapeRegexMetacharacters(input.getLeft());
    String escapedMiddle = escapeRegexMetacharacters(input.getMiddle());
    String escapedRight = escapeRegexMetacharacters(input.getRight());

    return Triple.of(escapedLeft, escapedMiddle, escapedRight);
  }

  public static void removeNestedKey(Map<String, Object> entityMap, String property) {
    List<String> keys = tokenizeProperty(property);
    if (keys.isEmpty()) {
      return;
    }
    removeNestedKeyRecursive(entityMap, keys, 0);
  }

  @SuppressWarnings("unchecked")
  private static boolean removeNestedKeyRecursive(Map<String, Object> entityMap, List<String> keys, int index) {
    if (index >= keys.size()) {
      return false;
    }

    String currentKey = keys.get(index);
    if (index == keys.size() - 1) {
      boolean keyExisted = entityMap.containsKey(currentKey);
      if (keyExisted) {
        entityMap.remove(currentKey);
        return entityMap.isEmpty();
      }
      return false;
    }

    Object value = entityMap.get(currentKey);
    if (!(value instanceof Map)) {
      return false;
    }

    Map<String, Object> childMap = (Map<String, Object>) value;
    boolean isEmptyChild = removeNestedKeyRecursive(childMap, keys, index + 1);
    if (isEmptyChild) {
      entityMap.remove(currentKey);
      return entityMap.isEmpty();
    }
    return false;
  }

  public static Map<String, Object> buildMap(String field, Object value) {
    Map<String, Object> resultMap = new HashMap<>();
    Map<String, Object> currentMap = resultMap;
    List<String> keys = tokenizeProperty(field);
    for (int i = 0; i < keys.size() - 1; i++) {
      String key = keys.get(i);
      Map<String, Object> newMap = new HashMap<>();
      currentMap.put(key, newMap);
      currentMap = newMap;
    }
    currentMap.put(keys.get(keys.size() - 1), value);

    return resultMap;
  }

  /**
   * Quote-aware property-path tokenizer. A "double-quoted" segment is one token, so embedded dots/
   * slashes inside quotes are preserved (e.g. {@code paths."POST /v1/x".enrichments} splits into
   * {@code [paths, POST /v1/x, enrichments]}).
   */
  public static List<String> tokenizeProperty(String field) {
    List<String> result = new ArrayList<>();
    String regex = "\"([^\"]*)\"|[^.]+";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(field);

    while (matcher.find()) {
      if (matcher.group(1) != null) {
        result.add(matcher.group(1));
      } else {
        result.add(matcher.group());
      }
    }

    return result;
  }

  public static void throwIfMongoWriteConflictError(Exception ex) {
    if (PersistenceUtils.isMongoWriteConflictError(ex)) {
      String writeConflictMessage = "Your requested operation could not be completed as there is another request "
          + "performing the same operation. Please retry your request.";
      throw new InvalidRequestException(writeConflictMessage);
    }
  }

  public static String buildSpacePath(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(orgIdentifier)) {
      return accountIdentifier;
    }
    if (isEmpty(projectIdentifier)) {
      return accountIdentifier + "/" + orgIdentifier;
    }
    return accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier;
  }
}
