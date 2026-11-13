package campus.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

/** Only application-owned SQL is accepted. Values always become MyBatis bound parameters. */
public interface SqlMapper {
  @SelectProvider(type = SqlProvider.class, method = "sql")
  List<Map<String, Object>> select(Map<String, Object> parameters);

  @UpdateProvider(type = SqlProvider.class, method = "sql")
  int update(Map<String, Object> parameters);

  @InsertProvider(type = SqlProvider.class, method = "sql")
  @Options(useGeneratedKeys = true, keyProperty = "generatedId", keyColumn = "id")
  int insert(Map<String, Object> parameters);
}
