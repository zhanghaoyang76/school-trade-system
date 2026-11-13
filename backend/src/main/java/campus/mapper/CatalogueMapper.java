package campus.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

public interface CatalogueMapper {
  List<Map<String, Object>> search(
      @Param("keyword") String keyword,
      @Param("shopId") Long shopId,
      @Param("sort") String sort,
      @Param("categoryId") Long categoryId);
}
