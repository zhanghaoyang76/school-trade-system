package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.stereotype.Service
public class AdminEndpointService {
  final Store db;
  final Security sec;
  final AdminService admin;
  final AccountService accounts;
  final CatalogueService catalogue;

  public AdminEndpointService(
      Store d, Security s, AdminService a, AccountService ac, CatalogueService c) {
    db = d;
    sec = s;
    admin = a;
    accounts = ac;
    catalogue = c;
  }

  Object ok(Object d) {
    return Collections.singletonMap("data", d);
  }

  Object registrations() {
    return ok(
        db.page(
            db
                .list(
                    "select * from users where role!='ADMIN' and status!='APPROVED' order by id"
                        + " desc")
                .stream()
                .map(sec::safe)
                .toList()));
  }

  @Audited
  Object reviewUser(long id, Map<String, Object> b) {
    admin.reviewUser(id, Boolean.TRUE.equals(b.get("approved")), str(b, "reason"));
    return ok(true);
  }

  Object products() {
    return ok(
        db.page(
            db.list("select id from products order by id desc").stream()
                .map(p -> catalogue.product(id(p, "id")))
                .toList()));
  }

  @Audited
  Object reviewProduct(long id, Map<String, Object> b) {
    admin.reviewProduct(id, Boolean.TRUE.equals(b.get("approved")), str(b, "reason"));
    return ok(true);
  }

  Object users() {
    return ok(
        db.page(db.list("select * from users order by id desc").stream().map(sec::safe).toList()));
  }

  @Audited
  Object edit(long id, Map<String, Object> b) {
    return ok(accounts.profile(id, b));
  }

  @Audited
  Object delete(long id) {
    admin.deleteUser(id);
    return ok(true);
  }

  @Audited
  Object recharge(long id, Map<String, Object> b, HttpServletRequest r) {
    admin.recharge(id, b, sec.uid(r));
    return ok(true);
  }

  Object shops(HttpServletRequest r) {
    return ok(
        db.page(
            db.list(
                "select s.*,u.name seller_name,u.role from shops s join users u on u.id=s.seller_id"
                    + (r.getRequestURI().endsWith("merchants") ? " where u.role='MERCHANT'" : "")
                    + " order by s.id desc")));
  }

  @Audited
  Object level(long id, Map<String, Object> b) {
    admin.level(id, b);
    return ok(true);
  }

  @Audited
  Object closure(long id, HttpServletRequest r) {
    admin.closure(id, r.getRequestURI().endsWith("closures"));
    return ok(true);
  }

  Object fees() {
    return ok(
        Map.of(
            "rates",
            List.of(0.001, 0.002, 0.005, 0.0075, 0.01),
            "wallet",
            db.one("select available,frozen from wallets where account_type='FEE'"),
            "entries",
            db.list(
                "select e.* from wallet_entries e join wallets w on w.id=e.wallet_id where"
                    + " w.account_type='FEE' order by e.id desc")));
  }

  Object blacklists(HttpServletRequest r) {
    boolean platform = r.getRequestURI().contains("/admin/");
    return ok(
        db.page(
            platform
                ? db.list(
                    "select b.*,u.name buyer_name from blacklists b join users u on u.id=b.buyer_id"
                        + " where b.scope='PLATFORM' and b.active=true")
                : db.list(
                    "select b.*,u.name buyer_name from blacklists b join users u on u.id=b.buyer_id"
                        + " join shops s on s.id=b.shop_id where s.seller_id=? and b.active=true",
                    sec.uid(r))));
  }

  @Audited
  Object blacklist(Map<String, Object> b, HttpServletRequest r) {
    boolean platform = r.getRequestURI().contains("/admin/");
    long actor = sec.uid(r), buyer = id(b, "buyerId");
    var u = db.one("select * from users where id=? and deleted=false", buyer);
    require(
        !str(u, "role").equals("ADMIN") && buyer != actor && !str(b, "reason").isBlank(),
        400,
        "请选择有效购买者并填写原因");
    long shop = platform ? 0 : id(catalogue.shop(actor), "id");
    db.insert(
        "insert into blacklists(scope,shop_id,operator_id,buyer_id,reason) values(?,?,?,?,?)",
        platform ? "PLATFORM" : "SHOP",
        platform ? null : shop,
        actor,
        buyer,
        b.get("reason"));
    return ok(true);
  }

  @Audited
  Object unblacklist(long id, HttpServletRequest r) {
    var b = db.one("select * from blacklists where id=?", id);
    boolean platform = r.getRequestURI().contains("/admin/");
    require(
        platform
            ? str(b, "scope").equals("PLATFORM")
            : str(b, "scope").equals("SHOP")
                && id(catalogue.shop(sec.uid(r)), "id") == id(b, "shopId"),
        403,
        "不能解除其他范围黑名单");
    db.update("update blacklists set active=false,released_at=current_timestamp where id=?", id);
    return ok(true);
  }
}
