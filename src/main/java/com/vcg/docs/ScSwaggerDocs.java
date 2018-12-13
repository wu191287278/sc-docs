package com.vcg.docs;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.collect.ImmutableMap;
import com.vcg.docs.controller.FileExploreController;
import com.vcg.docs.swaggerhub.SwaggerHubClient;
import com.vcg.docs.swaggerhub.SwaggerHubRequest;
import com.vcg.docs.translate.TransApi;
import com.vcg.docs.utils.Swagger2OpenApi;
import com.vcg.docs.visitor.ApiDocsGenerator;
import com.vcg.docs.visitor.RestVisitorAdapter;
import io.github.swagger2markup.GroupBy;
import io.github.swagger2markup.OrderBy;
import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.builder.Swagger2MarkupConfigBuilder;
import io.github.swagger2markup.markup.builder.MarkupLanguage;
import io.swagger.codegen.*;
import io.swagger.models.*;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.BasicAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.OAuth2Definition;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.wrapper.MavenWrapperMain;
import org.asciidoctor.*;
import org.jruby.RubyInstanceConfig;
import org.jruby.javasupport.JavaEmbedUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.asciidoctor.Asciidoctor.Factory.create;

@Slf4j
@SpringBootApplication
public class ScSwaggerDocs {

    private String title = "Api Documentation";

    private String description = "";

    private String version = "1.0";

    private String basePath = "/";

    private String host = "localhost";

