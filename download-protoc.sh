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
pushd ./tools
export PROTOC_VERSION="24.4"

ACTUAL_OS="$(uname -s)"
ACTUAL_ARCH="$(uname -m)"

# Detect operating system
PROTOC_OS=""
if [ "${ACTUAL_OS}" = "Darwin" ] ; then
  PROTOC_OS="osx"
elif [ "${ACTUAL_OS}" = "Linux" ]; then
  PROTOC_OS="linux"
else
  echo "Operating system ${ACTUAL_OS} not supported"
  exit 1
fi

#Detect architecture
PROTOC_ARCH=""
if [ "${ACTUAL_ARCH}" = "arm64" ] ; then
  PROTOC_ARCH="aarch_64"
elif [ "${ACTUAL_ARCH}" = "x86_64" ] ; then
  PROTOC_ARCH="${ACTUAL_ARCH}"
else
    echo "Architecture system ${ACTUAL_ARCH} not supported"
    exit 1
fi


echo "*** Downloading protoc (version: ${PROTOC_VERSION})"
export PROTOC_FILE="protoc-${PROTOC_VERSION}-${PROTOC_OS}-${PROTOC_ARCH}"
curl -o protoc.zip -sfL "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/${PROTOC_FILE}.zip"

echo "*** Extracting protoc.zip ***"
rm -rf ./protoc
unzip protoc.zip -d ./protoc
rm -f protoc.zip

chmod a+x ./protoc/bin/*





