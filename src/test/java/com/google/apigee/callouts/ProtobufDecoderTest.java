package com.google.apigee.callouts;

import com.google.common.primitives.Bytes;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;


class ProtobufDecoderTest {

    @Test
    void decodeAsText() throws IOException {
        ProtobufDecoder pbDecoder = new ProtobufDecoder(new HashMap());
        byte[] prefix = {0,0,0,0,0x11}; // 5 bytes header
        byte[] rawMsg = Base64.getDecoder().decode("CgVoZWxsbxIHCDUSA3JlZBIICDcSBGJsdWU=");
        String decoded = pbDecoder.decodeAsText(new ByteArrayInputStream(Bytes.concat(prefix, rawMsg)), System.out, System.err);
        assertNotNull(decoded);
        assertTrue(!decoded.isEmpty());
    }

    @Test
    void getPredictMethod() throws IOException, Descriptors.DescriptorValidationException {
        testProtoMethod("google.cloud.aiplatform.v1.PredictionService", "Predict");
    }

    @Test
    void getTranslateTextMethod() throws IOException, Descriptors.DescriptorValidationException {
        testProtoMethod("google.cloud.translation.v3.TranslationService", "TranslateText");
    }

    @Test
    void getAnalyzeSentimentMethod() throws IOException, Descriptors.DescriptorValidationException {
        testProtoMethod("google.cloud.language.v2.LanguageService", "AnalyzeSentiment");
    }

    void testProtoMethod(String rpcPackage, String rpcMethod) throws IOException, Descriptors.DescriptorValidationException {
        Properties prop = new Properties();
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(String.format("%s.properties", rpcPackage.replaceAll("\\.","_")));
        assertNotNull(resourceAsStream);
        prop.load(resourceAsStream);
        ProtobufDecoder pbDecoder = new ProtobufDecoder(new HashMap());
        String serviceBase64 = prop.getProperty("proto_service_descriptor");
        assertNotNull(serviceBase64);
        Descriptors.MethodDescriptor methodDescriptor = pbDecoder.getMethod(serviceBase64, String.format("%s/%s", rpcPackage, rpcMethod), new String[]{});
        assertNotNull(methodDescriptor);
    }
}