    private ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ObjectMapper yamlMapper = new YAMLMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4e00-\u9fa5]");


    public Map<String, Swagger> parse(String sourceDirectory, String basePackage) {
        copyDependencies(sourceDirectory);
        log.info("Parsing " + sourceDirectory);
        List<File> filteredDirectories = getSourceDirectories(sourceDirectory, basePackage);
        List<File> sourceDirectories = getSourceDirectories(sourceDirectory, basePackage);
        Map<String, Swagger> swaggerMap = new TreeMap<>();
        for (File filteredDirectory : filteredDirectories) {
            String projectPath = filteredDirectory.getAbsolutePath().replace("src/main/java", "")
                    .replace("src\\main\\java", "");

            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            for (File sourceFile : sourceDirectories) {
                typeSolver.add(new JavaParserTypeSolver(sourceFile));
            }

            try {
                typeSolver.add(new ReflectionTypeSolver(false));
                File dependency = new File(projectPath + "/target/dependency");
                JarTypeSolver jarTypeSolver = null;
                if (dependency.exists()) {
                    for (File jar : Objects.requireNonNull(dependency.listFiles(pathname -> pathname.getName().endsWith(".jar")))) {
                        jarTypeSolver = JarTypeSolver.getJarTypeSolver(jar.getAbsolutePath());
                    }
                }
                if (jarTypeSolver != null) {
                    typeSolver.add(jarTypeSolver);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }


            final RestVisitorAdapter restVisitorAdapter = new RestVisitorAdapter();
            Info info = new Info()
                    .title(this.title)
                    .description(this.description)
                    .version(this.version);
            final Swagger swagger = new Swagger()
                    .info(info)
                    .paths(new TreeMap<>())
                    .basePath(this.basePath)
                    .host(this.host)
                    .securityDefinition("api_key", new ApiKeyAuthDefinition("Authorization", In.HEADER))
                    .securityDefinition("oauth2", new OAuth2Definition()
                            .implicit("http://petstore.swagger.io/oauth/dialog")
                            .scope("write:pets", "modify pets in your account")
                            .scope("read:pets", "read your pets")
                    )
                    .securityDefinition("basic", new BasicAuthDefinition());


            ParserConfiguration parserConfiguration = new ParserConfiguration();
            parserConfiguration.setSymbolResolver(new JavaSymbolSolver(typeSolver));

            SourceRoot sourceRoot = new SourceRoot(Paths.get(filteredDirectory.getAbsolutePath()), parserConfiguration);
            List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParseParallelized();
            for (ParseResult<CompilationUnit> parseResult : parseResults) {
                parseResult.ifSuccessful(r -> r.accept(restVisitorAdapter, swagger));
            }
            for (Map.Entry<String, Model> entry : restVisitorAdapter.getModelMap().entrySet()) {
                swagger.model(entry.getKey(), entry.getValue());
            }


            Set<String> pathTagNames = new HashSet<>();
            if (swagger.getPaths() != null && !swagger.getPaths().isEmpty()) {
                String projectName = new File(projectPath).getName();
                String title = System.getProperty("docs." + projectName + ".info.title", projectName);
                String host = System.getProperty("docs." + projectName + ".host", this.host);
                String basePath = System.getProperty("docs." + projectName + ".basePath", "/");
                String scheme = System.getProperty("docs." + projectName + ".scheme", "http");
                swagger.getInfo().title(title);
                swagger.host(host);
                swagger.scheme(Scheme.valueOf(scheme.toUpperCase()));
                swagger.basePath(basePath);
                swaggerMap.put(projectName, swagger);
                for (Path path : swagger.getPaths().values()) {
                    for (Operation operation : path.getOperations()) {
                        List<String> tags = operation.getTags();
                        if (tags != null) {
                            pathTagNames.addAll(tags);
                        }
                        Map<String, List<String>> security = Stream.of("api_key", "oauth2", "basic")
                                .collect(Collectors.toMap(s -> s, s -> new ArrayList<String>()));
                        operation.setSecurity(Collections.singletonList(security));
                    }
                }
            }

            if (swagger.getTags() != null) {
                Map<String, Tag> tagMap = swagger.getTags()
                        .stream()
                        .collect(Collectors.toMap(Tag::getName, t -> t));

                for (String tagName : new HashSet<>(tagMap.keySet())) {
                    if (!pathTagNames.contains(tagName)) {
                        tagMap.remove(tagName);
                    }
                }

                swagger.tags(new ArrayList<>(new TreeMap<>(tagMap).values()));
            }

        }

        return swaggerMap;
    }

    private List<File> getSourceDirectories(String sourceDirectory, String basePackage) {
        List<File> files = new ArrayList<>();
        filterSourceDirectory(sourceDirectory, basePackage == null ? "" : basePackage, files);
        return files;
    }

    private void filterSourceDirectory(String sourceDirectory, String basePackage, List<File> files) {
        File parentDirectoryFile = new File(sourceDirectory);
        if (!parentDirectoryFile.isDirectory()) return;

        File sourceDirectoryFile = new File(sourceDirectory, "/src/main/java/" + basePackage.replace(".", "/"));

        if (!sourceDirectoryFile.exists()) {
            File[] listFiles = parentDirectoryFile.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    filterSourceDirectory(file.getAbsolutePath(), basePackage, files);
                }
            }
        } else {
            files.add(sourceDirectoryFile);
        }
    }

    public Swagger translate(Swagger swagger) {
        if (swagger.getTags() != null) {
            for (Tag tag : swagger.getTags()) {
                tag.setDescription(translate(tag.getDescription()));
            }
        }

        if (swagger.getPaths() != null) {
            for (Map.Entry<String, Path> entry : swagger.getPaths().entrySet()) {
                Path value = entry.getValue();
                for (Operation operation : value.getOperations()) {
                    for (Parameter parameter : operation.getParameters()) {
                        String translate = translate(parameter.getDescription());
                        parameter.setDescription(translate);
                    }

                    for (Map.Entry<String, Response> responseEntry : operation.getResponses().entrySet()) {
                        String description = responseEntry.getValue().getDescription();
                        responseEntry.getValue().description(translate(description));
                    }

                    operation.summary(translate(operation.getSummary()));
                }
            }
        }

        if (swagger.getDefinitions() != null) {
            for (Map.Entry<String, Model> entry : swagger.getDefinitions().entrySet()) {
                Model value = entry.getValue();
                value.setDescription(translate(value.getDescription()));
                Map<String, Property> properties = value.getProperties();
                if (properties != null) {
                    for (Property property : properties.values()) {
                        property.description(translate(property.getDescription()));
                    }
                }
            }
        }


        return swagger;
    }


    private String translate(String content) {
        if (StringUtils.isNotBlank(content) && CHINESE_PATTERN.matcher(content).find()) {
            try {

                return TransApi.translate(content.replaceAll("<[^>]+>", ""));
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        return content;
    }


    public void write(Swagger swagger, String format, String outDirectory) throws IOException {
        File outFile = new File(outDirectory);
        if (outFile.exists()) {
            outDirectory = outFile.getAbsolutePath();
        } else {
            outFile.mkdirs();
        }

        TemplateEngine templateEngine = templateEngine();

        Swagger2MarkupConfigBuilder builder = new Swagger2MarkupConfigBuilder()
                .withBasePathPrefix()
                .withInterDocumentCrossReferences()
                .withPathsGroupedBy(GroupBy.TAGS)
                .withTagOrdering(OrderBy.AS_IS)
                .withoutPathSecuritySection()
                .withGeneratedExamples()
                .withFlatBody();


        Attributes toc = new Attributes("toc");
        toc.setTitle(swagger.getInfo().getTitle());
        //auto, left, right, macro, preamble
        toc.setTableOfContents(Placement.LEFT);
        //html5, docbook5, docbook45
        toc.setDocType("docbook5");
        toc.setSectNumLevels(4);
        OptionsBuilder optionsBuilder = OptionsBuilder.options()
                .headerFooter(true)
                .inPlace(true)
                .toFile(true)
                .attributes(toc)
                .safe(SafeMode.SAFE);

        String redocs = templateEngine.process("redocs", new Context(Locale.CHINESE, ImmutableMap.of("title", swagger.getInfo().getTitle())));
        String swaggerUI = templateEngine.process("swagger-ui", new Context(Locale.CHINESE, ImmutableMap.of("title", swagger.getInfo().getTitle())));

        if ("md".equalsIgnoreCase(format)) {
            builder.withMarkupLanguage(MarkupLanguage.MARKDOWN);
        } else if ("html".equals(format)) {
            builder.withMarkupLanguage(MarkupLanguage.ASCIIDOC);
            optionsBuilder.backend("html5");
        } else if ("yaml".equals(format)) {
            try (FileWriter outputStream = new FileWriter(new File(outFile, "swagger.yml"))) {
                String json = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(swagger);
                outputStream.write(json);
            }

            try (FileWriter outputStream = new FileWriter(new File(outFile, "openapi.yml"))) {
                JsonNode openApi = Swagger2OpenApi.convert(swagger);
                String json = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openApi);
                outputStream.write(json);
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
            return;
        } else if ("api".equals(format)) {
            String folder = System.getProperty("java.io.tmpdir") + "/swagger-codegen/" + UUID.randomUUID().toString();
            ClientOpts clientOpts = new ClientOpts();
            clientOpts.setOutputDirectory(outFile.getAbsolutePath());
            ClientOptInput clientOptInput = new ClientOptInput()
                    .opts(clientOpts)
                    .swagger(swagger);
            CodegenConfig config = new ApiDocsGenerator();
            config.additionalProperties().put("swagger", swagger);
            config.additionalProperties().put(CodegenConstants.LIBRARY, "feign");
            config.setOutputDir(folder);
            clientOptInput.setConfig(config);
            List<File> files = new DefaultGenerator()
                    .opts(clientOptInput)
                    .generate();
            String docsify = templateEngine.process("docsify", new Context(Locale.CHINESE, ImmutableMap.of("title", swagger.getInfo().getTitle())));
            FileUtils.copyDirectory(new File(folder + "/docs"), new File(outDirectory + "/api/docs"));
            FileUtils.copyFile(new File(folder + "/README.md"), new File(outDirectory + "/api/README.md"));
            try (FileWriter docsifyWriter = new FileWriter(outDirectory + "/api/index.html")) {
                IOUtils.write(docsify, docsifyWriter);
            }
            return;
        } else {
            try (FileWriter outputStream = new FileWriter(new File(outFile, "swagger.json"));
                 FileWriter openApiOutputStream = new FileWriter(new File(outFile, "openapi.json"));
                 FileWriter redocsWriter = new FileWriter(new File(outFile, "redocs.html"));
                 FileWriter swaggerUIWriter = new FileWriter(new File(outFile, "swagger-ui.html"))) {
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(swagger);
                String openApi = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Swagger2OpenApi.convert(swagger));
                outputStream.write(json);
                openApiOutputStream.write(openApi);
                IOUtils.write(redocs, redocsWriter);
                IOUtils.write(swaggerUI, swaggerUIWriter);
                return;
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        Swagger2MarkupConverter.from(swagger)
                .withConfig(builder.build())
                .build()
                .toFile(Paths.get(outFile.getAbsolutePath() + "/index"));

        RubyInstanceConfig rubyInstanceConfig = new RubyInstanceConfig();
        rubyInstanceConfig.setLoader(this.getClass().getClassLoader());
        JavaEmbedUtils.initialize(Arrays.asList("META-INF/jruby.home/lib/ruby/2.0", "classpath:/gems/asciidoctor-1.5.4/lib"), rubyInstanceConfig);
        create(this.getClass().getClassLoader())
                .convertDirectory(new AsciiDocDirectoryWalker(outDirectory), optionsBuilder);
    }

    private TemplateEngine templateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver classLoaderTemplateResolver = new ClassLoaderTemplateResolver(this.getClass().getClassLoader());
        classLoaderTemplateResolver.setPrefix("/templates/");
        classLoaderTemplateResolver.setSuffix(".html");
        templateEngine.addTemplateResolver(classLoaderTemplateResolver);
        return templateEngine;
    }

    private void copyDependencies(String sourceDirectory) {
        System.setProperty("maven.multiModuleProjectDirectory", sourceDirectory);
        try {
            log.info("downloading maven...");
            MavenWrapperMain.main(new String[]{"install", "-Dmaven.test.skip=true", "-f", sourceDirectory});
            MavenWrapperMain.main(new String[]{"dependency:copy-dependencies", "-f", sourceDirectory});
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
//        args = new String[]{"-i", "/Users/wuyu/IdeaProjects/vc-chat2", "-o", "./docs"};
//        args = new String[]{"-serve", "/Users/wuyu/sc-generator/docs"};
        Options options = new Options();
        options.addOption(new Option("h", "help", false, "help"));
        options.addOption(new Option("i", "input", true, "Source directory"));
        options.addOption(new Option("o", "output", true, "Output directory"));
        options.addOption(new Option("t", "translation", false, "Translate description"));
        options.addOption(new Option("f", "format", true, "Formatted document eg: json,yaml,api,html,md"));
        options.addOption(new Option("serve", true, "Start server"));
        options.addOption(new Option("upload", true, "Local file containing the API definition in json"));
        HelpFormatter hf = new HelpFormatter();
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine commandLine = parser.parse(options, args);
            if (commandLine.hasOption("i")) {
                String sourceDirectory = commandLine.getOptionValue("i");
                ScSwaggerDocs scSwaggerDocsMojo = new ScSwaggerDocs();
                Map<String, Swagger> swaggerMap = scSwaggerDocsMojo.parse(sourceDirectory, null);
                for (Map.Entry<String, Swagger> entry : swaggerMap.entrySet()) {
                    Swagger swagger = entry.getValue();
                    if (commandLine.hasOption("t")) {
                        scSwaggerDocsMojo.translate(swagger);
                    }

                    String[] formats = commandLine.hasOption("f") ? commandLine.getOptionValue("f").split(",") : new String[]{"json", "api", "yaml", "html", "md"};
                    String outDirectory = "./docs/";
                    if (commandLine.hasOption("o")) {
                        outDirectory = commandLine.getOptionValue("o") + "/";
                    }
                    for (String format : formats) {
                        scSwaggerDocsMojo.write(swagger, format, outDirectory + entry.getKey());
                    }
                }
            }

            if (commandLine.hasOption("upload")) {
                String filePath = commandLine.getOptionValue("upload");
                String swagger = IOUtils.toString(new File(filePath).toURI(), Charset.forName("utf-8"));
                String isPrivate = System.getProperty("swaggerhub.isPrivate");
                String owner = System.getProperty("swaggerhub.owner");
                String name = System.getProperty("swaggerhub.name");
                String token = System.getProperty("swaggerhub.token");
                String version = System.getProperty("swaggerhub.version");

                String format = System.getProperty("swaggerhub.format");
                SwaggerHubRequest swaggerHubRequest = new SwaggerHubRequest()
                        .setSwagger(swagger)
                        .setOwner(owner)
                        .setToken(token)
                        .setVersion(version)
                        .setFormat(format == null ? "json" : format)
                        .setName(name)
                        .setPrivate(Boolean.parseBoolean(isPrivate));
                if (owner == null || name == null || token == null || version == null) {
                    throw new RuntimeException("Name|owner|name|token cannot be empty:\r\n" + swaggerHubRequest.toString());
                }

                log.info("Uploading...");
                SwaggerHubClient swaggerHubClient = new SwaggerHubClient();
                boolean isSuccess = false;
                for (int i = 0; i < 3; i++) {
                    isSuccess = swaggerHubClient.saveDefinition(swaggerHubRequest);
                    if (isSuccess) {
                        log.info("Uploaded successfully!");
                        break;
                    } else {
                        Thread.sleep(1000);
                    }
                }
                if (!isSuccess) {
                    log.error("Upload failed, please try again");
                }
            }

            if (commandLine.hasOption("serve")) {
                String aStatic = commandLine.getOptionValue("serve");
                if (aStatic != null) {
                    FileExploreController.STATIC_FILE = new File(aStatic);
                }
                ConfigurableApplicationContext ctx = SpringApplication.run(ScSwaggerDocs.class, args);
                Environment env = ctx.getBean(Environment.class);
                System.err.println("Serving files from undefined at http://localhost:" + env.getProperty("server.port", "8080") + "/\n" +
                        "Press Ctrl+C to quit\n");
            }


            if (commandLine.hasOption("h") || commandLine.getOptions().length == 0) {
                hf.printHelp("java -jar sc-docs.jar ", options, true);
            }
        } catch (ParseException e) {
            hf.printHelp("java -jar sc-docs.jar ", options, true);
        }
    }
}


