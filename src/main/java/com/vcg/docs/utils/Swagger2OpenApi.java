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
        JsonNode paths = getPaths(swaggerNode);
        JsonNode securityDefinitions = getSecurityDefinitions(swaggerNode);
        if (securityDefinitions != null) {
            components.set("securitySchemes", securityDefinitions);
        }
        if (schemas != null) {
            components.set("schemas", schemas);
        }
        objectNode.put("openapi", "3.0.0");
        objectNode.set("servers", servers);
        if (info != null) {
            objectNode.set("info", info);
        }
        if (tags != null) {
            objectNode.set("tags", tags);
        }
        objectNode.set("paths", paths);
        objectNode.set("components", components);

        String result = objectMapper.writeValueAsString(objectNode)
                .replace("#/definitions/", "#/components/schemas/")
                .replace("«", "")
                .replace("»", "");
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
        String host = jsonNode.get("host") == null ? "localhost" : jsonNode.get("host").asText();
        JsonNode path = jsonNode.get("basePath");
        JsonNode schemes = jsonNode.get("schemes");
        ArrayNode servers = objectMapper.createArrayNode();
        if (schemes != null) {
            for (JsonNode schemeNode : schemes) {
                String scheme = schemeNode.asText().toLowerCase();
                ObjectNode server = objectMapper.createObjectNode().put("url", scheme + "://" + host + (path == null ? "/" : path));
                servers.add(server);
            }
            return servers;
        }
        return servers.add(objectMapper.createObjectNode().put("url", "http://localhost"));
    }

    private static JsonNode getSchemas(JsonNode jsonNode) throws IOException {
        return jsonNode.get("definitions");
    }

    private static JsonNode getSecurityDefinitions(JsonNode jsonNode) {
        return jsonNode.get("securityDefinitions");
    }

    private static JsonNode getPaths(JsonNode jsonNode) throws IOException {
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
                ObjectNode fileNode = null;
                int fileNodeIndex = 0;
                for (int i = 0; i < parameters.size(); i++) {
                    ObjectNode parameterNode = (ObjectNode) parameters.get(i);
                    String in = parameterNode.get("in").asText();

                    //防止 get方法携带body体
                    if ("get".equalsIgnoreCase(method) && "body".equalsIgnoreCase(in)) {
                        parameterNode.put("in", "query");
                        in = "query";
                    }

                    JsonNode type = parameterNode.remove("type");
                    if ("body".equalsIgnoreCase(in)) {
                        bodyNodeIndex = i;
                        bodyNode = parameterNode;
                    } else if (type != null && "file".equalsIgnoreCase(type.asText())) {
                        fileNode = parameterNode;
                        fileNodeIndex = i;
                    } else {
                        JsonNode format = parameterNode.remove("format");
                        JsonNode aDefault = parameterNode.remove("default");
                        JsonNode example = parameterNode.remove("example");
                        JsonNode items = parameterNode.remove("items");
                        JsonNode enums = parameterNode.remove("enum");
                        parameterNode.remove("collectionFormat");
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

                        if (enums != null) {
                            schemaNode.set("enum", enums);
                        }

                        if (items != null) {
                            schemaNode.set("items", items);
                        }
                        parameterNode.set("schema", schemaNode);
                    }
                }

                if (fileNode != null) {
                    parameters.remove(fileNodeIndex);
                    JsonNode fileName = fileNode.remove("name");
                    JsonNode description = fileNode.remove("description");
                    JsonNode required = fileNode.remove("required");
                    ObjectNode requestBody = objectMapper.createObjectNode();
                    ObjectNode contentNode = objectMapper.createObjectNode();
                    ObjectNode schemaNode = objectMapper.createObjectNode();
                    schemaNode.put("type", "string");
                    schemaNode.put("format", "binary");
                    contentNode.set("application/octet-stream", objectMapper.createObjectNode().set("schema", schemaNode));
                    requestBody.set("content", contentNode);
                    requestBody.set("required", required);
                    if (description != null) {
                        requestBody.set("description", description);
                    }
                    methodNode.set("requestBody", requestBody);
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
                    JsonNode headers = responseNode.remove("headers");
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

                    if (headers != null) {
                        for (Iterator<String> headerIt = headers.fieldNames(); headerIt.hasNext(); ) {
                            String headerName = headerIt.next();
                            JsonNode headerValue = headers.get(headerName);
                            JsonNode headerSchema = objectMapper.createObjectNode().set("schema", headerValue);
                            responseNode.set("headers", objectMapper.createObjectNode().set(headerName, headerSchema));
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


}
