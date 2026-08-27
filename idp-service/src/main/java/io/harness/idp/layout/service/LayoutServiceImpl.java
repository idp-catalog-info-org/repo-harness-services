/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.idp.layout.beans.EntityKindLayout;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.helpers.LayoutManager;
import io.harness.idp.layout.helpers.LayoutServiceHelper;
import io.harness.idp.layout.mapper.LayoutMapper;
import io.harness.idp.layout.repositories.LayoutEntityRepository;
import io.harness.idp.proxy.layout.events.LayoutCreateEvent;
import io.harness.idp.proxy.layout.events.LayoutDeleteEvent;
import io.harness.idp.proxy.layout.events.LayoutUpdateEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.ExportDetails;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class LayoutServiceImpl implements LayoutService {
  @Inject LayoutServiceHelper layoutServiceHelper;
  @Inject LayoutEntityRepository layoutEntityRepository;
  @Inject KindEntityRepository kindEntityRepository;
  @Inject TransactionHelper transactionHelper;
  @Inject OutboxService outboxService;

  @Override
  public Response create(String harnessAccount, LayoutRequest layoutRequest) {
    if (layoutRequest == null || isEmpty(layoutRequest.getName())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "Name is missing in payload"))
          .build();
    }

    if (isEmpty(layoutRequest.getType()) || isEmpty(layoutRequest.getYaml())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "Yaml is missing in payload"))
          .build();
    }

    String yaml = layoutRequest.getYaml();

    boolean yamlValidation = layoutServiceHelper.validateYaml(yaml);
    if (!yamlValidation) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "failed to parse yaml"))
          .build();
    }

    boolean hierarchyKindValidation = layoutServiceHelper.hierarchyKindValidation(layoutRequest.getEntityKind(), yaml);
    if (!hierarchyKindValidation) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "Yaml is not valid", "errorDetails", ""))
          .build();
    }

    boolean entityLayoutTypeValidation = layoutServiceHelper.entityLayoutTypeValidation(layoutRequest.getType(), yaml);
    if (!entityLayoutTypeValidation) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "Yaml is not valid", "errorDetails", ""))
          .build();
    }

    LayoutEntity layoutEntity = LayoutMapper.DtoToEntity(harnessAccount, layoutRequest);
    Optional<LayoutEntity> optionalLayout =
        layoutEntityRepository.findByAccountIdentifierAndName(harnessAccount, layoutEntity.getName());
    if (optionalLayout.isEmpty()) {
      transactionHelper.performTransaction(() -> {
        layoutEntityRepository.save(layoutEntity);
        outboxService.save(new LayoutCreateEvent(layoutRequest, harnessAccount));
        return null;
      });
      return Response.ok(Map.of("success", true, "message", "success")).build();
    }

    LayoutEntity existingLayout = optionalLayout.get();
    LayoutRequest existingLayoutRequest = LayoutMapper.EntityToDto(existingLayout);
    layoutEntity.setId(existingLayout.getId());
    layoutEntity.setUniqueId(existingLayout.getUniqueId());
    layoutEntity.setHarnessManaged(existingLayout.isHarnessManaged());
    transactionHelper.performTransaction(() -> {
      layoutEntityRepository.save(layoutEntity);
      outboxService.save(new LayoutUpdateEvent(layoutRequest, existingLayoutRequest, harnessAccount));
      return null;
    });
    return Response.ok(Map.of("success", true, "message", "success")).build();
  }

  @Override
  public Response delete(String harnessAccount, LayoutRequest layoutRequest) {
    if (layoutRequest == null || isEmpty(layoutRequest.getName())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "Name is missing in payload"))
          .build();
    }
    String name = layoutRequest.getName();
    Optional<LayoutEntity> optionalLayout = layoutEntityRepository.findByAccountIdentifierAndName(harnessAccount, name);
    if (optionalLayout.isEmpty()) {
      log.error("Error in layout delete. No layout found for accountIdentifier = {} | name = {}", harnessAccount, name);
      throw new InvalidRequestException("Unable to delete layout as it doesn't exist");
    }
    LayoutEntity layoutEntity = optionalLayout.get();
    transactionHelper.performTransaction(() -> {
      layoutEntityRepository.delete(layoutEntity);
      outboxService.save(new LayoutDeleteEvent(layoutRequest, harnessAccount));
      return null;
    });
    return Response.ok(Collections.singletonMap("success", true)).build();
  }

  @Override
  public Response get(String harnessAccount, String name) {
    Optional<LayoutEntity> optionalLayout = layoutEntityRepository.findByAccountIdentifierAndName(harnessAccount, name);
    if (optionalLayout.isEmpty()) {
      log.error("Error in layout get. No layout found for accountIdentifier = {} | name = {}", harnessAccount, name);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Collections.singletonMap("errorMessage", "Layout not found"))
          .build();
    }
    LayoutEntity layoutEntity = optionalLayout.get();
    List<KindEntity> kindEntities = kindEntityRepository.findAllByAccountIdentifierInAndIdentifierIn(
        List.of(harnessAccount, GLOBAL_ACCOUNT_ID), Collections.singletonList(layoutEntity.getEntityKind()));
    if (kindEntities.isEmpty()) {
      log.error("Error in layout get. No kind found for accountIdentifier = {} | name = {}", harnessAccount, name);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Collections.singletonMap("errorMessage", "Kind not found"))
          .build();
    }
    return Response
        .ok(Collections.singletonMap("response", LayoutMapper.entityToObject(layoutEntity, kindEntities.get(0))))
        .build();
  }

  @Override
  public Response get(String harnessAccount) {
    List<LayoutEntity> layoutEntities = layoutEntityRepository.findAllByAccountIdentifier(harnessAccount);
    List<KindEntity> kindEntities =
        kindEntityRepository.findAllByAccountIdentifierInAndIdentifierIn(List.of(harnessAccount, GLOBAL_ACCOUNT_ID),
            layoutEntities.stream().map(LayoutEntity::getEntityKind).collect(Collectors.toList()));
    return Response
        .ok(Collections.singletonMap("response", LayoutMapper.entitiesToObjects(layoutEntities, kindEntities)))
        .build();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Response ingest(String harnessAccount, LayoutIngestRequest layoutIngestRequest) {
    List<String> allErrors = new ArrayList<>();

    String operationType = layoutIngestRequest.getOperationType();
    if (isEmpty(operationType)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("success", false, "errorMessage", "operationType is missing"))
          .build();
    }

    List<String> defaultLayouts = layoutIngestRequest.getExports().getDefaultEntityTypes();
    List<ExportDetails> exportDetails = layoutIngestRequest.getExports().getExportDetails();

    for (String layoutName : defaultLayouts) {
      Optional<LayoutEntity> optionalLayout =
          layoutEntityRepository.findByAccountIdentifierAndName(harnessAccount, layoutName);
      if (optionalLayout.isEmpty()) {
        allErrors.add(String.format("Layout %s not found, skipping", layoutName));
        continue;
      }
      LayoutEntity layoutEntity = optionalLayout.get();
      LayoutManager layoutManager = new LayoutManager(layoutEntity.getYaml());

      for (ExportDetails exportDetail : exportDetails) {
        Boolean addByDefault = exportDetail.isAddByDefault();
        if (addByDefault == null || !addByDefault) {
          continue;
        }

        String type = exportDetail.getType();
        List<String> errors;

        if ("CARD".equals(type)) {
          if ("ADD".equals(operationType)) {
            errors = layoutManager.addCardToOverviewPage(exportDetail, layoutName);
          } else {
            errors = layoutManager.removeCardFromOverviewPage(exportDetail, layoutName);
          }
          allErrors.addAll(errors);
        } else if ("TAB_CONTENT".equals(type)) {
          if ("ADD".equals(operationType)) {
            errors = layoutManager.addTabToLayout(exportDetail, layoutName);
          } else {
            errors = layoutManager.removeTabFromLayout(exportDetail, layoutName);
          }
          allErrors.addAll(errors);
        }
      }

      Map<String, Object> layoutToJson = layoutManager.getCurrentLayout();
      boolean isUpdatedLayoutValid = "EntityLayout".equals(layoutEntity.getType().name())
          && layoutServiceHelper.entityLayoutTypeValidation(
              layoutEntity.getType().name(), new Yaml().dump(layoutToJson));

      if (!isUpdatedLayoutValid) {
        allErrors.add(String.format("Updated layout %s failed validation", layoutName));
        continue;
      }

      layoutEntity.setYaml(new Yaml().dump(layoutToJson));
      layoutEntityRepository.save(layoutEntity);
    }

    if (layoutIngestRequest.getExports().getPages() != null && layoutIngestRequest.getExports().getPages() > 0) {
      Optional<LayoutEntity> sidenavOpt =
          layoutEntityRepository.findByAccountIdentifierAndName(harnessAccount, "sidenav");
      if (sidenavOpt.isPresent()) {
        LayoutEntity sidenavEntity = sidenavOpt.get();
        Map<String, Object> sideNavJson = new Yaml().load(sidenavEntity.getYaml());
        Map<String, Object> child = (Map<String, Object>) sideNavJson.get("page");

        List<ExportDetails> pageExports = new ArrayList<>();
        for (ExportDetails ed : exportDetails) {
          if ("PAGE".equals(ed.getType())) {
            pageExports.add(ed);
          }
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) child.get("children");
        if (children == null) {
          children = new ArrayList<>();
          child.put("children", children);
        }

        for (ExportDetails page : pageExports) {
          Map<String, Object> layoutSchemaSpecs = (Map<String, Object>) page.getLayoutSchemaSpecs();
          String path = layoutSchemaSpecs != null ? (String) layoutSchemaSpecs.get("path") : null;
          String title = layoutSchemaSpecs != null ? (String) layoutSchemaSpecs.get("title") : null;

          Map<String, Object> sidenavLinkPresent =
              children.stream()
                  .filter(c -> {
                    Map<String, Object> props = (Map<String, Object>) c.get("props");
                    return props != null && path != null && path.equals(props.get("to"));
                  })
                  .findFirst()
                  .orElse(null);

          if ("ADD".equals(operationType)) {
            if (sidenavLinkPresent != null) {
              allErrors.add(String.format("Sidenav link already present for %s", path));
            }
            Boolean addByDefault = page.isAddByDefault();
            if (addByDefault != null && addByDefault && sidenavLinkPresent == null) {
              Map<String, Object> newItem = new LinkedHashMap<>();
              newItem.put("name", "SidebarItem");
              Map<String, Object> props = new LinkedHashMap<>();
              props.put("to", path);
              props.put("text", title);
              newItem.put("props", props);
              children.add(newItem);
            }
          } else if ("REMOVE".equals(operationType)) {
            if (sidenavLinkPresent != null) {
              children.remove(sidenavLinkPresent);
            }
          }
        }

        sidenavEntity.setYaml(new Yaml().dump(sideNavJson));
        layoutEntityRepository.save(sidenavEntity);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("message", "success");
    result.put("warningMessages", allErrors);
    return Response.ok(result).build();
  }

  @Override
  public Response getV4(String harnessAccount) {
    List<LayoutEntity> layoutEntities = layoutEntityRepository.findAllByAccountIdentifier(harnessAccount);

    List<String> entityKinds = new ArrayList<>();
    for (LayoutEntity entity : layoutEntities) {
      entityKinds.add(entity.getEntityKind());
    }

    List<KindEntity> kindEntities = kindEntityRepository.findAllByAccountIdentifierInAndIdentifierIn(
        List.of(GLOBAL_ACCOUNT_ID, harnessAccount), entityKinds);
    Map<String, KindEntity> kindEntityMap = kindEntities.stream().collect(
        Collectors.toMap(k -> k.getAccountIdentifier() + ":" + k.getIdentifier(), Function.identity(), (a, b) -> a));

    LayoutEntity defaultLayout =
        layoutEntities.stream().filter(layout -> "default".equals(layout.getName())).findFirst().orElse(null);
    LayoutEntity sideNavLayout =
        layoutEntities.stream().filter(layout -> "sidenav".equals(layout.getName())).findFirst().orElse(null);

    Map<String, EntityKindLayout> newModifiedConfig = new LinkedHashMap<>();
    layoutEntities.forEach(config -> {
      if ("sidenav".equals(config.getName())) {
        return;
      }
      boolean isTypeDefault = isEmpty(config.getEntityType()) || "default".equals(config.getEntityType());
      String entityKind = config.getEntityKind();

      KindEntity kindEntity = kindEntityMap.get(harnessAccount + ":" + entityKind);
      if (kindEntity == null) {
        kindEntity = kindEntityMap.get(GLOBAL_ACCOUNT_ID + ":" + entityKind);
      }

      if (!newModifiedConfig.containsKey(entityKind)) {
        newModifiedConfig.put(entityKind,
            EntityKindLayout.builder()
                .kind(entityKind)
                .kindIcon(kindEntity != null ? kindEntity.getIcon() : "")
                .customKind(kindEntity != null && kindEntity.getKindType().equals(KindType.CUSTOM))
                .displayName(config.getDisplayName())
                .overrides(new ArrayList<>())
                .defaultLayout(defaultLayout)
                .build());
      }

      if (isTypeDefault) {
        newModifiedConfig.get(entityKind).setDefaultLayout(config);
      } else {
        newModifiedConfig.get(entityKind).getOverrides().add(config);
      }
    });

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("entityLayouts", new ArrayList<>(newModifiedConfig.values()));
    response.put("sidenavLayout", sideNavLayout);
    response.put("defaultLayout", defaultLayout);
    response.put("newModifiedConfig", newModifiedConfig);
    return Response.ok(Collections.singletonMap("response", response)).build();
  }
}
