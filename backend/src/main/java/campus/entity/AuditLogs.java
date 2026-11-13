package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("audit_logs")
public class AuditLogs {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long operatorId;
  public String action;
  public String objectType;
  public Long objectId;
  public String summary;
  public LocalDateTime createdAt;
}
