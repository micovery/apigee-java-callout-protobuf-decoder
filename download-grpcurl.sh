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
export GRPCURL_VERSION="1.8.8"

ACTUAL_OS="$(uname -s)"
ACTUAL_ARCH="$(uname -m)"


# Detect operating system
GRPCURL_OS=""
if [ "${ACTUAL_OS}" = "Darwin" ] ; then
  GRPCURL_OS="osx"
elif [ "${ACTUAL_OS}" = "Linux" ]; then
  GRPCURL_OS="linux"
else
  echo "Operating system ${ACTUAL_OS} not supported"
  exit 1
fi

#Detect architecture
GRPCURL_ARCH=""
if [ "${ACTUAL_ARCH}" = "arm64" ] ; then
  GRPCURL_ARCH="${ACTUAL_ARCH}"
elif [ "${ACTUAL_ARCH}" = "x86_64" ] ; then
  GRPCURL_ARCH="${ACTUAL_ARCH}"
else
    echo "Architecture system ${ACTUAL_ARCH} not supported"
    exit 1
fi


echo "*** Downloading grpcurl (version: ${GRPCURL_VERSION})"

export GRPCURL_FILE="grpcurl_${GRPCURL_VERSION}_${GRPCURL_OS}_${GRPCURL_ARCH}"
curl -o grpcurl.tar.gz -sfL "https://github.com/fullstorydev/grpcurl/releases/download/v${GRPCURL_VERSION}/${GRPCURL_FILE}.tar.gz"

echo "*** Extracting grpcurl.tar.gz ***"
rm -rf ./grpcurl
mkdir -p grpcurl/bin
tar -xzvf grpcurl.tar.gz -C ./grpcurl/bin "grpcurl"
rm -f grpcurl.tar.gz

chmod a+x ./grpcurl/bin/*





