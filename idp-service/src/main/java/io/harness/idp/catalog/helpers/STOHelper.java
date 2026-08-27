/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.ANNOTATIONS;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.catalog.utils.Constants.NAME;
import static io.harness.idp.catalog.utils.Constants.STO;
import static io.harness.idp.catalog.utils.Constants.STO_TEST_TARGET_ANNOTATION;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.sto.VulnerabilityScan;
import io.harness.sto.beans.FrontendIssueCounts;
import io.harness.sto.beans.IssueCountsRequestDto;
import io.harness.sto.beans.ScanIssueCountsWithExecutionInfo;
import io.harness.sto.beans.TargetAndVariantName;
import io.harness.sto.remote.STOServiceRestClient;
import io.harness.stoserviceclient.STOServiceUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class STOHelper {
  @Inject private STOServiceRestClient stoServiceRestClient;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject private STOServiceUtils stoServiceUtils;
  @Inject private CatalogServiceHelper catalogServiceHelper;
  private static final String API_KEY = "ApiKey ";
  static final Pattern ORG_IDENTIFIER_PATTERN = Pattern.compile("orgs/([^/]+)");
  static final Pattern PROJECT_IDENTIFIER_PATTERN = Pattern.compile("projects/([^/]+)");
  static final Pattern REPO_IDENTIFIER_PATTERN = Pattern.compile("repos/([^/]+)");

  public void populateSTOData(CatalogEntity catalogEntity) {
    Map<String, Object> decoratedEntityMap = catalogEntity.getDecoratedEntityMap();
    Map<String, Object> decoratedMetadata = getFailSafeMetadata(decoratedEntityMap);
    Map<String, Object> decoratedAnnotations = getFailSafeAnnotations(decoratedMetadata);
    List<Map<String, String>> stoTestTargets =
        (List<Map<String, String>>) decoratedAnnotations.get(STO_TEST_TARGET_ANNOTATION);
    String repoUrl = getRepoUrl(catalogEntity);

    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);
    Map<String, Object> metadata = getFailSafeMetadata(processedData);
    Map<String, Object> sto = new HashMap<>();

    Map<String, List<Pair<String, String>>> testTargetsMap = constructTestTargetsMap(
        stoTestTargets, repoUrl, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
    List<ScanIssueCountsWithExecutionInfo> issueCountsWithExecutionInfos =
        callScanSummaryApi(testTargetsMap, catalogEntity.getAccountIdentifier());
    boolean toBeSaved = false;
    if (!isEmpty(issueCountsWithExecutionInfos)) {
      for (ScanIssueCountsWithExecutionInfo scanIssueCountsWithExecutionInfo : issueCountsWithExecutionInfos) {
        for (FrontendIssueCounts frontendIssueCounts : scanIssueCountsWithExecutionInfo.getScanners()) {
          Map<String, Integer> scanResults = new HashMap<>();
          int critical = frontendIssueCounts.getCritical();
          int high = frontendIssueCounts.getHigh();
          int medium = frontendIssueCounts.getMedium();
          int low = frontendIssueCounts.getLow();
          int info = frontendIssueCounts.getInfo();
          int total = critical + high + medium + low + info;
          scanResults.put("critical", critical);
          scanResults.put("high", high);
          scanResults.put("medium", medium);
          scanResults.put("low", low);
          scanResults.put("info", info);
          scanResults.put("total", total);
          String targetName = scanIssueCountsWithExecutionInfo.getTargetName().toLowerCase();
          if (sto.containsKey(targetName)) {
            Map<String, Object> existingTarget = (Map<String, Object>) sto.get(targetName);
            existingTarget.put(frontendIssueCounts.getScanner(), scanResults);
          } else {
            sto.put(targetName, new HashMap<>(Map.of(frontendIssueCounts.getScanner(), scanResults)));
          }
        }
      }
      metadata.put(STO, sto);
      toBeSaved = true;
    } else if (!isEmpty(getFailSafeSTOData(metadata))) {
      metadata.remove(STO);
      toBeSaved = true;
    }

    if (toBeSaved) {
      processedData.put(METADATA, metadata);
      decorator.put(PROCESSED_DATA, processedData);
      catalogEntity.setDecorator(decorator);
      catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
      catalogEntityRepository.save(catalogEntity);
    }
  }

  public void processEvent(VulnerabilityScan vulnerabilityScan) {
    String accountIdentifier = vulnerabilityScan.getScope().getAccountIdentifier();
    String targetName = vulnerabilityScan.getArtifactInfo().getTargetName().toLowerCase();
    String targetVariant = vulnerabilityScan.getArtifactInfo().getVariant();
    String variantType = vulnerabilityScan.getArtifactInfo().getTargetType();
    boolean isBaseline = vulnerabilityScan.getArtifactInfo().getIsBaseline();

    Map<String, Object> constructQueryMap = new HashMap<>();
    constructQueryMap.put(
        METADATA + "." + ANNOTATIONS + "." + STO_TEST_TARGET_ANNOTATION.replace(".", DOT_REPLACEMENT) + "." + NAME,
        Pattern.compile(targetName, Pattern.CASE_INSENSITIVE));
    constructQueryMap.put("decorator._processed_data"
            + "." + METADATA + "." + ANNOTATIONS + "." + STO_TEST_TARGET_ANNOTATION.replace(".", DOT_REPLACEMENT) + "."
            + NAME,
        Pattern.compile(targetName, Pattern.CASE_INSENSITIVE));
    if (variantType.equals("repository")) {
      constructQueryMap.put(CatalogEntity.CatalogKeys.sourceLocation,
          Pattern.compile(".*" + targetName + ".*", Pattern.CASE_INSENSITIVE));
      constructQueryMap.put(CatalogEntity.CatalogKeys.spec + ".sourceCode.url",
          Pattern.compile(".*" + targetName + ".*", Pattern.CASE_INSENSITIVE));
    }
    List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesForArbitraryFields(
        accountIdentifier, constructQueryMap, String.join(",", CORE_KINDS));
    log.info("Total matching entities found {} entityRefs {} for targetName {}", catalogEntities.size(),
        catalogEntities.stream().map(CatalogUtils::entityRef).toList(), targetName);
    List<CatalogEntity> modifiedCatalogEntities = new ArrayList<>();
    if (!isEmpty(catalogEntities)) {
      for (CatalogEntity catalogEntity : catalogEntities) {
        Map<String, Object> decoratedEntityMap = catalogEntity.getDecoratedEntityMap();
        Map<String, Object> decoratedMetadata = getFailSafeMetadata(decoratedEntityMap);
        Map<String, Object> decoratedAnnotations = getFailSafeAnnotations(decoratedMetadata);
        List<Map<String, String>> stoTestTargets =
            (List<Map<String, String>>) decoratedAnnotations.get(STO_TEST_TARGET_ANNOTATION);
        boolean matchFound = false;
        if (!isEmpty(stoTestTargets)) {
          for (Map<String, String> stoTestTarget : stoTestTargets) {
            String name = stoTestTarget.get("name");
            String variant = stoTestTarget.get("variant");
            String scope = stoTestTarget.get("scope");
            String orgIdentifier, projectIdentifier;
            if (isEmpty(scope)) {
              orgIdentifier = catalogEntity.getOrgIdentifier();
              projectIdentifier = catalogEntity.getProjectIdentifier();
            } else {
              String[] scopeSplit = scope.split("\\.");
              if (scopeSplit.length == 1) {
                orgIdentifier = catalogEntity.getOrgIdentifier();
                projectIdentifier = scopeSplit[0];
              } else if (scopeSplit.length == 2) {
                orgIdentifier = scopeSplit[0];
                projectIdentifier = scopeSplit[1];
              } else {
                orgIdentifier = scopeSplit[1];
                projectIdentifier = scopeSplit[2];
              }
            }
            if (name.equalsIgnoreCase(targetName)
                && ((!isEmpty(variant) && variant.equals(targetVariant)) || (isEmpty(variant) && isBaseline))) {
              if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(vulnerabilityScan.getScope().getOrgIdentifier()))
                  || (!isEmpty(projectIdentifier)
                      && !projectIdentifier.equals(vulnerabilityScan.getScope().getProjectIdentifier()))) {
                matchFound = false;
              } else {
                matchFound = true;
                break;
              }
            }
          }
        }
        if (!matchFound) {
          String repoUrl = getRepoUrl(catalogEntity);
          if (!isEmpty(repoUrl) && getTargetName(repoUrl).equalsIgnoreCase(targetName) && isBaseline
              && Objects.equals(vulnerabilityScan.getScope().getOrgIdentifier(), catalogEntity.getOrgIdentifier())
              && Objects.equals(
                  vulnerabilityScan.getScope().getProjectIdentifier(), catalogEntity.getProjectIdentifier())) {
            matchFound = true;
          }
        }
        if (matchFound) {
          Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
          Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);
          Map<String, Object> metadata = getFailSafeMetadata(processedData);
          Map<String, Object> sto = getFailSafeSTOData(metadata);
          Map<String, Integer> scanResults = new HashMap<>();
          String tool = vulnerabilityScan.getScanResults().getTool();
          int critical = vulnerabilityScan.getScanResults().getCritical();
          int high = vulnerabilityScan.getScanResults().getHigh();
          int medium = vulnerabilityScan.getScanResults().getMedium();
          int low = vulnerabilityScan.getScanResults().getLow();
          int info = vulnerabilityScan.getScanResults().getInfo();
          int total = vulnerabilityScan.getScanResults().getTotal();
          scanResults.put("critical", critical);
          scanResults.put("high", high);
          scanResults.put("medium", medium);
          scanResults.put("low", low);
          scanResults.put("info", info);
          scanResults.put("total", total);
          if (sto.containsKey(targetName)) {
            Map<String, Object> existingTarget = (Map<String, Object>) sto.get(targetName);
            existingTarget.put(tool, scanResults);
          } else {
            sto.put(targetName, new HashMap<>(Map.of(tool, scanResults)));
          }
          metadata.put(STO, sto);
          processedData.put(METADATA, metadata);
          decorator.put(PROCESSED_DATA, processedData);
          catalogEntity.setDecorator(decorator);
          modifiedCatalogEntities.add(catalogEntity);
        }
      }
    }
    if (!isEmpty(modifiedCatalogEntities)) {
      modifiedCatalogEntities.forEach(
          catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
      catalogEntityRepository.saveAll(modifiedCatalogEntities);
    }
  }

  public Map<String, List<Pair<String, String>>> getSTOTestTargets(CatalogEntity catalogEntity) {
    Map<String, Object> decoratedEntityMap = catalogEntity.getDecoratedEntityMap();
    Map<String, Object> decoratedMetadata = getFailSafeMetadata(decoratedEntityMap);
    Map<String, Object> decoratedAnnotations = getFailSafeAnnotations(decoratedMetadata);
    List<Map<String, String>> stoTestTargets =
        (List<Map<String, String>>) decoratedAnnotations.get(STO_TEST_TARGET_ANNOTATION);
    String repoUrl = getRepoUrl(catalogEntity);
    return constructTestTargetsMap(
        stoTestTargets, repoUrl, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
  }

  private Map<String, Object> getFailSafeMetadata(Map<String, Object> processedData) {
    return Objects.isNull(processedData.get(METADATA)) ? new HashMap<>()
                                                       : (Map<String, Object>) processedData.get(METADATA);
  }

  private Map<String, Object> getFailSafeAnnotations(Map<String, Object> metadata) {
    return Objects.isNull(metadata.get(ANNOTATIONS)) ? new HashMap<>()
                                                     : (Map<String, Object>) metadata.get(ANNOTATIONS);
  }

  private Map<String, Object> getFailSafeSTOData(Map<String, Object> metadata) {
    return Objects.isNull(metadata.get(STO)) ? new HashMap<>() : (Map<String, Object>) metadata.get(STO);
  }

  private String getAuthToken(String accountId) {
    return API_KEY + stoServiceUtils.getSTOServiceToken(accountId, List.of("manager"));
  }

  private String getTargetName(String sourceLocation) {
    List<String> catalogLocationParts = new ArrayList<>(Arrays.asList(sourceLocation.split("/")));
    if (catalogLocationParts.size() >= 5) {
      String gitProvider = catalogLocationParts.get(2);
      if (gitProvider.contains("harness.io")) {
        String orgIdentifier = getValue(ORG_IDENTIFIER_PATTERN, sourceLocation);
        String projectIdentifier = getValue(PROJECT_IDENTIFIER_PATTERN, sourceLocation);
        String repoIdentifier = getValue(REPO_IDENTIFIER_PATTERN, sourceLocation);
        return ((!isEmpty(orgIdentifier) ? orgIdentifier + "/" : StringUtils.EMPTY)
            + (!isEmpty(projectIdentifier) ? projectIdentifier + "/" : StringUtils.EMPTY)
            + (!isEmpty(repoIdentifier) ? repoIdentifier : StringUtils.EMPTY))
            .toLowerCase();
      } else {
        StringBuilder subGroup = new StringBuilder();
        int index = 4;
        while (index < catalogLocationParts.size() && !catalogLocationParts.get(index).equals("-")
            && !catalogLocationParts.get(index).equals("tree") && !catalogLocationParts.get(index).equals("blob")
            && !catalogLocationParts.get(index).equals("browse") && !catalogLocationParts.get(index).equals("src")) {
          subGroup.append(catalogLocationParts.get(index)).append("/");
          index++;
        }
        return (catalogLocationParts.get(3) + "/" + subGroup.substring(0, subGroup.length() - 1)).toLowerCase();
      }
    }
    return null;
  }

  private String getValue(Pattern pattern, String sourceLocation) {
    Matcher matcher = pattern.matcher(sourceLocation);
    return matcher.find() ? matcher.group(1) : StringUtils.EMPTY;
  }

  private Map<String, List<Pair<String, String>>> constructTestTargetsMap(
      List<Map<String, String>> stoTestTargets, String repoUrl, String orgIdentifier, String projectIdentifier) {
    Map<String, List<Pair<String, String>>> testTargetsMap = new HashMap<>();
    if (!isEmpty(stoTestTargets) || !isEmpty(repoUrl)) {
      if (!isEmpty(stoTestTargets)) {
        for (Map<String, String> testTarget : stoTestTargets) {
          String targetName = testTarget.get("name").toLowerCase();
          String targetVariant = StringUtils.defaultIfEmpty(testTarget.get("variant"), StringUtils.EMPTY);
          String[] resolvedScope = resolveScope(testTarget.get("scope"), orgIdentifier, projectIdentifier);

          if (isEmpty(resolvedScope[0]) || isEmpty(resolvedScope[1])) {
            log.warn("Cannot make STO API call since orgIdentifier/projectIdentifier is empty");
            continue;
          }

          String scopeKey = resolvedScope[0] + "." + resolvedScope[1];
          if (isEmpty(testTargetsMap.get(scopeKey))) {
            testTargetsMap.put(scopeKey, new ArrayList<>());
          }
          List<Pair<String, String>> testTargetPairs = testTargetsMap.get(scopeKey);
          testTargetPairs.add(Pair.of(targetName, targetVariant));
        }
      }

      if (!isEmpty(repoUrl) && !isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
        String targetName = getTargetName(repoUrl);
        if (!isEmpty(targetName)) {
          String scopeKey = orgIdentifier + "." + projectIdentifier;
          if (isEmpty(testTargetsMap.get(scopeKey))) {
            testTargetsMap.put(scopeKey, new ArrayList<>());
          }
          List<Pair<String, String>> testTargetPairs = testTargetsMap.get(scopeKey);
          testTargetPairs.add(Pair.of(targetName, StringUtils.EMPTY));
        }
      }
    }
    return testTargetsMap;
  }

  private List<ScanIssueCountsWithExecutionInfo> callScanSummaryApi(
      Map<String, List<Pair<String, String>>> testTargetsMap, String accountIdentifier) {
    List<ScanIssueCountsWithExecutionInfo> issueCountsWithExecutionInfos = new ArrayList<>();
    for (Map.Entry<String, List<Pair<String, String>>> entry : testTargetsMap.entrySet()) {
      List<TargetAndVariantName> targetVariants = new ArrayList<>();
      for (Pair<String, String> pair : entry.getValue()) {
        targetVariants.add(
            TargetAndVariantName.builder().targetName(pair.getLeft()).targetVariantName(pair.getRight()).build());
      }
      IssueCountsRequestDto dto = IssueCountsRequestDto.builder().targetVariants(targetVariants).build();
      String[] scopeSplit = entry.getKey().split("\\.");
      String orgId = scopeSplit[0];
      String projectId = scopeSplit[1];
      List<ScanIssueCountsWithExecutionInfo> response =
          NGRestUtils.getGeneralResponse(stoServiceRestClient.getArtifactScanSummary(
              getAuthToken(accountIdentifier), accountIdentifier, orgId, projectId, dto));
      if (!isEmpty(response)) {
        issueCountsWithExecutionInfos.addAll(response);
      }
    }
    return issueCountsWithExecutionInfos;
  }

  private String[] resolveScope(String scope, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(scope)) {
      return new String[] {orgIdentifier, projectIdentifier};
    }

    String[] parts = scope.split("\\.");
    return switch (parts.length) {
      case 1 -> new String[] { orgIdentifier, parts[0] };
      case 2 -> new String[] { parts[0], parts[1] };
      case 3 -> new String[] { parts[1], parts[2] };
      default -> new String[] { null, null };
    };
  }

  private String getRepoUrl(CatalogEntity catalogEntity) {
    String repoUrl = null;
    Map<String, Object> spec = catalogEntity.getSpec();
    if (!isEmpty(spec)) {
      Map<String, Object> sourceCode = (Map<String, Object>) spec.get("sourceCode");
      if (!isEmpty(sourceCode)) {
        repoUrl = (String) sourceCode.get("url");
      }
    }
    if (isEmpty(repoUrl)) {
      repoUrl = catalogEntity.getSourceLocation();
    }
    return repoUrl;
  }
}
