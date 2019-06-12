# sc-docs

## Introduction

sc-docs 可以根据[Java docs规范](https://docs.oracle.com/javase/1.5.0/docs/tooldocs/windows/javadoc.html)以及springmvc 注解,jsr 311注解 生成 [swagger](https://swagger.io/specification/v2/) 文档,无需侵入到项目之中


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
curl -o sc-docs.jar -L https://github.com/wu191287278/sc-docs/releases/download/v2.7.0/sc-docs.jar
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
    @GetMapping(value = "user")
    public String user(@RequestParam(value = "nickname") String nickname) throws NotFoundException{
        return "user:"+nickname;
    }

}
```


## Getting Started


```shell
git clone https://github.com/shuzheng/zheng.git
java -jar sc-docs.jar -i ./zheng -o ./docs
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
