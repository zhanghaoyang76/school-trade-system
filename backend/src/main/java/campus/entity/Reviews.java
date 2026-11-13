package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("reviews")
public class Reviews {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long itemId;
  public String direction;
  public Long authorId;
  public Long targetId;
  public Integer productStars;
  public Integer serviceStars;
  public Integer buyerStars;
  public String content;
  public LocalDateTime createdAt;
}
