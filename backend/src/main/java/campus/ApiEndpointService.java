package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.stereotype.Service
public class ApiEndpointService {
  final Store db;
  final Security sec;
  final AccountService accounts;
  final CatalogueService catalogue;
  final TradeService trade;
  final AdminService admin;

  public ApiEndpointService(
      Store d, Security s, AccountService a, CatalogueService c, TradeService t, AdminService ad) {
    db = d;
    sec = s;
    accounts = a;
    catalogue = c;
    trade = t;
    admin = ad;
  }

  Object ok(Object d) {
    return Collections.singletonMap("data", d);
  }

  long uid(HttpServletRequest r) {
    return sec.uid(r);
  }

  Object csrf(HttpServletRequest r) {
    String token = (String) r.getSession().getAttribute("csrf");
    if (token == null) {
      token = UUID.randomUUID().toString();
      r.getSession().setAttribute("csrf", token);
    }
    return ok(Map.of("token", token, "headerName", "X-CSRF-TOKEN"));
  }

  Object captcha(HttpServletRequest r) throws Exception {
    String code = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
    r.getSession().setAttribute("captcha", code);
    r.getSession().setAttribute("captchaExpires", System.currentTimeMillis() + 180000);
    BufferedImage im = new BufferedImage(150, 50, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = im.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 150, 50);
    var random = new java.security.SecureRandom();
    for (int i = 0; i < 15; i++) {
      g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
      g.drawLine(random.nextInt(150), random.nextInt(50), random.nextInt(150), random.nextInt(50));
    }
    g.setFont(new Font("SansSerif", Font.BOLD, 32));
    g.setColor(Color.DARK_GRAY);
    g.drawString(code, 28, 37);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(im, "png", out);
    return ok(
        Map.of(
            "image",
            "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()),
            "captchaId",
            UUID.randomUUID().toString()));
  }

  Object register(Map<String, Object> b, HttpServletRequest r) {
    return ok(accounts.register(b, r));
  }

  Object login(Map<String, Object> b, HttpServletRequest r) {
    return ok(accounts.login(b, r));
  }

  Object logout(HttpServletRequest r) {
    r.getSession().invalidate();
    return ok(true);
  }

  Object me(HttpServletRequest r) {
    return ok(sec.safe(sec.user(r)));
  }

  @Audited
  Object profile(Map<String, Object> b, HttpServletRequest r) {
    return ok(accounts.profile(uid(r), b));
  }

  Object categories() {
    return ok(db.list("select * from categories order by sort_order,id"));
  }

  Object products(String keyword, Long shopId, String sort, Long categoryId, int page, int size) {
    return ok(catalogue.search(keyword, shopId, sort, categoryId, page, size));
  }

  Object product(long id, HttpServletRequest r) {
    var p = catalogue.product(id);
    if (!str(p, "status").equals("ON_SHELF")) {
      var u = sec.user(r);
      require(id(p, "sellerId") == id(u, "id") || str(u, "role").equals("ADMIN"), 403, "商品未上架");
    }
    return ok(p);
  }

  Object shop(long id) {
    var shop =
        db.one(
            "select s.*,u.name seller_name from shops s join users u on u.id=s.seller_id where"
                + " s.id=?",
            id);
    var stats =
        db.one(
            "select count(*) review_count,avg(case when r.service_stars>=4 then 100.0 else 0 end)"
                + " service_positive_rate from reviews r join order_items i on i.id=r.item_id join"
                + " orders o on o.id=i.order_id where o.shop_id=? and r.direction='BUYER'",
            id);
    shop.putAll(stats);
    shop.put(
        "soldCount",
        db.one(
                "select coalesce(sum(i.quantity),0) sold_count from order_items i join orders o on"
                    + " o.id=i.order_id where o.shop_id=? and o.status='CONFIRMED' and"
                    + " i.settlement_status!='REFUNDED'",
                id)
            .get("soldCount"));
    shop.put(
        "reviews",
        db.list(
            "select r.*,u.name author_name from reviews r join order_items i on i.id=r.item_id join"
                + " orders o on o.id=i.order_id join users u on u.id=r.author_id where o.shop_id=?"
                + " and r.direction='BUYER' order by r.id desc limit 10",
            id));
    return ok(shop);
  }

