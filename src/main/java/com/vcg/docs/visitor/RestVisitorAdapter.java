package com.vcg.docs.visitor;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.google.common.collect.ImmutableMap;
import com.vcg.docs.domain.Request;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RestVisitorAdapter extends VoidVisitorAdapter<Swagger> {

    private ResolveSwaggerType resolveSwaggerType = new ResolveSwaggerType();

    private final Set<String> controllers = new HashSet<>(Arrays.asList("Controller", "RestController", "FeignClient"));

    private final Set<String> mappings = new HashSet<>(Arrays.asList("RequestMapping",
            "GetMapping", "PutMapping", "DeleteMapping", "PostMapping", "FeignClient"));

    private final Map<String, String> methods = ImmutableMap.of("GetMapping", "get",
            "PostMapping", "post",
            "DeleteMapping", "delete",
            "PutMapping", "put",
            "PatchMapping", "patch"
    );

    private final Map<String, String> headers = new HashMap<>();

    {
        try {
            Class<?> mediaTypeClazz = Class.forName("org.springframework.http.MediaType");
            Field[] fields = mediaTypeClazz.getFields();
            for (Field field : fields) {
                try {
                    Object o = field.get(null);
                    headers.put(field.getName(), o.toString());
                } catch (IllegalAccessException e) {
                    log.warn(e.getMessage());
                }
            }
        } catch (Exception e) {
            headers.put("APPLICATION_FORM_URLENCODED", "application/x-www-form-urlencoded");
            headers.put("APPLICATION_JSON_VALUE", "application/json");
            headers.put("APPLICATION_JSON_UTF8_VALUE", "application/json;charset=UTF-8");
            headers.put("APPLICATION_OCTET_STREAM_VALUE", "application/octet-stream");
            headers.put("APPLICATION_XML_VALUE", "application/xml");
            headers.put("TEXT_HTML_VALUE", "text/html");
        }

    }

    @Override
    public void visit(MethodDeclaration n, Swagger swagger) {
        List<AnnotationExpr> annotationExprs = n.getAnnotations()
                .stream()
                .filter(a -> mappings.contains(a.getNameAsString()))
                .collect(Collectors.toList());

        if (annotationExprs.isEmpty()) return;


        Request request = new Request();
        Map<String, Path> paths = swagger.getPaths();
        parse((ClassOrInterfaceDeclaration) n.getParentNode().get(), n, request);


        String parentMappingPath = request.getParentPath() == null ? "" : request.getParentPath();
        Operation operation = new Operation()
                .tag(request.getClazzSimpleName())
                .consumes(request.getConsumes().isEmpty() ? null : request.getConsumes())
                .produces(request.getProduces().isEmpty() ? null : request.getProduces())
                .description(request.getMethodNotes())
                .summary(request.getSummary())
                .response(200, new Response()
                        .description(request.getReturnDescription() == null ? "" : request.getReturnDescription())
                        .schema(request.getReturnType())
                );
        operation.setParameters(request.getParameters());
        if (request.getMethodErrorDescription() != null) {
            operation.response(500, new Response().description("{\"message\":\"" + request.getMethodErrorDescription() + "\"}"));
        }

        //方法上如果只打入注解没有url,将使用类上的url
        List<String> pathList = request.getPaths();
        if (pathList.isEmpty()) {
            String fullPath = ("/" + parentMappingPath).replaceAll("[/]+", "/");
            Path path = paths.computeIfAbsent(fullPath, s -> new Path());
            paths.put(fullPath, path);
            for (String method : request.getMethods()) {
                path.set(method, operation.operationId(n.getNameAsString()));
            }
        }

        for (String methodPath : pathList) {
            String fullPath = ("/" + parentMappingPath + "/" + methodPath).replaceAll("[/]+", "/");
            Path path = paths.computeIfAbsent(fullPath, s -> new Path());
            paths.put(fullPath, path);
            for (String method : request.getMethods()) {
                path.set(method, operation.operationId(n.getNameAsString()));
            }
        }


        super.visit(n, swagger);
    }


    @Override
    public void visit(ClassOrInterfaceDeclaration n, Swagger swagger) {
        List<AnnotationExpr> annotationExprs = n.getAnnotations()
                .stream()
                .filter(a -> controllers.contains(a.getNameAsString()))
                .collect(Collectors.toList());

        if (annotationExprs.isEmpty()) return;
        Tag tag = new Tag()
                .name(n.getNameAsString());
        swagger.addTag(tag);
        n.getJavadoc().ifPresent(c -> tag.description(StringUtils.isBlank(c.getDescription().toText()) ? null : c.getDescription().toText()));
        super.visit(n, swagger);

    }

    private void parse(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, MethodDeclaration n, Request request) {
        request.setClazzSimpleName(classOrInterfaceDeclaration.getNameAsString());
        parseMapping(classOrInterfaceDeclaration, n, request);
        parseMethodParameters(n, request);
        parseReturnType(n, request);
    }


    private void parseMapping(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, MethodDeclaration n, Request request) {
        for (AnnotationExpr annotation : classOrInterfaceDeclaration.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            if (mappings.contains(annotationName)) {
                if ("FeignClient".equalsIgnoreCase(annotationName)) {
                    if (annotation instanceof NormalAnnotationExpr) {
                        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                            String name = pair.getNameAsString();
                            if ("path".equals(name)) {
                                List<String> values = parseAttribute(pair);
                                request.setParentPath(values.isEmpty() ? null : values.get(0));
                            }
                        }
                    }
                } else {
                    if (annotation instanceof SingleMemberAnnotationExpr) {
                        SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotation;
                        request.setParentPath(singleMemberAnnotationExpr.getMemberValue().asStringLiteralExpr().asString());
                    }
                    if (annotation instanceof NormalAnnotationExpr) {
                        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                            String name = pair.getNameAsString();
                            if ("path".equals(name) || "value".equals(name)) {
                                List<String> values = parseAttribute(pair);
                                request.setParentPath(values.isEmpty() ? null : values.get(0));
                            }
                        }
                    }
                }
            }
        }

        for (AnnotationExpr annotation : n.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            if (mappings.contains(annotationName)) {

                if (annotation instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotation;
                    Expression memberValue = singleMemberAnnotationExpr.getMemberValue();
                    if (memberValue.isStringLiteralExpr()) {
                        request.getPaths().add(memberValue.asStringLiteralExpr().asString());
                    }

                    if (memberValue.isArrayInitializerExpr()) {
                        NodeList<Expression> values = memberValue.asArrayInitializerExpr().getValues();
                        for (Expression value : values) {
                            if (value.isStringLiteralExpr()) {
                                request.getPaths().add(value.asStringLiteralExpr().asString());
                            }
                        }
                    }

                }
                if (annotation instanceof NormalAnnotationExpr) {
                    for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                        String name = pair.getNameAsString();
                        List<String> values = parseAttribute(pair);
                        if ("path".equals(name) || "value".equals(name)) {
                            request.getPaths().addAll(values);
                        }

                        if ("headers".equals(name)) {
                            request.getHeaders().addAll(values);
                        }

                        if ("produces".equals(name)) {
                            request.getProduces().addAll(values);
                        }

                        if ("consumes".equals(name)) {
                            request.getConsumes().addAll(values);
                        }

                        if ("method".equals(name)) {
                            for (String value : values) {
                                request.getMethods().add(value.toLowerCase());
                            }
                        }
                    }
                }

                if (annotationName.equals("RequestMapping") && request.getMethods().isEmpty()) {
                    request.getMethods().add("post");
                } else {
                    String method = methods.get(annotationName);
                    if (method != null) {
                        request.getMethods().add(method);
                    }
                }

            }
        }


    }

    private List<String> parseAttribute(MemberValuePair pair) {
        List<String> values = new ArrayList<>();
        Expression value = pair.getValue();
        if (value instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr arrayAccessExpr = value.asArrayInitializerExpr();
            for (Expression expression : arrayAccessExpr.getValues()) {
                if (expression instanceof StringLiteralExpr) {
                    String path = expression.asStringLiteralExpr().asString();
                    values.add(path);
                }

                if (expression instanceof FieldAccessExpr) {
                    FieldAccessExpr field = expression.asFieldAccessExpr();
                    String name = field.getName().asString();
                    values.add(name);
                }
            }
        } else if (value instanceof FieldAccessExpr) {
            FieldAccessExpr field = value.asFieldAccessExpr();
            String name = field.getName().asString();
            String header = this.headers.get(name);
            if (header != null) {
                values.add(header);
            } else {
                values.add(name);
            }

        } else {
            values.add(value.asStringLiteralExpr().asString());
        }
        return values;
    }

    private void parseMethodParameters(MethodDeclaration n, Request request) {
        parseMethodComment(n, request);
        for (Parameter parameter : n.getParameters()) {
            String typeName = parameter.getType().toString();
            if (typeName.contains("HttpServletRequest") || typeName.contains("HttpServletResponse")) {
                continue;
            }
            String variableName = parameter.getNameAsString();
            Map<String, String> paramsDescription = request.getParamsDescription();
            String description = paramsDescription.get(variableName);
            Property property = resolveSwaggerType.resolve(parameter.getType());
            Property paramProperty = property instanceof ObjectProperty ? new StringProperty()
                    .description(property.getDescription()) : property;
            io.swagger.models.parameters.Parameter param = new QueryParameter()
                    .property(paramProperty);
            for (AnnotationExpr annotation : parameter.getAnnotations()) {
                String annotationName = annotation.getNameAsString();

                if (annotation.isAnnotationExpr()) {
                    switch (annotationName) {
                        case "PathVariable":
                            param = new PathParameter()
                                    .property(paramProperty);
                            break;
                        case "RequestBody":
                            Model model = resolveSwaggerType.convertToModel(property);
                            BodyParameter bodyParameter = new BodyParameter().schema(new ModelImpl().type("object"));
                            if (model != null) {
                                bodyParameter.schema(model);
                            }
                            param = bodyParameter;
                            break;

                        case "RequestPart":
                            param = new FormParameter()
                                    .property(paramProperty);
                            request.getConsumes().add("multipart/form-data");
                            break;
                        case "RequestHeader":
                            param = new HeaderParameter()
                                    .property(paramProperty);
                            break;
                        case "CookieValue":
                            param = new CookieParameter()
                                    .property(property);

                    }

                    if (param instanceof PathParameter || param instanceof QueryParameter || param instanceof HeaderParameter || param instanceof CookieParameter) {
                        if (annotation.isSingleMemberAnnotationExpr()) {
                            param.setRequired(true);
                            SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                            if ("value".equals(single.getNameAsString())) {
                                String value = single.getMemberValue().asStringLiteralExpr().asString();
                                if (StringUtils.isNotBlank(value)) {
                                    param.setName(value);
                                }
                            }
                        }

                        if (annotation.isNormalAnnotationExpr()) {
                            boolean isRequire = true;
                            for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                                if ("required".equals(pair.getNameAsString())) {
                                    isRequire = pair.getValue().asBooleanLiteralExpr().getValue();
                                }

                                if ("defaultValue".equals(pair.getNameAsString())) {
                                    Expression value = pair.getValue();
                                    if (value.isStringLiteralExpr()) {
                                        ((AbstractSerializableParameter) param).setDefault(value.asStringLiteralExpr().asString());
                                    }
                                    isRequire = false;
                                }

                                if ("value".equals(pair.getNameAsString())) {
                                    String value = pair.getValue().asStringLiteralExpr().asString();
                                    if (StringUtils.isNotBlank(value)) {
                                        param.setName(value);
                                    }
                                }
                            }
                            param.setRequired(isRequire);
                        }
                    }
                }
            }


            param.setDescription(property.getDescription() != null ? property.getDescription() : description);
            param.setName(variableName);

            request.getParameters().add(param);
        }
    }

    private void parseMethodComment(MethodDeclaration n, Request request) {
        n.getJavadocComment().ifPresent(c -> {
            if (c.isJavadocComment()) {
                JavadocComment javadocComment = c.asJavadocComment();
                Javadoc parse = javadocComment.parse();
                JavadocDescription description = parse.getDescription();
                request.setSummary(description.toText());
                for (JavadocBlockTag blockTag : parse.getBlockTags()) {
                    switch (blockTag.getTagName().toLowerCase()) {
                        case "throws":
                            request.setMethodErrorDescription(blockTag.getContent().toText());
                            break;
                        case "return":
                            request.setReturnDescription(blockTag.getContent().toText());
                            break;
                        case "apiNote":
                            request.setMethodNotes(blockTag.getContent().toText());
                        default:
                            blockTag.getName().ifPresent(t -> request.getParamsDescription().put(t, blockTag.getContent().toText()));
                            break;
                    }
                }
            }
        });
    }

    private void parseReturnType(MethodDeclaration n, Request request) {
        Type type = n.getType();
        if (type.isVoidType()) {
            return;
        }

        String name = type.toString();

        if ((name.contains("ResponseEntity") || name.contains("Mono")) && type.isClassOrInterfaceType()) {
            Optional<NodeList<Type>> types = type.asClassOrInterfaceType().getTypeArguments();
            if (types.isPresent()) {
                type = types.get().isEmpty() ? type : types.get().get(0);
            }
        }

        Property property = resolveSwaggerType.resolve(type);
        if (property.getName() != null) {
            request.setReturnType(new RefProperty("#/definitions/" + property.getName()));
        } else {
            request.setReturnType(property);
        }
    }

    public Map<String, Model> getModelMap() {
        return resolveSwaggerType.getModelMap();
    }

}
