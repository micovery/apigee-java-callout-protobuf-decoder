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


APIGEE_ENV=${APIGEE_ENV:-eval}
APIGEE_ORG=${APIGEE_ORG:-${PROJECT_ID}}

if [ -z "${APIGEE_ENV}" ] ; then
  echo "APIGEE_ENV is not set "
  exit 1
fi

if [ -z "${APIGEE_ORG}" ] ; then
  echo "APIGEE_ORG is not set"
  exit 1
fi


echo "APIGEE_ENV=${APIGEE_ENV}"
echo "APIGEE_ORG=${APIGEE_ORG}"

export TOKEN="$(gcloud auth print-access-token --project "${APIGEE_ORG}")"

TARGET_SERVER="language-grpc"
TARGET_SERVER_HOST="language.googleapis.com"
#delete target server if already exists
if apigeecli targetservers get --name "${TARGET_SERVER}" \
     --org "${APIGEE_ORG}" \
     --env "${APIGEE_ENV}" \
     --token "${TOKEN}" &> /dev/null ; then

  echo "*** Target server ${TARGET_SERVER} already exists, updating it ..."
  apigeecli targetservers update \
     --name "${TARGET_SERVER}" \
     --port 443 \
     --host "${TARGET_SERVER_HOST}" \
     --enable \
     --tls true  \
     --org "${APIGEE_ORG}" \
     --env "${APIGEE_ENV}" \
     --protocol GRPC_TARGET \
     --token "${TOKEN}"
else
  echo "Creating target server ${TARGET_SERVER} ..."
  apigeecli targetservers create \
     --name "${TARGET_SERVER}" \
     --port 443 \
     --host "${TARGET_SERVER_HOST}" \
     --enable \
     --tls true  \
     --org "${APIGEE_ORG}" \
     --env "${APIGEE_ENV}" \
     --protocol GRPC_TARGET \
     --token "${TOKEN}"

fi


echo  "*** Creating language-grpc API Poxy bundle ..."
apigeecli apis create bundle \
  --token "${TOKEN}" \
  --org "${APIGEE_ORG}" \
  --proxy-folder ./apiproxy \
  --name language-grpc

echo "*** Deploying language-grpc API Proxy bundle ..."
apigeecli apis deploy \
  --token "${TOKEN}" \
  --org "${APIGEE_ORG}" \
  --env "${APIGEE_ENV}" \
  --name language-grpc  \
  --ovr \
  --wait