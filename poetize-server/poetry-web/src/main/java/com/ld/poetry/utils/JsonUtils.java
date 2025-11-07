package com.ld.poetry.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * JSON工具类
 */
public class JsonUtils {
    
    /**
     * 对象转JSON字符串
     * @param object 对象
     * @return JSON字符串
     */
    public static String toJsonString(Object object) {
        try {
            return JSON.toJSONString(object, SerializerFeature.WriteMapNullValue);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * JSON字符串转对象
     * @param jsonString JSON字符串
     * @param clazz 对象类型
     * @return 对象
     */
    public static <T> T parseObject(String jsonString, Class<T> clazz) {
        try {
            return JSON.parseObject(jsonString, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * JSON字符串转JSONObject
     * @param jsonString JSON字符串
     * @return JSONObject
     */
    public static JSONObject parseObject(String jsonString) {
        try {
            return JSON.parseObject(jsonString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}