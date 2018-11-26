package com.vcg.docs.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vcg.docs.controller.FileExploreController;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class SwaggerCache {

    private static final Cache<String, Swagger> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    public static Swagger get(String application) {
        Swagger swagger = CACHE.getIfPresent(application);
        if (swagger != null) {
            return swagger;
        }

        File swaggerFile = new File(FileExploreController.STATIC_FILE, application + "/swagger.json");
        if (swaggerFile.exists()) {
            swagger = new SwaggerParser().read(swaggerFile.getAbsolutePath());
            CACHE.put(application, swagger);
        }
        return swagger;
    }

    public static void put(String application, Swagger swagger) {
        CACHE.put(application, swagger);
    }
}
