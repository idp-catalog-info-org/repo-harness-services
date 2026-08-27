/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class Constants {
  public static final String HARNESS_API_VERSION = "harness.io/v1";
  public static final String BACKSTAGE_API_VERSION = "backstage.io/v1alpha1";
  public static final String BACKSTAGE_TEMPLATE_API_VERSION = "scaffolder.backstage.io/v1beta3";

  // Backstage kinds
  public static final String KIND = "kind";
  public static final String COMPONENT = "Component";
  public static final String TEMPLATE = "Template";
  public static final String API = "API";
  public static final String USER = "User";
  public static final String GROUP = "Group";
  public static final String RESOURCE = "Resource";
  public static final String SYSTEM = "System";
  public static final String AIASSET = "AIAsset";

  public static final String API_VERSION = "apiVersion";

  public static final String UNSUPPORTED = "";

  // metadata
  public static final String METADATA = "metadata";
  public static final String ANNOTATIONS = "annotations";
  public static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";
  public static final String MANAGED_BY_LOCATION_ANNOTATION = "backstage.io/managed-by-location";
  public static final String MANAGED_BY_ORIGIN_LOCATION_ANNOTATION = "backstage.io/managed-by-origin-location";
  public static final String ENTITY_UUID_ANNOTATION = "harness.io/entity-uuid";
  public static final String ROLES_ANNOTATION = "harness.io/roles";
  public static final String UUID = "uuid";
  public static final String NAME = "name";
  public static final String TITLE = "title";
  public static final String NAMESPACE = "namespace";
  public static final String DEFAULT_NAMESPACE = "default";
  public static final String DESCRIPTION = "description";
  public static final String TAGS = "tags";
  public static final String METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION =
      "metadata.annotations.backstage\\.io/source-location";
  public static final String STO_TEST_TARGET_ANNOTATION = "harness.io/sto-test-target";
  public static final String STO = "sto";
  public static final String METADATA_TAGS = "metadata.tags";

  // status
  public static final String LEVEL = "level";
  public static final String MESSAGE = "message";

  // relations
  public static final String RELATIONS = "relations";
  public static final String TARGET = "target";
  public static final String TARGET_REF = "targetRef";
  public static final String OWNED_BY = "ownedBy";
  public static final String OWNER_OF = "ownerOf";

  public static final String PROVIDES_API = "providesApis";
  public static final String API_PROVIDED_BY = "apiProvidedBy";
  public static final String CONSUMES_API = "consumesApis";
  public static final String API_CONSUMED_BY = "apiConsumedBy";
  public static final String DEPENDS_ON = "dependsOn";
  public static final String DEPENDENCY_OF = "dependencyOf";
  public static final String PARENT_OF = "parentOf";
  public static final String CHILD_OF = "childOf";
  public static final String LEADER_OF = "leaderOf";
  public static final String HAS_LEADER = "hasLeader";
  public static final String MEMBER_OF = "memberOf";
  public static final String HAS_MEMBER = "hasMember";
  public static final String PART_OF = "partOf";
  public static final String HAS_PART = "hasPart";
  public static final String SUB_COMPONENT_OF = "subcomponentOf";

  // spec
  public static final String SPEC = "spec";
  public static final String TYPE = "type";
  public static final String LIFECYCLE = "lifecycle";
  public static final String OWNER = "owner";
  public static final String SYSTEM_SPEC_RELATION_REF = "system";

  public static final String PARENT = "parent";
  public static final String LEADERS = "leaders";
  public static final String CHILDREN = "children";
  public static final String MEMBERS = "members";
  public static final String PROFILE = "profile";
  public static final String DISPLAY_NAME = "displayName";
  public static final String EMAIL = "email";

  public static final String API_COMPONENT_RESOURCE_TEMPLATE_TYPE = "api_component_resource_template";
  public static final String USER_GROUP_TYPE = "user_group";
  public static final String USER_TYPE = "user";
  public static final String IS_CUSTOM_USER_GROUP = "isCustomUserGroup";

  public static final List<String> CORE_KINDS = List.of("api", "component", "resource", "system", "aiasset");
  public static final List<String> SUPPORTED_KINDS = List.of("api", "component", "resource", "user", "group",
      "workflow", "system", "environmentblueprint", "environment", "hierarchy", "aiasset");
  public static final List<String> SUPPORTED_MIGRATE_KINDS = List.of("api", "component", "resource", "workflow");
  public static final List<String> RELATION_REFS = List.of(PROVIDES_API, API_PROVIDED_BY, CONSUMES_API, API_CONSUMED_BY,
      DEPENDS_ON, DEPENDENCY_OF, PART_OF, HAS_PART, SUB_COMPONENT_OF, SYSTEM_SPEC_RELATION_REF, MEMBERS, HAS_MEMBER,
      MEMBER_OF, PARENT, CHILD_OF, PARENT_OF, LEADERS, HAS_LEADER, LEADER_OF);

  public final static String COMPONENT_KIND = "component";
  public final static String API_KIND = "api";
  public final static String RESOURCE_KIND = "resource";
  public final static String SYSTEM_KIND = "system";
  public final static String WORKFLOW_KIND = "workflow";
  public final static String HIERARCHY_KIND = "hierarchy";
  public final static String GROUP_KIND = "group";
  public final static String USER_KIND = "user";
  public final static String ENVIRONMENT_KIND = "environment";
  public final static String ENVIRONMENT_BLUEPRINT_KIND = "environmentblueprint";
  public final static String AI_ASSET_KIND = "aiasset";

  public final static String TEMPLATE_KIND = "template";

  public final static Set<String> RESERVED_KIND_IDENTIFIERS =
      Set.of(API_KIND, COMPONENT_KIND, ENVIRONMENT_KIND, ENVIRONMENT_BLUEPRINT_KIND, GROUP_KIND, USER_KIND,
          HIERARCHY_KIND, RESOURCE_KIND, SYSTEM_KIND, WORKFLOW_KIND, AI_ASSET_KIND);
  public final static Set<String> SUPPORTED_HARNESS_TO_IDP_SYNC_KINDS =
      Set.of(API_KIND, COMPONENT_KIND, RESOURCE_KIND, SYSTEM_KIND, USER_KIND, WORKFLOW_KIND);
  public final static Set<String> SUPPORTED_HARNESS_TO_IDP_CONVERSION_KINDS =
      Set.of(AI_ASSET_KIND, API_KIND, COMPONENT_KIND, GROUP_KIND, RESOURCE_KIND, SYSTEM_KIND, USER_KIND, WORKFLOW_KIND);
  public static final Set<String> NON_FILTERABLE_KINDS = Set.of(USER_KIND);

  public static final List<String> SOURCE_LOCATION_UNSUPPORTED_KINDS = List.of(SYSTEM_KIND, WORKFLOW_KIND,
      HIERARCHY_KIND, GROUP_KIND, USER_KIND, ENVIRONMENT_KIND, ENVIRONMENT_BLUEPRINT_KIND, AI_ASSET_KIND);
  public final static Set<String> NON_INHERITABLE_KINDS =
      Set.of(WORKFLOW_KIND, ENVIRONMENT_KIND, ENVIRONMENT_BLUEPRINT_KIND, GROUP_KIND, USER_KIND);

  public static final String NAMESPACE_FOR_ENTITY_CONFLICT = "namespace_for_entity_conflict";
  public static final String ENTITY_CONFLICT = "entity_conflict";

  public static final String SCOPES = "scopes";

  public static final List<String> REFERENCED_TYPES = List.of(
      "ownerOf", "apiProvidedBy", "apiConsumedBy", "dependencyOf", "hasPart", "memberOf", "parentOf", "leaderOf");

  public static final List<String> VERSION_SUPPORTED_KINDS = List.of("environmentblueprint");

  public static final String RELATIONSHIP_STREAM_KEY = "catalog:entity:processing";
  public static final String RELATIONSHIP_LOCK_PREFIX = "catalog:entity:lock:";
  public static final long LOCK_WAIT_TIME_MS = 5000;
  public static final long LOCK_LEASE_TIME_MS = 30000;
}
