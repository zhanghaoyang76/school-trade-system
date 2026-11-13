package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Component
public class Security implements HandlerInterceptor, WebMvcConfigurer {
  final Store db;

  public Security(Store d) {
    db = d;
  }

  public void addInterceptors(InterceptorRegistry r) {
    r.addInterceptor(this).addPathPatterns("/api/**");
  }

  public long uid(HttpServletRequest r) {
    Object id = r.getSession().getAttribute("uid");
    require(id != null, 401, "请先登录");
    return ((Number) id).longValue();
  }

  public Map<String, Object> user(HttpServletRequest r) {
    var u = db.one("select * from users where id=?", uid(r));
    require(
        !Boolean.TRUE.equals(u.get("deleted")) && str(u, "status").equals("APPROVED"),
        403,
        "账号已停用或未通过审核");
    return u;
  }

  public boolean preHandle(HttpServletRequest r, HttpServletResponse p, Object h) {
    String path = r.getRequestURI(), method = r.getMethod();
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(method))
      require(
          Objects.equals(r.getSession().getAttribute("csrf"), r.getHeader("X-CSRF-TOKEN"))
              && r.getHeader("X-CSRF-TOKEN") != null,
          403,
          "CSRF令牌失效，请刷新页面");
    boolean pub =
        path.startsWith("/api/auth/")
            || path.equals("/api/files")
            || method.equals("GET")
                && (path.startsWith("/api/products")
                    || path.startsWith("/api/shops")
                    || path.equals("/api/categories")
                    || path.startsWith("/api/files/")
                    || path.matches("/api/users/\\d+/reputation"));
    if (!pub) {
      var u = user(r);
      if (path.startsWith("/api/admin/")) require(str(u, "role").equals("ADMIN"), 403, "仅管理员可操作");
      else require(!str(u, "role").equals("ADMIN"), 403, "管理员仅可使用管理功能");
    }
    return true;
  }

  public Map<String, Object> safe(Map<String, Object> input) {
    var u = new LinkedHashMap<>(input);
    u.remove("passwordHash");
    String bank = str(u, "bankAccount");
    if (bank.length() == 16)
      u.put("bankAccount", bank.substring(0, 4) + "********" + bank.substring(12));
    return u;
  }
}
