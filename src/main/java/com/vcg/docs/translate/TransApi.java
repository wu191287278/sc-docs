package com.vcg.docs.translate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class TransApi {

    private static final String TRANS_API_HOST = "http://api.fanyi.baidu.com/api/trans/vip/translate";

    public static String translate(String query) {
        if (System.getProperty("baidu.appId") == null || System.getProperty("baidu.securityKey") == null) {
            return query;
        }
        String from = System.getProperty("baidu.from", "auto");
        String to = System.getProperty("baidu.to", "en");
        Map<String, String> params = buildParams(query, from, to);
        String result = HttpGet.get(TRANS_API_HOST, params);
        JSONObject json = JSON.parseObject(result);
        JSONArray trans_result = json.getJSONArray("trans_result");
        if (trans_result.size() > 0) {
            return trans_result.getJSONObject(0).getString("dst");
        }
        return query;
    }

    private static Map<String, String> buildParams(String query, String from, String to) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("q", query);
        params.put("from", from);
        params.put("to", to);

        params.put("appid", System.getProperty("baidu.appId"));

        // 随机数
        String salt = String.valueOf(System.currentTimeMillis());
        params.put("salt", salt);

        // 签名
        String src = System.getProperty("baidu.appId") + query + salt + System.getProperty("baidu.securityKey"); // 加密前的原文
        params.put("sign", MD5.md5(src));

        return params;
    }

}
