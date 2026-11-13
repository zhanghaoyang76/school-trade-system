package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("product_images")
public class ProductImages {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long productId;
  public Long fileId;
  public Integer sortOrder;
  public LocalDateTime createdAt;
}
