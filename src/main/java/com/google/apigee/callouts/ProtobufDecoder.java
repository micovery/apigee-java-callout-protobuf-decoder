// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.apigee.callouts;

import com.apigee.flow.message.Message;
import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;

import com.google.protobuf.util.JsonFormat;


import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.google.apigee.callouts.util.Debug;
import com.google.apigee.callouts.util.VarResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class ProtobufDecoder implements Execution {
  public static final String CALLOUT_VAR_PREFIX = "pb-decoder";
  public static final int PAYLOAD_OFFSET = 5;
  public static final String PROCESS_REQUEST = "request";
  public static final String PROCESS_RESPONSE = "response";

  public static final String PROP_MSG_REF = "pb-message-ref";
  public static final String PROP_SERVICE_METHOD_REF = "pb-service-method-ref";
  public static final String PROP_DESCRIPTOR_BASE64_REF = "pb-descriptor-base64-ref";

  private final Map properties;
  private ByteArrayOutputStream stdoutOS;
  private ByteArrayOutputStream stderrOS;
  private PrintStream stdout;
  private PrintStream stderr;

  public ProtobufDecoder(Map properties) throws UnsupportedEncodingException {
    this.properties = properties;
    this.stdoutOS = new ByteArrayOutputStream();
    this.stderrOS = new ByteArrayOutputStream();
    this.stdout = new PrintStream(stdoutOS, true, StandardCharsets.UTF_8.name());
    this.stderr = new PrintStream(stderrOS, true, StandardCharsets.UTF_8.name());
  }

  private void saveOutputs(MessageContext msgCtx) {
    msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stdout", new String(stdoutOS.toByteArray(), StandardCharsets.UTF_8));
    msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stderr", new String(stderrOS.toByteArray(), StandardCharsets.UTF_8));
  }

  public MethodDescriptor getMethod(String descriptorBas64, String requestPath) throws IOException, Descriptors.DescriptorValidationException {
    //get the service name, and method name (e.g. example.package.ServiceName/MethodName)
    if (requestPath == null || requestPath.isEmpty()) {
      throw new RuntimeException("No request path found");
    }

    String requestPathParts[] = requestPath.split("/");

    if (requestPathParts.length < 2) {
      throw new RuntimeException("expected at least 2 path segments");
    }

    String methodName = requestPathParts[requestPathParts.length-1];
    String pkgAndService = requestPathParts[requestPathParts.length - 2];

    String[] pkgAndServiceParts = pkgAndService.split("\\.");
    String serviceName = pkgAndServiceParts[pkgAndServiceParts.length - 1];

    //get the protobuf descriptor
    byte[] descriptorBytes = Base64.getDecoder().decode(descriptorBas64.getBytes());
    FileDescriptorSet set = FileDescriptorSet.parseFrom(descriptorBytes);
    FileDescriptorProto file = set.getFile(0);

    FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[]{});

    //get the service descriptor
    ServiceDescriptor service = fileDescriptor.findServiceByName(serviceName);
    if (service == null) {
      throw new RuntimeException(String.format("could not find service named %s", serviceName));
    }

    //get the method descriptor
    MethodDescriptor method = service.findMethodByName(methodName);
    if (method == null) {
      throw new RuntimeException(String.format("could not find method named %s in service %s", methodName, serviceName));
    }

    return method;
  }

  String decode(Message message, Descriptor descriptor) throws IOException, Descriptors.DescriptorValidationException {
    byte[] messagePayload = message.getContentAsStream().readAllBytes();
    if (messagePayload.length == 0) {
      return null;
    }

    DynamicMessage msg = DynamicMessage.parseFrom(descriptor, Arrays.copyOfRange(messagePayload, PAYLOAD_OFFSET, messagePayload.length));
    return JsonFormat.printer().print(msg);
  }

  public ExecutionResult execute(MessageContext messageContext, ExecutionContext executionContext) {
    try {
      VarResolver vars = new VarResolver(messageContext, properties);
      Debug dbg = new Debug(messageContext, CALLOUT_VAR_PREFIX);

      String protoDescriptorBase64 = vars.getVar(vars.getProp(PROP_DESCRIPTOR_BASE64_REF));
      String protoServiceMethodPath = vars.getVar(vars.getProp(PROP_SERVICE_METHOD_REF));
      String protoProcessMessage = vars.getProp(PROP_MSG_REF);

      MethodDescriptor method = getMethod(protoDescriptorBase64, protoServiceMethodPath);

      if (method == null) {
        throw new RuntimeException("could not find protobuf service/method");
      }

      String json = null;
      switch (protoProcessMessage) {
        case PROCESS_RESPONSE:
          json = decode(messageContext.getResponseMessage(), method.getOutputType());
          break;
        case PROCESS_REQUEST:
        default:
          json = decode(messageContext.getRequestMessage(), method.getInputType());
      }

      messageContext.setVariable(String.format("%s.%s", CALLOUT_VAR_PREFIX, "message-json"), json);

      return ExecutionResult.SUCCESS;
    } catch (Error | Exception e) {
      e.printStackTrace(stderr);
      return ExecutionResult.ABORT;
    } finally {
      saveOutputs(messageContext);
    }
  }
}