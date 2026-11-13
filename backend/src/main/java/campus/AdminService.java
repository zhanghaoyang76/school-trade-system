package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
public class AdminService implements ApplicationRunner {
  final Store db;
  final TradeService trade;
  final org.springframework.core.env.Environment env;

  public AdminService(Store d, TradeService t, org.springframework.core.env.Environment e) {
    db = d;
    trade = t;
    env = e;
  }

  public void run(ApplicationArguments a) {
    if (db.list("select id from wallets where account_type='ESCROW'").isEmpty()) {
      db.insert("insert into wallets(account_type) values('ESCROW')");
      db.insert("insert into wallets(account_type) values('FEE')");
    }
    if (db.list("select id from categories").isEmpty())
      for (String c : List.of("数码电子", "书籍教材", "生活用品", "运动户外", "服装配饰", "其他"))
        db.insert("insert into categories(name) values(?)", c);
    String user = env.getProperty("market.admin.username", System.getenv("MARKET_ADMIN_USERNAME")),
        password = env.getProperty("market.admin.password", System.getenv("MARKET_ADMIN_PASSWORD"));
    if (user != null
        && password != null
        && password.length() >= 8
        && db.list("select id from users where role='ADMIN'").isEmpty())
      db.insert(
          "insert into users(username,password_hash,role,status,name)"
              + " values(?,?,'ADMIN','APPROVED','管理员')",
          user,
          new BCryptPasswordEncoder().encode(password));
  }

  @Transactional
  public void reviewUser(long uid, boolean approved, String reason) {
    trade.platform("ESCROW");
    var u = db.one("select * from users where id=? for update", uid);
    require(!str(u, "role").equals("ADMIN"), 403, "不可审核管理员");
    require(str(u,"status").equals("PENDING"),409,"仅待审注册可审核");
    require(approved || !reason.isBlank(), 400, "拒绝须填写原因");
    db.update(
        "update users set status=?,review_reason=? where id=?",
        approved ? "APPROVED" : "REJECTED",
        reason,
        uid);
  }

  @Transactional
  public void reviewProduct(long pid, boolean approved, String reason) {
    trade.platform("ESCROW");
    var p =
        db.one(
            "select p.*,s.status shop_status,u.status seller_status,u.deleted seller_deleted from"
                + " products p join shops s on s.id=p.shop_id join users u on u.id=s.seller_id"
                + " where p.id=? for update",
            pid);
    require(
        !Boolean.TRUE.equals(p.get("sellerDeleted"))
            && str(p, "sellerStatus").equals("APPROVED")
            && str(p, "shopStatus").equals("OPEN"),
        409,
        "店铺已关闭");
    require(str(p,"status").equals("PENDING"),409,"仅待审商品可审核");
    require(approved || !reason.isBlank(), 400, "拒绝须填写原因");
    db.update(
        "update products set status=?,review_reason=? where id=?",
        approved ? "ON_SHELF" : "REJECTED",
        reason,
        pid);
  }

  @Transactional
  public void recharge(long uid, Map<String, Object> b, long actor) {
    trade.platform("ESCROW");
    var amount = money(b, "amount");
    require(
        amount.signum() > 0 && amount.scale() <= 2 && !str(b, "requestKey").isBlank(),
        400,
        "充值金额或幂等号错误");
    String key = "recharge:" + uid + ":" + str(b, "requestKey");
    var wallet = trade.wallet(uid);
    if (!db.list("select id from wallet_entries where business_key=?", key).isEmpty()) return;
    trade.entry(wallet, amount, BigDecimal.ZERO, key, "RECHARGE", 0, actor);
  }

  @Transactional
  public void closure(long sid, boolean closed) {
    trade.platform("ESCROW");
    var owner =
        db.one(
            "select u.* from shops s join users u on u.id=s.seller_id where s.id=? for update",
            sid);
    require(
        closed
            || !Boolean.TRUE.equals(owner.get("deleted"))
                && str(owner, "status").equals("APPROVED"),
        409,
        "卖家账号不可用");
    db.update("update shops set status=? where id=?", closed ? "CLOSED" : "OPEN", sid);
    if (closed) db.update("update products set status='OFF_SHELF' where shop_id=?", sid);
  }

