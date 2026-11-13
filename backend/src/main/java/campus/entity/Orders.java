package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("orders")
public class Orders {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long batchId;
  public Long buyerId;
  public Long sellerId;
  public Long shopId;
  public String orderNo;
  public String status;
  public String appointmentTime;
  public String appointmentPlace;
  public LocalDateTime deliveredAt;
  public LocalDateTime confirmedAt;
  public BigDecimal feeRate;
  public String cancelReason;
  public LocalDateTime createdAt;
}
