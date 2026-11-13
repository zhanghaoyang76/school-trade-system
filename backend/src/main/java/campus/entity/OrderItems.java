package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("order_items")
public class OrderItems {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long orderId;
  public Long productId;
  public String productName;
  public BigDecimal unitPrice;
  public Integer quantity;
  public BigDecimal paidAmount;
  public Integer usedPoints;
  public Integer rewardPoints;
  public BigDecimal fee;
  public String settlementStatus;
  public LocalDateTime createdAt;
}
