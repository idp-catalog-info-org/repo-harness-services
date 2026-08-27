/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.steps.beans.stepinfo;

import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.steps.stepinfo.PluginCompatibleStep;
import io.harness.filters.WithConnectorRef;
import io.harness.idp.steps.Constants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(Constants.REGISTER_CATALOG)
@TypeAlias("RegisterCatalogStepInfo")
@OwnedBy(HarnessTeam.IDP)
@Builder
@AllArgsConstructor
@RecasterAlias("io.harness.idp.pipeline.steps.beans.stepinfo.IdpRegisterCatalogStepInfo")
public class IdpRegisterCatalogStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;

  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> filePath;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> organization;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> repository;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> branch;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> workspace; // needed in case of bitbucket
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String> project; // needed in case of gitlab and bitbucket
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> groupPath;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> connectorType;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> harnessCodeRepoType;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> xApiKey;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> connectorRef;

  ContainerResource resources;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.REGISTER_CATALOG).build();
  }

  @Override
  public StepType getStepType() {
    return Constants.REGISTER_CATALOG_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<List<String>> getBaseImageConnectorRefs() {
    return new ParameterField<>();
  }
}
