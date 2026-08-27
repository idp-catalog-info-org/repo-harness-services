#!/bin/bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

function usage {
    echo "usage: $0 [-e environment] [-m executeMode] [-a bearerAuthorization] [-l fileLocation] [-i routingAccountIdentifier] [-d debugMode]"
    echo "  -e      Environment (Optional) - prod / prod3 / qa / stress | Defaults to prod"
    echo "  -m      Execute mode - CREATE / UPDATE"
    echo "  -a      Harness Bearer Authorization which has access to create / update NG license"
    echo "  -l      File location for CREATE / UPDATE"
    echo "  -i      Routing Account Identifier (Optional)"
    echo "  -d      Debug mode (Optional) - y / n"
    exit 1
}

while getopts e:m:a:l:i:d:h flag
do
    case "${flag}" in
        e) ENVIRONMENT=${OPTARG};;
        m) MODE=${OPTARG};;
        a) BEARER_AUTHORIZATION=${OPTARG};;
        l) FILE_LOCATION=${OPTARG};;
        i) ROUTING_ACCOUNT_IDENTIFIER=${OPTARG};;
        d) DEBUG_MODE=${OPTARG};;
        h | ?) usage
    esac
done

function validate {
    if [[ "$MODE" != "CREATE" ]] && [[ "$MODE" != "UPDATE" ]]; then
        usage
    fi

    if [ -z "${BEARER_AUTHORIZATION}" ]; then
        usage
    fi

    if [ -z "${FILE_LOCATION}" ]; then
        usage
    fi

    if [ ! -f "${FILE_LOCATION}" ]; then
        echo "File ${FILE_LOCATION} not found"
        exit 1
    fi

    if [ ! -r "${FILE_LOCATION}" ]; then
        echo "File ${FILE_LOCATION} is not readable"
        exit 1
    fi
}

function get_cluster() {
    CLUSTER=""

    if [ "$1" == "prod" ]; then
      CLUSTER="app"
    fi

    if [ "$1" == "prod3" ]; then
      CLUSTER="app3"
    fi

    if [ "$1" == "qa" ]; then
      CLUSTER="qa"
    fi

    if [ "$1" == "stress" ]; then
      CLUSTER="stress"
    fi

    __="${CLUSTER}"
}

function print_debug_info() {
    if [ "$DEBUG_MODE" == "y" ]; then
        echo -e "\nExecuting $1 $2 with request body - $3 with authorization header - $4\n"
    fi
}

echo -e "Validating program arguments..."
validate
echo -e "Validated program arguments..."

index=0

echo -e "Reading file ${FILE_LOCATION} for license ${MODE}..."

while IFS= read -r line; do
    if [ -n "$line" ]; then
        INPUTS[$index]="$line"
        index=$(($index+1))
    fi
done < ${FILE_LOCATION}

echo -e "Read file ${FILE_LOCATION} for license ${MODE}..."

index=1

echo -e "Started license ${MODE} processing..."

if [ -z "${ENVIRONMENT}" ]; then
  ENVIRONMENT="prod"
fi

if [ -z "${DEBUG_MODE}" ]; then
  DEBUG_MODE="n"
fi

CURL_VERBOSE=""
if [ "$DEBUG_MODE" == "y" ]; then
    CURL_VERBOSE="-v"
fi

get_cluster "${ENVIRONMENT}"
ENVIRONMENT=$__

