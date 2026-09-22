package com.lamiprover.db;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 单一 ObjectMapper（record 友好），仓储/导入导出共用以保证口径一致。 */
public final class JsonCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonCodec() {
  }

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("JSON 序列化失败", e);
    }
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      throw new IllegalStateException("JSON 反序列化失败: " + e.getMessage(), e);
    }
  }
}
