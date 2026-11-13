package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("return_requests")
public class ReturnRequests {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long itemId;
  public Long buyerId;
  public String reason;
  public String status;
  public String reviewReason;
  public LocalDateTime reviewedAt;
  public LocalDateTime refundedAt;
  public LocalDateTime receivedAt;
  public LocalDateTime createdAt;
}