  @Transactional
  public void deleteUser(long uid) {
    trade.platform("ESCROW");
    var u = db.one("select * from users where id=? for update", uid);
    require(!str(u, "role").equals("ADMIN"), 403, "不可删除管理员");
    require(
        db.list(
                    "select id from orders where (buyer_id=? or seller_id=?) and status not in"
                        + " ('CONFIRMED','CANCELLED')",
                    uid,
                    uid)
                .isEmpty()
            && db.list(
                    "select oi.id from order_items oi join orders o on o.id=oi.order_id where"
                        + " (o.buyer_id=? or o.seller_id=?) and oi.settlement_status='FROZEN'",
                    uid,
                    uid)
                .isEmpty()
            && db.list(
                    "select r.id from return_requests r join order_items i on i.id=r.item_id join"
                        + " orders o on o.id=i.order_id where (o.buyer_id=? or o.seller_id=?) and"
                        + " r.status in ('PENDING','TRANSIT')",
                    uid,
                    uid)
                .isEmpty(),
        409,
        "存在未完成交易或售后");
    db.update("update users set deleted=true where id=?", uid);
    for (var s : db.list("select id from shops where seller_id=?", uid)) closure(id(s, "id"), true);
  }

  @Transactional
  public void level(long sid, Map<String, Object> b) {
    trade.platform("ESCROW");
    String mode = str(b, "mode");
    require(Set.of("MANUAL", "AUTO").contains(mode), 400, "等级模式错误");
    int level = num(b, "level");
    require(mode.equals("AUTO") || level >= 1 && level <= 5, 400, "等级须1至5");
    db.update(
        "update shops set level_mode=?,level=? where id=?",
        mode,
        mode.equals("AUTO") ? 1 : level,
        sid);
    if (mode.equals("AUTO")) recalculate(sid);
  }

  @Transactional
  public void recalculate(long sid) {
    trade.platform("ESCROW");
    var s =
        db.one("select s.*,u.role from shops s join users u on u.id=s.seller_id where s.id=?", sid);
    if (!str(s, "levelMode").equals("AUTO") || !str(s, "role").equals("MERCHANT")) return;
    BigDecimal amount =
        money(
            db.one(
                "select coalesce(sum(i.paid_amount),0) amount from order_items i join orders o on"
                    + " o.id=i.order_id where o.shop_id=? and i.settlement_status='RELEASED'",
                sid),
            "amount");
    var stats =
        db.one(
            "select avg(case when r.service_stars>=4 then 100.0 else 0 end) positive from reviews r"
                + " join order_items i on i.id=r.item_id join orders o on o.id=i.order_id where"
                + " o.shop_id=? and r.direction='BUYER'",
            sid);
    int level = 1;
    int[] thresholds = {0, 500, 2000, 5000, 10000}, percent = {0, 80, 85, 90, 95};
    if (stats.get("positive") != null)
      for (int k = 1; k < 5; k++)
        if (amount.compareTo(BigDecimal.valueOf(thresholds[k])) >= 0
            && money(stats, "positive").compareTo(BigDecimal.valueOf(percent[k])) >= 0)
          level = k + 1;
    db.update("update shops set level=? where id=? and level_mode='AUTO'", level, sid);
  }

  @Scheduled(fixedDelay = 60000)
  public void tasks() {
    for (var o :
        db.list(
            "select id,buyer_id from orders where status='DELIVERED' and delivered_at<=?",
            LocalDateTime.now().minusDays(7)))
      try {
        trade.confirm(id(o, "id"), id(o, "buyerId"));
      } catch (BusinessException ignored) {
      }
    for (var i : db.list("select id from order_items where settlement_status='FROZEN'"))
      trade.release(id(i, "id"));
    for (var s : db.list("select id from shops where level_mode='AUTO'")) recalculate(id(s, "id"));
  }
}
