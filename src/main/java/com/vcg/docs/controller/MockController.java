package com.vcg.docs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import com.vcg.docs.cache.SwaggerCache;
import io.swagger.models.*;
import io.swagger.models.properties.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

@RestController
public class MockController {

    @Autowired
    private ObjectMapper objectMapper;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    private Faker faker = new Faker();

    @RequestMapping(value = "/mock/{application}/**")
    public void mock(@PathVariable(value = "application") String application,
                     HttpServletRequest request,
                     HttpServletResponse response) throws IOException {
        Swagger swagger = SwaggerCache.get(application);
        if (swagger == null) {
            response.setStatus(404);
            return;
        }

        String path = request.getRequestURI()
                .replace("/mock/" + application, "")
                .replaceAll("[/]+", "/");

        Operation operation = lookup(swagger, request.getMethod(), path);
        if (operation == null) {
            response.setStatus(404);
            return;
        }

        reply(swagger, operation, response);

    }

    private Operation lookup(Swagger swagger, String method, String path) {
        Path value = swagger.getPath(path);
        if (value != null) {
            Map<HttpMethod, Operation> operationMap = value.getOperationMap();
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
            return operationMap.get(httpMethod);
        }

        Map<String, Path> paths = swagger.getPaths();
        for (Map.Entry<String, Path> entry : paths.entrySet()) {
            boolean match = antPathMatcher.match(entry.getKey(), path);
            if (match) {
                Map<HttpMethod, Operation> operationMap = entry.getValue().getOperationMap();
                HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
                return operationMap.get(httpMethod);
            }
        }

        return null;
    }


    private Object getExample(Swagger swagger, Response response) {
        Map<String, Object> examples = response.getExamples();
        if (examples != null) {
            return examples;
        }

        Property schema = response.getSchema();
        if (schema != null) {
            if (schema instanceof RefProperty) {
                RefProperty refProperty = (RefProperty) schema;
                Model definitions = swagger.getDefinitions().get(refProperty.getSimpleRef());
                if (definitions.getExample() != null) {
                    return definitions.getExample();
                }
            }
            if (schema.getExample() != null) {
                return schema.getExample();
            }
        }

        return null;
    }

    private Object generateExample(Swagger swagger, Response response) {
        Property schema = response.getSchema();
        if (schema != null) {
            return random(swagger, schema.getName(), schema);
        }

        return null;
    }

    private Object random(Swagger swagger, String name, Property schema) {
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        if (schema instanceof StringProperty) {
            String format = schema.getFormat();
            if ("date-time".equals(format)) {
                return faker.date().between(new Date(), new Date(System.currentTimeMillis() - 86400000));
            }

            if (name != null) {
                switch (name) {
                    case "name":
                        return faker.name().fullName();
                    case "firstName":
                        return faker.name().firstName();
                    case "lastName":
                        return faker.name().lastName();
                    case "fullName":
                        return faker.name().fullName();
                    case "email":
                        return faker.number().digit() + "@qq.com";
                    case "username":
                        return faker.name().username();
                    case "phone":
                        return faker.phoneNumber().phoneNumber();
                    case "mobile":
                        return faker.phoneNumber().phoneNumber();
                    case "music":
                        return faker.music().instrument();
                    case "color":
                        return faker.color().name();
                    case "address":
                        return faker.address().fullAddress();
                    case "city":
                        return faker.address().cityName();
                    case "country":
                        return faker.address().country();
                    case "state":
                        return faker.number().numberBetween(0, 10);
                    case "company":
                        return faker.company().name();
                    case "avatar":
                        return faker.company().logo();
                    case "url":
                        return faker.company().logo();
                }
            }
            return faker.name().fullName();
        }

        if (schema instanceof IntegerProperty) {
            return faker.number().numberBetween(0, 1000);
        }

        if (schema instanceof LongProperty) {
            return faker.number().numberBetween(0, 1000);
        }

        if (schema instanceof DoubleProperty) {
            return faker.number().randomDouble(2, 10, 100);
        }

        if (schema instanceof FileProperty) {
            return faker.file().fileName();
        }

        if (schema instanceof BinaryProperty) {
            return faker.slackEmoji().emoji().getBytes();
        }

        if (schema instanceof BooleanProperty) {
            return faker.bool().bool();
        }

        if (schema instanceof ArrayProperty) {
            ArrayProperty arrayProperty = (ArrayProperty) schema;
            Property items = arrayProperty.getItems();
            return Arrays.asList(random(swagger, null, items),
                    random(swagger, null, items),
                    random(swagger, null, items));
        }

        if (schema instanceof MapProperty) {
            MapProperty mapProperty = (MapProperty) schema;
            Property additionalProperties = mapProperty.getAdditionalProperties();
            Map<String, Object> map = new HashMap<>();
            map.put(faker.color().name(), random(swagger, null, additionalProperties));
            map.put(faker.color().name(), random(swagger, null, additionalProperties));
            map.put(faker.color().name(), random(swagger, null, additionalProperties));
            return map;
        }

        if (schema instanceof RefProperty) {
            RefProperty refProperty = (RefProperty) schema;
            Map<String, Model> definitions = swagger.getDefinitions();
            if (definitions != null) {
                Model model = definitions.get(refProperty.getSimpleRef());
                if (model != null) {
                    Map<String, Object> map = new HashMap<>();
                    Map<String, Property> properties = model.getProperties();
                    if (properties != null) {
                        for (Map.Entry<String, Property> entry : properties.entrySet()) {
                            String key = entry.getKey();
                            Property value = entry.getValue();
                            Object random = random(swagger, key, value);
                            map.put(key, random);
                        }
                    }
                    return map;
                }
            }
        }

        if (schema instanceof ObjectProperty) {
            ObjectProperty objectProperty = (ObjectProperty) schema;
            Map<String, Property> properties = objectProperty.getProperties();
            if (properties != null) {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, Property> entry : properties.entrySet()) {
                    String key = entry.getKey();
                    Property value = entry.getValue();
                    Object random = random(swagger, key, value);
                    map.put(key, random);
                }
                return map;
            }
        }

        return new HashMap<>();
    }

    private void reply(Swagger swagger, Operation operation, HttpServletResponse httpServletResponse) throws IOException {
        List<String> produces = operation.getProduces() == null ? Collections.EMPTY_LIST : operation.getProduces();
        Map<String, Response> responses = operation.getResponses();
        Response response = responses.get("200");
        if (response == null) {
            response = responses.entrySet().iterator().next().getValue();
        }
        String contentType = produces.size() > 0 ? produces.get(0) : MediaType.APPLICATION_JSON_UTF8_VALUE;
        Object example = getExample(swagger, response);
        if (example == null) {
            example = generateExample(swagger, response);
        }
        if (example != null) {
            byte[] content = objectMapper.writeValueAsBytes(example);
            httpServletResponse.setContentType(contentType);
            try (OutputStream out = httpServletResponse.getOutputStream()) {
                out.write(content);
            }
        }
    }
}
