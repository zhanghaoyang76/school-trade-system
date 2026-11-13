package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("shops")
public class Shops {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long sellerId;
  public String name;
  public String status;
  public Integer level;
  public String levelMode;
  public LocalDateTime createdAt;
}
