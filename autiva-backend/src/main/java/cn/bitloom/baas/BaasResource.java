package cn.bitloom.baas;

import com.alibaba.fastjson2.JSONObject;

public record BaasResource(
    String type,
    String name,
    JSONObject connectionInfo
) {}
