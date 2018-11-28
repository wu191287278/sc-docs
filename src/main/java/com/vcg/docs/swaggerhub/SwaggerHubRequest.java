package com.vcg.docs.swaggerhub;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
public class SwaggerHubRequest {

    private String token;

    private String owner;

    private String name;

    private String version = "1.0";

    private String format = "json";

    private boolean isPrivate = true;

    private String swagger;
}
