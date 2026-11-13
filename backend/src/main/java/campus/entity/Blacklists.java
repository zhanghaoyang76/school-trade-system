package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("blacklists")
public class Blacklists {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String scope;
  public Long shopId;
  public Long operatorId;
  public Long buyerId;
  public String reason;
  public Boolean active;
  public LocalDateTime releasedAt;
  public LocalDateTime createdAt;
}
