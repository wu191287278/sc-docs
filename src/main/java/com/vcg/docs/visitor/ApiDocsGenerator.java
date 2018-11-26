package com.vcg.docs.visitor;

import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.SupportingFile;
import io.swagger.codegen.languages.JavaClientCodegen;

/**
 * Created by wuyu on 2017/2/28.
 */
public class ApiDocsGenerator extends JavaClientCodegen {

    public ApiDocsGenerator() {
        additionalProperties.put(FULL_JAVA_UTIL, "true");
        additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, serializableModel);
        typeMapping.put("Map", "java.util.Map");
        typeMapping.put("HashMap", "java.util.Map");
        typeMapping.put("hashMap", "java.util.Map");
        typeMapping.put("jSONObject", "java.util.Map");
        typeMapping.put("JSONObject", "java.util.Map");
        typeMapping.put("JSONArray", "java.util.List");
        typeMapping.put("jSONArray", "java.util.List");
        typeMapping.put("Iterable", "java.util.List");
        typeMapping.put("iterable", "java.util.List");
        typeMapping.put("Set", "java.util.Set");
        typeMapping.put("set", "java.util.Set");
    }

    @Override
    public void processOpts() {

        super.processOpts();
        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");
//        supportingFiles.clear();
        supportingFiles.add(new SupportingFile("README.mustache", "README.md"));
    }

    @Override
    public String getName() {
        return "feign";
    }
}
