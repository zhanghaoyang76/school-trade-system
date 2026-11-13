package campus.mapper;

import campus.BusinessException;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Table data access shared by focused business services. SQL never comes from HTTP inputs. */
@Repository
public class Store {
  final SqlMapper mapper;

  public Store(SqlMapper mapper) {
    this.mapper = mapper;
  }

  Map<String, Object> parameters(String sql, Object[] args) {
    Map<String, Object> p = new HashMap<>();
    p.put("sql", sql);
    p.put("args", args);
    return p;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (var row : mapper.select(parameters(sql, args))) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (var entry : row.entrySet()) {
        String col = entry.getKey().toLowerCase();
        StringBuilder key = new StringBuilder();
        boolean upper = false;
        for (char c : col.toCharArray()) {
          if (c == '_') {
            upper = true;
            continue;
          }
          key.append(upper ? Character.toUpperCase(c) : c);
          upper = false;
        }
        Object value = entry.getValue();
        if (value instanceof java.sql.Timestamp ts) value = ts.toLocalDateTime().toString();
        if (value instanceof java.time.LocalDateTime dt) value = dt.toString();
        result.put(key.toString(), value);
      }
      rows.add(result);
    }
    return rows;
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    BusinessException.require(!rows.isEmpty(), 404, "记录不存在");
    return rows.getFirst();
  }

  public int update(String sql, Object... args) {
    return mapper.update(parameters(sql, args));
  }

  public long insert(String sql, Object... args) {
    var p = parameters(sql, args);
    mapper.insert(p);
    return ((Number) p.get("generatedId")).longValue();
  }

  public static long id(Map<String, ?> m, String key) {
    Object v = m.get(key);
    return v == null ? 0 : Long.parseLong(v.toString());
  }

  public static int num(Map<String, ?> m, String key) {
    try {
      return Math.toIntExact(id(m, key));
    } catch (ArithmeticException error) {
      throw new BusinessException(400, "整数参数超出范围");
    }
  }

  public static String str(Map<String, ?> m, String key) {
    Object v = m.get(key);
    return v == null ? "" : v.toString();
  }

  public static BigDecimal money(Map<String, ?> m, String key) {
    Object v = m.get(key);
    return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
  }

  public Map<String, Object> page(List<?> rows) {
    return Map.of("records", rows, "total", rows.size());
  }
}
