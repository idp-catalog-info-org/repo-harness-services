/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.PipelinePatchRequestBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelinePatchRequestBodyMixinTest extends CategoryTest {
  private ObjectMapper objectMapper;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    PipelinePatchRequestBodyMixin.configure(objectMapper);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldPreserveOmittedDescription() throws Exception {
    PipelinePatchRequestBody requestBody = objectMapper.readValue("{}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getDesc()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldConvertExplicitNullDescriptionToClearMarker() throws Exception {
    PipelinePatchRequestBody requestBody = objectMapper.readValue("{\"desc\":null}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getDesc()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldPreserveDescriptionValue() throws Exception {
    PipelinePatchRequestBody requestBody =
        objectMapper.readValue("{\"desc\":\"new description\"}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getDesc()).isEqualTo("new description");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldPreserveOmittedTags() throws Exception {
    PipelinePatchRequestBody requestBody = objectMapper.readValue("{}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getTags()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldConvertExplicitNullTagsToClearMarker() throws Exception {
    PipelinePatchRequestBody requestBody = objectMapper.readValue("{\"tags\":null}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getTags()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void shouldPreserveTagsValue() throws Exception {
    PipelinePatchRequestBody requestBody =
        objectMapper.readValue("{\"tags\":{\"k\":\"v\"}}", PipelinePatchRequestBody.class);

    assertThat(requestBody.getTags()).containsEntry("k", "v");
  }
}
