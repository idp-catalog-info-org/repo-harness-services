# Overview
Todo

# Compliance
In case of any changes made to the module, ensure the following is complied
- Dependency graph: Update dependency graph
```
bazel run tools/dependency-analyzer/src/main/java/io/harness/depanalyzer:dep_analyzer -- --workspace=$PWD --module 332-ci-manager/service/src --assumedPackagePrefixesWithBuildFile all --exportGraphToDotFile
dot -Tpng harness_dependency_graph.dot -o harness_dependency_graph.png
```
Install graphviz to use dot command `brew install graphviz`. Also clean up the `.build-cleaner-path-index` and `.dependency-analyzer-graph` to create a fresh graph.

- Troubleshooting: [Confluence page](https://harness.atlassian.net/wiki/spaces/BT/pages/21597061994/Plan+for+Bazel+Target+Optimisation#Troubleshooting)
- Module build sanity: Run module build sanity commands post changes to ensure proper functioning of code change
```
bazel build //sub_package_path:module
bazel build //selected_module_path:module
bazel build //...
```

# Dependencies
## Cyclic Dependencies
No cycles in this module.

To determine cycles run the following command:
```
bazel run tools/dependency-analyzer/src/main/java/io/harness/depanalyzer:dep_analyzer -- --workspace=$PWD --module 332-ci-manager/service/src --assumedPackagePrefixesWithBuildFile all --findCycles
```

## Dependency Graph
![dependency-graph](harness_dependency_graph.png)

# Build and Test
### Bazel build module
```
bazel build //332-ci-manager/service:module
```

### Bazel build tests
```
bazel build //332-ci-manager/service:tests
```

### Bazel Test
```
bazel test //332-ci-manager/service/...
```

### Bazel profiler
```
bazel build \
--generate_json_trace_profile --profile=~/Downloads/to-gsdk-profile-before.json.gz \
--experimental_profile_include_target_label \
--noslim_profile \
--disk_cache= --noremote_accept_cached //332-ci-manager/service
```