  Object shopProducts(long id, int page, int size) {
    return ok(catalogue.search("", id, "", null, page, size));
  }

  Object sellerProducts(HttpServletRequest r) {
    return ok(catalogue.sellerProducts(uid(r)));
  }

  Object create(Map<String, Object> b, HttpServletRequest r) {
    return ok(catalogue.save(uid(r), null, b));
  }

  Object edit(long id, Map<String, Object> b, HttpServletRequest r) {
    return ok(catalogue.save(uid(r), id, b));
  }

  Object submit(long id, HttpServletRequest r) {
    catalogue.own(uid(r), id);
    require(str(catalogue.shop(uid(r)), "status").equals("OPEN"), 409, "店铺已关闭");
    db.update("update products set status='PENDING' where id=?", id);
    return ok(true);
  }

  Object off(long id, HttpServletRequest r) {
    catalogue.own(uid(r), id);
    db.update("update products set status='OFF_SHELF' where id=?", id);
    return ok(true);
  }

  Object cart(HttpServletRequest r) {
    return ok(catalogue.cart(uid(r)));
  }

  Object addCart(Map<String, Object> b, HttpServletRequest r) {
    catalogue.addCart(uid(r), b);
    return ok(true);
  }

  Object editCart(long id, Map<String, Object> b, HttpServletRequest r) {
    require(num(b, "quantity") > 0, 400, "数量须大于0");
    require(
        db.update(
                "update cart_items set quantity=? where id=? and buyer_id=?",
                num(b, "quantity"),
                id,
                uid(r))
            == 1,
        404,
        "购物车记录不存在");
    return ok(true);
  }

  Object deleteCart(long id, HttpServletRequest r) {
    db.update("delete from cart_items where id=? and buyer_id=?", id, uid(r));
    return ok(true);
  }

  Object checkout(Map<String, Object> b, HttpServletRequest r) {
    return ok(trade.checkout(uid(r), b));
  }

  Object orders(HttpServletRequest r) {
    boolean seller = r.getRequestURI().contains("/seller/");
    return ok(
        db.page(
            db
                .list(
                    "select id from orders where "
                        + (seller ? "seller_id" : "buyer_id")
                        + "=? order by id desc",
                    uid(r))
                .stream()
                .map(o -> trade.order(id(o, "id")))
                .toList()));
  }

  Object order(long id, HttpServletRequest r) {
    var o = trade.order(id);
    require(id(o, "buyerId") == uid(r) || id(o, "sellerId") == uid(r), 403, "订单不属于当前用户");
    return ok(o);
  }

  Object cancel(long id, Map<String, Object> b, HttpServletRequest r) {
    trade.cancel(id, uid(r), r.getRequestURI().contains("/seller/"), str(b, "reason"));
    return ok(true);
  }

  Object confirm(long id, HttpServletRequest r) {
    trade.confirm(id, uid(r));
    return ok(true);
  }

  Object delivery(long id, HttpServletRequest r) {
    trade.delivery(id, uid(r));
    return ok(true);
  }

  Object appointment(long id, Map<String, Object> b, HttpServletRequest r) {
    var o = trade.order(id);
    require(id(o, "sellerId") == uid(r), 403, "非本人订单");
    require(
        str(o, "status").equals("PAID")
            && !str(b, "appointmentTime").isBlank()
            && !str(b, "appointmentPlace").isBlank(),
        409,
        "只能修改待交付订单的有效约定");
    db.update(
        "update orders set appointment_time=?,appointment_place=? where id=?",
        b.get("appointmentTime"),
        b.get("appointmentPlace"),
        id);
    return ok(true);
  }

