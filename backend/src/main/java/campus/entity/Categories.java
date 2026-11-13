package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("categories")
public class Categories {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String name;
  public Integer sortOrder;
  public LocalDateTime createdAt;
}
