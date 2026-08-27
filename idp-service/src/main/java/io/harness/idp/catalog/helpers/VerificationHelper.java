/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK_WEBHOOK;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.notification.templates.PredefinedTemplate.IDP_CATALOG_ENTITIES_VERIFICATION_JOB_NOTIFICATION_SLACK;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.SlackChannel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class VerificationHelper {
  @Inject IdpCommonService idpCommonService;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject @Named("notificationConfigs") HashMap<String, String> notificationConfigs;
  @Inject CatalogServiceHelper catalogServiceHelper;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  public void verifyHarnessAndIDPEntities(String accountIdentifier) {
    try {
      log.info("Starting the verification job for Harness and IDP entities for account {}", accountIdentifier);
      Set<CatalogEntity> catalogEntities = new HashSet<>();
      Page<CatalogEntity> catalogEntitiesPaged;
      String kind = "api,component,resource,workflow,user,group";
      int page = 0;
      do {
        catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
            catalogServiceHelper
                .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, catalogServiceHelper.getAllScopes(), null)
                .getLeft(),
            page, 1000, null, null, null, null, kind, null, null, null, null, null, null);
        if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
          catalogEntities.addAll(catalogEntitiesPaged.getContent());
        }
        page++;
      } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);
      Set<String> harnessEntityRefs = getEntityRefs(new HashSet<>(catalogEntities));

      String url = String.format("%s/idp/api/catalog/"
              + "entities?filter=kind=api&filter=kind=component&filter=kind=resource&filter=kind="
              + "template&filter=kind=user&filter=kind=group",
          accountIdentifier);
      Object response = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
      List<Map<String, Object>> entities =
          objectMapper.convertValue(response, new TypeReference<List<Map<String, Object>>>() {});

      for (Map<String, Object> entity : entities) {
        CommonUtils.normalizeSystemField(entity);
      }
      List<Object> backstageCatalogEntities = convert(entities, BackstageCatalogEntity.class);
      Set<String> idpEntityRefs = getEntityRefs(new HashSet<>(backstageCatalogEntities));

      Set<String> modifiedIdpEntityRefs =
          idpEntityRefs.stream()
              .map(entityRef -> {
                Triple<String, String, String> kindScopeIdentifier =
                    catalogServiceHelper.getKindScopeIdentifier(entityRef);
                String normalizedScope =
                    kindScopeIdentifier.getMiddle().equals("default") ? "account" : kindScopeIdentifier.getMiddle();
                return kindScopeIdentifier.getLeft() + ":" + normalizedScope + "/" + kindScopeIdentifier.getRight();
              })
              .collect(Collectors.toSet());

      compareEntityRefsAndSendNotification(accountIdentifier, harnessEntityRefs, modifiedIdpEntityRefs);
      log.info("Completed the verification job for Harness and IDP entities for account {}", accountIdentifier);
    } catch (Exception e) {
      log.error("Error occurred while running verification job for Harness and IDP entities for account {}",
          accountIdentifier, e);
    }
  }

  private Set<String> getEntityRefs(Set<Object> entities) {
    return entities.stream().map(CatalogUtils::getEntityRef).collect(Collectors.toSet());
  }

  private void compareEntityRefsAndSendNotification(
      String accountIdentifier, Set<String> harnessEntityRefs, Set<String> idpEntityRefs) {
    Set<String> replacedIdpEntityRefs = modifyGroupKind(idpEntityRefs);
    Set<String> replacedHarnessEntityRefs = removePostfixAndReplacePlusSignature(harnessEntityRefs);
    Set<String> missingIdpEntityRefs = new HashSet<>(replacedHarnessEntityRefs);
    missingIdpEntityRefs.removeAll(replacedIdpEntityRefs);

    Set<String> missingHarnessEntityRefs = new HashSet<>(replacedIdpEntityRefs);
    missingHarnessEntityRefs.removeAll(replacedHarnessEntityRefs);

    if (!isEmpty(missingIdpEntityRefs) || !isEmpty(missingHarnessEntityRefs)) {
      Map<String, String> templateData = new HashMap<>();
      templateData.put("missingIdpEntityRefs", missingIdpEntityRefs.toString());
      templateData.put("missingIdpEntityRefsCount", String.valueOf(missingIdpEntityRefs.size()));
      templateData.put("missingHarnessEntityRefs", missingHarnessEntityRefs.toString());
      templateData.put("missingHarnessEntityRefsCount", String.valueOf(missingHarnessEntityRefs.size()));
      sendNotification(accountIdentifier, templateData);
    }
  }

  private Set<String> modifyGroupKind(Set<String> idpEntityRefs) {
    return idpEntityRefs.stream()
        .map(idpEntityRef -> {
          String kindAndNamespace = idpEntityRef.split("/")[0];
          String kind = kindAndNamespace.split(":")[0];
          String namespace = kindAndNamespace.split(":")[1];
          String name = idpEntityRef.split("/")[1];
          if (kind.equals("group") && name.startsWith("harness_")) {
            return kind + ":" + namespace + "/" + name.substring(7);
          } else {
            return idpEntityRef;
          }
        })
        .collect(Collectors.toSet());
  }

  private Set<String> removePostfixAndReplacePlusSignature(Set<String> harnessEntityRefs) {
    return harnessEntityRefs.stream()
        .map(harnessEntityRef -> harnessEntityRef.split("@")[0].replaceAll("\\+", "plus").toLowerCase())
        .collect(Collectors.toSet());
  }

  private void sendNotification(String accountIdentifier, Map<String, String> templateData) {
    SlackChannel slackChannel =
        SlackChannel.builder()
            .accountId(accountIdentifier)
            .userGroups(Collections.emptyList())
            .templateId(IDP_CATALOG_ENTITIES_VERIFICATION_JOB_NOTIFICATION_SLACK.getIdentifier())
            .templateData(templateData)
            .team(Team.IDP)
            .webhookUrls(Collections.singletonList(
                notificationConfigs.get(CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK_WEBHOOK)))
            .build();
    idpCommonService.sendNotification(slackChannel);
  }
}
