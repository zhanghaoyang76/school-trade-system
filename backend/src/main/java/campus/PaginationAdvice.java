package campus;

import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class PaginationAdvice implements ResponseBodyAdvice<Object> {
  public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> t) {
    return true;
  }

  public Object beforeBodyWrite(
      Object body,
      MethodParameter p,
      MediaType media,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof Map<?, ?> envelope
        && envelope.get("data") instanceof Map<?, ?> data
        && data.get("records") instanceof List<?> records
        && data.get("total") instanceof Number total
        && records.size() == total.intValue()
        && request instanceof ServletServerHttpRequest servlet) {
      var r = servlet.getServletRequest();
      int page = 1, size = 12;
      try {
        if (r.getParameter("page") != null) page = Integer.parseInt(r.getParameter("page"));
        if (r.getParameter("size") != null) size = Integer.parseInt(r.getParameter("size"));
      } catch (NumberFormatException e) {
        throw new BusinessException(400, "分页参数错误");
      }
      page = Math.max(1, page);
      size = Math.max(1, Math.min(100, size));
      int start = (int) Math.min((long) (page - 1) * size, records.size());
      Map<String, Object> result = new LinkedHashMap<>();
      data.forEach((k, v) -> result.put(k.toString(), v));
      result.put("records", records.subList(start, Math.min(start + size, records.size())));
      Map<String, Object> wrapped = new LinkedHashMap<>();
      envelope.forEach((k, v) -> wrapped.put(k.toString(), v));
      wrapped.put("data", result);
      return wrapped;
    }
    return body;
  }
}
