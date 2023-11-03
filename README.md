# Google APIS Passthrough API Proxy

This is a sample Apigee X API proxy that is able to dynamically pass through both gRPC, and REST APIs for *.googleapis.com

## How it works

For REST APIs, the API proxy uses a target server that points to private.googleapis.com


For gRPC APIs, the API proxy uses a target server that points to an envoy Dynamic forward proxy deployed in Cloud Run.
(This is necessary as Apigee does not currently support setting the `authority` or `host` header for gRPC targets)

## Deploying the API Proxy

* Download the `apigeecli` tool 
  ```shell
  ./download-apigeecli.sh
  ```

* Set the Apigee X organization and environment variables
  ```shell
    export APIGEE_ORG="your-apigee-org-name"
    export APIGEE_ENV="your-apigee-env-name"
  ```

* Deploy the target server definitions 
  ```shell 
  ./deploy-apigee-targetservers.sh
  ```

* Deploy the resource files with protobuf descriptors
  ```shell 
  ./deploy-apigee-resources.sh
  ```
  Pre compiled protobuf descriptors are provided for:
  * google.cloud.aiplatform.v1.PredictionService
  * google.cloud.translation.v3.TranslationService
  * google.cloud.language.v2.LanguageService
  

* Deploy the Apigee API Proxy ([apiproxy](/apiproxy))
  ```shell 
  ./deploy-apigee-apiproxy.sh
  ```
  
## Testing the API Proxy - gRPC

For testing, gRPC pass through let's use `language.googleapis.com`.

* Add `language.googleapis.com` to the list of hostnames for the environment group.
  (where you deployed the API Proxy to)


* Download the `grpcurl` client tool
  ```shell
  ./download-grpcurl.sh
  ```

* Download the googleapis proto files  (if not already)
  ```shell
  ./download-googleapis-protos.sh
  ```

* Go to [Google OAuth2 Playground](https://developers.google.com/oauthplayground), and get an OAuth 2 token for the `Cloud Natural Language API V2` API. The scopes needed are:
  * `https://www.googleapis.com/auth/cloud-language`
  * `https://www.googleapis.com/auth/cloud-platform`


  
* Use the `grpcurl` tool to invoke the API Proxy
```shell
export TOKEN="ya29.a0AfB_byD9YhJAKEIB3f9Xm7..."
export APIGEE_IP="34.160.129.252"

./tools/grpcurl/bin/grpcurl -insecure \
   -authority 'language.googleapis.com' \
   -H "Authorization: Bearer $TOKEN" \
   -import-path ./tools/googleapis \
   -proto ./tools/googleapis/google/cloud/language/v2/language_service.proto -d '{
  "document": {
    "type": "PLAIN_TEXT",
    "content": "hello world"
  }
}' ${APIGEE_IP}:443"  google.cloud.language.v2.LanguageService.AnalyzeSentiment
```

## Testing the API Proxy - REST

For testing, REST pass through let's use `language.googleapis.com`.

* Add `language.googleapis.com` to the list of hostnames for the environment group.
  (where you deployed the API Proxy to)


* Go to [Google OAuth2 Playground](https://developers.google.com/oauthplayground), and get an OAuth 2 token for the `Cloud Natural Language API V2` API. The scopes needed are:
  * `https://www.googleapis.com/auth/cloud-language`
  * `https://www.googleapis.com/auth/cloud-platform`


* Use the `cURL` tool to invoke the API Proxy
```shell
export TOKEN="ya29.a0AfB_byD9YhJAKEIB3f9Xm7..."
export APIGEE_IP="34.160.129.252"

curl -k -X POST "https://${APIGEE_IP}/v2/documents:analyzeSentiment" \
    -H "Host: language.googleapis.com" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '
{
  "document": {
    "type": "PLAIN_TEXT",
    "content": "hello world"
  }
}'
```

## License

This code is released under the Apache Source License v2.0. For information see the [LICENSE](LICENSE) file.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

