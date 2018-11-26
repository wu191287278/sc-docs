## Getting Started

Please follow the [installation](#installation) instruction and execute the following Java code:

```java
package io.swagger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = {"io.swagger"})
public class Application {

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

}

```

## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AdminControllerApi* | [**changePosition**](docs/AdminControllerApi.md#changePosition) | **PUT** /admin/changePosition | Change the offset of binlog
*AdminControllerApi* | [**rebuild**](docs/AdminControllerApi.md#rebuild) | **PUT** /admin/rebuild | Rebuilding Elastic Search Index
*AdminControllerApi* | [**recognizeUnClassicResource**](docs/AdminControllerApi.md#recognizeUnClassicResource) | **PUT** /admin/recognizeUnClassicResource | Identify unclassified images
*SearchControllerApi* | [**clearGeoInfo**](docs/SearchControllerApi.md#clearGeoInfo) | **PUT** /search/v2/clearGeoInfo | 
*SearchControllerApi* | [**hunt**](docs/SearchControllerApi.md#hunt) | **GET** /search/v2/hunt | 
*SearchControllerApi* | [**invite**](docs/SearchControllerApi.md#invite) | **PUT** /search/v2/invite/{resourceId} | Editor invitation
*SearchControllerApi* | [**nearbyUser2**](docs/SearchControllerApi.md#nearbyUser2) | **GET** /search/v2/findNearbyUsers | 
*SearchControllerApi* | [**recommend**](docs/SearchControllerApi.md#recommend) | **PUT** /search/v2/recommend/{resourceId} | User recommendation invitation
*SearchControllerApi* | [**search2**](docs/SearchControllerApi.md#search2) | **GET** /search/v2/search | 
*SearchControllerApi* | [**sql**](docs/SearchControllerApi.md#sql) | **GET** /search/v2/_sql | 
*SearchControllerApi* | [**suggest**](docs/SearchControllerApi.md#suggest) | **GET** /search/v2/suggest | Search suggestion interface, the front-end processing is not very good, resulting in multiple queries in the background, affecting performance. Direct fuse off without treatment


## Documentation for Models

 - [PageInfo](docs/PageInfo.md)
 - [PageInfoUser](docs/PageInfoUser.md)
 - [ResponseDTO](docs/ResponseDTO.md)
 - [User](docs/User.md)


## Documentation for Authorization

All endpoints do not require authorization.
Authentication schemes defined for the API:

## Recommendation

It's recommended to create an instance of `ApiClient` per thread in a multithreaded environment to avoid any potential issues.

## Author



