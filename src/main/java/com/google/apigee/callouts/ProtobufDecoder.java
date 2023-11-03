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

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.apigee.callouts.util.Debug;
import com.google.apigee.callouts.util.VarResolver;
import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.util.JsonFormat;

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

    public static final String PROP_DECODED_MESSAGE_REF = "pb-decoded-message-ref";


    private final Map properties;


    public ProtobufDecoder(Map properties) throws UnsupportedEncodingException {
        this.properties = properties;
    }

    private void saveOutputs(MessageContext msgCtx, ByteArrayOutputStream stdoutOS, ByteArrayOutputStream stderrOS) {
        msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stdout", new String(stdoutOS.toByteArray(), StandardCharsets.UTF_8));
        msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stderr", new String(stderrOS.toByteArray(), StandardCharsets.UTF_8));
    }

    public FileDescriptor build(FileDescriptorProto proto, Map<String, FileDescriptorProto> protoMap) throws Descriptors.DescriptorValidationException {
        return build(proto, protoMap, new HashMap<>());
    }

    public FileDescriptor build(FileDescriptorProto proto, Map<String, FileDescriptorProto> protoMap, Map<String, FileDescriptor> buildCache) throws Descriptors.DescriptorValidationException {
        FileDescriptor cachedEntry = buildCache.get(proto.getName());
        if (cachedEntry != null) {
            return cachedEntry;
        }

        List<String> filesDependencyList = proto.getDependencyList();
        List<FileDescriptor> descDependencyList = new ArrayList<>();

        for (String fileDependency : filesDependencyList) {
            FileDescriptorProto fileProto = protoMap.get(fileDependency);
            if (fileProto == null) {
                throw new RuntimeException(String.format("proto dependency %s not found", fileDependency));
            }

            FileDescriptor fileDesc = build(fileProto, protoMap, buildCache);

            descDependencyList.add(fileDesc);
        }

        FileDescriptor fileDesc = Descriptors.FileDescriptor.buildFrom(proto, descDependencyList.toArray(new Descriptors.FileDescriptor[]{}));
        buildCache.put(proto.getName(), fileDesc);

        return fileDesc;
    }

    public MethodDescriptor getMethod(String descriptorBase64, String requestPath, String[] dependenciesBase64) throws IOException, Descriptors.DescriptorValidationException {
        if (descriptorBase64 == null || descriptorBase64.isEmpty()) {
            throw new RuntimeException("No protobuf descriptor provided");
        }

        //get the service name, and method name (e.g. example.package.ServiceName/MethodName)
        if (requestPath == null || requestPath.isEmpty()) {
            throw new RuntimeException("No request path provided");
        }

        String requestPathParts[] = requestPath.split("/");

        if (requestPathParts.length < 2) {
            throw new RuntimeException("expected at least 2 path segments");
        }

        String methodName = requestPathParts[requestPathParts.length - 1];
        String pkgAndService = requestPathParts[requestPathParts.length - 2];

        String[] pkgAndServiceParts = pkgAndService.split("\\.");
        String serviceName = pkgAndServiceParts[pkgAndServiceParts.length - 1];


        //get the protobuf descriptor
        byte[] descriptorBytes = Base64.getDecoder().decode(descriptorBase64.getBytes());
        FileDescriptorSet set = FileDescriptorSet.parseFrom(descriptorBytes);

        //build lookup maps
        Map<String, FileDescriptorProto> serviceLookupMap = new HashMap<>();
        Map<String, FileDescriptorProto> protoLookupMap = new HashMap<>();

        for (FileDescriptorProto fileDescProto : set.getFileList()) {
            protoLookupMap.put(fileDescProto.getName(), fileDescProto);
            List<ServiceDescriptorProto> serviceList = fileDescProto.getServiceList();
            for (ServiceDescriptorProto serviceDescProto : serviceList) {
                serviceLookupMap.put(serviceDescProto.getName(), fileDescProto);
            }
        }

        if (!serviceLookupMap.containsKey(serviceName)) {
            throw new RuntimeException(String.format("service %s not found in proto", serviceName));
        }

        FileDescriptorProto file = serviceLookupMap.get(serviceName);

        FileDescriptor fileDescriptor = build(file, protoLookupMap);

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


    String decodeAsText(InputStream inputStream, PrintStream stdout, PrintStream stderr) throws IOException {
        byte[] messagePayload = inputStream.readAllBytes();
        if (messagePayload.length == 0) {
            return "";
        }


        byte[] msgPayload = Arrays.copyOfRange(messagePayload, PAYLOAD_OFFSET, messagePayload.length);
        stdout.println("message-length: " + msgPayload.length);

        UnknownFieldSet unknownFieldSet = UnknownFieldSet.parseFrom(msgPayload);
        return TextFormat.printer().printToString(unknownFieldSet);
    }

    String decodeAsJSON(InputStream inputStream, Descriptor descriptor,  PrintStream stdout, PrintStream stderr) throws IOException, Descriptors.DescriptorValidationException {
        byte[] messagePayload = inputStream.readAllBytes();
        if (messagePayload.length == 0) {
            return "{}";
        }

        byte[] msgPayload = Arrays.copyOfRange(messagePayload, PAYLOAD_OFFSET, messagePayload.length);
        stdout.println("message-length: " + msgPayload.length);

        DynamicMessage msg = DynamicMessage.parseFrom(descriptor, msgPayload);
        return JsonFormat.printer().print(msg);
    }

    public ExecutionResult execute(MessageContext messageContext, ExecutionContext executionContext) {
        ByteArrayOutputStream stdoutOS = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrOS = new ByteArrayOutputStream();
        PrintStream stderr = null;
        PrintStream stdout = null;
        try {
            stdout = new PrintStream(stdoutOS, true, StandardCharsets.UTF_8.name());
            stderr = new PrintStream(stderrOS, true, StandardCharsets.UTF_8.name());

            VarResolver vars = new VarResolver(messageContext, properties);
            Debug dbg = new Debug(messageContext, CALLOUT_VAR_PREFIX);

            String protoDescriptorBase64 = vars.getVar(vars.getProp(PROP_DESCRIPTOR_BASE64_REF));
            String protoServiceMethodPath = vars.getVar(vars.getProp(PROP_SERVICE_METHOD_REF));
            String protoDecodedMessageRef = vars.getProp(PROP_DECODED_MESSAGE_REF);
            String protoProcessMessage = vars.getProp(PROP_MSG_REF);

            MethodDescriptor method = null;
            try {
                method = getMethod(protoDescriptorBase64, protoServiceMethodPath, new String[]{});
            } catch (Exception ex) {
                stdout.println("could not find protobuf service/method. " + ex.getMessage());
            }

            Message msg = null;
            Descriptor desc = null;
            if (PROCESS_RESPONSE.equals(protoProcessMessage)) {
                msg = messageContext.getResponseMessage();
                desc = (method == null) ? null : method.getOutputType();
            } else { // PROCESS_REQUEST
                msg = messageContext.getRequestMessage();
                desc = (method == null) ? null : method.getInputType();
            }

            String decoded = null;
            try {
                if (desc == null) {
                    //message protobuf is not know
                    decoded = decodeAsText(msg.getContentAsStream(), stdout, stderr);
                    messageContext.setVariable(String.format("%s.%s", CALLOUT_VAR_PREFIX, "message-format"), "text");
                } else {
                    //message protobuf is known
                    decoded = decodeAsJSON(msg.getContentAsStream(), desc, stderr, stderr);
                    messageContext.setVariable(String.format("%s.%s", CALLOUT_VAR_PREFIX, "message-format"), "json");
                }
            } catch (Exception ex) {
                stderr.println("could not decode protobuf. " + ex.getMessage());
            }

            if (decoded != null) {
                messageContext.setVariable(String.format("%s.%s", CALLOUT_VAR_PREFIX, "message-data"), decoded);
                if (protoDecodedMessageRef != null && !protoDecodedMessageRef.isEmpty()) {
                    messageContext.setVariable(protoDecodedMessageRef, decoded);
                }
            }

            return ExecutionResult.SUCCESS;
        } catch (Error | Exception e) {
            e.printStackTrace(stderr);
            return ExecutionResult.ABORT;
        } finally {
            saveOutputs(messageContext, stdoutOS, stderrOS);
        }
    }
}