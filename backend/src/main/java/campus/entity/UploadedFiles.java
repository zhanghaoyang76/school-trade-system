package campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uploaded_files")
public class UploadedFiles {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long ownerId;
  public String sessionKey;
  public String purpose;
  public String storageName;
  public String originalName;
  public String contentType;
  public Long sizeBytes;
  public LocalDateTime createdAt;
}
