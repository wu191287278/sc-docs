package com.vcg.docs.controller;

import com.vcg.docs.cache.SwaggerCache;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

@RestController
@Slf4j
public class ProxyController {


    @RequestMapping(value = "/proxy/{application}/**")
    public void proxy(@PathVariable("application") String application,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {

        Swagger swagger = SwaggerCache.get(application);
        if (swagger == null) {
            response.setStatus(404);
            return;
        }

        List<Scheme> schemes = swagger.getSchemes();
        String schemeStr = "http://";
        for (Scheme scheme : schemes) {
            if (scheme.toValue().equals("https")) {
                schemeStr = "https://";
            }
        }
        String basePath = swagger.getBasePath() + request.getRequestURI().replace("/proxy/" + application + "/", "");
        String queryString = getQueryString(request);
        String host = schemeStr + swagger.getHost() + basePath + (queryString == null ? "" : "?" + queryString);

        HttpResponse execute = execute(request, host);

        response.setCharacterEncoding("utf-8");
        response.setStatus(execute.getStatusLine().getStatusCode());


        for (Header header : execute.getAllHeaders()) {
            //防止分块解码
            if (header.getName().equals("Transfer-Encoding")) {
                continue;
            }

            response.addHeader(header.getName(), header.getValue());
        }

        try (InputStream in = execute.getEntity().getContent();
             OutputStream out = response.getOutputStream()) {
            IOUtils.copy(in, out);
        }


    }


    private HttpResponse execute(HttpServletRequest request, String host) throws IOException {
        CloseableHttpClient client = buildHttpClient(request);
        Header[] headers = getHeaders(request);
        HttpRequestBase httpRequestBase = buildContent(request);
        httpRequestBase.setHeaders(headers);
        httpRequestBase.setURI(URI.create(host));
        return client.execute(httpRequestBase);
    }

    private String getQueryString(HttpServletRequest request) {
        return request.getQueryString();
    }


    private Header[] getHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        List<Header> headers = new ArrayList<>();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.equalsIgnoreCase("accept") || name.equalsIgnoreCase("Content-Type")) {
                String value = request.getHeader(name);
                headers.add(new BasicHeader(name, value));
            }
        }
        return headers.toArray(new Header[]{});
    }


    private CloseableHttpClient buildHttpClient(HttpServletRequest request) {
        BasicCookieStore basicCookieStore = new BasicCookieStore();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                BasicClientCookie basicClientCookie = new BasicClientCookie(cookie.getName(), cookie.getValue());
                basicClientCookie.setComment(cookie.getComment());
                basicClientCookie.setPath(cookie.getPath());
                basicClientCookie.setSecure(cookie.getSecure());
                basicClientCookie.setDomain(cookie.getDomain());
                basicClientCookie.setExpiryDate(new Date(System.currentTimeMillis() + cookie.getMaxAge()));
                basicCookieStore.addCookie(basicClientCookie);
            }
        }
        return HttpClientBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setCharset(Charset.forName("UTF-8"))
                        .build())
                .setDefaultCookieStore(basicCookieStore)
                .build();
    }

    private HttpRequestBase buildContent(HttpServletRequest request) throws IOException {
        HttpRequestBase httpRequestBase = buildHttpRequestBase(request);
        try (InputStream in = request.getInputStream()) {
            if (request.getInputStream().available() > 0 && !(httpRequestBase instanceof HttpGet)) {
                HttpEntityEnclosingRequestBase httpEntityEnclosingRequestBase = (HttpEntityEnclosingRequestBase) httpRequestBase;
                httpEntityEnclosingRequestBase.setEntity(new InputStreamEntity(in));
            }
        }
        return httpRequestBase;
    }

    private HttpRequestBase buildHttpRequestBase(HttpServletRequest request) {

        String method = request.getMethod().toUpperCase();
        switch (method) {
            case "GET":
                return new HttpGet();
            case "POST":
                return new HttpPost();
            case "DELETE":
                return new HttpDelete();
            case "PUT":
                return new HttpPut();
            case "PATCH":
                return new HttpPatch();
            case "OPTION":
                return new HttpOptions();
        }

        return new HttpRequestBase() {
            @Override
            public String getMethod() {
                return request.getMethod();
            }
        };
    }


}
