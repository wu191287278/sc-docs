package com.vcg.docs.visitor;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.vcg.docs.domain.Request;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class DubboVisitorAdapter extends VoidVisitorAdapter<Swagger> {

    private ResolveSwaggerType resolveSwaggerType = new ResolveSwaggerType();

    @Override
    public void visit(MethodDeclaration n, Swagger swagger) {
        Request request = new Request();
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = (ClassOrInterfaceDeclaration) n.getParentNode().get();
        parse(classOrInterfaceDeclaration, n, request);

        String parentMappingPath = request.getClazzSimpleName();
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
        Map<String, Path> paths = swagger.getPaths();
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
        for (AnnotationExpr annotation : n.getAnnotations()) {
            String id = annotation.resolve().getId();
            if ("com.alibaba.dubbo.config.annotation.Service".equals(id)) {
                Tag tag = new Tag()
                        .name(n.getNameAsString());
                swagger.tag(tag);
                n.getJavadoc().ifPresent(c -> tag.description(StringUtils.isBlank(c.getDescription().toText()) ? null : c.getDescription().toText()));
                super.visit(n, swagger);
                break;
            }
        }
    }

    private void parse(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, MethodDeclaration n, Request request) {
        request.setClazzSimpleName(classOrInterfaceDeclaration.getNameAsString());
        parseMethodParameters(n, request);
        parseMethodComment(n, request);
        parseReturnType(n, request);
    }

    private void parseMethodParameters(MethodDeclaration n, Request request) {
        request.getConsumes().add("application/dubbo");
        request.setMethodName(n.getName().asString());
        parseMethodComment(n, request);
        request.getMethods().add("post");
        request.getPaths().add(n.getNameAsString());
        boolean bodyFlag = false;
        for (Parameter parameter : n.getParameters()) {
            String variableName = parameter.getNameAsString();
            Map<String, String> paramsDescription = request.getParamsDescription();
            String description = paramsDescription.get(variableName);
            Property property = resolveSwaggerType.resolve(parameter.getType());
            Property paramProperty = property instanceof ObjectProperty ? new StringProperty()
                    .description(property.getDescription()) : property;
            io.swagger.models.parameters.Parameter param = new QueryParameter()
                    .property(paramProperty);
            ;

            if (!bodyFlag) {
                if (paramProperty instanceof ObjectProperty || paramProperty instanceof MapProperty || paramProperty instanceof RefProperty) {
                    Model model = resolveSwaggerType.convertToModel(property);
                    BodyParameter bodyParameter = new BodyParameter().schema(new ModelImpl().type("object"));
                    if (model != null) {
                        bodyParameter.schema(model);
                    }
                    param = bodyParameter;
                    bodyFlag = true;
                } else if (paramProperty instanceof ArrayProperty) {
                    Property items = ((ArrayProperty) paramProperty).getItems();
                    if (items instanceof ObjectProperty ||
                            items instanceof MapProperty ||
                            items instanceof RefProperty ||
                            items instanceof ArrayProperty) {
                        Model model = resolveSwaggerType.convertToModel(property);
                        BodyParameter bodyParameter = new BodyParameter().schema(new ModelImpl().type("object"));
                        if (model != null) {
                            bodyParameter.schema(model);
                        }
                        bodyFlag = true;
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

        Property property = resolveSwaggerType.resolve(type);
        if (property.getName() != null) {
            request.setReturnType(new RefProperty("#/definitions/" + property.getName()));
        } else {
            request.setReturnType(property);
        }
    }

    public String resolveImplementedName(ClassOrInterfaceDeclaration n) {
        NodeList<ClassOrInterfaceType> implementedTypes = n.getImplementedTypes();
        if (n.getImplementedTypes().isEmpty()) return null;
        return implementedTypes.get(0).resolve().getQualifiedName();
    }

    public Map<String, Model> getModelMap() {
        return resolveSwaggerType.getModelMap();
    }

}
