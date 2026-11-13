package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("products")
public class Products {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long shopId;
  public Long categoryId;
  public String name;
  public BigDecimal originalPrice;
  public BigDecimal discount;
  public BigDecimal price;
  public String description;
  public String size;
  public String instructions;
  public Boolean negotiable;
  public String conditionText;
  public Integer stock;
  public String status;
  public String reviewReason;
  public LocalDateTime createdAt;
}
