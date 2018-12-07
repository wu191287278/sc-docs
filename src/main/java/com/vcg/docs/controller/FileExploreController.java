package com.vcg.docs.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class FileExploreController {

    @Autowired
    private SpringTemplateEngine springTemplateEngine;

    private ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static File STATIC_FILE = new File("./");

    @GetMapping(value = "/static/**")
    public Object staticDirectory(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getRequestURI() == null ? "/" : request.getRequestURI();
        File file = new File(STATIC_FILE, pathInfo.replace("/static/", "/"));
        if (file.exists()) {
            response.setCharacterEncoding("utf-8");
            if (file.isDirectory()) {
                ModelAndView modelAndView = new ModelAndView("index");
                File[] list = file.listFiles();
                if (list != null) {

                    String directory = pathInfo.equals("/") ? "" : (pathInfo + "/").replaceAll("[/]+", "/");
                    List<FileItem> fileItems = Arrays.stream(list)
                            .map(f -> new FileItem(f.getName(), f.isDirectory()))
                            .collect(Collectors.toList());
                    FileInfo fileInfo = new FileInfo(directory, fileItems);
                    modelAndView.addObject("fileInfo", fileInfo);
                }
                return modelAndView;
            } else {
                Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(file.getName());
                if (mediaType.isPresent()) {
                    response.setContentType(mediaType.get().toString());
                } else {
                    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                }
                try (ServletOutputStream outputStream = response.getOutputStream();
                     BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {

                    //改变swagger 默认host 指定到代理服务中
                    String referer = request.getHeader("referer");
                    if ("swagger.json".equals(file.getName()) && referer != null && referer.contains("swagger-ui.html")) {
                        Swagger swaggerParser = new SwaggerParser()
                                .read(file.getAbsolutePath());
                        String scheme = request.getScheme();
                        int serverPort = request.getServerPort();
                        String serverName = request.getServerName();
                        swaggerParser.setHost(serverName + ":" + serverPort);
                        swaggerParser.setSchemes(Collections.singletonList(Scheme.valueOf(scheme.toUpperCase())));
                        String proxyBasePath = ("/proxy/" + file.getParentFile().getName() + "/" + swaggerParser.getBasePath())
                                .replaceAll("[/]+", "/");
                        swaggerParser.setBasePath(proxyBasePath);
                        IOUtils.write(objectMapper.writeValueAsBytes(swaggerParser), outputStream);
                    } else {
                        IOUtils.copy(in, outputStream);
                    }
                }
                return null;
            }
        }
        response.setStatus(404);
        return null;
    }

    @PostMapping(value = "/fileExplore/save")
    @ResponseBody
    public void save(@RequestParam(value = "filePath") String path,
                     @RequestBody String content) throws IOException {
        File file = new File(STATIC_FILE, path);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            writer.flush();
        }
    }

    @GetMapping(value = "/fileExplore/tree")
    @ResponseBody
    public Tree tree(HttpServletRequest request) {
        return recursiveTree(
                new Tree()
                        .setText(STATIC_FILE.getName())
                        .setNodes(new ArrayList<>())
                , STATIC_FILE);
    }

    private Tree recursiveTree(Tree tree, File file) {

        Tree childTree = new Tree()
                .setText(file.getName())
                .setNodes(file.isDirectory() ? new ArrayList<>() : null)
                .setHref(tree.getHref() + "/" + file.getName());

        if (file == STATIC_FILE) {
            childTree.setHref("/static")
                    .setText("/");
        }
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File childFile : list) {
                    recursiveTree(childTree, childFile);
                }
            }
        }

        tree.getNodes().add(childTree);

        return childTree;
    }

    @GetMapping(value = "/")
    public String index() {
        return "redirect:/static/";
    }

    @GetMapping(value = "/monaco")
    public String monaco() {
        return "monaco";
    }

    private static Map<String, Swagger> loadSwaggerFiles() {
        return FileUtils.listFiles(FileExploreController.STATIC_FILE, new String[]{"json"}, true)
                .stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getName().equals("swagger.json"))
                .collect(Collectors.toMap(f -> f.getParentFile().getName(), f -> new SwaggerParser()
                        .read(f.getAbsolutePath()))
                );
    }

    @Data
    @AllArgsConstructor
    private static class FileInfo {

        private String directory;

        private List<FileItem> fileItems;
    }

    @Data
    @AllArgsConstructor
    private static class FileItem {

        private String name;

        private boolean directory;

    }

    @Data
    @Accessors(chain = true)
    private static class Tree {

        private String text;

        @JsonInclude(value = JsonInclude.Include.NON_NULL)
        private String icon;

        @JsonInclude(value = JsonInclude.Include.NON_NULL)
        private List<Tree> nodes;

        private String href;
    }
}
