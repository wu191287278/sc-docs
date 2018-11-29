package com.vcg.docs.swaggerhub;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

@Slf4j
public class SwaggerHubClient {

    public boolean saveDefinition(String token,
                                  String owner,
                                  String name,
                                  String version,
                                  String format,
                                  boolean isPrivate,
                                  String swagger) throws IOException {
        CloseableHttpClient client = HttpClientBuilder.create()
                .build();
        String swaggerHubApi = "https://api.swaggerhub.com/apis";
        String apiUrl = swaggerHubApi + "/" + owner + "/" + name + "?isPrivate=" + isPrivate + "&version=" + version;
        HttpPost httpPost = new HttpPost(apiUrl);
        httpPost.addHeader("Authorization", token);
        httpPost.addHeader("Content-Type", "application/" + format);
        httpPost.setEntity(new StringEntity(swagger, Charset.forName("utf-8")));
        CloseableHttpResponse response = client.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (response.getStatusLine().getStatusCode() != 200) {
            if (entity != null && entity.getContent() != null) {
                try (InputStream ignored = entity.getContent()) {
                }
            }
            return false;
        }
        return true;
    }

    public boolean saveDefinition(SwaggerHubRequest request) throws IOException {
        return saveDefinition(request.getToken(),
                request.getOwner(),
                request.getName(),
                request.getVersion(),
                request.getFormat(),
                request.isPrivate(),
                request.getSwagger()
        );
    }

}
