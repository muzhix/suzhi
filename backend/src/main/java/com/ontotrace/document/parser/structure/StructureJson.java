package com.ontotrace.document.parser.structure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 结构方案 JSON 序列化。
 *
 * @author hanbd
 */
public final class StructureJson {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private StructureJson() {}

    /**
     * 读取 JSON。
     *
     * @param json 文本
     * @param type 类型
     * @param <T> 目标类型
     * @return 对象
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析结构方案 JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * 读取字节。
     *
     * @param bytes 内容
     * @param type 类型
     * @param <T> 目标类型
     * @return 对象
     */
    public static <T> T read(byte[] bytes, Class<T> type) {
        try {
            return MAPPER.readValue(bytes, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析结构方案 JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * 写成 JSON。
     *
     * @param value 对象
     * @return 文本
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法序列化结构方案", ex);
        }
    }
}
