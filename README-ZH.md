# sc-docs

## Introduction

sc-docs 可以根据[Java docs规范](https://docs.oracle.com/javase/1.5.0/docs/tooldocs/windows/javadoc.html) 生成 [swagger](https://swagger.io/specification/v2/) 文档 


## Requirements

需要安装环境 [Maven](https://maven.apache.org/) and [Java8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) .


## installation

At first generate the JAR by executing:

```shell
mvn package
```

Then manually install the following JARs:

* target/sc-docs.jar

## Download

```
curl -o sc-docs.jar -L https://github.com/wu191287278/sc-docs/releases/download/v2.2.0/sc-docs.jar
```

## Java docs Example
```java
/**
 * 搜索接口
 */
@RestController
@RequestMapping(value="/search")
public class SearchController {

    /**
     * 搜索用户
     *
     * @param nickname 用户昵称
     * @throws 未找到用户
     * @return 用户列表
     */
    @GetMapping(value = "searchUser")
    public String searchUser(@RequestParam(value = "nickname") String nickname) throws NotFoundException{
        return "user:"+nickname;
    }

}
```


## Getting Started


```shell
java -jar sc-docs.jar -i 代码根目录(需要跟pom.xml一级,如果是多模块 请填写顶级模块路径) -o ./docs
```

## 启动静态服务器

```shell
java -jar sc-docs.jar -serve ./docs
```

## Other
```
usage: java -jar sc-docs.jar  [-i <arg>] [-o <arg>] [-serve <arg>] [-t]
-i,--input <arg>    Source directory
-o,--output <arg>   Output directory
-serve <arg>        Start server
-t,--translation    Translate description
```

## 支持环境变量替换swagger.json

Name | Description
---|---
-Ddocs.**projectName**.host=localhost|指定host
-Ddocs.**projectName**.basePath=/|指定基础路径
-Ddocs.**projectName**.scheme=http|指定http/https
-Ddocs.**projectName**.info.title=demo|指定标题

## 环境变量使用样例

```shell
java -Ddocs.projectName.host=localhost:8080 -Ddocs.projectName.scheme=http -Ddocs.projectName.info.title=demo -jar sc-docs.jar -i sourceDirectory -o outDirectory -t
```
