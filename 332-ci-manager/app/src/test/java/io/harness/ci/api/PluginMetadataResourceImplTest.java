/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.api.PluginMetadataResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.plugin.PluginMetadataService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class PluginMetadataResourceImplTest {
  @Mock private PluginMetadataService pluginMetadataService;
  @InjectMocks private PluginMetadataResourceImpl pluginMetadataResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testList() {
    int page = 0;
    int size = 10;
    String searchTerm = "docker";
    String kind = "plugin";

    PluginMetadataResponse pluginResponse =
        PluginMetadataResponse.builder().name("docker").description("Docker plugin").kind("plugin").build();
    PageResponse<PluginMetadataResponse> pageResponse = PageResponse.<PluginMetadataResponse>builder()
                                                            .content(Collections.singletonList(pluginResponse))
                                                            .pageSize(size)
                                                            .pageIndex(page)
                                                            .totalItems(1L)
                                                            .totalPages(1L)
                                                            .build();
    when(pluginMetadataService.listPlugins(searchTerm, kind, page, size)).thenReturn(pageResponse);

    ResponseDTO<PageResponse<PluginMetadataResponse>> response =
        pluginMetadataResource.list(page, size, searchTerm, kind);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(1);
    assertThat(response.getData().getContent().get(0).getName()).isEqualTo("docker");
    verify(pluginMetadataService).listPlugins(searchTerm, kind, page, size);
  }
}