  Object requestReturn(long id, Map<String, Object> b, HttpServletRequest r) {
    return ok(trade.requestReturn(id, uid(r), str(b, "reason")));
  }

  Object returns(HttpServletRequest r) {
    return ok(
        db.page(
            db.list(
                "select rr.*,i.product_name,i.quantity,i.paid_amount,b.name buyer_name,s.name"
                    + " seller_name from return_requests rr join order_items i on i.id=rr.item_id"
                    + " join orders o on o.id=i.order_id join users b on b.id=o.buyer_id join users"
                    + " s on s.id=o.seller_id where "
                    + (r.getRequestURI().contains("/seller/") ? "o.seller_id" : "o.buyer_id")
                    + "=? order by rr.id desc",
                uid(r))));
  }

  Object reviewReturn(long id, Map<String, Object> b, HttpServletRequest r) {
    trade.reviewReturn(id, uid(r), Boolean.TRUE.equals(b.get("approved")), str(b, "reason"));
    return ok(true);
  }

  Object receipt(long id, HttpServletRequest r) {
    trade.receipt(id, uid(r));
    return ok(true);
  }

  Object wallet(HttpServletRequest r) {
    return ok(db.one("select available,frozen from wallets where user_id=?", uid(r)));
  }

  Object walletEntries(HttpServletRequest r) {
    return ok(
        db.page(
            db.list(
                "select e.* from wallet_entries e join wallets w on w.id=e.wallet_id where"
                    + " w.user_id=? order by e.id desc",
                uid(r))));
  }

  Object points(HttpServletRequest r) {
    var u = sec.user(r);
    return ok(Map.of("available", u.get("points"), "frozen", u.get("frozenPoints")));
  }

  Object pointEntries(HttpServletRequest r) {
    return ok(
        db.page(db.list("select * from point_entries where user_id=? order by id desc", uid(r))));
  }

  Object reputation(long id) {
    return ok(
        db.one(
            "select count(*) review_count,avg(case when buyer_stars>=4 then 100.0 else 0 end)"
                + " positive_rate from reviews where target_id=? and direction='SELLER'",
            id));
  }

  Object reviews(long id) {
    return ok(
        db.page(
            db.list(
                "select r.*,u.name author_name from reviews r join order_items i on i.id=r.item_id"
                    + " join users u on u.id=r.author_id where i.product_id=? and"
                    + " r.direction='BUYER' order by r.id desc",
                id)));
  }

  Object review(long id, Map<String, Object> b, HttpServletRequest r) {
    boolean seller = r.getRequestURI().contains("/seller/");
    var i = db.one("select * from order_items where id=?", id);
    var o = trade.order(id(i, "orderId"));
    require(
        id(o, seller ? "sellerId" : "buyerId") == uid(r) && str(o, "status").equals("CONFIRMED"),
        403,
        "仅确认收货后的交易双方可评价");
    for (String field : seller ? List.of("buyerStars") : List.of("productStars", "serviceStars"))
      require(num(b, field) >= 1 && num(b, field) <= 5, 400, "评分须1至5星");
    require(
        db.list(
                "select id from reviews where item_id=? and direction=?",
                id,
                seller ? "SELLER" : "BUYER")
            .isEmpty(),
        409,
        "已评价");
    db.insert(
        "insert into"
            + " reviews(item_id,direction,author_id,target_id,product_stars,service_stars,buyer_stars,content)"
            + " values(?,?,?,?,?,?,?,?)",
        id,
        seller ? "SELLER" : "BUYER",
        uid(r),
        id(o, seller ? "buyerId" : "sellerId"),
        b.get("productStars"),
        b.get("serviceStars"),
        b.get("buyerStars"),
        str(b, "content"));
    admin.recalculate(id(o, "shopId"));
    return ok(true);
  }
}
