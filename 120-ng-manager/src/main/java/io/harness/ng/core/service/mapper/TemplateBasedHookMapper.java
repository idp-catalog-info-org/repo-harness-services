/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.unified.cd.service.hooks.ServiceHookAction;
import io.harness.unified.cd.service.hooks.ServiceHookConfig;
import io.harness.unified.cd.service.hooks.ServiceHookStoreConfig;
import io.harness.unified.cd.service.hooks.ServiceHookType;
import io.harness.unified.cd.service.manifests.StoreType;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TemplateBasedHookMapper {
  public List<ServiceHookConfig> toUnifiedHooks(List<ServiceHookWrapper> hookWrappers) {
    if (isEmpty(hookWrappers)) {
      return new ArrayList<>();
    }

    List<ServiceHookConfig> result = new ArrayList<>();
    int order = 1;
    for (ServiceHookWrapper wrapper : hookWrappers) {
      ServiceHookConfig config = toUnifiedHookConfig(wrapper, order);
      if (config != null) {
        result.add(config);
        order++;
      }
    }
    return result;
  }

  private ServiceHookConfig toUnifiedHookConfig(ServiceHookWrapper wrapper, int order) {
    if (wrapper == null) {
      return null;
    }

    ServiceHook hook = wrapper.getHook();
    if (hook == null) {
      return null;
    }

    ServiceHookType type = wrapper.getPreHook() != null ? ServiceHookType.PRE_HOOK : ServiceHookType.POST_HOOK;

    List<ServiceHookAction> actions = hook.getActions() == null
        ? new ArrayList<>()
        : hook.getActions()
              .stream()
              .map(ngAction -> ServiceHookAction.fromString(ngAction.getDisplayName()))
              .collect(Collectors.toList());

    String content = extractContent(hook.getStore());

    return ServiceHookConfig.builder()
        .identifier(hook.getIdentifier())
        .type(type)
        .actions(actions)
        .storeType(StoreType.INLINE)
        .store(ServiceHookStoreConfig.builder().content(content).build())
        .order(order)
        .build();
  }

  private String extractContent(StoreConfig storeConfig) {
    if (storeConfig instanceof InlineStoreConfig inlineStore) {
      String value = ParameterFieldHelper.getParameterFieldFinalValueString(inlineStore.getContent());
      return value != null ? value : "";
    }
    throw new InvalidRequestException(
        String.format("Unsupported store type for service hook: %s. Only Inline store is supported.",
            storeConfig.getClass().getSimpleName()));
  }
}
