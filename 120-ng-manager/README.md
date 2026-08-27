# Overview
This module contains the manager side logic of Harness Next Gen Application. It is the core service that handles all tasks, communications with delegates and background jobs. Here we store deployment configuration and manage the pipelines.

# Compliance
In case of any changes made to the module, ensure the following is complied
- Dependency graph: Update dependency graph
```
bazel run tools/dependency-analyzer/src/main/java/io/harness/depanalyzer:dep_analyzer -- --workspace=$PWD --module 120-ng-manager/src/main/java --assumedPackagePrefixesWithBuildFile all --exportGraphToDotFile
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
bazel run tools/dependency-analyzer/src/main/java/io/harness/depanalyzer:dep_analyzer -- --workspace=$PWD --module 120-ng-manager/src/main/java --assumedPackagePrefixesWithBuildFile all --findCycles
```

## Dependency Graph
![dependency-graph](harness_dependency_graph.png)

# Build and Run - Experimental
The following approach is proposed and implemented to address the slowness and inconsistent builder experience(using only disk_cache) on local machines.

This experimental approach employs pre-configured, warmed-up bazel-runner docker containers with advanced configurations like GCP remote cache, disk cache, repository cache etc. to significantly accelerate the local builder experience of the 120-ng-manager module.

![local build architecture](../img/local-build-arch.png)


## Benefits
The approach provides us the following benefits:

* **Day-0 Setup**: A one time Day-0 local build powered by an automated setup mechanism (including the prerequisite steps and a build) could be in the order of 30-40 minutes (depending on the internet bandwidth).

* **New commits on develop**: A recurring local rebase build should be in the order of 5-10 minutes (depends on the internet bandwidth and a persistent bazel-runner container running).

* **New commits on local Branch**: A local incremental build should be in the order of seconds (with a persistent bazel-runner container running).

**The following has been tested and verified on M1 macbook pro and above configurations.**
   
## Prerequisites
- Tested on `MacOS Sonoma 14.5`. Having a different OS version may cause issues. 
- Quit Rancher Desktop if running.

    ![image](../img/120-ng-manager/quit_rd.png)

- Install the latest Colima for bazel-runner containers:  

```bash
    brew install colima
```
- Install docker:
```bash 
    brew install docker
```

