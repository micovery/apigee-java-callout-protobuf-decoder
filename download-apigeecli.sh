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
export APIGEECLI_VERSION="v1.123.2-beta"

echo "*** Downloading apigeecli (version: ${APIGEECLI_VERSION})"
export APIGEECLI_DIR="apigeecli_${APIGEECLI_VERSION}_$(uname -s)_$(uname -m)"
curl -o apigeecli.zip -sfL "https://github.com/apigee/apigeecli/releases/download/${APIGEECLI_VERSION}/${APIGEECLI_DIR}.zip"

echo "*** Extracting apigeecli ***"
rm -rf ./apigeecli
mkdir -p ./apigeecli/bin

unzip -o -p ./apigeecli.zip "${APIGEECLI_DIR}/apigeecli" > ./apigeecli/bin/apigeecli
rm -f ./apigeecli.zip

chmod a+x ./apigeecli/bin/*
