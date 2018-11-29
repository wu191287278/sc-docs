package com.vcg.docs.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import java.io.IOException;
import java.util.*;

public class Swagger2OpenApi {

    private static ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static JsonNode convert(Swagger swagger) throws IOException {
        ObjectNode objectNode = objectMapper.createObjectNode();
        JsonNode swaggerNode = toJsonNode(swagger);
        ObjectNode components = objectMapper.createObjectNode();
        JsonNode info = getInfo(swaggerNode);
        JsonNode tags = getTags(swaggerNode);
        JsonNode servers = getServers(swaggerNode);
        JsonNode schemas = getSchemas(swaggerNode);
        JsonNode paths = getPaths(swaggerNode, components);
        JsonNode securityDefinitions = getSecurityDefinitions(swaggerNode);
        if (securityDefinitions != null) {
            components.set("securitySchemes", securityDefinitions);
        }
        components.set("schemas", schemas);
        objectNode.put("openapi", "3.0.0");
        objectNode.set("servers", servers);
        objectNode.set("info", info);
        objectNode.set("tags", tags);
        objectNode.set("paths", paths);
        objectNode.set("components", components);

        String result = objectMapper.writeValueAsString(objectNode)
                .replace("#/definitions/", "#/components/schemas/");
        return objectMapper.readValue(result, JsonNode.class);
    }

    public static JsonNode convert(String swagger) throws IOException {
        return convert(new SwaggerParser().parse(swagger));
    }


    private static JsonNode getInfo(JsonNode jsonNode) throws IOException {
        return jsonNode.get("info");
    }

    private static JsonNode getTags(JsonNode jsonNode) throws IOException {
        return jsonNode.get("tags");
    }

    private static JsonNode getServers(JsonNode jsonNode) throws IOException {
        String host = jsonNode.get("host").asText();
        String path = jsonNode.get("basePath").asText();
        JsonNode schemes = jsonNode.get("schemes");
        ArrayNode servers = objectMapper.createArrayNode();
        for (JsonNode schemeNode : schemes) {
            String scheme = schemeNode.asText().toLowerCase();
            ObjectNode server = objectMapper.createObjectNode().put("url", scheme + "://" + host + path);
            servers.add(server);
        }
        return servers;
    }

    private static JsonNode getSchemas(JsonNode jsonNode) throws IOException {
        return jsonNode.get("definitions");
    }

    private static JsonNode getSecurityDefinitions(JsonNode jsonNode) {
        return jsonNode.get("securityDefinitions");
    }

    private static JsonNode getPaths(JsonNode jsonNode, ObjectNode components) throws IOException {
        JsonNode paths = jsonNode.get("paths");
        for (Iterator<String> it = paths.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            JsonNode path = paths.get(name);
            for (Iterator<String> it2 = path.fieldNames(); it2.hasNext(); ) {
                String method = it2.next();
                ObjectNode methodNode = (ObjectNode) path.get(method);
                ArrayNode parameters = (ArrayNode) methodNode.get("parameters");
                ObjectNode bodyNode = null;
                int bodyNodeIndex = 0;
                for (int i = 0; i < parameters.size(); i++) {
                    ObjectNode parameterNode = (ObjectNode) parameters.get(i);
                    String in = parameterNode.get("in").asText();
                    if ("body".equalsIgnoreCase(in)) {
                        bodyNodeIndex = i;
                        bodyNode = parameterNode;
                    } else {
                        JsonNode type = parameterNode.remove("type");
                        JsonNode format = parameterNode.remove("format");
                        JsonNode aDefault = parameterNode.remove("default");
                        JsonNode example = parameterNode.remove("example");
                        ObjectNode schemaNode = objectMapper.createObjectNode();
                        if (type != null) {
                            schemaNode.set("type", type);
                        }
                        if (format != null) {
                            schemaNode.set("format", format);
                        }
                        if (aDefault != null) {
                            schemaNode.set("default", aDefault);
                        }
                        if (example != null) {
                            schemaNode.set("example", example);
                        }
                        parameterNode.set("schema", schemaNode);
                    }
                }

                JsonNode consumes = methodNode.remove("consumes");

                if (bodyNode != null) {
                    parameters.remove(bodyNodeIndex);
                    ObjectNode requestBodyNode = objectMapper.createObjectNode();
                    ObjectNode contentNode = objectMapper.createObjectNode();
                    JsonNode schema = bodyNode.remove("schema");
                    JsonNode bodyName = bodyNode.remove("name");
                    JsonNode description = bodyNode.get("description");
                    if (schema != null) {
                        if (consumes != null) {
                            for (JsonNode consume : consumes) {
                                ObjectNode consumeNode = objectMapper.createObjectNode();
                                consumeNode.set("schema", schema);
                                contentNode.set(consume.asText(), consumeNode);
                            }
                        } else {
                            ObjectNode consumeNode = objectMapper.createObjectNode();
                            consumeNode.set("schema", schema);
                            contentNode.set("application/json", consumeNode);
                        }
                        if (description != null) {
                            requestBodyNode.set("description", description);
                        }
                    }
                    if (contentNode.size() > 0) {
//                        ObjectNode requestBodies = (ObjectNode) components.get("RequestBodies");
//                        if (requestBodies == null) {
//                            requestBodies = objectMapper.createObjectNode();
//                            components.set("RequestBodies", requestBodies);
//                        }
//                        contentNode.put("required", true);
                        requestBodyNode.set("content", contentNode);
//                        requestBodies.set(bodyName.asText(), requestBodyNode);
                        methodNode.set("requestBody", requestBodyNode);
                    }
                }

                JsonNode produces = methodNode.remove("produces");
                JsonNode responses = methodNode.get("responses");
                for (JsonNode response : responses) {
                    ObjectNode contentNode = objectMapper.createObjectNode();
                    ObjectNode responseNode = (ObjectNode) response;
                    JsonNode schema = responseNode.remove("schema");
                    if (schema != null) {
                        if (produces != null) {
                            for (JsonNode produce : produces) {
                                ObjectNode produceNode = objectMapper.createObjectNode();
                                produceNode.set("schema", schema);
                                contentNode.set(produce.asText(), produceNode);
                            }
                        } else {
                            ObjectNode produceNode = objectMapper.createObjectNode();
                            produceNode.set("schema", schema);
                            contentNode.set("application/json", produceNode);
                        }
                    }
                    if (contentNode.size() > 0) {
                        responseNode.set("content", contentNode);
                    }
                }
            }
        }

        return paths;
    }

    private <T> Map<String, T> toMap(Object value, Class<T> clazz) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(HashMap.class, String.class, clazz);
        return objectMapper.readValue(objectMapper.writeValueAsString(value), javaType);
    }

    private <T> List<T> toArray(Object value, Class<T> clazz) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(ArrayList.class, clazz);
        return objectMapper.readValue(objectMapper.writeValueAsString(value), javaType);
    }

    private <T> T toObject(Object value, Class<T> clazz) throws IOException {
        return objectMapper.readValue(objectMapper.writeValueAsString(value), clazz);
    }

    private static JsonNode toJsonNode(Object value) throws IOException {
        return objectMapper.readValue(objectMapper.writeValueAsBytes(value), JsonNode.class);
    }

    public static void main(String[] args) throws IOException {
        Swagger swagger = new SwaggerParser().read("/Users/wuyu/IdeaProjects/sc-docs/docs/vc-chat-api/swagger.json");
        new Swagger2OpenApi();
        JsonNode openApi = convert(swagger);
        System.err.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openApi));
    }

}
