#!/bin/bash

# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e
export PATH="$(pwd)/tools/apigeecli/bin:$PATH"

export REGION=${REGION:-us-west1}
export OPERATION=${1:-create}
export RESOURCE=${2}
export FILENAME=$(basename -- "${RESOURCE}")
export FILENAME="${FILENAME%.*}"

export APIGEE_ENV=${APIGEE_ENV:-eval}
export APIGEE_ORG=${APIGEE_ORG:-${PROJECT_ID}}

if [ -z "${APIGEE_ENV}" ] ; then
  echo "APIGEE_ENV is not set "
  exit 1
fi

if [ -z "${APIGEE_ORG}" ] ; then
  echo "APIGEE_ORG is not set"
  exit 1
fi

if  [ "${OPERATION}" != "create" ] && [ "${OPERATION}" != "update" ] && [ "${OPERATION}" != "delete" ]; then
  echo "Only create / update / delete operations supported ..."
  extit 1
fi

if [ ! -f "${RESOURCE}" ] ; then
  echo "Cannot locate resource ${RESOURCE} ..."
  exit 1
fi


export TOKEN="$(gcloud auth print-access-token --project "${APIGEE_ORG}")"

echo "${OPERATION} ${FILENAME} resource ..."
./tools/apigeecli/bin/apigeecli resources "${OPERATION}" \
   --name "${FILENAME}" \
   --type properties \
   --respath "${RESOURCE}" \
   --org "${APIGEE_ORG}" \
   --env "${APIGEE_ENV}" \
   --token "$TOKEN"

