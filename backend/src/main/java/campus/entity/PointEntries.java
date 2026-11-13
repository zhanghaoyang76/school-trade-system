package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("point_entries")
public class PointEntries {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long userId;
  public String businessKey;
  public Integer availableDelta;
  public Integer frozenDelta;
  public Long itemId;
  public LocalDateTime createdAt;
}
