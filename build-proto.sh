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
export PATH="$(pwd)/tools/protoc/bin:$PATH"



echo "*** Compiling Natural Language proto file ***"
protoc -I ./tools/googleapis  --descriptor_set_out=./target/language_service.desc ./tools/googleapis/google/cloud/language/v2/language_service.proto
echo ""
echo ""


echo "*** Copying proto descriptor to apiproxy ***"
cat << EOF > ./apiproxy/resources/properties/protos.properties
language_service=$(cat ./target/language_service.desc | base64 | tr -d '\n\r')
EOF
