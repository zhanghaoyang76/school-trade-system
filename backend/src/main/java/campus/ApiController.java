package campus;

import static campus.mapper.Store.*;

import jakarta.servlet.http.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  final ApiEndpointService service;

  public ApiController(ApiEndpointService service) {
    this.service = service;
  }

  Object ok(Object d) {
    return Collections.singletonMap("data", d);
  }

  long uid(HttpServletRequest r) {
    return service.uid(r);
  }

  @GetMapping("/auth/csrf")
  Object csrf(HttpServletRequest r) {
    return service.csrf(r);
  }

  @GetMapping("/auth/captcha")
  Object captcha(HttpServletRequest r) throws Exception {
    return service.captcha(r);
  }

  @PostMapping("/auth/registrations")
  Object register(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.register(b, r);
  }

  @PostMapping("/auth/login")
  Object login(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.login(b, r);
  }

  @PostMapping("/auth/logout")
  Object logout(HttpServletRequest r) {
    return service.logout(r);
  }

  @GetMapping({"/auth/me", "/users/me"})
  Object me(HttpServletRequest r) {
    return service.me(r);
  }

  @PatchMapping("/users/me")
  Object profile(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.profile(b, r);
  }

  @GetMapping("/categories")
  Object categories() {
    return service.categories();
  }

  @GetMapping("/products")
  Object products(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(required = false) Long shopId,
      @RequestParam(defaultValue = "") String sort,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return service.products(keyword, shopId, sort, categoryId, page, size);
  }

  @GetMapping("/products/{id}")
  Object product(@PathVariable long id, HttpServletRequest r) {
    return service.product(id, r);
  }

  @GetMapping("/shops/{id}")
  Object shop(@PathVariable long id) {
    return service.shop(id);
  }

  @GetMapping("/shops/{id}/products")
  Object shopProducts(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return service.shopProducts(id, page, size);
  }

  @GetMapping("/seller/products")
  Object sellerProducts(HttpServletRequest r) {
    return service.sellerProducts(r);
  }

  @PostMapping("/seller/products")
  Object create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(b, r);
  }

  @PatchMapping("/seller/products/{id}")
  Object edit(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.edit(id, b, r);
  }

  @PostMapping("/seller/products/{id}/submissions")
  Object submit(@PathVariable long id, HttpServletRequest r) {
    return service.submit(id, r);
  }

  @PostMapping("/seller/products/{id}/off-shelf")
  Object off(@PathVariable long id, HttpServletRequest r) {
    return service.off(id, r);
  }

  @GetMapping("/cart/items")
  Object cart(HttpServletRequest r) {
    return service.cart(r);
  }

  @PostMapping("/cart/items")
  Object addCart(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.addCart(b, r);
  }

  @PatchMapping("/cart/items/{id}")
  Object editCart(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.editCart(id, b, r);
  }

  @DeleteMapping("/cart/items/{id}")
  Object deleteCart(@PathVariable long id, HttpServletRequest r) {
    return service.deleteCart(id, r);
  }

  @PostMapping("/checkouts")
  Object checkout(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.checkout(b, r);
  }

  @GetMapping({"/orders", "/seller/orders"})
  Object orders(HttpServletRequest r) {
    return service.orders(r);
  }

  @GetMapping("/orders/{id}")
  Object order(@PathVariable long id, HttpServletRequest r) {
    return service.order(id, r);
  }

  @PostMapping({"/orders/{id}/cancellations", "/seller/orders/{id}/cancellations"})
  Object cancel(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.cancel(id, b, r);
  }

  @PostMapping("/orders/{id}/confirmations")
  Object confirm(@PathVariable long id, HttpServletRequest r) {
    return service.confirm(id, r);
  }

  @PostMapping("/seller/orders/{id}/deliveries")
  Object delivery(@PathVariable long id, HttpServletRequest r) {
    return service.delivery(id, r);
  }

  @PatchMapping("/seller/orders/{id}/appointment")
  Object appointment(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.appointment(id, b, r);
  }

  @PostMapping("/order-items/{id}/returns")
  Object requestReturn(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.requestReturn(id, b, r);
  }

  @GetMapping({"/returns", "/seller/returns"})
  Object returns(HttpServletRequest r) {
    return service.returns(r);
  }

  @PostMapping("/seller/returns/{id}/reviews")
  Object reviewReturn(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.reviewReturn(id, b, r);
  }

  @PostMapping("/seller/returns/{id}/receipts")
  Object receipt(@PathVariable long id, HttpServletRequest r) {
    return service.receipt(id, r);
  }

  @GetMapping("/wallet")
  Object wallet(HttpServletRequest r) {
    return service.wallet(r);
  }

  @GetMapping("/wallet/entries")
  Object walletEntries(HttpServletRequest r) {
    return service.walletEntries(r);
  }

  @GetMapping("/points")
  Object points(HttpServletRequest r) {
    return service.points(r);
  }

  @GetMapping("/points/entries")
  Object pointEntries(HttpServletRequest r) {
    return service.pointEntries(r);
  }

  @GetMapping("/users/{id}/reputation")
  Object reputation(@PathVariable long id) {
    return service.reputation(id);
  }

  @GetMapping("/products/{id}/reviews")
  Object reviews(@PathVariable long id) {
    return service.reviews(id);
  }

  @PostMapping({"/order-items/{id}/reviews", "/seller/order-items/{id}/reviews"})
  Object review(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.review(id, b, r);
  }
}
