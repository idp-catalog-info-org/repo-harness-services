/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.utils.RuntimeInputFileUtils.getFilePath;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.security.dto.PrincipalType.USER;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedServiceAccount;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.user.UserInfo;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.pipeline.FileDownloadResponseDTO;
import io.harness.pms.pipeline.FilesUploadExecutionDetailsDTO;
import io.harness.pms.pipeline.dto.FileMetadata;
import io.harness.pms.pipeline.mappers.InputFileMapper;
import io.harness.pms.pipeline.service.helper.PipelineLogContextHelper;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.utils.RuntimeInputFileUtils;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.RuntimeFileInputDataRepository;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.steps.upload.FileInfo;
import io.harness.steps.upload.FileInfo.FileInfoKeys;
import io.harness.steps.upload.RuntimeFileInputData;
import io.harness.steps.upload.RuntimeFileInputData.RuntimeFileInputDataKeys;
import io.harness.user.remote.UserClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class InputFileServiceImpl implements InputFileService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject ServiceAccountClient serviceAccountClient;
  @Inject CurrentUserHelper currentUserHelper;
  @Inject UserClient userClient;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccessControlClient accessControlClient;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Nullable @Inject @Named("FileInputObjectStoreClient") private ObjectStoreClient objectStoreClient;

  @Inject private RuntimeFileInputDataRepository runtimeFileInputDataRepository;
  public static final int MAX_NUMBER_OF_FILES_PER_NODE_EXECUTION_ID = 3;
  private static final String DEFAULT_MIME_TYPE = "application/json";

  @Override
  public boolean deleteFile(String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName) {
    try (AutoLogContext logContext = new AutoLogContext(
             PipelineLogContextHelper.getContextMap(accountIdentifier, planExecutionId, nodeExecutionId, fileName),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      validateFileUploadPermissions(accountIdentifier, planExecutionId, nodeExecutionId);
      validateObjectStoreClient();
      List<String> objectPaths = new ArrayList<>();
      String objectPath = getFilePath(accountIdentifier, planExecutionId, nodeExecutionId, fileName);
      objectPaths.add(objectPath);
      boolean isObjectDeleted;
      try {
        isObjectDeleted = objectStoreClient.deleteObjectsByPaths(objectPaths).get(objectPath);
      } catch (Exception ex) {
        log.error(String.format("Error in deleting the file {%s} from object store.", objectPath), ex);
        throw new InternalServerErrorException("We could not delete the requested file. Please try again, or contact "
                + "Harness support if the issue persists.",
            ex);
      }
      if (!isObjectDeleted) {
        log.error(String.format("Error in deleting the file {%s} from object store", objectPath));
        throw new InternalServerErrorException("We could not delete the requested file. Please try again, or contact "
            + "Harness support if the issue persists.");
      }
      log.info(String.format("File {%s} has been successfully deleted from the object store", objectPath));
      // Find and update the RuntimeFileInputData and update the list of fileIdentifiers
      Update updateOps = new Update();
      updateOps.pull(RuntimeFileInputDataKeys.fileInfos, query(Criteria.where(FileInfoKeys.filePath).is(objectPath)));
      Query query = new Query(Criteria.where(RuntimeFileInputDataKeys.nodeExecutionId).is(nodeExecutionId));
      try {
        runtimeFileInputDataRepository.update(query, updateOps);
      } catch (Exception ex) {
        log.error(String.format(
                      "Error in updating the file {%s} associated to the node {%s} in DB", objectPath, nodeExecutionId),
            ex);
      }
      return isObjectDeleted;
    }
  }

  @Override
  public void deleteFilesForAllExecutions(
      Set<String> planExecutionIds, Boolean retainPipelineExecutionDetailsAfterDelete) {
    if (objectStoreClient == null) {
      log.info("Cannot perform the requested operation as ObjectStore settings are not enabled. Please configure the "
          + "settings or contact Harness support.");
      return;
    }
    if (!retainPipelineExecutionDetailsAfterDelete) {
      List<RuntimeFileInputData> filesToBeDeleted = findFileInfosByPlanExecutionIds(planExecutionIds);

      List<String> filePaths = new ArrayList<>();
      for (RuntimeFileInputData data : filesToBeDeleted) {
        if (data.getFileInfos() != null) {
          filePaths.addAll(data.getFileInfos().stream().map(FileInfo::getFilePath).collect(Collectors.toList()));
        }
      }
      try {
        Map<String, Boolean> isObjectDeleted = objectStoreClient.deleteObjectsByPaths(filePaths);

        List<String> failedPaths = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : isObjectDeleted.entrySet()) {
          String objectPath = entry.getKey();
          Boolean isDeleted = entry.getValue();

          if (Boolean.FALSE.equals(isDeleted)) {
            failedPaths.add(objectPath);
            log.error(String.format("Error in deleting the file {%s} from object store.", objectPath));
          }
        }

        if (!failedPaths.isEmpty()) {
          throw new InternalServerErrorException(
              String.format("We could not delete the following files: %s. Please try again, or contact Harness support "
                      + "if the issue persists.",
                  String.join(", ", failedPaths)));
        }
      } catch (Exception ex) {
        log.error("Failed to delete objects from the object store.", ex);
        throw ex;
      }

      try {
        runtimeFileInputDataRepository.deleteAllByPlanExecutionIdIn(planExecutionIds);
      } catch (Exception ex) {
        log.error(String.format("RuntimeFileInput instances could not be deleted from DB upon pipeline deletion"), ex);
        throw new InternalServerErrorException(
            String.format("RuntimeFileInput instances could not be deleted from DB upon pipeline deletion"));
      }
    }
  }
  @Override
  public RuntimeFileInputData resumeExecution(
      String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    try (AutoLogContext logContext = new AutoLogContext(
             PipelineLogContextHelper.getContextMap(accountIdentifier, planExecutionId, nodeExecutionId, null),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      validateFileUploadPermissions(accountIdentifier, planExecutionId, nodeExecutionId);
      RuntimeFileInputData runtimeFileInputData = runtimeFileInputDataRepository.findByNodeExecutionId(nodeExecutionId);
      if (runtimeFileInputData == null) {
        log.error(String.format("No File input data found nodeExecutionId {%s}", nodeExecutionId));
        throw new EntityNotFoundException(
            String.format("No File input data found for nodeExecutionId {%s}", nodeExecutionId));
      }
      if (!planExecutionId.equals(runtimeFileInputData.getPlanExecutionId())) {
        throw new InvalidRequestException(String.format(
            "The plan execution ID {%s} does not match the expected ID {%s} for the given node execution.",
            planExecutionId, runtimeFileInputData.getPlanExecutionId()));
      }
      if (isEmpty(runtimeFileInputData.getFileInfos())) {
        log.error("There are no files uploaded for the give node execution.");
        throw new InvalidRequestException("There are no files uploaded yet. Please upload files before proceeding.");
      }
      String correlationId = runtimeFileInputData.getUuid();
      Update updateOps = new Update();
      updateOps.set(RuntimeFileInputDataKeys.submittedBy, getEmbeddedUser());
      try {
        runtimeFileInputDataRepository.update(
            new Query(Criteria.where(RuntimeFileInputDataKeys.nodeExecutionId).is(nodeExecutionId)), updateOps);
      } catch (Exception ex) {
        log.error(String.format("Error in updating the data associated to the node {%s} in db", nodeExecutionId), ex);
      }
      waitNotifyEngine.doneWith(correlationId, null);
      return runtimeFileInputData;
    }
  }

  @Override
  public FileMetadata getMetadata(String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    try (AutoLogContext logContext = new AutoLogContext(
             PipelineLogContextHelper.getContextMap(accountIdentifier, planExecutionId, nodeExecutionId, null),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      validateFileUploadPermissions(accountIdentifier, planExecutionId, nodeExecutionId);
      RuntimeFileInputData runtimeFileInputData = runtimeFileInputDataRepository.findByNodeExecutionId(nodeExecutionId);
      if (runtimeFileInputData == null) {
        throw new EntityNotFoundException(
            String.format("No File input data found for nodeExecutionId [%s]", nodeExecutionId));
      }
      return FileMetadata.builder()
          .accountIdentifier(runtimeFileInputData.getAccountIdentifier())
          .planExecutionId(runtimeFileInputData.getPlanExecutionId())
          .nodeExecutionId(runtimeFileInputData.getNodeExecutionId())
          .fileInfos(InputFileMapper.getFileInfoResponse(runtimeFileInputData.getFileInfos()))
          .build();
    }
  }

  @Override
  public FileDownloadResponseDTO getFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName) {
    validateFileUploadPermissions(accountIdentifier, planExecutionId, nodeExecutionId);
    String mimeType = DEFAULT_MIME_TYPE;
    try {
      mimeType = getMIMEType(fileName);
    } catch (Exception ex) {
      log.error(
          String.format("Unexpected error occurred while getting the filetype from the fileName {}", fileName), ex);
      throw new InternalServerErrorException(
          "Unexpected error occurred while getting the filetype. Please contact Harness Support.");
    }
    String filePath = getFilePath(accountIdentifier, planExecutionId, nodeExecutionId, fileName);
    return FileDownloadResponseDTO.builder()
        .output(getFileInternal(accountIdentifier, nodeExecutionId, filePath))
        .mimeType(mimeType)
        .build();
  }

  @Override
  public FileDownloadResponseDTO getFile(String accountIdentifier, String filePath) {
    FilesUploadExecutionDetailsDTO filesUploadExecutionDetails =
        RuntimeInputFileUtils.extractExecutionDetailsFromFilePath(filePath);
    if (!Objects.equals(accountIdentifier, filesUploadExecutionDetails.getAccountIdentifier())) {
      log.error(String.format("The account ID '%s' does not match the expected ID {%s} for the given file path",
          accountIdentifier, filesUploadExecutionDetails.getAccountIdentifier()));
      throw new InvalidRequestException(
          String.format("The account ID '%s' does not match the expected ID {%s} for the given file path",
              accountIdentifier, filesUploadExecutionDetails.getAccountIdentifier()));
    }
    String mimeType = DEFAULT_MIME_TYPE;
    validateFileUploadPermissions(accountIdentifier, filesUploadExecutionDetails.getPlanExecutionId(),
        filesUploadExecutionDetails.getNodeExecutionId());
    try {
      mimeType = getMIMEType(filesUploadExecutionDetails.getFileName());
    } catch (Exception ex) {
      log.error(String.format("Unexpected error occurred while getting the filetype from the fileName {}",
                    filesUploadExecutionDetails.getFileName()),
          ex);
      throw new InternalServerErrorException(
          "Unexpected error occurred while getting the filetype. Please contact Harness Support.");
    }
    return FileDownloadResponseDTO.builder()
        .output(getFileInternal(accountIdentifier, filesUploadExecutionDetails.getNodeExecutionId(), filePath))
        .mimeType(mimeType)
        .build();
  }

  @Override
  public void uploadFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName, InputStream stream) {
    try (AutoLogContext logContext = new AutoLogContext(
             PipelineLogContextHelper.getContextMap(accountIdentifier, planExecutionId, nodeExecutionId, fileName),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      validateFileUploadPermissions(accountIdentifier, planExecutionId, nodeExecutionId);
      validateObjectStoreClient();
      RuntimeFileInputData fetchedRuntimeFileInputData =
          runtimeFileInputDataRepository.findByNodeExecutionId(nodeExecutionId);
      validateNumberOfFiles(fetchedRuntimeFileInputData);
      String filePath = getFilePath(accountIdentifier, planExecutionId, nodeExecutionId, fileName);
      StorageObject object = objectStoreClient.uploadObject(filePath, stream);
      log.info(String.format(
          "Successfully uploaded the file with fileName [%s] and filePath [%s] onto GCS", fileName, filePath));
      upsertRuntimeFileInputData(nodeExecutionId, filePath, object.getSize());
    }
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }
    Criteria planExecutionIdCriteria = Criteria.where(RuntimeFileInputDataKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(planExecutionIdCriteria);
    Update ops = new Update();
    ops.set(RuntimeFileInputDataKeys.validUntil, ttlDate);
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      mongoTemplate.updateMulti(query, ops, RuntimeFileInputData.class);
      return true;
    });
  }

  private void validateNumberOfFiles(RuntimeFileInputData fetchedRuntimeFileInputData) {
    if (fetchedRuntimeFileInputData != null && isNotEmpty(fetchedRuntimeFileInputData.getFileInfos())) {
      if (fetchedRuntimeFileInputData.getFileInfos().size() >= MAX_NUMBER_OF_FILES_PER_NODE_EXECUTION_ID) {
        log.error(String.format("For the given nodeExecutionId [%s] there are [%s] run time file uploaded.",
            fetchedRuntimeFileInputData.getNodeExecutionId(), fetchedRuntimeFileInputData.getFileInfos().size()));
        List<String> filePaths =
            fetchedRuntimeFileInputData.getFileInfos().stream().map(FileInfo::getFilePath).collect(Collectors.toList());
        throw new InvalidRequestException(String.format("Can upload max [%d] run time input files for a given node "
                + "execution. Pls delete few of the uploaded files [%s].",
            MAX_NUMBER_OF_FILES_PER_NODE_EXECUTION_ID, String.join(", ", filePaths)));
      }
    }
  }

  private void upsertRuntimeFileInputData(String nodeExecutionId, String filePath, Long size) {
    EmbeddedUser embeddedServiceAccount = getEmbeddedUser();
    Criteria criteria = new Criteria();
    criteria.and(RuntimeFileInputDataKeys.nodeExecutionId).is(nodeExecutionId);

    Update update = new Update();
    update.addToSet(RuntimeFileInputDataKeys.fileInfos,
        FileInfo.builder().filePath(filePath).uploadedBy(embeddedServiceAccount).size(size).build());
    runtimeFileInputDataRepository.upsert(new Query(criteria), update);
  }

  private EmbeddedUser getEmbeddedUser() {
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    if (!(USER.equals(principal.getType()) || SERVICE_ACCOUNT.equals(principal.getType()))) {
      // TODO: handle api key and service account approvals
      throw new InvalidRequestException(principal.getType() + " is not supported for Harness Approval Step yet");
    }

    if (USER.equals(principal.getType())) {
      String userId = principal.getName();
      Optional<UserInfo> userOptional = CGRestUtils.getResponse(userClient.getUserById(userId));
      if (!userOptional.isPresent()) {
        throw new InvalidRequestException(String.format("Invalid user: %s", userId));
      }
      UserInfo user = userOptional.get();
      return EmbeddedUser.builder().uuid(user.getUuid()).name(user.getName()).email(user.getEmail()).build();
    } else if (SERVICE_ACCOUNT.equals(principal.getType())) {
      ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
      String uniqueId = serviceAccountPrincipal.getUniqueId();
      ServiceAccountDTOInternal serviceAccount = null;
      if (EmptyPredicate.isNotEmpty(uniqueId)) {
        List<ServiceAccountDTOInternal> serviceAccounts =
            getResponse(serviceAccountClient.listServiceAccountsByUniqueIdInternal(
                serviceAccountPrincipal.getAccountId(), Arrays.asList(uniqueId)));
        serviceAccount = serviceAccounts.get(0);
      }
      if (serviceAccount == null) {
        throw new InvalidRequestException(
            String.format("Service account [%s] does not exist.", serviceAccountPrincipal.getName()));
      }
      return EmbeddedServiceAccount.builder()
          .uuid(serviceAccount.getUniqueIdInternal())
          .name(serviceAccount.getName())
          .email(serviceAccount.getEmail())
          .accountIdentifier(serviceAccount.getAccountIdentifier())
          .orgIdentifier(serviceAccount.getOrgIdentifier())
          .projectIdentifier(serviceAccount.getProjectIdentifier())
          .serviceAccountIdentifier(serviceAccount.getIdentifier())
          .build();
    }
    return EmbeddedUser.builder().build();
  }

  private void validateFileName(String nodeExecutionId, List<String> filePathsFromDb, String filePath) {
    if (!filePathsFromDb.contains(filePath)) {
      log.error(String.format("FileName [%s] is not present for the nodeExecutionId [%s] ", filePath, nodeExecutionId));
      throw new InvalidRequestException(
          String.format("Received invalid fileName [%s] for nodeExecutionId [%s]", filePath, nodeExecutionId));
    }
  }

  private void validateObjectStoreClient() {
    if (objectStoreClient == null) {
      throw new UnsupportedOperationException("Cannot perform the requested operation as ObjectStore settings are not "
          + "enabled. Please configure the settings or contact Harness support.");
    }
  }

  private StreamingOutput getFileInternal(String accountIdentifier, String nodeExecutionId, String filePath) {
    try (AutoLogContext logContext = new AutoLogContext(
             PipelineLogContextHelper.getContextMap(accountIdentifier, null, nodeExecutionId, filePath),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      validateObjectStoreClient();
      RuntimeFileInputData runtimeFileInputData = runtimeFileInputDataRepository.findByNodeExecutionId(nodeExecutionId);
      if (runtimeFileInputData == null) {
        throw new EntityNotFoundException(
            String.format("No File input data found for nodeExecutionId [%s]", nodeExecutionId));
      }

      if (isEmpty(runtimeFileInputData.getFileInfos())) {
        throw new InvalidRequestException(
            String.format("There are no files uploaded for the given node execution id {%s}", nodeExecutionId));
      }

      List<String> filePathsFromDb = runtimeFileInputData.getFileInfos().stream().map(FileInfo::getFilePath).toList();
      validateFileName(nodeExecutionId, filePathsFromDb, filePath);
      return output -> objectStoreClient.getObject(filePath, output);
    }
  }

  private String getMIMEType(String fileName) throws IOException {
    File file = new File(fileName);
    String mimeType = Files.probeContentType(file.toPath());
    if (mimeType == null) {
      // Fallback
      mimeType = DEFAULT_MIME_TYPE;
    }
    return mimeType;
  }

  private void validateFileUploadPermissions(String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    if (!pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT)) {
      log.error("File upload as runtime input is disabled for the account. Please enable the feature flag to proceed.");
      throw new InvalidRequestException("File upload as runtime input feature is not enabled for the account. Please "
          + "contact Harness support to enable this feature.");
    }
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance);
    if (nodeExecution == null) {
      log.error(String.format("No record found in nodeExecution for nodeExecutionId [%s].", nodeExecutionId));
      throw new EntityNotFoundException(
          String.format("No Execution details found for nodeExecutionId [%s].", nodeExecutionId));
    }
    if (!accountIdentifier.equals(nodeExecution.getAccountId())) {
      log.error(String.format("The account ID '%s' does not match the expected ID {%s} for the given plan execution.",
          nodeExecutionId, nodeExecution.getAccountId()));
      throw new InvalidRequestException(String.format(
          "Invalid request: The account ID '%s' does not match the expected ID for the given plan execution.",
          accountIdentifier));
    }
    if (!planExecutionId.equals(nodeExecution.getPlanExecutionId())) {
      log.error(
          String.format("The plan execution ID '%s' does not match the expected ID {%s} for the given node execution.",
              nodeExecutionId, nodeExecution.getPlanExecutionId()));
      throw new InvalidRequestException(String.format(
          "Invalid request: The plan execution ID '%s' does not match the expected ID for the given node execution.",
          planExecutionId));
    }
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(
            nodeExecution.getAccountId(), nodeExecution.getOrgIdentifier(), nodeExecution.getProjectIdentifier()),
        Resource.of("PIPELINE", nodeExecution.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  private List<RuntimeFileInputData> findFileInfosByPlanExecutionIds(Set<String> planExecutionIds) {
    Criteria criteria = new Criteria();
    criteria.and(RuntimeFileInputDataKeys.planExecutionId).in(planExecutionIds);
    return runtimeFileInputDataRepository.find(criteria);
  }
}
