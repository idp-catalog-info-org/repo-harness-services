#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# Script to generate codebase hash for pipeline-service
# Usage: generateCodebaseHash.sh [output_directory]

set -e

echo "Generating codebase hash for pipeline-service..."

# Set up variables
module=pipeline-service
moduleName=pipeline-service
WORKSPACE_ROOT="/opt/harness/harness-core"
OUTPUT_DIR="${1:-$WORKSPACE_ROOT}"

# Change to workspace directory
cd "$WORKSPACE_ROOT"

# Set up bazel configuration
bazelrc="--bazelrc=bazelrc.docker --noworkspace_rc"
BAZEL_ARGUMENTS="--jobs=20 --experimental_convenience_symlinks=normal --remote_download_outputs=all"

echo "Extracting Kryo dependencies..."
bazel query "deps(//${module}/service:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text

echo "Extracting Proto dependencies..."
cp scripts/interface-hash/module-deps.sh .
sh module-deps.sh //${module}/service:module > /tmp/ProtoDeps.text

echo "Generating interface hash..."
bazel ${bazelrc} run ${BAZEL_ARGUMENTS} //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > "${OUTPUT_DIR}/${moduleName}-protocol.info"

echo "Cleaning up temporary files..."
rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text

echo "Codebase hash generated successfully!"
echo "Hash file location: ${OUTPUT_DIR}/${moduleName}-protocol.info"
cat "${OUTPUT_DIR}/${moduleName}-protocol.info"
