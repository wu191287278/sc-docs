package com.vcg.docs.domain;

import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Request {

    private String clazzSimpleName;

    private String methodName;

    private String parentPath;

    private String clazzDescription;

    private String clazzNotes;

    private String summary;

    private String methodNotes;

    private String methodErrorDescription;

    private String returnDescription;

    private Property returnType;

    private Map<String, String> paramsDescription = new HashMap<>();

    private List<String> paths = new ArrayList<>();

    private List<String> headers = new ArrayList<>();

    private List<String> methods = new ArrayList<>();

    private List<String> produces = new ArrayList<>();

    private List<String> consumes = new ArrayList<>();

    private List<Parameter> parameters = new ArrayList<>();


}
