package campus.mapper;

import java.util.*;

public class SqlProvider {
  public static String sql(Map<String, Object> parameters) {
    String source = (String) parameters.get("sql");
    StringBuilder sql = new StringBuilder();
    int index = 0;
    boolean quoted = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '\'') quoted = !quoted;
      if (c == '?' && !quoted) sql.append("#{args[").append(index++).append("]}");
      else sql.append(c);
    }
    return sql.toString();
  }
}
