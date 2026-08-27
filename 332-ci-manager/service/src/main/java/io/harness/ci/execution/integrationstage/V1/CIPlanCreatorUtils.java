/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.beans.execution.DeleteType.BRANCH_DELETE;
import static io.harness.beans.execution.DeleteType.TAG_DELETE;
import static io.harness.beans.yaml.extended.volumes.ConfigMapVolumeYaml.ConfigMapVolumeYamlSpec;
import static io.harness.beans.yaml.extended.volumes.EmptyDirYaml.EmptyDirYamlSpec;
import static io.harness.beans.yaml.extended.volumes.HostPathYaml.HostPathYamlSpec;
import static io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml.PersistentVolumeClaimYamlSpec;
import static io.harness.beans.yaml.extended.volumes.SecretVolumeYaml.SecretVolumeYamlSpec;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MODULE_IMPLICIT_NODES_INFO;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.DeleteWebhookEvent;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.v1.CloneRef;
import io.harness.beans.steps.v1.CloneType;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.beans.Shell;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContextV1;
import io.harness.beans.yaml.extended.platform.V1.Arch;
import io.harness.beans.yaml.extended.platform.V1.OS;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeImageSpec;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeImageSpec.CloudRuntimeImageSpecBuilder;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec.CloudRuntimeSpecBuilder;
import io.harness.beans.yaml.extended.runtime.DockerRuntime;
import io.harness.beans.yaml.extended.runtime.DockerRuntime.DockerRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.ConfigMapVolumeYaml;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.beans.yaml.extended.volumes.SecretVolumeYaml;
import io.harness.beans.yaml.extended.volumes.V1.CIVolumeV1;
import io.harness.beans.yaml.extended.volumes.V1.ConfigMapVolumeYamlV1;
import io.harness.beans.yaml.extended.volumes.V1.EmptyDirYamlV1;
import io.harness.beans.yaml.extended.volumes.V1.HostPathYamlV1;
import io.harness.beans.yaml.extended.volumes.V1.PersistentVolumeClaimYamlV1;
import io.harness.beans.yaml.extended.volumes.V1.SecretVolumeYamlV1;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.utils.WebhookTriggerProcessorUtils;
import io.harness.ci.states.codebase.ScmGitRefManager;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.clone.Clone;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.CommitShaBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.TagBuildSpec;
import io.harness.yaml.options.Options;
import io.harness.yaml.repository.Repository;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIPlanCreatorUtils {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ScmGitRefManager scmGitRefManager;

  public Optional<CodeBase> getCodebase(PlanCreationContext ctx, GitCloneStepInfoV1 stageClone) {
    // If clone is not defined, it means no clone
    if (stageClone == null) {
      return Optional.empty();
    }

    if (ParameterField.isNotNull(stageClone.getEnabled()) && Boolean.FALSE.equals(stageClone.getEnabled().getValue())) {
      return Optional.empty();
    }
    // Build repository from clone configuration (repo is now part of clone)
    Repository repository =
        Repository.builder().connector(stageClone.getConnector()).name(stageClone.getRepo()).build();
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ctx.getAccountIdentifier())
                                .orgIdentifier(ctx.getOrgIdentifier())
                                .projectIdentifier(ctx.getProjectIdentifier())
                                .build();
    ParameterField<PRCloneStrategy> prCloneStrategyParameterField = ParameterField.ofNull();
    if (stageClone.getStrategy() != null) {
      prCloneStrategyParameterField = ParameterField.createValueField(stageClone.getStrategy().toPRCloneStrategy());
    }
    CodeBaseBuilder codeBaseBuilder =
        CodeBase.builder()
            .uuid(stageClone.getUuid() != null ? stageClone.getUuid() : generateUuid())
            .depth(stageClone.getDepth() != null ? stageClone.getDepth() : ParameterField.createValueField(50))
            .sslVerify(
                stageClone.getInsecure() != null ? stageClone.getInsecure() : ParameterField.createValueField(false))
            .prCloneStrategy(prCloneStrategyParameterField)
            .lfs(stageClone.getLfs())
            .debug(stageClone.getTrace())
            .fetchTags(stageClone.getTags())
            .submoduleStrategy(stageClone.getSubmodules())
            .sparseCheckout(stageClone.getSparseCheckout())
            .preFetchCommand(stageClone.getPreFetchCommand())
            .persistCredentials(stageClone.getPersistCredentials())
            .cloneDirectory(stageClone.getClonedir())
            .resources(stageClone.getResources())
            .runAsUser(stageClone.getUser());
    ParameterField<CloneRef> ref = ParameterField.ofNull();
    if (stageClone != null && stageClone.getRef() != null) {
      ref = stageClone.getRef();
    }

    PipelineStoreType pipelineStoreType = ctx.getPipelineStoreType();
    switch (pipelineStoreType) {
      case REMOTE:
        codeBaseBuilder = buildCodebaseForRemotePipeline(ctx, ngAccess, repository, ref, codeBaseBuilder);
        break;
      case INLINE:
        codeBaseBuilder = buildCodebaseForInlinePipeline(ctx, ngAccess, repository, ref, codeBaseBuilder);
        break;
      default:
        throw new InvalidRequestException("Invalid Pipeline Store Type : " + pipelineStoreType);
    }
    return Optional.of(codeBaseBuilder.build());
  }

  private ParameterField<OSType> toOSType(ParameterField<OS> os) {
    if (ParameterField.isNull(os) || (ParameterField.isNotNull(os) && os.getValue() == null)) {
      return ParameterField.createValueField(OS.LINUX.toOSType());
    }
    return ParameterField.createValueField(os.getValue().toOSType());
  }

  public Infrastructure getInfrastructure(
      RuntimeV1 runtime, PlatformV1 platformV1, ParameterField<List<CIVolumeV1>> stageVolumes) {
    Platform platform = platformV1.toPlatform();
    if (runtime.getCloud() != null) {
      CloudRuntime cloudRuntime = convertV1CloudToV0(runtime.getCloud());
      validateNestedVirtualizationPlatform(runtime.getCloud(), platformV1);
      return HostedVmInfraYaml.builder()
          .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(platform))
                    .runtime(ParameterField.createValueField(cloudRuntime))
                    .build())
          .build();
    } else if (runtime.getShell() != null) {
      DockerInfraYaml.DockerInfraSpec.DockerInfraSpecBuilder specBuilder =
          DockerInfraYaml.DockerInfraSpec.builder().platform(ParameterField.createValueField(platform));

      if (runtime.getShell().getConnector() != null && !ParameterField.isNull(runtime.getShell().getConnector())) {
        DockerRuntime dockerRuntime =
            DockerRuntime.builder()
                .spec(DockerRuntimeSpec.builder().harnessImageConnectorRef(runtime.getShell().getConnector()).build())
                .build();
        specBuilder.runtime(ParameterField.createValueField(dockerRuntime));
      }

      return DockerInfraYaml.builder().spec(specBuilder.build()).build();
    } else if (runtime.getVm() != null) {
      return VmInfraYaml.builder()
          .spec(VmPoolYaml.builder()
                    .spec(VmPoolYamlSpec.builder()
                              .poolName(runtime.getVm().getPool())
                              .os(toOSType(runtime.getVm().getOs()))
                              .harnessImageConnectorRef(runtime.getVm().getHarnessImageConnector())
                              .initTimeout(runtime.getVm().getTimeout())
                              .build())
                    .build())
          .build();
    } else if (runtime.getKubernetes() != null) {
      return K8sDirectInfraYaml.builder()
          .type(Infrastructure.Type.KUBERNETES_DIRECT)
          .spec(K8sDirectInfraYamlSpec.builder()
                    .connectorRef(runtime.getKubernetes().getConnector())
                    .namespace(runtime.getKubernetes().getNamespace())
                    .runAsUser(runtime.getKubernetes().getUser())
                    .imagePullPolicy(toImagePullPolicy(runtime.getKubernetes().getImagePullPolicy()))
                    .harnessImageConnectorRef(runtime.getKubernetes().getHarnessImageConnector())
                    .os(platform.getOs())
                    .automountServiceAccountToken(runtime.getKubernetes().getServiceToken())
                    .priorityClassName(runtime.getKubernetes().getPriorityClass())
                    .tolerations(runtime.getKubernetes().getTolerations())
                    .hostNames(runtime.getKubernetes().getHost())
                    .nodeSelector(runtime.getKubernetes().getNode())
                    .initTimeout(runtime.getKubernetes().getTimeout())
                    .serviceAccountName(runtime.getKubernetes().getServiceAccount())
                    .labels(runtime.getKubernetes().getLabels())
                    .annotations(runtime.getKubernetes().getAnnotations())
                    .volumes(mergeVolumes(runtime.getKubernetes().getVolumes(), stageVolumes))
                    .podSpecOverlay(runtime.getKubernetes().getPodSpecOverlay())
                    .containerSecurityContext(toSecurityContext(runtime.getKubernetes().getSecurityContext()))
                    .build())
          .build();
    } else {
      throw new InvalidRequestException("Invalid Runtime - " + runtime);
    }
  }

  private CloudRuntime convertV1CloudToV0(RuntimeV1.CloudRuntimeSpec cloudSpec) {
    CloudRuntimeSpecBuilder specBuilder = CloudRuntimeSpec.builder();

    if (cloudSpec.getSize() != null) {
      specBuilder.size(cloudSpec.getSize());
    }

    specBuilder.nestedVirtualization(cloudSpec.getNestedVirtualization());

    if (cloudSpec.getImageName() != null && !ParameterField.isBlank(cloudSpec.getImageName())) {
      CloudRuntimeImageSpecBuilder imageSpecBuilder =
          CloudRuntimeImageSpec.builder().imageName(cloudSpec.getImageName());
      if (cloudSpec.getConnector() != null) {
        imageSpecBuilder.connectorRef(cloudSpec.getConnector());
      }
      specBuilder.imageSpec(imageSpecBuilder.build());
    }

    return CloudRuntime.builder().spec(specBuilder.build()).build();
  }

  private void validateNestedVirtualizationPlatform(RuntimeV1.CloudRuntimeSpec cloudSpec, PlatformV1 platformV1) {
    boolean isNestedVirtualizationSet = cloudSpec.getNestedVirtualization() != null
        && ParameterField.isNotNull(cloudSpec.getNestedVirtualization())
        && Boolean.TRUE.equals(cloudSpec.getNestedVirtualization().getValue());
    boolean isPlatformExpression = platformV1.getOs().isExpression() || platformV1.getArch().isExpression();
    boolean isLinuxAmd =
        OS.LINUX.equals(platformV1.getOs().getValue()) && Arch.AMD_64.equals(platformV1.getArch().getValue());
    if (isNestedVirtualizationSet && !isPlatformExpression && !isLinuxAmd) {
      throw new InvalidYamlException(
          "Invalid Yaml, Nested virtualization parameter must be set with VM of Os type Linux and Arch type AMD64");
    }
  }

  private ParameterField<List<CIVolume>> mergeVolumes(
      ParameterField<List<CIVolumeV1>> runtimeVolumes, ParameterField<List<CIVolumeV1>> stageVolumes) {
    List<CIVolume> merged = new ArrayList<>();
    if (!ParameterField.isNull(runtimeVolumes)) {
      runtimeVolumes.obtainValue().stream().map(this::toCIVolume).forEach(merged::add);
    }
    if (!ParameterField.isNull(stageVolumes)) {
      stageVolumes.obtainValue().stream().map(this::toCIVolume).forEach(merged::add);
    }
    return merged.isEmpty() ? new ParameterField<>() : ParameterField.createValueField(merged);
  }

  private ParameterField<ImagePullPolicy> toImagePullPolicy(ParameterField<PullPolicy> pullPolicy) {
    if (ParameterField.isNull(pullPolicy)) {
      return ParameterField.createValueField(ImagePullPolicy.IFNOTPRESENT);
    }
    // Preserve unresolved expressions so they can be evaluated at runtime instead of NPEing at plan creation
    // (obtainValue() is null for an expression ParameterField).
    if (pullPolicy.isExpression()) {
      return ParameterField.createExpressionField(true, pullPolicy.getExpressionValue(), null, false);
    }
    return ParameterField.createValueField(pullPolicy.obtainValue().toImagePullPolicy());
  }

  private ParameterField<SecurityContext> toSecurityContext(
      ParameterField<SecurityContextV1> securityContextV1ParameterField) {
    SecurityContext securityContext = new SecurityContext();
    if (ParameterField.isNull(securityContextV1ParameterField)) {
      return ParameterField.createValueField(securityContext);
    }
    var securityContextV1 = securityContextV1ParameterField.obtainValue();
    securityContext.setAllowPrivilegeEscalation(securityContextV1.getAllowPrivilegeEscalation());
    securityContext.setProcMount(securityContextV1.getProcMount());
    securityContext.setPrivileged(securityContextV1.getPrivileged());
    securityContext.setReadOnlyRootFilesystem(securityContextV1.getReadOnlyRootFilesystem());
    securityContext.setRunAsNonRoot(securityContextV1.getRunAsNonRoot());
    securityContext.setRunAsGroup(securityContextV1.getRunAsGroup());
    securityContext.setRunAsUser(securityContextV1.getUser());
    securityContext.setCapabilities(securityContextV1.getCapabilities());
    return ParameterField.createValueField(securityContext);
  }

  private SecretVolumeYaml toSecretVolumeYaml(SecretVolumeYamlV1 secretVolumeYamlV1) {
    var secretVolumeYaml = SecretVolumeYaml.builder();
    var with = secretVolumeYamlV1.getWith();
    var spec = SecretVolumeYamlSpec.builder();
    if (with != null) {
      if (ParameterField.isNotNull(with.getName())) {
        spec.name(with.getName());
      }
      if (ParameterField.isNotNull(with.getOptional())) {
        spec.optional(with.getOptional());
      }
      if (ParameterField.isNotNull(with.getMountPath())) {
        secretVolumeYaml.mountPath(with.getMountPath());
      }
    }
    secretVolumeYaml.spec(spec.build());
    return secretVolumeYaml.build();
  }

  private ConfigMapVolumeYaml toConfigMapVolumeYaml(ConfigMapVolumeYamlV1 configMapVolumeYamlV1) {
    var configVolumeYaml = ConfigMapVolumeYaml.builder();
    var with = configMapVolumeYamlV1.getWith();
    var spec = ConfigMapVolumeYamlSpec.builder();
    if (with != null) {
      if (ParameterField.isNotNull(with.getName())) {
        spec.name(with.getName());
      }
      if (ParameterField.isNotNull(with.getOptional())) {
        spec.optional(with.getOptional());
      }
      if (ParameterField.isNotNull(with.getMountPath())) {
        configVolumeYaml.mountPath(with.getMountPath());
      }
    }
    configVolumeYaml.spec(spec.build());
    return configVolumeYaml.build();
  }

  private EmptyDirYaml toEmptyDirYaml(EmptyDirYamlV1 emptyDirYamlV1) {
    var emptyDirVolumeYaml = EmptyDirYaml.builder();
    var with = emptyDirYamlV1.getWith();
    var spec = EmptyDirYamlSpec.builder();
    if (with != null) {
      if (ParameterField.isNotNull(with.getMedium())) {
        spec.medium(with.getMedium());
      }
      if (ParameterField.isNotNull(with.getSize())) {
        spec.size(with.getSize());
      }
      if (ParameterField.isNotNull(with.getMountPath())) {
        emptyDirVolumeYaml.mountPath(with.getMountPath());
      }
    }
    emptyDirVolumeYaml.spec(spec.build());
    return emptyDirVolumeYaml.build();
  }

  private HostPathYaml toHostPathYaml(HostPathYamlV1 hostPathYamlV1) {
    var hostPathVolumeYaml = HostPathYaml.builder();
    var with = hostPathYamlV1.getWith();
    var spec = HostPathYamlSpec.builder();
    if (with != null) {
      if (ParameterField.isNotNull(with.getPath())) {
        spec.path(with.getPath());
      }
      if (ParameterField.isNotNull(with.getType())) {
        spec.type(with.getType());
      }
      if (ParameterField.isNotNull(with.getMountPath())) {
        hostPathVolumeYaml.mountPath(with.getMountPath());
      }
    }
    hostPathVolumeYaml.spec(spec.build());
    return hostPathVolumeYaml.build();
  }

  private PersistentVolumeClaimYaml toPersistentVolumeClaimYaml(
      PersistentVolumeClaimYamlV1 persistentVolumeClaimYamlV1) {
    var persistentVolumeClaimVolumeYaml = PersistentVolumeClaimYaml.builder();
    var with = persistentVolumeClaimYamlV1.getWith();
    var spec = PersistentVolumeClaimYamlSpec.builder();
    if (with != null) {
      if (ParameterField.isNotNull(with.getClaimName())) {
        spec.claimName(with.getClaimName());
      }
      if (ParameterField.isNotNull(with.getReadOnly())) {
        spec.readOnly(with.getReadOnly());
      }
      if (ParameterField.isNotNull(with.getMountPath())) {
        persistentVolumeClaimVolumeYaml.mountPath(with.getMountPath());
      }
    }
    persistentVolumeClaimVolumeYaml.spec(spec.build());
    return persistentVolumeClaimVolumeYaml.build();
  }

  private CIVolume toCIVolume(CIVolumeV1 ciVolumeV1) {
    switch (ciVolumeV1.getUses()) {
          case EMPTY_DIR -> {
              return toEmptyDirYaml((EmptyDirYamlV1) ciVolumeV1);
          }
          case HOST_PATH -> {
              return toHostPathYaml((HostPathYamlV1) ciVolumeV1);
          }
          case PERSISTENT_VOLUME_CLAIM -> {
              return toPersistentVolumeClaimYaml((PersistentVolumeClaimYamlV1) ciVolumeV1);
          }
          case SECRET -> {
              return toSecretVolumeYaml((SecretVolumeYamlV1) ciVolumeV1);
          }
          case CONFIG_MAP -> {
              return toConfigMapVolumeYaml((ConfigMapVolumeYamlV1) ciVolumeV1);
          }
          default -> {
              throw new InvalidRequestException("Invalid volume type");
          }
      }
  }

  public Optional<Options> getDeserializedOptions(Dependency dependency) {
    Optional<Object> optionalOptions = getDeserializedObjectFromDependency(dependency, YAMLFieldNameConstants.OPTIONS);
    Options options = (Options) optionalOptions.orElse(Options.builder().build());
    return Optional.of(options);
  }

  public Optional<Repository> getDeserializedRepo(Dependency dependency) {
    Optional<Object> optionalOptions = getDeserializedObjectFromDependency(dependency, YAMLFieldNameConstants.REPO);
    Repository repo = (Repository) optionalOptions.orElse(Repository.builder().build());
    return Optional.of(repo);
  }

  public Optional<GitCloneStepInfoV1> getDeserializedClone(Dependency dependency) {
    Optional<Object> optionalOptions = getDeserializedObjectFromDependency(dependency, YAMLFieldNameConstants.CLONE);
    if (optionalOptions.isEmpty()) {
      return Optional.empty();
    }
    GitCloneStepInfoV1 gitCloneStepInfoV1 = (GitCloneStepInfoV1) optionalOptions.get();
    return Optional.of(gitCloneStepInfoV1);
  }

  @SuppressWarnings("unchecked")
  public Optional<Map<String, String>> getDeserializedPermissions(Dependency dependency) {
    return getDeserializedObjectFromDependency(dependency, YAMLFieldNameConstants.PERMISSIONS)
        .map(o -> (Map<String, String>) o);
  }

  public Optional<Object> getDeserializedObjectFromDependency(Dependency dependency, String key) {
    return PlanCreatorUtilsV1.getDeserializedObjectFromDependency(dependency, kryoSerializer, key, false);
  }

  public static List<YamlField> getStepYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }

  /*
  This method assume v1 harness yaml
   */
  public static ExecutionWrapperConfig getExecutionConfig(YamlField step) {
    YamlField parallelField = step.getNode().getField(YAMLFieldNameConstants.PARALLEL);
    YamlField groupField = step.getNode().getField(YAMLFieldNameConstants.GROUP);

    if (parallelField != null) {
      YamlField parallelChildrenField = parallelField;
      if (parallelField.getNode().getField(YAMLFieldNameConstants.STEPS) != null) {
        parallelChildrenField = parallelField.getNode().getField(YAMLFieldNameConstants.STEPS);
      } else if (parallelField.getNode().getField(YAMLFieldNameConstants.STAGES) != null) {
        parallelChildrenField = parallelField.getNode().getField(YAMLFieldNameConstants.STAGES);
      }
      List<YamlField> parallelNodes = getStepYamlFields(parallelChildrenField);
      ParallelStepElementConfig parallelStepElementConfig =
          ParallelStepElementConfig.builder()
              .sections(parallelNodes.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList()))
              .build();
      return ExecutionWrapperConfig.builder()
          .uuid(step.getUuid())
          .version(HarnessYamlVersion.V1)
          .parallel(getJsonNode(parallelStepElementConfig))
          .build();
    } else if (groupField != null) {
      List<YamlField> groupNodes = getStepYamlFields(groupField.getNode().getField(YAMLFieldNameConstants.STEPS));
      List<YamlField> groupRollbackNodes = null;
      if(groupField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1) != null) {
          groupRollbackNodes = getStepYamlFields(groupField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1));
      }
      String stepId = step.getId() == null ? "step_group" + generateUuid() :
            step.getId();
            String stepName = step.getNodeName() == null ? "step_group" + generateUuid() : step.getNodeName();
            var stepGroupElementConfig =
                StepGroupElementConfig.builder()
                    .identifier(IdentifierGeneratorUtils.getId(stepId))
                    .name(stepName)
                    .steps(
                        groupNodes.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList()));
            if (isNotEmpty(groupRollbackNodes)) {
              stepGroupElementConfig.rollback(
                  groupRollbackNodes.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList()));
            }
            return ExecutionWrapperConfig.builder()
                .version(HarnessYamlVersion.V1)
                .uuid(step.getUuid())
                .stepGroup(getJsonNode(stepGroupElementConfig.build()))
                .build();
    }
    else {
      JsonNode node = step.getNode().getCurrJsonNode();
      if (node != null && node.isObject() && node.get(YAMLFieldNameConstants.NAME) != null) {
        ObjectNode objectNode = (ObjectNode) node;
        objectNode.put(YAMLFieldNameConstants.IDENTIFIER,
            IdentifierGeneratorUtils.getId(objectNode.get(YAMLFieldNameConstants.NAME).asText()));
      }
      return ExecutionWrapperConfig.builder()
          .version(HarnessYamlVersion.V1)
          .uuid(step.getUuid())
          .step(step.getNode().getCurrJsonNode())
          .build();
    }
  }

  /**
   * Recursively filters and trims ExecutionWrapperConfigs to only keep step group rollback content.
   * - Simple steps (run/plugin): discarded — no rollback sections.
   * - Step groups: kept only if they (or nested children) have rollback;
   * - Parallel sections: recursed into; groups extracted directly (no parallel wrapper needed
   *   since we only need container provisioning, not parallel execution ordering).
   */
  public static List<ExecutionWrapperConfig> extractStepGroupRollbackExecutionWrapperConfigs(
      List<ExecutionWrapperConfig> configs) {
    List<ExecutionWrapperConfig> result = new ArrayList<>();
    for (ExecutionWrapperConfig config : configs) {
      if (config.getStepGroup() != null && !config.getStepGroup().isNull()) {
        StepGroupElementConfig sg = IntegrationStageUtils.getStepGroupElementConfig(config);

        // Recursively extract rollback from nested groups within this group's steps
        List<ExecutionWrapperConfig> nestedRollback = isNotEmpty(sg.getSteps())
            ? extractStepGroupRollbackExecutionWrapperConfigs(sg.getSteps())
            : Collections.emptyList();

        // Skip if neither this group nor any descendant has rollback
        if (isEmpty(sg.getRollback()) && isEmpty(nestedRollback)) {
          continue;
        }

        // Rebuild: steps = own rollback steps + nested groups with rollback
        List<ExecutionWrapperConfig> newSteps = new ArrayList<>();
        if (isNotEmpty(sg.getRollback())) {
          newSteps.addAll(sg.getRollback());
        }
        newSteps.addAll(nestedRollback);

        StepGroupElementConfig trimmed =
            StepGroupElementConfig.builder().identifier(sg.getIdentifier()).name(sg.getName()).steps(newSteps).build();
        try {
          result.add(ExecutionWrapperConfig.builder()
                         .version(HarnessYamlVersion.V1)
                         .uuid(config.getUuid())
                         .stepGroup(JsonPipelineUtils.getMapper().readTree(JsonPipelineUtils.writeJsonString(trimmed)))
                         .build());
        } catch (IOException e) {
          throw new CIStageExecutionException("Failed to serialize step group rollback config", e);
        }
      } else if (config.getParallel() != null && !config.getParallel().isNull()) {
        // Recurse into parallel sections to find step groups with rollback.
        // Groups are extracted directly (not wrapped in ParallelStepElementConfig)
        // because we only need container provisioning, not parallel execution ordering.
        ParallelStepElementConfig pConfig = IntegrationStageUtils.getParallelStepElementConfig(config);
        result.addAll(extractStepGroupRollbackExecutionWrapperConfigs(pConfig.getSections()));
      }
    }
    return result;
  }

  public static ParameterField<CIShellType> getShell(ParameterField<Shell> shellParameterField) {
    if (ParameterField.isBlank(shellParameterField)) {
      return ParameterField.ofNull();
    }
    return shellParameterField.isExpression()
        ? ParameterField.createExpressionField(
              true, shellParameterField.getExpressionValue(), shellParameterField.getInputSetValidator(), true)
        : ParameterField.createValueField(shellParameterField.getValue().toShellType());
  }

  public static ParameterField<ImagePullPolicy> getImagePullPolicy(ParameterField<PullPolicy> pullParameterField) {
    if (ParameterField.isBlank(pullParameterField)) {
      return ParameterField.ofNull();
    }
    return pullParameterField.isExpression()
        ? ParameterField.createExpressionField(
              true, pullParameterField.getExpressionValue(), pullParameterField.getInputSetValidator(), true)
        : ParameterField.createValueField(pullParameterField.getValue().toImagePullPolicy());
  }

  public boolean shouldCloneManually(PlanCreationContext ctx, CodeBase codeBase) {
    if (codeBase == null) {
      return false;
    }

    switch (ctx.getTriggerInfo().getTriggerType()) {
      case WEBHOOK:
        Dependency globalDependency = ctx.getMetadata().getGlobalDependency();
        Optional<Options> optionalOptions = getDeserializedOptions(globalDependency);
        Options options = optionalOptions.orElse(Options.builder().build());
        Clone clone = options.getClone();
        if (clone == null || ParameterField.isNull(clone.getRef())) {
          return false;
        }
        break;
      default:
    }
    return true;
  }

  public Map<String, Object> getModulesImplicitNodesInfo(PlanCreationContext ctx) {
    Map<String, Object> modulesImplicitNodesInfo = new HashMap<>();
    Optional<Object> modulesImplicitNodesInfoOpt =
        getDeserializedObjectFromDependency(ctx.getDependency(), MODULE_IMPLICIT_NODES_INFO);
    if (modulesImplicitNodesInfoOpt.isPresent()) {
      modulesImplicitNodesInfo = (Map<String, Object>) modulesImplicitNodesInfoOpt.get();
    }
    return modulesImplicitNodesInfo;
  }

  private CodeBaseBuilder buildCodebaseForRemotePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess,
      Repository repository, ParameterField<CloneRef> refField, CodeBaseBuilder builder) {
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
    if (gitSyncBranchContext == null) {
      throw new InvalidRequestException("Git sync data cannot be null for remote pipeline");
    }
    boolean connectorOverride = !ParameterField.isBlank(repository.getConnector())
        && !repository.getConnector().fetchFinalValue().equals(ctx.getPipelineConnectorRef());

    ParameterField<String> repoName =
        connectorOverride || ParameterField.isNotBlank(repository.getName()) ? repository.getName() : null;
    ParameterField<String> connector =
        ParameterField.isNotBlank(repository.getConnector()) ? repository.getConnector() : null;

    var codeBaseBuilder = builder.build(ParameterField.createValueField(
        getBuildForRemotePipeline(ctx, ngAccess, repository, refField, gitSyncBranchContext, connectorOverride)));

    if (ParameterField.isNotBlank(connector)) {
      codeBaseBuilder.connectorRef(connector);
    }
    if (ParameterField.isNotBlank(repoName)) {
      codeBaseBuilder.repoName(repoName);
    }
    populateConnectorRefAndRepoNameForGitSync(codeBaseBuilder, ctx, gitSyncBranchContext);
    return codeBaseBuilder;
  }

  private void populateConnectorRefAndRepoNameForGitSync(
      CodeBaseBuilder builder, PlanCreationContext ctx, GitSyncBranchContext gitSyncBranchContext) {
    // If pipeline is associated with both webhook and gitsync then webhook will take priority
    populateConnectorRefAndRepoNameFromWebhookSource(ctx, builder);
    var codeBase = builder.build();
    if (ParameterField.isBlank(codeBase.getConnectorRef())) {
      builder.connectorRef(ParameterField.createValueField(ctx.getPipelineConnectorRef()));
    }
    if (ParameterField.isBlank(codeBase.getRepoName())) {
      builder.repoName(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getRepoName()));
    }
  }

  private CodeBaseBuilder buildCodebaseForInlinePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess,
      Repository repository, ParameterField<CloneRef> refField, CodeBaseBuilder builder) {
    if (shouldThrowExceptionIfConnectorAndRepoNameEmpty(ctx, repository)) {
      throw new InvalidRequestException("Connector should not be empty for inline pipeline");
    }
    // If connector is blank but repository name is present, leave connector empty
    // This allows downstream code (like ConnectorUtils) to handle Harness Code connector logic properly
    if (ParameterField.isBlank(repository.getConnector()) && ParameterField.isNotBlank(repository.getName())) {
      return builder.build(ParameterField.createValueField(getBuild(ctx, ngAccess, repository, refField)))
          .repoName(repository.getName());
    }
    var codeBaseBuilder = builder.build(ParameterField.createValueField(getBuild(ctx, ngAccess, repository, refField)));

    // Populating connector and repoName from pipeline/stage clone
    if (ParameterField.isNotBlank(repository.getConnector())) {
      codeBaseBuilder.connectorRef(repository.getConnector());
    }
    if (ParameterField.isNotBlank(repository.getName())) {
      codeBaseBuilder.repoName(repository.getName());
    }

    // If the source is webhook then we are populating connector and name from webhook if connector and name from YAML
    // is null.
    populateConnectorRefAndRepoNameFromWebhookSource(ctx, codeBaseBuilder);
    return codeBaseBuilder;
  }

  private String getRepoNameFromWebhookPayload(TriggerPayload triggerPayload) {
    if (triggerPayload == null || triggerPayload.getParsedPayload() == null) {
      return null;
    }

    ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
    io.harness.product.ci.scm.proto.Repository repo = switch (parsedPayload.getPayloadCase()) {
            case PR -> parsedPayload.getPr().getRepo();
            case PUSH -> parsedPayload.getPush().getRepo();
            case RELEASE -> parsedPayload.getRelease().getRepo();
            case BRANCH -> parsedPayload.getBranch().getRepo();
            case TAG -> parsedPayload.getTag().getRepo();
            default -> null;
        };

        if (repo == null) {
            return null;
        }

        // Build full repo name with namespace if available
        return isEmpty(repo.getNamespace())
                ? repo.getName()
                :
              format("%s/%s", repo.getNamespace(), repo.getName());
    }

    private void populateConnectorRefAndRepoNameFromWebhookSource(PlanCreationContext ctx, CodeBaseBuilder builder) {
      boolean isWebhookTrigger = TriggerType.WEBHOOK.equals(ctx.getTriggerInfo().getTriggerType());
      var codeBase = builder.build();
      if (isWebhookTrigger) {
        TriggerPayload triggerPayload = ctx.getTriggerPayload();
        if (ParameterField.isBlank(codeBase.getConnectorRef()) && isNotEmpty(triggerPayload.getConnectorRef())) {
          builder.connectorRef(ParameterField.createValueField(triggerPayload.getConnectorRef()));
        }
        String repoName = getRepoNameFromWebhookPayload(triggerPayload);
        if (ParameterField.isBlank(codeBase.getRepoName()) && isNotEmpty(repoName)) {
          builder.repoName(ParameterField.createValueField(repoName));
        }
      }
    }

    private boolean shouldThrowExceptionIfConnectorAndRepoNameEmpty(PlanCreationContext ctx, Repository repository) {
      boolean isWebhookTriggerType = TriggerType.WEBHOOK.equals(ctx.getTriggerInfo().getTriggerType());
      if (!isWebhookTriggerType && ParameterField.isBlank(repository.getConnector())
          && ParameterField.isBlank(repository.getName())) {
        return true;
      }
      return false;
    }

    private Build getBuildForRemotePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess, Repository repository,
        ParameterField<CloneRef> refField, GitSyncBranchContext gitSyncBranchContext, boolean connectorOverride) {
      BuildBuilder builder = builder();
      if (ctx.getTriggerInfo().getTriggerType() != TriggerType.WEBHOOK) {
        if (!connectorOverride && ParameterField.isNull(refField)) {
          return builder.type(BuildType.BRANCH)
              .spec(BranchBuildSpec.builder()
                        .branch(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getBranch()))
                        .build())
              .build();
        }
      }
      return getBuild(ctx, ngAccess, repository, refField);
    }

    private Build getBuild(
        PlanCreationContext ctx, BaseNGAccess ngAccess, Repository repository, ParameterField<CloneRef> refField) {
      BuildBuilder builder = builder();

      switch (ctx.getTriggerInfo().getTriggerType()) {
        case WEBHOOK:
          ParsedPayload parsedPayload = ctx.getTriggerPayload().getParsedPayload();
          WebhookExecutionSource webhookExecutionSource =
              WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
          // Mirrors V0 IntegrationStageUtils.treatWebhookAsManualExecution: if the YAML clone.ref holds a
          // concrete (non-expression) value it takes precedence, so fall through to the ref parser below.
          // Otherwise (ref is null or still an unresolved <+trigger.*> expression) build from the webhook payload.
          if (!treatWebhookAsManualExecution(refField, webhookExecutionSource)) {
            switch (webhookExecutionSource.getWebhookEvent().getType()) {
              case PR:
                PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
                return builder.type(BuildType.PR)
                    .spec(
                        PRBuildSpec.builder()
                            .number(ParameterField.createValueField(String.valueOf(prWebhookEvent.getPullRequestId())))
                            .build())
                    .build();
              case BRANCH:
                BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
                // A merge queue check must clone the speculative merge commit, not the target branch tip -
                // the merge commit is not reachable from that branch by construction. This mirrors the V0
                // fix in CodeBaseTaskStep#buildWebhookCodebaseSweepingOutput.
                if (WebhookTriggerProcessorUtils.isMergeQueueEvent(branchWebhookEvent.getBaseAttributes())) {
                  return builder.type(BuildType.COMMIT_SHA)
                      .spec(CommitShaBuildSpec.builder()
                                .commitSha(
                                    ParameterField.createValueField(branchWebhookEvent.getBaseAttributes().getAfter()))
                                .build())
                      .build();
                }
                return builder.type(BuildType.BRANCH)
                    .spec(BranchBuildSpec.builder()
                              .branch(ParameterField.createValueField(branchWebhookEvent.getBranchName()))
                              .build())
                    .build();
              case RELEASE:
                ReleaseWebhookEvent releaseWebhookEvent =
                    (ReleaseWebhookEvent) webhookExecutionSource.getWebhookEvent();
                return builder.type(BuildType.TAG)
                    .spec(TagBuildSpec.builder()
                              .tag(ParameterField.createValueField(releaseWebhookEvent.getReleaseTag()))
                              .build())
                    .build();
              case DELETE:
                DeleteWebhookEvent deleteWebhookEvent = (DeleteWebhookEvent) webhookExecutionSource.getWebhookEvent();
                if (BRANCH_DELETE.equals(deleteWebhookEvent.getDeleteType())) {
                  return builder.type(BuildType.BRANCH)
                      .spec(BranchBuildSpec.builder()
                                .branch(ParameterField.createValueField(
                                    deleteWebhookEvent.getRef().replaceFirst("^refs/heads/", "")))
                                .build())
                      .build();
                } else if (TAG_DELETE.equals(deleteWebhookEvent.getDeleteType())) {
                  return builder.type(BuildType.TAG)
                      .spec(TagBuildSpec.builder()
                                .tag(ParameterField.createValueField(
                                    deleteWebhookEvent.getRef().replaceFirst("^refs/tags/", "")))
                                .build())
                      .build();
                }
                break;
              default:
            }
          }
          break;
        default:
          // if reference is null (or blank literal), try to fetch default branch and clone with that.
          // An unresolved expression is treated as a valid ref: it must be preserved and resolved at
          // runtime, matching V0 behaviour where clone config fields aren't consumed at plan creation.
          if (ParameterField.isNull(refField)
              || (refField.getValue().getType() == CloneType.BRANCH
                  && ParameterField.isBlank(refField.getValue().getName()))) {
            Optional<String> optionalDefaultBranch = getDefaultBranchIfApplicable(ngAccess, repository);
            if (optionalDefaultBranch.isPresent()) {
              return builder.type(BuildType.BRANCH)
                  .spec(BranchBuildSpec.builder()
                            .branch(ParameterField.createValueField(optionalDefaultBranch.get()))
                            .build())
                  .build();
            }
          }
      }

      CloneRef ref = refField.getValue();
      if (ref.getType() == null) {
        throw new InvalidRequestException("Reference type cannot be empty");
      }

      // Treat unresolved expressions as valid (non-blank) refs. ParameterField.isBlank considers both the
      // literal value and the expressionValue, so an expression ref won't be misclassified as empty.
      if (ParameterField.isBlank(ref.getName()) && ParameterField.isBlank(ref.getSha())
          && ParameterField.isBlank(ref.getNumber())) {
        throw new InvalidRequestException("Reference value cannot be empty");
      }

      // Pass the ParameterField through as-is so an expression ref is preserved for runtime resolution
      // (V0 parity). Previously createValueField(obtainValue()) would silently strip the expression.
      ParameterField<String> name = ref.getName();
      switch (ref.getType()) {
        case BRANCH:
          builder = builder.type(BuildType.BRANCH).spec(BranchBuildSpec.builder().branch(name).build());
          break;
        case TAG:
          builder = builder.type(BuildType.TAG).spec(TagBuildSpec.builder().tag(name).build());
          break;
        case PR:
          ParameterField<Integer> prNumber = ref.getNumber();
          if (ParameterField.isBlank(prNumber)) {
            throw new InvalidRequestException("PR Number is required with clone type PR");
          }
          // If the PR number is still an expression at plan creation, preserve it as a string expression
          // ParameterField so it resolves at runtime instead of NPEing on obtainValue().toString().
          ParameterField<String> number = prNumber.isExpression()
              ? ParameterField.createExpressionField(true, prNumber.getExpressionValue(), null, true)
              : ParameterField.createValueField(prNumber.obtainValue().toString());
          builder = builder.type(BuildType.PR).spec(PRBuildSpec.builder().number(number).build());
          break;
        case COMMIT:
          ParameterField<String> sha = ref.getSha();
          if (ParameterField.isBlank(sha)) {
            throw new InvalidRequestException("Commit id is required with clone type commit");
          }
          builder = builder.type(BuildType.COMMIT_SHA).spec(CommitShaBuildSpec.builder().commitSha(sha).build());
          break;
        default:
          throw new InvalidRequestException(format("Invalid reference type given: %s", ref.getType()));
      }
      return builder.build();
    }

    /**
     * V1 equivalent of V0's {@code IntegrationStageUtils.treatWebhookAsManualExecution}. For a webhook trigger the
     * webhook payload is used to build the codebase, UNLESS the pipeline YAML supplies a concrete (non-expression)
     * {@code clone.ref} value - in which case the YAML value takes precedence (treated as a manual build), matching V0.
     *
     * Returns true  -> honor the YAML clone.ref (caller falls through to the ref parser).
     * Returns false -> ref is absent or still an unresolved {@code <+trigger.*>} expression, use the webhook payload.
     */
    private boolean treatWebhookAsManualExecution(
        ParameterField<CloneRef> refField, WebhookExecutionSource webhookExecutionSource) {
      if (ParameterField.isNull(refField) || refField.isExpression()) {
        return false;
      }
      CloneRef ref = refField.getValue();
      if (ref == null || ref.getType() == null) {
        return false;
      }
      switch (ref.getType()) {
        case PR:
          if (isConcreteValue(ref.getNumber())) {
            return true;
          }
          // PR number is an unresolved expression (e.g. <+trigger.prNumber>); it can only resolve for PR events.
          if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
            throw new CIStageExecutionException(
                "Building PR with expression <+trigger.prNumber> for push event is not supported");
          }
          return false;
        case BRANCH:
        case TAG:
          return isConcreteValue(ref.getName());
        case COMMIT:
          return isConcreteValue(ref.getSha());
        default:
          return false;
      }
    }

    private static boolean isConcreteValue(ParameterField<?> field) {
      return field != null && !field.isExpression() && field.getValue() != null;
    }

    private Optional<String> getDefaultBranchIfApplicable(BaseNGAccess ngAccess, Repository repository) {
      if (ParameterField.isNull(repository.getConnector())) {
        return Optional.empty();
      }
      String connectorIdentifier = (String) repository.getConnector().fetchFinalValue();
      String repoName = (String) repository.getName().fetchFinalValue();
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier, true);
      try {
        String defaultBranch = scmGitRefManager.getDefaultBranch(
            scmGitRefManager.getScmConnector(connectorDetails, ngAccess.getAccountIdentifier(), repoName),
            connectorIdentifier);
        return Optional.of(defaultBranch);
      } catch (Exception ex) {
        throw new InvalidRequestException(
            String.format("Cannot find default branch for connector: %s", connectorIdentifier));
      }
    }

    private GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
      if (isEmpty(byteString)) {
        return null;
      }
      byte[] bytes = byteString.toByteArray();
      return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
    }

    private String retrieveLastCommitSha(WebhookExecutionSource webhookExecutionSource) {
      if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
        PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
        return prWebhookEvent.getBaseAttributes().getAfter();
      } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
        BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
        return branchWebhookEvent.getBaseAttributes().getAfter();
      }
      log.error("Non supported event type, status will be empty");
      return "";
    }

    private static JsonNode getJsonNode(Object object) {
      try {
        String json = JsonPipelineUtils.writeJsonString(object);
        return JsonPipelineUtils.getMapper().readTree(json);
      } catch (IOException e) {
        throw new CIStageExecutionException("Failed to serialise node", e);
      }
    }
  }
