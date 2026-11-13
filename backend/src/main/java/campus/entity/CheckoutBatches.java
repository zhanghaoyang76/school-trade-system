package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("checkout_batches")
public class CheckoutBatches {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long buyerId;
  public String requestKey;
  public BigDecimal totalAmount;
  public BigDecimal paidAmount;
  public Integer usedPoints;
  public LocalDateTime createdAt;
}
