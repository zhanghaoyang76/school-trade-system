package campus;

import static campus.mapper.Store.*;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminController {
  final AdminEndpointService service;

  public AdminController(AdminEndpointService service) {
    this.service = service;
  }

  Object ok(Object d) {
    return Collections.singletonMap("data", d);
  }

  @GetMapping("/admin/registrations")
  Object registrations() {
    return service.registrations();
  }

  @PostMapping("/admin/registrations/{id}/reviews")
  Object reviewUser(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return service.reviewUser(id, b);
  }

  @GetMapping("/admin/products")
  Object products() {
    return service.products();
  }

  @PostMapping("/admin/products/{id}/reviews")
  Object reviewProduct(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return service.reviewProduct(id, b);
  }

  @GetMapping("/admin/users")
  Object users() {
    return service.users();
  }

  @PatchMapping("/admin/users/{id}")
  Object edit(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return service.edit(id, b);
  }

  @DeleteMapping("/admin/users/{id}")
  Object delete(@PathVariable long id) {
    return service.delete(id);
  }

  @PostMapping("/admin/users/{id}/recharges")
  Object recharge(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.recharge(id, b, r);
  }

  @GetMapping({"/admin/shops", "/admin/merchants"})
  Object shops(HttpServletRequest r) {
    return service.shops(r);
  }

  @PatchMapping("/admin/shops/{id}/level")
  Object level(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return service.level(id, b);
  }

  @PostMapping({"/admin/shops/{id}/closures", "/admin/shops/{id}/reopenings"})
  Object closure(@PathVariable long id, HttpServletRequest r) {
    return service.closure(id, r);
  }

  @GetMapping("/admin/fees")
  Object fees() {
    return service.fees();
  }

  @GetMapping({"/seller/blacklists", "/admin/blacklists"})
  Object blacklists(HttpServletRequest r) {
    return service.blacklists(r);
  }

  @PostMapping({"/seller/blacklists", "/admin/blacklists"})
  Object blacklist(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.blacklist(b, r);
  }

  @DeleteMapping({"/seller/blacklists/{id}", "/admin/blacklists/{id}"})
  Object unblacklist(@PathVariable long id, HttpServletRequest r) {
    return service.unblacklist(id, r);
  }
}
