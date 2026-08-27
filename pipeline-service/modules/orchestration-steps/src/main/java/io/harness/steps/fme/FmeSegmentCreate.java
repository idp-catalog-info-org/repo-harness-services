/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.Segment;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.enums.SegmentType;

import io.split.client.dtos.URN;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeSegmentCreate extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_SEGMENT_CREATE_STEP_TYPE;
  private static final String STEP_NAME = "FME Segment Create";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_SEGMENT_CREATE_STEP...");
    logCallback.saveExecutionLog("Starting FME Segment Create", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    // Extract spec parameters
    FmeSegmentCreateParameters p = (FmeSegmentCreateParameters) stepParameters.getSpec();
    String segmentName = p.getName() != null ? p.getName().obtainValue() : null;
    String trafficType = p.getTrafficType() != null ? p.getTrafficType().obtainValue() : null;
    String segmentType = p.getSegmentType() != null ? p.getSegmentType().obtainValue() : null;

    // Optional fields
    String description = Optional.ofNullable(p.getDescription()).map(ParameterField::obtainValue).orElse(null);

    // Log inputs
    logCallback.saveExecutionLog(
        format("FME Segment Create Inputs -> "
                + "account: %s, org: %s, project: %s, segment: %s, trafficType: %s, segmentType: %s",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segmentName,
            trafficType, segmentType),
        LogLevel.INFO);

    // Validate required inputs
    assertNotEmpty(segmentName, "Missing required parameter: segment name");
    assertNotEmpty(trafficType, "Missing required parameter: traffic type");
    assertNotEmpty(segmentType, "Missing required parameter: segment type");

    // Convert segmentType string to enum
    SegmentType segmentTypeEnum = SegmentType.fromValue(segmentType);

    // Owners are optional - backend will auto-populate with admin team if empty
    List<String> owners = Optional.ofNullable(p.getOwners()).map(ParameterField::obtainValue).orElse(List.of());
    owners = fmeOwnerResolver.resolveOwners(owners);
    List<URN> ownerUrns = FmeOwnerHelper.parseOwners(owners);

    List<String> tags = Optional.ofNullable(p.getTags()).map(ParameterField::obtainValue).orElse(List.of());
    List<URN> tagUrns = tags.stream()
                            .map(t -> {
                              URN tag = new URN();
                              tag.type = "Tag";
                              tag.name = t;
                              return tag;
                            })
                            .toList();

    URN trafficTypeUrn = new URN();
    trafficTypeUrn.type = "TrafficType";
    trafficTypeUrn.name = trafficType;

    Segment segment = Segment.builder()
                          .name(segmentName)
                          .description(description)
                          .trafficType(trafficTypeUrn)
                          .tags(tagUrns)
                          .owners(ownerUrns)
                          .segmentType(segmentTypeEnum.getFmeType())
                          .addDefaultDefinition(true)
                          .build();

    createSegment(logCallback, scope, segment);

    logCallback.saveExecutionLog(
        format("FME Segment Created: segment: %s", segment.getName()), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME, format("created segment %s", segmentName));
  }

  protected Segment createSegment(NGLogCallback logCallback, Scope scope, Segment segment) {
    logCallback.saveExecutionLog(format("Calling FME API to create segment '%s'", segment.getName()), LogLevel.INFO);

    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(segment.getName()).operationName("create").build();

    return FmeApiExecutor.execute(fmePipelineClient.createSegment(scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flagName, env, errorBody) -> format("FME Segment creation request failed. Error: %s", errorBody));
  }
}
