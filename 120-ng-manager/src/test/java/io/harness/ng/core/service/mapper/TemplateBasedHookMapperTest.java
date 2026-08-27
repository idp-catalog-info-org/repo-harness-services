/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.rule.OwnerRule.LOKESH_BIHANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.hooks.ServiceHookAction;
import io.harness.unified.cd.service.hooks.ServiceHookConfig;
import io.harness.unified.cd.service.hooks.ServiceHookType;
import io.harness.unified.cd.service.manifests.StoreType;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TemplateBasedHookMapperTest extends CategoryTest {
  private TemplateBasedHookMapper hookMapper;

  @Before
  public void setUp() {
    hookMapper = new TemplateBasedHookMapper();
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_withNullInput() {
    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(null);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_withPreHook() {
    ServiceHook preHook =
        ServiceHook.builder()
            .identifier("myPreHook")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    ServiceHookWrapper wrapper = ServiceHookWrapper.builder().preHook(preHook).build();

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(List.of(wrapper));

    assertThat(result).hasSize(1);
    ServiceHookConfig config = result.get(0);
    assertThat(config.getIdentifier()).isEqualTo("myPreHook");
    assertThat(config.getType()).isEqualTo(ServiceHookType.PRE_HOOK);
    assertThat(config.getActions()).containsExactly(ServiceHookAction.FETCH_FILES);
    assertThat(config.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(config.getOrder()).isEqualTo(1);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_withPostHook() {
    ServiceHook postHook =
        ServiceHook.builder()
            .identifier("myPostHook")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    ServiceHookWrapper wrapper = ServiceHookWrapper.builder().postHook(postHook).build();

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(List.of(wrapper));

    assertThat(result).hasSize(1);
    ServiceHookConfig config = result.get(0);
    assertThat(config.getIdentifier()).isEqualTo("myPostHook");
    assertThat(config.getType()).isEqualTo(ServiceHookType.POST_HOOK);
    assertThat(config.getActions()).containsExactly(ServiceHookAction.FETCH_FILES);
    assertThat(config.getOrder()).isEqualTo(1);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_withMultipleActions() {
    ServiceHook hook =
        ServiceHook.builder()
            .identifier("multiActionHook")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES,
                io.harness.cdng.hooks.ServiceHookAction.TEMPLATE_MANIFEST))
            .build();

    ServiceHookWrapper wrapper = ServiceHookWrapper.builder().preHook(hook).build();

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(List.of(wrapper));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getActions())
        .containsExactly(ServiceHookAction.FETCH_FILES, ServiceHookAction.TEMPLATE_MANIFEST);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_withMultipleWrappers() {
    ServiceHook preHook =
        ServiceHook.builder()
            .identifier("preHook1")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    ServiceHook postHook =
        ServiceHook.builder()
            .identifier("postHook1")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    List<ServiceHookWrapper> wrappers = List.of(
        ServiceHookWrapper.builder().preHook(preHook).build(), ServiceHookWrapper.builder().postHook(postHook).build());

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(wrappers);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getType()).isEqualTo(ServiceHookType.PRE_HOOK);
    assertThat(result.get(0).getOrder()).isEqualTo(1);
    assertThat(result.get(1).getType()).isEqualTo(ServiceHookType.POST_HOOK);
    assertThat(result.get(1).getOrder()).isEqualTo(2);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_nullWrapperIsFiltered() {
    ServiceHook hook =
        ServiceHook.builder()
            .identifier("validHook")
            .storetype(StoreConfigType.INLINE)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    List<ServiceHookWrapper> wrappers = Arrays.asList(null, ServiceHookWrapper.builder().preHook(hook).build());

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(wrappers);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIdentifier()).isEqualTo("validHook");
    assertThat(result.get(0).getOrder()).isEqualTo(1);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_nullStoreTypeDefaultsToInline() {
    ServiceHook hook =
        ServiceHook.builder()
            .identifier("defaultStoreType")
            .storetype(null)
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("echo hello")).build())
            .actions(Arrays.asList(io.harness.cdng.hooks.ServiceHookAction.FETCH_FILES))
            .build();

    ServiceHookWrapper wrapper = ServiceHookWrapper.builder().preHook(hook).build();

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(List.of(wrapper));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStoreType()).isEqualTo(StoreType.INLINE);
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void testToUnifiedHooks_wrapperWithNullHookIsFiltered() {
    ServiceHookWrapper wrapper = ServiceHookWrapper.builder().preHook(null).postHook(null).build();

    List<ServiceHookConfig> result = hookMapper.toUnifiedHooks(List.of(wrapper));

    assertThat(result).isEmpty();
  }
}
