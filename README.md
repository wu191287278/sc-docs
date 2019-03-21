# sc-docs

## Introduction

sc-docs is a [swagger](https://swagger.io/specification/v2/) tools that can be generated according to the [Java docs specification](https://docs.oracle.com/javase/1.5.0/docs/tooldocs/windows/javadoc.html)


## Requirements

Building the library requires [Maven](https://maven.apache.org/) and [Java8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) to be installed.


## installation

At first generate the JAR by executing:

```shell
mvn package
```

Then manually install the following JARs:

* target/sc-docs.jar

## Download

```
curl -o sc-docs.jar -L https://github.com/wu191287278/sc-docs/releases/download/v2.1/sc-docs.jar
```

## Java docs Example
```java
/**
 * search interface
 */
@RestController
@RequestMapping(value="/search")
public class SearchController {

    /**
     * search user
     *
     * @param nickname user nickname
     * @throws No user found
     * @return users
     */
    @GetMapping(value = "searchUser")
    public List<User> searchUser(@RequestParam(value = "nickname") String nickname) throws NotFoundException{
        return Arrays.as(User);
    }

}
```


## Getting Started

Please follow the [installation](#installation) instruction and execute the following shell code:

```shell
java -jar sc-docs.jar -i projectPath -o ./docs
```

## Start Server

```shell
java -jar sc-docs.jar serve ./docs
```

## Other
```
usage: java -jar sc-docs.jar  [-i <arg>] [-o <arg>] [-serve <arg>] [-t]
-i,--input <arg>    Source directory
-o,--output <arg>   Output directory
-serve <arg>        Start server
-t,--translation    Translate description
```

## Support environment variables

Name | Description
---|---
-Ddocs.**projectName**.host=localhost|swagger.json host
-Ddocs.**projectName**.basePath=/|swagger.json basePath
-Ddocs.**projectName**.scheme=http|swagger.json scheme
-Ddocs.**projectName**.info.title=demo|swagger.json info.title

## Using the environment example

```shell
java -Ddocs.projectName.host=localhost:8080 -Ddocs.projectName.scheme=http -Ddocs.projectName.info.title=demo -jar sc-docs.jar -i sourceDirectory -o outDirectory -t
```
