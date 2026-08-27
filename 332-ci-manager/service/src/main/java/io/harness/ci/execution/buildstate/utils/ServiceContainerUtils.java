/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.ID_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.IMAGE_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.SERVICE_ARG_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.WIN_STEP_COMMAND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CI)
public class ServiceContainerUtils {
  @Inject private HarnessImageUtils harnessImageUtils;

  public static List<String> getCommand(OSType os) {
    String cmd = UNIX_STEP_COMMAND;
    if (os == OSType.Windows) {
      cmd = WIN_STEP_COMMAND;
    }

    List<String> command = new ArrayList<>();
    command.add(cmd);
    return command;
  }

  public static List<String> getArguments(String serviceID, String image, Integer port) {
    List<String> args = new ArrayList<>();
    args.add(SERVICE_ARG_COMMAND);
    args.add(ID_PREFIX);
    args.add(serviceID);
    args.add(IMAGE_PREFIX);
    args.add(image);

    args.add(PORT_PREFIX);
    args.add(port.toString());
    return args;
  }

  public String getResolvedImagePullPolicy(CIServiceInfo service, IntegrationStageNode stageNode, Ambiance ambiance) {
    String imagePullPolicy = RunTimeInputHandler.resolveImagePullPolicy(service.getImagePullPolicy());
    if (Objects.isNull(stageNode) || Objects.isNull(stageNode.getIntegrationStageConfig())
        || !StringUtils.isBlank(imagePullPolicy)) {
      return imagePullPolicy;
    }
    return harnessImageUtils.getUpdatedImagePullPolicyBasedOnAmbiance(
        imagePullPolicy, stageNode.getIntegrationStageConfig().getInfrastructure(), ambiance);
  }
}
