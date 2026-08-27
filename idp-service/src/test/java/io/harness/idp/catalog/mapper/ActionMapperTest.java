/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionBuiltinConfig;
import io.harness.idp.catalog.entities.ActionHttpConfig;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ActionBuiltinConfigDTO;
import io.harness.spec.server.idp.v1.model.ActionCreateRequest;
import io.harness.spec.server.idp.v1.model.ActionHttpConfigDTO;
import io.harness.spec.server.idp.v1.model.ActionResponse;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ActionMapperTest extends CategoryTest {
  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void fromCreateRequest_mapsAllFields() {
    ActionCreateRequest request = new ActionCreateRequest();
    request.setIdentifier("my-action");
    request.setName("My Action");
    request.setDescription("desc");
    request.setVersion("1.0.0");
    request.setType(ActionCreateRequest.TypeEnum.HTTP);
    request.setCategory("infra");
    request.setTags(List.of("tag1"));
    request.setConnectorRef("connectorRef");
    request.setDelegateSelectors(List.of("sel1"));
    request.setInputSchema(Map.of("type", "object"));
    request.setOutputMapping(Map.of("out", "$.result"));

    Action action = ActionMapper.fromCreateRequest(request);

    assertThat(action.getIdentifier()).isEqualTo("my-action");
    assertThat(action.getName()).isEqualTo("My Action");
    assertThat(action.getDescription()).isEqualTo("desc");
    assertThat(action.getVersion()).isEqualTo("1.0.0");
    assertThat(action.getType()).isEqualTo(ActionType.HTTP);
    assertThat(action.getCategory()).isEqualTo("infra");
    assertThat(action.getTags()).containsExactly("tag1");
    assertThat(action.getConnectorRef()).isEqualTo("connectorRef");
    assertThat(action.getDelegateSelectors()).containsExactly("sel1");
    assertThat(action.getInputSchema()).containsEntry("type", "object");
    assertThat(action.getOutputMapping()).containsEntry("out", "$.result");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void fromCreateRequest_httpConfigMapped() {
    ActionHttpConfigDTO httpConfigDTO = new ActionHttpConfigDTO();
    httpConfigDTO.setMethod(ActionHttpConfigDTO.MethodEnum.POST);
    httpConfigDTO.setPath("/api/trigger");
    httpConfigDTO.setUrl("https://example.com");
    httpConfigDTO.setBody("{\"key\":\"val\"}");
    httpConfigDTO.setTimeoutMs(5000);
    httpConfigDTO.setExpectedStatusCodes(List.of("200", "201"));
    httpConfigDTO.setHeaders(Map.of("Content-Type", "application/json"));
    httpConfigDTO.setQueryParams(Map.of("env", "prod"));

    ActionCreateRequest request = new ActionCreateRequest();
    request.setIdentifier("http-action");
    request.setName("HTTP Action");
    request.setVersion("1.0.0");
    request.setType(ActionCreateRequest.TypeEnum.HTTP);
    request.setHttpConfig(httpConfigDTO);

    Action action = ActionMapper.fromCreateRequest(request);
    ActionHttpConfig config = action.getHttpConfig();

    assertThat(config).isNotNull();
    assertThat(config.getMethod()).isEqualTo("POST");
    assertThat(config.getPath()).isEqualTo("/api/trigger");
    assertThat(config.getUrl()).isEqualTo("https://example.com");
    assertThat(config.getBody()).isEqualTo("{\"key\":\"val\"}");
    assertThat(config.getTimeoutMs()).isEqualTo(5000);
    assertThat(config.getExpectedStatusCodes()).containsExactly("200", "201");
    assertThat(config.getHeaders()).containsEntry("Content-Type", "application/json");
    assertThat(config.getQueryParams()).containsEntry("env", "prod");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void fromCreateRequest_builtinConfigMapped() {
    ActionBuiltinConfigDTO builtinDTO = new ActionBuiltinConfigDTO();
    builtinDTO.setHandler("run-pipeline");

    ActionCreateRequest request = new ActionCreateRequest();
    request.setIdentifier("builtin-action");
    request.setName("Builtin Action");
    request.setVersion("1.0.0");
    request.setType(ActionCreateRequest.TypeEnum.BUILTIN);
    request.setBuiltinConfig(builtinDTO);

    Action action = ActionMapper.fromCreateRequest(request);

    assertThat(action.getBuiltinConfig()).isNotNull();
    assertThat(action.getBuiltinConfig().getHandler()).isEqualTo("run-pipeline");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void applyUpdate_replacesAllMutableFields() {
    Action existing = Action.builder()
                          .identifier("action")
                          .name("Old Name")
                          .description("Old Desc")
                          .version("1.0.0")
                          .status(ActionStatus.DRAFT)
                          .type(ActionType.HTTP)
                          .category("old-cat")
                          .tags(List.of("old-tag"))
                          .connectorRef("old-conn")
                          .build();

    ActionUpdateRequest request = new ActionUpdateRequest();
    request.setName("New Name");
    request.setDescription("New Desc");
    request.setCategory("new-cat");
    request.setTags(List.of("new-tag"));
    request.setConnectorRef("new-conn");

    ActionMapper.applyUpdate(request, existing);

    assertThat(existing.getName()).isEqualTo("New Name");
    assertThat(existing.getDescription()).isEqualTo("New Desc");
    assertThat(existing.getCategory()).isEqualTo("new-cat");
    assertThat(existing.getTags()).containsExactly("new-tag");
    assertThat(existing.getConnectorRef()).isEqualTo("new-conn");
    // Immutable fields unchanged
    assertThat(existing.getIdentifier()).isEqualTo("action");
    assertThat(existing.getVersion()).isEqualTo("1.0.0");
    assertThat(existing.getStatus()).isEqualTo(ActionStatus.DRAFT);
    assertThat(existing.getType()).isEqualTo(ActionType.HTTP);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void applyUpdate_clearsFieldsWhenAbsentInRequest() {
    Action existing = Action.builder()
                          .identifier("action")
                          .name("Name")
                          .version("1.0.0")
                          .status(ActionStatus.DRAFT)
                          .type(ActionType.HTTP)
                          .description("has desc")
                          .category("has cat")
                          .connectorRef("has conn")
                          .tags(List.of("tag1"))
                          .build();

    ActionUpdateRequest request = new ActionUpdateRequest();
    request.setName("Name");
    // description, category, connectorRef left null — PUT semantics clears them

    ActionMapper.applyUpdate(request, existing);

    assertThat(existing.getDescription()).isNull();
    assertThat(existing.getCategory()).isNull();
    assertThat(existing.getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void toResponse_mapsAllFields() {
    Action action = Action.builder()
                        .id("mongo-id")
                        .identifier("my-action")
                        .name("My Action")
                        .description("desc")
                        .version("1.0.0")
                        .status(ActionStatus.PUBLISHED)
                        .type(ActionType.HTTP)
                        .category("infra")
                        .tags(List.of("tag1"))
                        .connectorRef("connRef")
                        .delegateSelectors(List.of("sel1"))
                        .inputSchema(Map.of("type", "object"))
                        .outputMapping(Map.of("out", "$.result"))
                        .httpConfig(ActionHttpConfig.builder().method("GET").path("/ping").build())
                        .build();

    ActionResponse response = ActionMapper.toResponse(action);

    assertThat(response.getId()).isEqualTo("mongo-id");
    assertThat(response.getIdentifier()).isEqualTo("my-action");
    assertThat(response.getName()).isEqualTo("My Action");
    assertThat(response.getDescription()).isEqualTo("desc");
    assertThat(response.getVersion()).isEqualTo("1.0.0");
    assertThat(response.getStatus()).isEqualTo(ActionResponse.StatusEnum.PUBLISHED);
    assertThat(response.getType()).isEqualTo(ActionResponse.TypeEnum.HTTP);
    assertThat(response.getCategory()).isEqualTo("infra");
    assertThat(response.getTags()).containsExactly("tag1");
    assertThat(response.getConnectorRef()).isEqualTo("connRef");
    assertThat(response.getDelegateSelectors()).containsExactly("sel1");
    assertThat(response.getHttpConfig()).isNotNull();
    assertThat(response.getHttpConfig().getMethod()).isEqualTo(ActionHttpConfigDTO.MethodEnum.GET);
    assertThat(response.getHttpConfig().getPath()).isEqualTo("/ping");
    assertThat(response.getBuiltinConfig()).isNull();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void toResponse_nullConfigs_safe() {
    Action action = Action.builder()
                        .id("id")
                        .identifier("action")
                        .name("Action")
                        .version("1.0.0")
                        .status(ActionStatus.DRAFT)
                        .type(ActionType.HTTP)
                        .build();

    ActionResponse response = ActionMapper.toResponse(action);

    assertThat(response.getHttpConfig()).isNull();
    assertThat(response.getBuiltinConfig()).isNull();
  }
}
