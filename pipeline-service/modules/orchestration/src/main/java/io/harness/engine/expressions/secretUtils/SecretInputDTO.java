/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonSubTypes({
  @JsonSubTypes.Type(value = SSHSecretInputDTO.class, name = "ssh")
  , @JsonSubTypes.Type(value = WinrmSecretInputDTO.class, name = "winrm")
})
@Data
@SuperBuilder
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "secretType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
public abstract class SecretInputDTO {
  String id;
  Scope scope;
}