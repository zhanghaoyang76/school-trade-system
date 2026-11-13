package campus;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.lang.annotation.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.request.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface Audited {}

@Aspect
@Component
class AuditAspect {
  final Store db;

  AuditAspect(Store s) {
    db = s;
  }

  @AfterReturning("@annotation(campus.Audited)")
  public void audit(JoinPoint p) {
    var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return;
    HttpServletRequest r = attrs.getRequest();
    Object uid = r.getSession().getAttribute("uid");
    Long objectId = null;
    java.util.Set<String> fields = new java.util.TreeSet<>();
    for (Object argument : p.getArgs()) {
      if (argument instanceof Number number && objectId == null) objectId = number.longValue();
      if (argument instanceof java.util.Map<?, ?> map)
        for (Object key : map.keySet()) fields.add(key.toString());
    }
    db.insert(
        "insert into audit_logs(operator_id,action,object_type,object_id,summary)"
            + " values(?,?,?,?,?)",
        uid,
        p.getSignature().getName(),
        r.getRequestURI(),
        objectId,
        "操作成功；变更字段=" + String.join(",", fields) + "；字段值脱敏未记录");
  }
}
