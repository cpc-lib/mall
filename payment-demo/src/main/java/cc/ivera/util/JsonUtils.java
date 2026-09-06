package cc.ivera.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    public static Map<String, Object> toObjectMap(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAP_TYPE);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON解析失败", e);
        }
    }

    public static Map<String, Object> toObjectMap(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.convertValue(value, OBJECT_MAP_TYPE);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON对象转换失败", e);
        }
    }

}
