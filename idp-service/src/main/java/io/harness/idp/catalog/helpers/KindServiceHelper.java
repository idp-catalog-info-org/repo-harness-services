/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.RESERVED_KIND_IDENTIFIERS;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.entities.LayoutType;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class KindServiceHelper {
  @Inject KindEntityRepository kindEntityRepository;

  private final LoadingCache<String, String> draftSchemaCache =
      CacheBuilder.newBuilder().maximumSize(1).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String ignored) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/draft-07-schema.json");
        }
      });

  private final LoadingCache<String, String> baseSchemaCache =
      CacheBuilder.newBuilder().maximumSize(1).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String ignored) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/base.v1.schema.json");
        }
      });

  public void validateIdentifier(String identifier) {
    if (isEmpty(identifier)) {
      throw new InvalidRequestException("Unable to create kind as identifier is null / empty");
    }
    final Pattern identifierPattern = EntityIdentifierValidator.IDENTIFIER_PATTERN;
    if (!identifierPattern.matcher(identifier).matches()) {
      throw new InvalidRequestException("Kind identifier = " + identifier + " is invalid");
    }
    if (RESERVED_KIND_IDENTIFIERS.contains(identifier)) {
      throw new InvalidRequestException("Unable to create kind as it already exist as reserved built-in kind");
    }
  }

  public KindEntity kindEntity(String accountIdentifier, String identifier) {
    Optional<KindEntity> optionalKindEntity;
    if (RESERVED_KIND_IDENTIFIERS.contains(identifier)) {
      optionalKindEntity =
          kindEntityRepository.findByAccountIdentifierAndIdentifierWithoutSchema(GLOBAL_ACCOUNT_ID, identifier);
    } else {
      optionalKindEntity = kindEntityRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    }
    if (optionalKindEntity.isEmpty()) {
      log.error("No kind found for accountIdentifier = {} | identifier = {}", accountIdentifier, identifier);
      throw new InvalidRequestException("Unable to get kind as it doesn't exist");
    }
    return optionalKindEntity.get();
  }

  public List<KindEntity> findByAccountIdentifierIn(String accountIdentifier) {
    return kindEntityRepository.findByAccountIdentifierIn(List.of(GLOBAL_ACCOUNT_ID, accountIdentifier));
  }

  public ScopeInfo accountScopeInfo(String accountIdentifier) {
    return ScopeInfo.builder()
        .accountIdentifier(accountIdentifier)
        .scopeType(ScopeLevel.ACCOUNT)
        .uniqueId(accountIdentifier)
        .build();
  }

  public void validateKindsIfExistInEntityRefs(String accountIdentifier, List<String> entityRefs) {
    Set<String> kinds =
        entityRefs.stream().map(entityRef -> entityRef.split(":")[0].toLowerCase()).collect(Collectors.toSet());
    validateKindsIfExist(accountIdentifier, new ArrayList<>(kinds));
  }

  public void validateKindIfExist(String accountIdentifier, String kind) {
    validateKindsIfExist(accountIdentifier, List.of(kind.toLowerCase()));
  }

  public void validateKindsIfExist(String accountIdentifier, List<String> kinds) {
    Map<String, KindEntity> kindEntityMap = findByAccountIdentifierIn(accountIdentifier)
                                                .stream()
                                                .collect(Collectors.toMap(KindEntity::getIdentifier, entity -> entity));
    kinds.forEach(kind -> {
      if (!kindEntityMap.containsKey(kind)) {
        throw new InvalidRequestException("Kind " + kind + " does not exist");
      }
    });
  }

  public String baseSchema() {
    try {
      return baseSchemaCache.get("");
    } catch (Exception e) {
      log.error("Error in base schema retrieval");
      throw new UnexpectedException();
    }
  }

  public String draftSchema() {
    try {
      return draftSchemaCache.get("");
    } catch (Exception e) {
      log.error("Error in draft schema retrieval");
      throw new UnexpectedException();
    }
  }

  public LayoutEntity layoutForKind(KindEntity kindEntity) {
    LayoutEntity layoutEntity = new LayoutEntity();
    layoutEntity.setAccountIdentifier(kindEntity.getAccountIdentifier());
    layoutEntity.setParentUniqueId(kindEntity.getParentUniqueId());
    layoutEntity.setName(kindEntity.getIdentifier());
    layoutEntity.setDisplayName(kindEntity.getName());
    String defaultLayout = """
            page:
              name: EntityLayout
              tabs:
                - name: Overview
                  path: /
                  title: Overview
                  contents:
                    - component: EntityOrphanWarning
                    - component: EntityRelationWarning
                    - component: EntityProcessingErrorsPanel
                    - component: EntityAboutCard
                      specs:
                        props:
                          variant: gridItem
                        gridProps:
                          md: 6
                    - component: EntityScoreCard
                      specs:
                        gridProps:
                          md: 6
                    - component: EntityCatalogGraphCard
                      specs:
                        props:
                          variant: gridItem
                          height: 400
                        gridProps:
                          md: 6
                          xs: 12
                    - component: EntityLinksCard
                      specs:
                        props:
                          variant: gridItem
                          item: 401
                        gridProps:
                          md: 6
                    - component: EntityHasSubcomponentsCard
                      specs:
                        props:
                          variant: gridItem
                          height: 400
                        gridProps:
                          md: 8
                          xs: 12
                    - component: EntityGithubCodespacesCard
                      specs:
                        gridProps:
                          md: 6
                    - component: EntityGithubCodespacesWidget
                      specs:
                        gridProps:
                          md: 6
                    - component: EntityJiraOverviewCard
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isPagerDutyAvailable
                            content:
                              component: EntityPagerDutyCard
                    - component: EntitySonarQubeCard
                      specs:
                        gridProps:
                          md: 6
                    - component: EntityBugsnagErrorsOverviewTable
                      specs:
                        gridProps:
                          md: 6
                - name: EntityGithubCodespacesContent
                  path: /github-codespaces-entity
                  title: CodespacesEntityContent
                  contents:
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isGithubCodespacesAvailable
                            content:
                              component: EntityGithubCodespacesContent
                - name: EntityGithubCodespacesRepoContent
                  path: /github-codespaces-repo
                  title: CodespacesRepoContent
                  contents:
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isGithubCodespacesAvailable
                            content:
                              component: EntityGithubCodespacesRepoContent
                - name: code-insights
                  path: /code-insights
                  title: Code Insights
                  contents:
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isGithubInsightsAvailable
                            content:
                              component: EntityGithubInsightsContent
                - name: pull-requests
                  path: /pull-requests
                  title: Pull Requests
                  contents:
                    - component: EntityGithubPullRequestsContent
                - name: ci-cd
                  path: /ci-cd
                  title: CI/CD
                  contents:
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isJenkinsAvailable
                            content:
                              component: EntityJenkinsContent
                          - content:
                              component: EmptyState
                              specs:
                                props:
                                  title: No CI/CD available for this entity
                                  missing: info
                                  description: You need to add an annotation to your component if you want to
                                    enable CI/CD for it. You can read more about annotations
                                    in Backstage by clicking the button below.
                - name: Scorecard
                  path: /scorecard
                  title: Scorecard
                  contents:
                    - component: EntityScorecardContent
                - name: API
                  path: /api
                  title: API
                  contents:
                    - component: EntityProvidedApisCard
                      specs:
                        gridProps:
                          md: 12
                    - component: EntityConsumedApisCard
                      specs:
                        gridProps:
                          md: 12
                - name: EntityAdrContent
                  path: /adrs
                  title: ADRs
                  contents:
                    - component: EntitySwitch
                      specs:
                        cases:
                          - if: isAdrAvailable
                            content:
                              component: EntityAdrContent
                - name: Deps
                  path: /dependencies
                  title: Dependencies
                  contents:
                    - component: EntityDependsOnComponentsCard
                      specs:
                        props:
                          variant: gridItem
                        gridProps:
                          md: 12
                    - component: EntityConsumedApisCard
                      specs:
                        props:
                          variant: gridItem
                        gridProps:
                          md: 12
                - name: kubernetes
                  path: /kubernetes
                  title: Kubernetes
                  contents:
                    - component: EntityKubernetesContent
                - name: EntityHarnessFeatureFlagContent
                  path: /feature-flag
                  title: Feature Flag
                  contents:
                    - component: EntityHarnessFeatureFlagContent
                - name: TechDocs
                  path: /docs
                  title: Docs
                  contents:
                    - component: EntityTechdocsContent
                      specs:
                        children:
                          - component: TechDocsAddons
                            specs:
                              children:
                                - component: ReportIssue
            """;
    layoutEntity.setYaml(defaultLayout);
    layoutEntity.setDefaultYaml(defaultLayout);
    layoutEntity.setDescription(null);
    layoutEntity.setType(LayoutType.EntityLayout);
    layoutEntity.setEntityKind(kindEntity.getIdentifier());
    layoutEntity.setEntityType("default");
    return layoutEntity;
  }

  public Set<String> groupingKinds(String accountIdentifier) {
    try {
      return kindEntityRepository.findGroupingKindsByAccountIdentifierIn(List.of(GLOBAL_ACCOUNT_ID, accountIdentifier))
          .stream()
          .map(KindEntity::getIdentifier)
          .collect(Collectors.toSet());
    } catch (Exception ex) {
      log.warn("Error in fetching grouping kinds for accountIdentifier = {} Error = {}", accountIdentifier,
          ex.getMessage(), ex);
      return new HashSet<>();
    }
  }
}
