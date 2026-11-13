package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("wallets")
public class Wallets {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long userId;
  public String accountType;
  public BigDecimal available;
  public BigDecimal frozen;
  public LocalDateTime createdAt;
}
