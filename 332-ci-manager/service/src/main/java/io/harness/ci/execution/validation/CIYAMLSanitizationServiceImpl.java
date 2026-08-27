/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.beans.steps.CIStepInfoType.RUN;
import static io.harness.beans.steps.CIStepInfoType.RUN_TESTS;
import static io.harness.beans.steps.CIStepInfoType.TESTV2;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestStepV2Info;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIYAMLSanitizationServiceImpl implements CIYAMLSanitizationService {
  @Inject private CIMiningPatternJob ciMiningPatternJob;
  @Inject
  public CIYAMLSanitizationServiceImpl() {}

  @Override
  public boolean validate(List<ExecutionWrapperConfig> wrapper) {
    Set<String> maliciousMiningPatterns = ciMiningPatternJob.getMaliciousMiningPatterns();

    for (ExecutionWrapperConfig executionWrapper : wrapper) {
      if (executionWrapper.getStep() == null || executionWrapper.getStep().isNull()) {
        continue;
      }
      String command = null;
      if (HarnessYamlVersion.isV1(executionWrapper.getVersion())) {
        StepNodeV1 stepNodeV1 = IntegrationStageUtils.getStepNodeV1(executionWrapper);
        if (stepNodeV1.getRun() != null && stepNodeV1.getRun().getValue().getScript() != null) {
          ParameterField<String> commandParam = stepNodeV1.getRun().getValue().getScript();
          if (defaultCommand(commandParam)) {
            continue;
          }
          command = commandParam.getValue();
        } else if (stepNodeV1.getBackground() != null && stepNodeV1.getBackground().getValue().getScript() != null) {
          ParameterField<String> commandParam = stepNodeV1.getBackground().getValue().getScript();
          if (defaultCommand(commandParam)) {
            continue;
          }
          command = commandParam.getValue();
        } else if (stepNodeV1.getRunTest() != null && stepNodeV1.getRunTest().getValue().getScript() != null) {
          ParameterField<String> commandParam = stepNodeV1.getRunTest().getValue().getScript();
          if (defaultCommand(commandParam)) {
            continue;
          }
          command = commandParam.getValue();
        }
      } else {
        CIAbstractStepNode abstractNode = IntegrationStageUtils.getStepNode(executionWrapper);
        if (abstractNode == null || !(abstractNode.getStepSpecType() instanceof CIStepInfo)) {
          continue;
        }

        CIStepInfo ciStepInfo = (CIStepInfo) abstractNode.getStepSpecType();

        if (ciStepInfo.getNonYamlInfo().getStepInfoType() == RUN) {
          ParameterField<String> commandParam = ((RunStepInfo) ciStepInfo).getCommand();
          if (defaultCommand(commandParam)) {
            continue;
          }
          command = commandParam.getValue();
        } else if (ciStepInfo.getNonYamlInfo().getStepInfoType() == RUN_TESTS) {
          ParameterField<String> preCommand = ((RunTestsStepInfo) ciStepInfo).getPreCommand();
          ParameterField<String> postCommand = ((RunTestsStepInfo) ciStepInfo).getPostCommand();
          if (defaultCommand(preCommand) && defaultCommand(postCommand)) {
            continue;
          }
          command = preCommand.getValue() + " " + postCommand.getValue();
        } else if (ciStepInfo.getNonYamlInfo().getStepInfoType() == TESTV2) {
          ParameterField<String> commandParam = ((RunTestStepV2Info) ciStepInfo).getCommand();
          if (defaultCommand(commandParam)) {
            continue;
          }
          command = commandParam.getValue();
        }
      }
      if (command == null) {
        continue;
      }

      for (String maliciousKeyword : maliciousMiningPatterns) {
        if (command.contains(maliciousKeyword)) {
          log.error("Malicious keyword: \"{}\", detected in command: \"{}\"", maliciousKeyword, command);
          throw new CIStageExecutionException("Invalid step - Malicious activity detected");
        }
      }
    }
    return true;
  }

  private boolean defaultCommand(ParameterField<String> command) {
    if (command != null && command.getValue() != null) {
      return command.getValue().equals(command.getDefaultValue());
    }
    // when command is null we treat it as default and no further validation required
    return true;
  }
}
