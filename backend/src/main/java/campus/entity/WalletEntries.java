package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("wallet_entries")
public class WalletEntries {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String businessKey;
  public Long walletId;
  public String type;
  public BigDecimal availableDelta;
  public BigDecimal frozenDelta;
  public Long orderId;
  public Long operatorId;
  public LocalDateTime createdAt;
}
