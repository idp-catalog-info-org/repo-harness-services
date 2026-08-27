/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolved unified config-file store (git vs Harness secret files) built from {@code ConfigFile#getInputs()}. */
@OwnedBy(HarnessTeam.CI)
public interface FileStoreSpec {
  default boolean isGit() {
    return this instanceof GitFileStoreSpec;
  }

  default boolean isHarness() {
    return this instanceof HarnessFileStoreSpec;
  }

  /**
   * Accepts either a JSON/YAML array of strings or a single string (matches legacy {@code fromInputsMap} behavior).
   */
  class SingleOrListOfStringsDeserializer extends JsonDeserializer<List<String>> {
    @Override
    @SuppressWarnings("unchecked")
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      JsonToken token = p.currentToken();
      if (token == JsonToken.VALUE_NULL) {
        return Collections.emptyList();
      }
      if (token == JsonToken.VALUE_STRING) {
        String s = p.getText();
        return isEmpty(s) ? Collections.emptyList() : List.of(s);
      }
      if (token == JsonToken.START_ARRAY) {
        List<?> raw = ctxt.readValue(p, List.class);
        if (raw == null) {
          return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object o : raw) {
          if (o != null) {
            out.add(o.toString());
          }
        }
        return out;
      }
      Object v = ctxt.readValue(p, Object.class);
      if (v == null) {
        return Collections.emptyList();
      }
      return isEmpty(v.toString()) ? Collections.emptyList() : List.of(v.toString());
    }
  }
}
