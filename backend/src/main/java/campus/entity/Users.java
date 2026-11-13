package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("users")
public class Users {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String username;
  public String passwordHash;
  public String role;
  public String status;
  public String name;
  public String phone;
  public String email;
  public String city;
  public String gender;
  public String bankAccount;
  public Long licenseFileId;
  public Long identityFileId;
  public String reviewReason;
  public Integer points;
  public Integer frozenPoints;
  public Boolean deleted;
  public LocalDateTime createdAt;
}
