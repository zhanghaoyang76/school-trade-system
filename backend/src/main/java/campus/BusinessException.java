package campus;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public class BusinessException extends RuntimeException {
  public final int status;

  public BusinessException(int status, String message) {
    super(message);
    this.status = status;
  }

  public static void require(boolean yes, int status, String message) {
    if (!yes) throw new BusinessException(status, message);
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(java.util.Map.of("message", e.getMessage()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    NumberFormatException.class
  })
  ResponseEntity<?> parameter(Exception e) {
    return ResponseEntity.badRequest().body(java.util.Map.of("message", "请求参数格式不正确"));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(java.util.Map.of("message", "记录重复或不满足数据约束"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> error(Exception e) {
    return ResponseEntity.status(500).body(java.util.Map.of("message", "服务器处理失败，请检查参数或联系管理员"));
  }
}
