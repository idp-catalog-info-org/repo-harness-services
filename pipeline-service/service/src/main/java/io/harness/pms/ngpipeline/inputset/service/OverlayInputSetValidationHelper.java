/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.OverlayInputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.yaml.HarnessYamlVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
@UtilityClass
public class OverlayInputSetValidationHelper {
  public void validateOverlayInputSet(PMSInputSetService inputSetService, InputSetEntity inputSetEntity,
      boolean isParentIdQueryingEnabled, ScopeInfo scopeInfo) {
    String accountId = inputSetEntity.getAccountId();
    // These deprecated fields can be removed because when isParentIdQueryingEnabled is true,
    // you do not validate yaml with organization and project identifiers.
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();

    String identifier = InputSetYamlHelper.getStringField(yaml, "identifier", "overlayInputSet");
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("Identifier cannot be empty");
    }
    if (identifier.length() > 127) {
      throw new InvalidRequestException("Overlay Input Set identifier length cannot be more that 127 characters.");
    }
    List<String> inputSetReferences = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml);
    if (inputSetReferences.isEmpty()) {
      throw new InvalidRequestException("Input Set References can't be empty");
    }

    InputSetYamlHelper.confirmPipelineIdentifierInOverlayInputSet(yaml, pipelineIdentifier);
    if (!isParentIdQueryingEnabled) {
      InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml, "overlayInputSet", orgIdentifier, projectIdentifier);
    }
    // orgIdentifier might be outdated here because of move to another project
    List<Optional<InputSetEntity>> inputSets = findAllReferredInputSets(inputSetService, inputSetReferences, accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
    Map<String, String> invalidReferences =
        InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, inputSetReferences);
    if (!invalidReferences.isEmpty()) {
      OverlayInputSetErrorWrapperDTOPMS overlayInputSetErrorWrapperDTOPMS =
          OverlayInputSetErrorWrapperDTOPMS.builder().invalidReferences(invalidReferences).build();
      throw new InvalidOverlayInputSetException(
          "Some fields in the Overlay Input Set are invalid.", overlayInputSetErrorWrapperDTOPMS, inputSetEntity);
    }
  }

  public void validateOverlayInputSetsForGivenInputSet(PMSInputSetService inputSetService,
      InputSetEntity validatedInputSet, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (validatedInputSet.getStoreType() != StoreType.INLINE
        || validatedInputSet.getInputSetEntityType() != InputSetEntityType.INPUT_SET) {
      return;
    }
    String accountId = validatedInputSet.getAccountId();
    String orgIdentifier = validatedInputSet.getOrgIdentifier();
    String projectIdentifier = validatedInputSet.getProjectIdentifier();
    String pipelineIdentifier = validatedInputSet.getPipelineIdentifier();
    Criteria criteriaOverlay = PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.OVERLAY_INPUT_SET,
        scopeInfo != null ? scopeInfo.getUniqueId() : null, isParentIdQueryingEnabled);
    List<InputSetEntity> allOverlayInputSets = inputSetService.list(criteriaOverlay);
    for (InputSetEntity overlayInputSet : allOverlayInputSets) {
      if (!overlayInputSet.getIsInvalid()) {
        continue;
      }
      List<String> inputSetReferences = getOverlayInputSetReferences(overlayInputSet);
      if (!inputSetReferences.contains(validatedInputSet.getIdentifier())) {
        continue;
      }
      List<Optional<InputSetEntity>> inputSets = findAllReferredInputSets(inputSetService, inputSetReferences,
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
      Map<String, String> invalidReferences =
          InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, inputSetReferences);
      if (invalidReferences.isEmpty()) {
        inputSetService.switchValidationFlag(overlayInputSet, false, isParentIdQueryingEnabled);
      }
    }
  }

  public void invalidateOverlayInputSetsReferringDeletedInputSet(PMSInputSetService inputSetService,
      InputSetEntity deletedInputSet, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (deletedInputSet.getStoreType() != StoreType.INLINE
        || deletedInputSet.getInputSetEntityType() != InputSetEntityType.INPUT_SET) {
      return;
    }
    String accountId = deletedInputSet.getAccountId();
    String orgIdentifier = deletedInputSet.getOrgIdentifier();
    String projectIdentifier = deletedInputSet.getProjectIdentifier();
    String pipelineIdentifier = deletedInputSet.getPipelineIdentifier();
    Criteria criteriaOverlay = PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.OVERLAY_INPUT_SET,
        scopeInfo != null ? scopeInfo.getUniqueId() : null, isParentIdQueryingEnabled);
    List<InputSetEntity> allOverlayInputSets = inputSetService.list(criteriaOverlay);
    for (InputSetEntity overlayInputSet : allOverlayInputSets) {
      if (overlayInputSet.getIsInvalid()) {
        continue;
      }
      List<String> inputSetReferences = getOverlayInputSetReferences(overlayInputSet);
      if (inputSetReferences.contains(deletedInputSet.getIdentifier())) {
        inputSetService.switchValidationFlag(overlayInputSet, true, isParentIdQueryingEnabled);
      }
    }
  }

  /**
   * Resolves overlay references using entity harnessVersion. V1 overlays use {@code spec.input_sets}; V0 uses
   * {@code overlayInputSet.inputSetReferences}. Prefers persisted {@code inputSetReferences} for non-remote V1
   * overlays when present; remote overlays always re-parse YAML.
   */
  List<String> getOverlayInputSetReferences(InputSetEntity overlayInputSet) {
    if (HarnessYamlVersion.isV1(overlayInputSet.getHarnessVersion())) {
      if (overlayInputSet.getStoreType() != StoreType.REMOTE
          && EmptyPredicate.isNotEmpty(overlayInputSet.getInputSetReferences())) {
        return overlayInputSet.getInputSetReferences();
      }
      return InputSetYamlHelper.getReferencesFromOverlayInputSetYamlV1(overlayInputSet.getYaml());
    }
    return InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(overlayInputSet.getYaml());
  }

  private List<Optional<InputSetEntity>> findAllReferredInputSets(PMSInputSetService inputSetService,
      List<String> referencesInOverlay, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      String repoIdentifier = GitContextHelper.getGitEntityInfo().getYamlGitConfigId();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder()
              .gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).yamlGitConfigId(repoIdentifier).build())
              .build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        return findAllReferredInputSetsMetadata(inputSetService, referencesInOverlay, accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
      }
    } else {
      return findAllReferredInputSetsMetadata(inputSetService, referencesInOverlay, accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
    }
  }

  // todo: optimise this
  private List<Optional<InputSetEntity>> findAllReferredInputSetsMetadata(PMSInputSetService inputSetService,
      List<String> referencesInOverlay, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<Optional<InputSetEntity>> inputSets = new ArrayList<>();
    referencesInOverlay.forEach(identifier -> {
      if (EmptyPredicate.isEmpty(identifier)) {
        throw new InvalidRequestException("Empty Input Set Identifier not allowed in Input Set References");
      }
      inputSets.add(inputSetService.getMetadataWithoutValidations(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, identifier, false, false, true, scopeInfo, isParentIdQueryingEnabled));
    });
    return inputSets;
  }
  public static InputSetYamlDiffDTO getYAMLDiffForOverlayInputSet(GitSyncSdkService gitSyncSdkService,
      PMSInputSetService inputSetService, InputSetEntity inputSetEntity, String pipelineYaml, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = (scopeInfo != null && scopeInfo.getOrgIdentifier() != null)
        ? scopeInfo.getOrgIdentifier()
        : inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String parentUniqueId = (scopeInfo != null) ? scopeInfo.getUniqueId() : null;
    String yaml = inputSetEntity.getYaml();

    List<String> currentReferences = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml);
    List<Optional<InputSetEntity>> inputSets = findAllReferredInputSetsMetadata(inputSetService, currentReferences,
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
    Map<String, String> invalidReferencesWithErrors =
        InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, currentReferences, pipelineYaml);
    List<String> existingButInvalidReferences =
        invalidReferencesWithErrors.keySet()
            .stream()
            .filter(ref
                -> invalidReferencesWithErrors.get(ref).equals(InputSetErrorsHelper.INVALID_INPUT_SET_MESSAGE)
                    || invalidReferencesWithErrors.get(ref).equals(InputSetErrorsHelper.OUTDATED_INPUT_SET_MESSAGE))
            .collect(Collectors.toList());
    Set<String> invalidReferences = invalidReferencesWithErrors.keySet();
    List<String> validReferences =
        currentReferences.stream().filter(ref -> !invalidReferences.contains(ref)).collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(validReferences)) {
      String newYaml = InputSetYamlHelper.setReferencesFromOverlayInputSetYaml(yaml, validReferences);
      return InputSetYamlDiffDTO.builder()
          .oldYAML(yaml)
          .newYAML(newYaml)
          .isInputSetEmpty(false)
          .noUpdatePossible(false)
          .invalidReferences(existingButInvalidReferences)
          .build();
    }

    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, InputSetListTypePMS.INPUT_SET, null, false, parentUniqueId, isParentIdQueryingEnabled);
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      criteria = criteria.and(InputSetEntityKeys.branch)
                     .is(gitEntityInfo.getBranch())
                     .and(InputSetEntityKeys.yamlGitConfigRef)
                     .is(gitEntityInfo.getYamlGitConfigId());
    }
    boolean hasInputSets = EmptyPredicate.isNotEmpty(inputSetService.list(criteria));
    if (hasInputSets) {
      return InputSetYamlDiffDTO.builder()
          .isInputSetEmpty(true)
          .noUpdatePossible(false)
          .invalidReferences(existingButInvalidReferences)
          .build();
    } else {
      return InputSetYamlDiffDTO.builder()
          .isInputSetEmpty(true)
          .noUpdatePossible(true)
          .invalidReferences(existingButInvalidReferences)
          .build();
    }
  }
}
