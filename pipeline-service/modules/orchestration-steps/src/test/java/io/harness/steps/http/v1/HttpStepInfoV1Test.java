/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http.v1;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpStepInfoV1Test extends CategoryTest {
  private static final String url = "https://www.google.com/";
  private static final String method = "GET";
  private static final String certRef = "certRef";
  private static final String certKeyRef = "certKeyRef";
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testExtractSecretRefs() {
    HttpStepInfoV1 httpStepInfoV1 = new HttpStepInfoV1(
        null, null, null, ParameterField.createValueField(certRef), ParameterField.createValueField(certKeyRef));
    httpStepInfoV1.setUrl(ParameterField.createValueField(url));
    httpStepInfoV1.setMethod(ParameterField.createValueField(method));
    Map<String, ParameterField<String>> secretRefs = httpStepInfoV1.extractSecretRefs();
    assertThat(secretRefs)
        .hasSize(2)
        .containsEntry(YAMLFieldNameConstants.CERTIFICATE_V1, ParameterField.createValueField(certRef))
        .containsEntry(YAMLFieldNameConstants.CERTIFICATE_KEY_V1, ParameterField.createValueField(certKeyRef));
    httpStepInfoV1 = new HttpStepInfoV1(null, null, null, ParameterField.createValueField(certRef), null);
    secretRefs = httpStepInfoV1.extractSecretRefs();
    assertThat(secretRefs)
        .hasSize(1)
        .containsEntry(YAMLFieldNameConstants.CERTIFICATE_V1, ParameterField.createValueField(certRef));
  }
}
