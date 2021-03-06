package com.vcg.docs.visitor;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.vcg.docs.domain.Request;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class JavaxRsVisitorAdapter extends VoidVisitorAdapter<Swagger> {

    private ResolveSwaggerType resolveSwaggerType = new ResolveSwaggerType();

    private final Set<String> controllers = new HashSet<>(Arrays.asList("Path"));

    private final Set<String> mappings = new HashSet<>(Arrays.asList("Path", "GET",
            "DELETE", "POST", "PUT", "PATCH", "HEAD", "OPTIONS"));


    private final Map<String, String> methods = new HashMap<>();

    public JavaxRsVisitorAdapter() {
        methods.put("GET", "get");
        methods.put("POST", "post");
        methods.put("DELETE", "delete");
        methods.put("PUT", "put");
        methods.put("PATCH", "patch");
        methods.put("OPTIONS", "options");
        methods.put("HEAD", "head");
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
            if (request.getMethods().isEmpty()) {
                path.set("get", operation.operationId(n.getNameAsString()));
            }
        }

        for (String methodPath : pathList) {
            String fullPath = ("/" + parentMappingPath + "/" + methodPath).replaceAll("[/]+", "/");
            Path path = paths.computeIfAbsent(fullPath, s -> new Path());
            paths.put(fullPath, path);
            for (String method : request.getMethods()) {
                path.set(method, operation.operationId(n.getNameAsString()));
            }
            if (request.getMethods().isEmpty()) {
                path.set("get", operation.operationId(n.getNameAsString()));
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
                if (annotation instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotation;
                    request.setParentPath(singleMemberAnnotationExpr.getMemberValue().asStringLiteralExpr().asString());
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
                }
            }
        }
    }

    private void parseMethodParameters(MethodDeclaration n, Request request) {
        request.setMethodName(n.getName().asString());
        parseMethodComment(n, request);
        for (AnnotationExpr annotation : n.getAnnotations()) {
            String name = annotation.getName().asString();
            String method = methods.get(name);
            if (method != null) {
                request.getMethods().add(method);
            }
        }
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
                        case "Path":
                            param = new PathParameter()
                                    .property(paramProperty);
                            break;
                        case "BeanParam":
                            Model model = resolveSwaggerType.convertToModel(property);
                            BodyParameter bodyParameter = new BodyParameter().schema(new ModelImpl().type("object"));
                            if (model != null) {
                                bodyParameter.schema(model);
                            }
                            param = bodyParameter;
                            break;

                        case "FormParam":
                            param = new FormParameter()
                                    .property(paramProperty);
                            request.getConsumes().add("multipart/form-data");
                            break;
                        case "HeaderParam":
                            param = new HeaderParameter()
                                    .property(paramProperty);
                            break;
                        case "CookieParam":
                            param = new CookieParameter()
                                    .property(property);

                    }
                }
            }

            for (AnnotationExpr annotation : parameter.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if ("Consumes".equals(annotationName)) {
                    if (annotation.isArrayInitializerExpr()) {
                        NodeList<Expression> values = annotation.asArrayInitializerExpr().getValues();
                        for (Expression value : values) {
                            if (value.isStringLiteralExpr()) {
                                request.getConsumes().add(value.asStringLiteralExpr().asString());
                            }
                        }
                    }
                }
                if ("Produces".equals(annotationName)) {
                    if (annotation.isArrayInitializerExpr()) {
                        NodeList<Expression> values = annotation.asArrayInitializerExpr().getValues();
                        for (Expression value : values) {
                            if (value.isStringLiteralExpr()) {
                                request.getProduces().add(value.asStringLiteralExpr().asString());
                            }
                        }
                    }
                }

                if ("DefaultValue".equals(annotationName)) {
                    if (annotation.isStringLiteralExpr()) {
                        property.setDefault(annotation.asStringLiteralExpr().asString());
                    }
                }


                if ("NotNull".equals(annotationName)) {
                    property.setRequired(true);
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
