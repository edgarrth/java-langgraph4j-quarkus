package pe.axiz.copilot.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.util.*;

public final class Json {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private Json() {}

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalArgumentException("No se pudo serializar el contenido", ex);
    }
  }

  public static <T> T read(String text, Class<T> type) {
    try {
      return MAPPER.readValue(text, type);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Contenido JSON inválido", ex);
    }
  }

  public static Map<String, Object> map(Object value) {
    return MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {});
  }

  public static List<Map<String, Object>> rows(String text) {
    try {
      return MAPPER.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      throw new IllegalArgumentException("Lista JSON inválida", ex);
    }
  }

  public static Map<String, Object> object(String text) {
    try {
      return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      throw new IllegalArgumentException("Objeto JSON inválido", ex);
    }
  }
}