for input in "${INPUTS[@]}"
do
    IFS=',' read -ra LINE_INPUT <<< "$input"

    if [[ "$MODE" == "CREATE" ]] && [[ ${#LINE_INPUT[@]} != 8 ]]; then
        echo -e "Invalid input row in file ${FILE_LOCATION} line number $index for $MODE | Hint: Mode is create, but line input doesn't contain required 8 fields seperated by comma"
        exit 1
    fi

    if [[ "$MODE" == "UPDATE" ]] && [[ ${#LINE_INPUT[@]} != 9 ]]; then
        echo -e "Invalid input row in file ${FILE_LOCATION} line number $index for $MODE | Hint: Mode is update, but line input doesn't contain required 9 fields seperated by comma"
        exit 1
    fi

    if [[ "$MODE" == "CREATE" ]]; then
        ACCOUNT_IDENTIFIER=${LINE_INPUT[0]}
        EDITION=${LINE_INPUT[1]}
        LICENSE_TYPE=${LINE_INPUT[2]}
        STATUS=${LINE_INPUT[3]}
        START_TIME=${LINE_INPUT[4]}
        EXPIRY_TIME=${LINE_INPUT[5]}
        NUMBER_OF_DEVELOPERS=${LINE_INPUT[6]}
        SELF_SERVICE=${LINE_INPUT[7]}

        if [ -z "${ACCOUNT_IDENTIFIER}" ] || [ -z "${EDITION}" ] || [ -z "${LICENSE_TYPE}" ] || [ -z "${STATUS}" ] || [ -z "${START_TIME}" ] || [ -z "${EXPIRY_TIME}" ] || [ -z "${NUMBER_OF_DEVELOPERS}" ] || [ -z "${SELF_SERVICE}" ]; then
            echo -e "Invalid input row in file ${FILE_LOCATION} line number $index for $MODE | Hint: Mode is create and some of the line input fields are empty"
        fi

        POST_DATA="{\"accountIdentifier\": \"${ACCOUNT_IDENTIFIER}\", \"moduleType\": \"IDP\", \"edition\": \"${EDITION}\", \"licenseType\": \"${LICENSE_TYPE}\", \"status\": \"${STATUS}\", \"startTime\": ${START_TIME}, \"expiryTime\": ${EXPIRY_TIME}, \"numberOfDevelopers\": ${NUMBER_OF_DEVELOPERS}, \"selfService\": ${SELF_SERVICE}}"

        if [ -z "${ROUTING_ACCOUNT_IDENTIFIER}" ]; then
            ROUTING_ACCOUNT_IDENTIFIER=${ACCOUNT_IDENTIFIER}
        fi

        print_debug_info "POST" "https://${ENVIRONMENT}.harness.io/gateway/api/account/${ROUTING_ACCOUNT_IDENTIFIER}/ng/license?routingId=${ROUTING_ACCOUNT_IDENTIFIER}" "${POST_DATA}" "Authorization: Bearer ${BEARER_AUTHORIZATION}"

        RESULT_HTTP_CODE=$(curl --write-out %{http_code} -s "${CURL_VERBOSE}" --output /dev/null -H "cache-control: no-cache" -H "Accept: application/json" -H "Content-Type: application/json" -H "Authorization: Bearer ${BEARER_AUTHORIZATION}" -X POST --data "${POST_DATA}" "https://${ENVIRONMENT}.harness.io/gateway/api/account/${ROUTING_ACCOUNT_IDENTIFIER}/ng/license?routingId=${ROUTING_ACCOUNT_IDENTIFIER}")

        echo "Create license for account $ACCOUNT_IDENTIFIER with data ${POST_DATA} resulted in ${RESULT_HTTP_CODE}"
    fi

    if [[ "$MODE" == "UPDATE" ]]; then
        ID=${LINE_INPUT[0]}
        ACCOUNT_IDENTIFIER=${LINE_INPUT[1]}
        EDITION=${LINE_INPUT[2]}
        LICENSE_TYPE=${LINE_INPUT[3]}
        STATUS=${LINE_INPUT[4]}
        START_TIME=${LINE_INPUT[5]}
        EXPIRY_TIME=${LINE_INPUT[6]}
        NUMBER_OF_DEVELOPERS=${LINE_INPUT[7]}
        SELF_SERVICE=${LINE_INPUT[8]}

        if [ -z "${ID}" ] || [ -z "${ACCOUNT_IDENTIFIER}" ] || [ -z "${EDITION}" ] || [ -z "${LICENSE_TYPE}" ] || [ -z "${STATUS}" ] || [ -z "${START_TIME}" ] || [ -z "${EXPIRY_TIME}" ] || [ -z "${NUMBER_OF_DEVELOPERS}" ] || [ -z "${SELF_SERVICE}" ]; then
            echo -e "Invalid input row in file ${FILE_LOCATION} line number $index for $MODE | Hint: Mode is update and some of the line input fields are empty"
        fi

        PUT_DATA="{\"id\": \"${ID}\", \"accountIdentifier\": \"${ACCOUNT_IDENTIFIER}\", \"moduleType\": \"IDP\", \"edition\": \"${EDITION}\", \"licenseType\": \"${LICENSE_TYPE}\", \"status\": \"${STATUS}\", \"startTime\": ${START_TIME}, \"expiryTime\": ${EXPIRY_TIME}, \"numberOfDevelopers\": ${NUMBER_OF_DEVELOPERS}, \"selfService\": ${SELF_SERVICE}}"

        if [ -z "${ROUTING_ACCOUNT_IDENTIFIER}" ]; then
            ROUTING_ACCOUNT_IDENTIFIER=${ACCOUNT_IDENTIFIER}
        fi

        print_debug_info "PUT" "https://${ENVIRONMENT}.harness.io/gateway/api/account/${ROUTING_ACCOUNT_IDENTIFIER}/ng/license?routingId=${ROUTING_ACCOUNT_IDENTIFIER}" "${PUT_DATA}" "Authorization: Bearer ${BEARER_AUTHORIZATION}"

        RESULT_HTTP_CODE=$(curl --write-out %{http_code} -s "${CURL_VERBOSE}" --output /dev/null -H "cache-control: no-cache" -H "Accept: application/json" -H "Content-Type: application/json" -H "Authorization: Bearer ${BEARER_AUTHORIZATION}" -X PUT --data "${PUT_DATA}" "https://${ENVIRONMENT}.harness.io/gateway/api/account/${ROUTING_ACCOUNT_IDENTIFIER}/ng/license?routingId=${ROUTING_ACCOUNT_IDENTIFIER}")

        echo "Update license for account $ACCOUNT_IDENTIFIER with data ${PUT_DATA} resulted in ${RESULT_HTTP_CODE}"
    fi

    index=$(($index+1))
done

echo -e "Completed license ${MODE} processing..."

exit 0