- Follow the instructions [here](https://cloud.google.com/sdk/docs/install) to install gcloud.

- Login and configure gcloud as below:
```bash
    gcloud auth login
    gcloud auth configure-docker
```

- Initialize the bazel-runner container with:
```
    make init
```

- Please ensure that your current docker context is set to Colima (* denotes your current context).  
Context can be switched to Colima by running `docker context use colima`. 

![local build architecture](../img//120-ng-manager/docker_context.png)  
- For any issues that you might face please refer to the [Troubleshooting](#troubleshooting) section.
   

- Optionally rerun `make init` until you see the following output: 
```
    ...
    ...
    Bazel is now working in the container.

    Init Successfull
    
```

## Build using IntelliJ : 120-ng-manager
  
![120-ng-manager launch](../img/120-ng-manager/launch_config.png)
  
### Build  
Launch the  `NextGen Manager-build` configuration to build `120-ng-manager:module_deploy.jar` . You should see the following logs at the end of console output.
```
    .....
    .....
    INFO: Elapsed time: 860.366s, Critical Path: 156.54s
    INFO: 9760 processes: 9613 remote cache hit, 147 internal.
    INFO: Build completed successfully, 9760 total actions
```
You can verify and validate the build runtime benefits as mentioned [here](#benefits).

### Build & Run
Launch the `NextGen Manager-onejar` configuration to build and run the **120-ng-manager** module.

You can verify and validate the build runtime benefits as mentioned [here](#benefits).
### Debug
Launch the `NextGen Manager-onejar` configuration in `debug mode` to build and debug the 120-ng-manager module.

### Testing
To verify the 120-ng-manager service, please follow the instructions as outlined [here](#validation).

## Build using CLI: 120-ng-manager
### Build  
Build 120-ng-manager:
```
    make build
        .....
        .....
        INFO: Elapsed time: 860.366s, Critical Path: 156.54s
        INFO: 9760 processes: 9613 remote cache hit, 147 internal.
        INFO: Build completed successfully, 9760 total actions
```
You can verify and validate the build runtime benefits as mentioned [here](#benefits).

### Build & Run
Run 120-ng-manager:
```
    make run t=120-ng-manager
```

You can verify and validate the build runtime benefits as mentioned [here](#benefits).

### Testing
To verify the 120-ng-manager service, please follow the instructions as outlined [here](#validation).

## Troubleshooting

### Commons Errors:
```makefile
Checking if container is running...
Cannot connect to the Docker daemon at unix:///Users/<user>/.rd/docker.sock. Is the docker daemon running?
Starting the container...
docker: Cannot connect to the Docker daemon at unix:///Users/<user>/.rd/docker.sock. Is the docker daemon running?.
See 'docker run --help'.
make: *** [init] Error 125
```

```makefile
Init Successfull

Starting local Bazel server and connecting to it...
make[1]: *** [build-with-retry] Error 37
make: *** [build] Error 2
```
If you face any of the above errors,  please follow the below steps:
- Check your `env` / `.zshrc` / `.bashrc` files for the following lines and comment them out if present.
```
export DOCKER_HOST=unix://$HOME/.rd/docker.sock

export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

export TESTCONTAINERS_HOST_OVERRIDE=$(rdctl shell ip a show vznat | awk '/inet / {sub("/.*",""); print $2}')

export PATH="/Users/<user>/.rd/bin:$PATH"
```

- You must be in `harness-core` directory.
```bash
cd harness-core
```
- Delete the `.bazel_runner` directory
```bash
rm -rf ../.bazel_runner
```
- Switch the docker context to Colima context by running 
```
docker context use colima
``` 
- re-run `make init` to initialize the bazel-runner container. 
```makefile
make init
```

### Bazel Related Errors:

- In most scenarios `make clean t=bazel_kill` should deal with unresponsive bazel server errors. 
- Additionally run `make clean` to view other clean options.
  
# Build and Test
### Bazel build module
```
bazel build //120-ng-manager:module
```

### Bazel build tests
```
bazel build //120-ng-manager:tests
```

### Bazel Test
```
bazel test //120-ng-manager/...
```

### Testing
To verify the 120-ng-manager service, please follow the instructions as outlined [here](#validation).

### Bazel profiler
```
bazel build \
--generate_json_trace_profile --profile=~/Downloads/to-gsdk-profile-before.json.gz \
--experimental_profile_include_target_label \
--noslim_profile \
--disk_cache= --noremote_accept_cached //120-ng-manager:module
```

# Validation

The NG Manager service requires **Redis** and **Mongo** dependencies to run. Please set up and verify as mentioned below:
- Setup redis
```
    brew install redis
    brew services start redis
```
- Setup mongo instance
```
    docker run -p 127.0.0.1:27017:27017 -v ~/_mongodb_data:/data --name mongoContainer -d --rm mongo:4.2
```
- Use any of the above-mentioned [methods](#build-and-run---experimental) to run the 120-ng-manager service. 
  
- Verify the service ``health`` endpoint:
```
    curl -kv https://localhost:7090/health
```
Response:
```json
        {
        "status": "SUCCESS",
        "data": "healthy",
        "metaData": null,
        "correlationId": "69e0c99a-e2a9-4a76-88eb-148e4e67a8ff"
        }
```

- Verify the service ``version`` endpoint:
```
    curl -kv https://localhost:7090/version
```
Response:
```json
        {
        "metaData": {},
        "resource": {
            "versionInfo": {
            "version": "${build.fullVersion}",
            "buildNo": "${build.number}",
            "gitCommit": "${gitCommitId}",
            "gitBranch": "${gitBranch}",
            "timestamp": "${buildTimeStamp}",
            "patch": "${build.patch}"
            },
            "runtimeInfo": {
            "primary": true,
            "primaryVersion": "*",
            "deployMode": null
            }
        },
        "responseMessages": []
        }
```