/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class MetadataSerializerTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock JsonGenerator jsonGenerator;
  @Mock SerializerProvider serializerProvider;
  @InjectMocks MetadataSerializer metadataSerializer;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSerialize() throws IOException {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("uid", "uid");
    Map<String, Object> annotations = new HashMap<>();
    annotations.put("backstage.io/managed-by-location", "value1");
    annotations.put("backstage.io/custom-annotation", "value2");
    metadata.put("annotations", annotations);

    metadataSerializer.serialize(metadata, jsonGenerator, serializerProvider);

    verify(jsonGenerator).writeStartObject();
    verify(jsonGenerator)
        .writeObjectField(eq("annotations"),
            argThat(filtered
                -> filtered instanceof Map && ((Map<?, ?>) filtered).size() == 1
                    && ((Map<?, ?>) filtered).containsKey("backstage.io/custom-annotation")
                    && !((Map<?, ?>) filtered).containsKey("backstage.io/managed-by-location")));
    verify(jsonGenerator).writeEndObject();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
