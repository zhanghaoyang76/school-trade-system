package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("cart_items")
public class CartItems {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long buyerId;
  public Long productId;
  public Integer quantity;
  public LocalDateTime createdAt;
}
