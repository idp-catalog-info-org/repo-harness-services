#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex
# Define constants
BEARER_TOKEN=$BEARER_TOKEN
BASE_URL="https://app.harness.io/gateway/code/api/v1/repos/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core/+/commits"
GIT_REF="refs%2Fheads%2Frelease%2Fpipeline-service_"$CURRENT_VERSION""
AFTER_REF="refs%2Fheads%2Frelease%2Fpipeline-service_"$PREV_VERSION""
LIMIT=100
PAGE_NUMBER=1

# Function to fetch commits for a specific page
fetch_commits() {
  local page_number=$1
  curl -s "$BASE_URL?routingId=vpCkHKsDSxK9_KYfjCTMKA&limit=$LIMIT&page=$page_number&git_ref=$GIT_REF&after=$AFTER_REF" \
    -H "accept: */*" \
    -H "accept-language: en-GB,en-US;q=0.9,en;q=0.8" \
    -H "authorization: Bearer $BEARER_TOKEN" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
}

# Fetch initial response to determine the total number of commits
initial_response=$(fetch_commits $PAGE_NUMBER)

# Extract total commits
total_commits=$(echo "$initial_response" | jq -r '.total_commits')
echo "Total commits: $total_commits"

# Calculate total pages
total_pages=$((total_commits / LIMIT + 1))
echo "Total pages: $total_pages"

# Function to extract keys from the response
extract_keys() {
  local response=$1
  echo "$response" | jq -r '.commits[].title' | grep -o 'PIPE-[0-9]\+' | sort | uniq
}

# Extract_keys based on CDS project with registered author_emails
extract_CDS_keys() {
    local response=$1
    local key
    local author
    local final_keys=()

    local valid_authors=(
        "adithya.viswanathan@harness.io"
        "amit.singh@harness.io"
        "ankit.tiwari@harness.io"
        "archit.singla@harness.io"
        "aveesha.jindal@harness.io"
        "ayushi.tiwari@harness.io"
        "brijesh.dhakar@harness.io"
        "fardeen.kamal@harness.io"
        "hemanth.sridhar@harness.io"
        "abhinav.hinger@harness.io"
        "lucas.sales@harness.io"
        "meena.ravichandran@harness.io"
        "mohit.garg@harness.io"
        "rishabh.gupta@harness.io"
        "rishikesh.chaudhary@harness.io"
        "rohit.karelia@harness.io"
        "sahil.hindwani@harness.io"
        "shalini.agrawal@harness.io"
        "shivam.negi@harness.io"
        "sonali.goyal@harness.io"
        "sourabh.awashti@harness.io"
        "utkarsh.choubey@harness.io"
        "vinícius.calasans@harness.io"
        "vivek.dixit@harness.io"
        "yagyansh.bhatia@harness.io"
        "aditya.rana@harness.io"
        "jatin.punase@harness.io"
        "chetan.sinha@harness.io"
    )

    # Iterate over each CDS key and check if the author is in the list of valid authors
    for key in $(echo "$response" | jq -r '.commits[].title' | grep -o 'CDS-[0-9]\+' | sort | uniq); do
        author=$(echo "$response" | jq -r --arg key "$key" '.commits[] | select(.title | contains($key)).author.identity.email')
        # shellcheck disable=SC2199
        # shellcheck disable=SC2076
        if [[ " ${valid_authors[@]} " =~ " $author " ]]; then
            final_keys+=("$key")
        fi
    done

    echo "${final_keys[@]}"
}

# Extract keys from the initial response
KEYS=$(extract_keys "$initial_response")
FINAL_KEYS=$(extract_CDS_keys "$initial_response")


# Fetch and process remaining pages
for ((page=2; page<=total_pages; page++)); do
  response=$(fetch_commits $page)
  page_keys=$(extract_keys "$response")
  cds_keys=$(extract_CDS_keys "$response")
  KEYS="$KEYS"$'\n'"$page_keys"
  FINAL_KEYS="$FINAL_KEYS"$'\n'"$cds_keys"
done

# Output total number of unique keys with PIPE project
total_pipe_keys=$(echo "$KEYS" | wc -l)
echo "Total number of unique keys with PIPE project: $total_pipe_keys"

# Output total number of unique keys with CDS project done by Pipeline team
total_cds_keys=$(echo "$FINAL_KEYS" | wc -w)
echo "Total number of unique keys with CDS project and pipeline team developer: $total_cds_keys"

# Remove duplicates and sort the keys
KEYS=$(echo "$KEYS" | tr ' ' '\n' | sort | uniq | tr '\n' ' ')

#FIX_PIE_VERSION value to be same as used in release-branch-create-pie-version.sh
FIX_PIE_VERSION="PIPE-""$VERSION"

# shellcheck disable=SC2068
for KEY in ${KEYS[@]}
  do
    response=$(curl -X GET -H "Content-Type: application/json" \
          https://harness.atlassian.net/rest/api/2/issue/${KEY}/?components \
          --user $JIRA_USERNAME:$JIRA_PASSWORD)

    components=$(echo "${response}" | jq -r '.fields.components')

    if name=$(echo "${components}" | jq -r '.[] | select(.name == "Pipeline" or .name == "YAML" or .name == "Orchestration Engine" or .name == "Expression Engine" or .name == "Triggers Framework") | .name'); test -n "${name}"; then
      FINAL_KEYS+=( "$KEY" )
    fi
  done

# shellcheck disable=SC2068
echo ${FINAL_KEYS[@]}

# shellcheck disable=SC2068
for KEY in ${FINAL_KEYS[@]}
  do
    echo "$KEY"
    EXCLUDE_PROJECTS=","
    # Extract Jira project from Jira key
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    # If it is in the exclude projects list, then do not attempt to set the fix version
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $KEY - project is archived or not relevant to versions."
    else
      response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
        "update": {
          "fixVersions": [
            {"add":
              {"name": "'"$FIX_PIE_VERSION"'" }
            }
          ]
        }
      }')
      if [[ "$response" -eq 204 ]] ; then
        echo "$KEY fixVersion set to $FIX_PIE_VERSION"
      elif [[ "$response" -eq 400 ]] ; then
        echo "Could not set fixVersion on $KEY - field hidden for the issue type"
      fi
    fi
  done
