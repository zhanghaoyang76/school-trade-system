package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
public class TradeService {
  final Store db;
  final CatalogueService catalogue;

  public TradeService(Store d, CatalogueService c) {
    db = d;
    catalogue = c;
  }

  static BigDecimal cents(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal rate(int level) {
    return new BigDecimal(new String[] {"0", "0.001", "0.002", "0.005", "0.0075", "0.01"}[level]);
  }

  Map<String, Object> wallet(long uid) {
    return db.one("select * from wallets where user_id=? for update", uid);
  }

  Map<String, Object> platform(String type) {
    return db.one("select * from wallets where account_type=? for update", type);
  }

  void entry(
      Map<String, Object> w,
      BigDecimal available,
      BigDecimal frozen,
      String key,
      String type,
      long order,
      long actor) {
    require(
        money(w, "available").add(available).signum() >= 0
            && money(w, "frozen").add(frozen).signum() >= 0,
        409,
        "账户余额不足");
    db.update(
        "update wallets set available=available+?,frozen=frozen+? where id=?",
        available,
        frozen,
        id(w, "id"));
    db.insert(
        "insert into"
            + " wallet_entries(business_key,wallet_id,type,available_delta,frozen_delta,order_id,operator_id)"
            + " values(?,?,?,?,?,?,?)",
        key,
        id(w, "id"),
        type,
        available,
        frozen,
        order,
        actor);
    w.put("available", money(w, "available").add(available));
    w.put("frozen", money(w, "frozen").add(frozen));
  }

  void points(long uid, int available, int frozen, String key, long item) {
    require(
        db.update(
                "update users set points=points+?,frozen_points=frozen_points+? where id=? and"
                    + " points+?>=0 and frozen_points+?>=0",
                available,
                frozen,
                uid,
                available,
                frozen)
            == 1,
        409,
        "积分不足");
    db.insert(
        "insert into point_entries(user_id,business_key,available_delta,frozen_delta,item_id)"
            + " values(?,?,?,?,?)",
        uid,
        key,
        available,
        frozen,
        item);
  }

  @Transactional
  public Object checkout(long uid, Map<String, Object> b) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var purchaser = db.one("select * from users where id=? for update", uid);
    require(
        !Boolean.TRUE.equals(purchaser.get("deleted"))
            && str(purchaser, "status").equals("APPROVED")
            && !str(purchaser, "role").equals("ADMIN"),
        403,
        "账号不能购买");
    String key = str(b, "requestKey");
    require(!key.isBlank() && key.length() <= 100, 400, "需要结账幂等号");
    var existing =
        db.list("select * from checkout_batches where buyer_id=? and request_key=?", uid, key);
    if (!existing.isEmpty()) return batch(id(existing.getFirst(), "id"));
    var input = (List<Map<String, Object>>) b.getOrDefault("items", List.of());
    require(!input.isEmpty(), 400, "请选择商品");
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    for (var i : input) {
      require(num(i, "quantity") > 0, 400, "数量须大于0");
      try {
        quantities.merge(id(i, "productId"), num(i, "quantity"), Math::addExact);
      } catch (ArithmeticException overflow) {
        throw new BusinessException(400, "商品数量过大");
      }
    }
    List<Map<String, Object>> products = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (var e : quantities.entrySet()) {
      var p =
          db.one(
              "select p.*,s.seller_id,s.status shop_status,s.level,u.role seller_role,u.status"
                  + " seller_status,u.deleted seller_deleted from products p join shops s on"
                  + " s.id=p.shop_id join users u on u.id=s.seller_id where p.id=? for update",
              e.getKey());
      require(
          !Boolean.TRUE.equals(p.get("sellerDeleted"))
              && str(p, "sellerStatus").equals("APPROVED")
              && id(p, "sellerId") != uid
              && str(p, "status").equals("ON_SHELF")
              && str(p, "shopStatus").equals("OPEN")
              && num(p, "stock") >= e.getValue(),
          409,
          "商品不可购买或库存不足");
      require(
          db.list(
                  "select id from blacklists where buyer_id=? and active=true and (scope='PLATFORM'"
                      + " or shop_id=?)",
                  uid,
                  id(p, "shopId"))
              .isEmpty(),
          403,
          "购买受到黑名单限制");
      p.put("quantity", e.getValue());
      p.put("lineAmount", cents(money(p, "price").multiply(BigDecimal.valueOf(e.getValue()))));
      products.add(p);
      total = total.add(money(p, "lineAmount"));
    }
    int used = num(b, "points");
    require(
        used >= 0 && used % 100 == 0 && BigDecimal.valueOf(used, 2).compareTo(total) <= 0,
        400,
        "积分须100整数倍且不超过总额");
    BigDecimal paid = total.subtract(BigDecimal.valueOf(used, 2));
    long batch =
        db.insert(
            "insert into"
                + " checkout_batches(buyer_id,request_key,total_amount,paid_amount,used_points)"
                + " values(?,?,?,?,?)",
            uid,
            key,
            total,
            paid,
            used);
    var w = wallet(uid);
    var escrow = platform("ESCROW");
    entry(w, paid.negate(), BigDecimal.ZERO, "checkout:" + batch, "PAY", 0, uid);
    entry(escrow, paid, BigDecimal.ZERO, "escrow:" + batch, "ESCROW", 0, uid);
    points(uid, -used, 0, "checkout:" + batch, 0);
    int allocated = 0;
    for (var p : products) {
      int n =
          BigDecimal.valueOf(used)
              .multiply(money(p, "lineAmount"))
              .divide(total, 0, RoundingMode.DOWN)
              .intValue();
      p.put("allocated", n);
      allocated += n;
    }
    int remainder = used - allocated;
    for (var p : products) {
      if (remainder == 0) break;
      if (num(p, "allocated") < money(p, "lineAmount").movePointRight(2).intValue()) {
        p.put("allocated", num(p, "allocated") + 1);
        remainder--;
      }
    }
    require(remainder == 0, 409, "积分分配失败");
    Map<Long, Long> orders = new LinkedHashMap<>();
    var appointments = (List<Map<String, Object>>) b.getOrDefault("appointments", List.of());
    for (var p : products) {
      long sid = id(p, "shopId");
      Long oid = orders.get(sid);
      if (oid == null) {
        var ap =
            appointments.stream().filter(a -> id(a, "shopId") == sid).findFirst().orElse(Map.of());
        require(
            !str(ap, "appointmentTime").isBlank() && !str(ap, "appointmentPlace").isBlank(),
            400,
            "请填写各店铺交易时间地点");
        oid =
            db.insert(
                "insert into"
                    + " orders(batch_id,buyer_id,seller_id,shop_id,order_no,status,appointment_time,appointment_place,fee_rate,buyer_name,seller_name,shop_name)"
                    + " values(?,?,?,?,?,'PAID',?,?,?,?,?,?)",
                batch,
                uid,
                id(p, "sellerId"),
                sid,
                "M" + UUID.randomUUID().toString().replace("-", ""),
                ap.get("appointmentTime"),
                ap.get("appointmentPlace"),
                str(p, "sellerRole").equals("MERCHANT") ? rate(num(p, "level")) : BigDecimal.ZERO,
                purchaser.get("name"),
                db.one("select name from users where id=?", id(p, "sellerId")).get("name"),
                db.one("select name from shops where id=?", sid).get("name"));
        orders.put(sid, oid);
      }
      int discount = num(p, "allocated");
      BigDecimal actual = money(p, "lineAmount").subtract(BigDecimal.valueOf(discount, 2));
      db.insert(
          "insert into"
              + " order_items(order_id,product_id,product_name,unit_price,quantity,paid_amount,used_points,reward_points,fee,settlement_status)"
              + " values(?,?,?,?,?,?,?,?,0,'ESCROW')",
          oid,
          id(p, "id"),
          p.get("name"),
          p.get("price"),
          num(p, "quantity"),
          actual,
          discount,
          actual.intValue());
      require(
          db.update(
                  "update products set stock=stock-? where id=? and stock>=?",
                  num(p, "quantity"),
                  id(p, "id"),
                  num(p, "quantity"))
              == 1,
          409,
          "库存不足");
      db.update("delete from cart_items where buyer_id=? and product_id=?", uid, id(p, "id"));
    }
    return batch(batch);
  }

  public Object batch(long id) {
    var b = db.one("select * from checkout_batches where id=?", id);
    b.put(
        "orders",
        db.list("select id from orders where batch_id=?", id).stream()
            .map(o -> order(id(o, "id")))
            .toList());
    return b;
  }

  public Map<String, Object> order(long id) {
    var o =
        db.one(
            "select o.*,coalesce(o.shop_name,s.name)"
                + " shop_display_name,coalesce(o.buyer_name,b.name)"
                + " buyer_display_name,coalesce(o.seller_name,u.name) seller_display_name from"
                + " orders o join shops s on s.id=o.shop_id join users b on b.id=o.buyer_id join"
                + " users u on u.id=o.seller_id where o.id=?",
            id);
    o.put("shopName", o.remove("shopDisplayName"));
    o.put("buyerName", o.remove("buyerDisplayName"));
    o.put("sellerName", o.remove("sellerDisplayName"));
    var items =
        db.list(
            "select oi.*,(select rr.status from return_requests rr where rr.item_id=oi.id order by"
                + " rr.id desc limit 1) return_status from order_items oi where oi.order_id=? order"
                + " by oi.id",
            id);
    o.put("items", items);
    o.put(
        "paidAmount",
        items.stream().map(i -> money(i, "paidAmount")).reduce(BigDecimal.ZERO, BigDecimal::add));
    o.put(
        "totalAmount",
        items.stream()
            .map(i -> money(i, "unitPrice").multiply(BigDecimal.valueOf(num(i, "quantity"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    return o;
  }

  Map<String, Object> lockOrder(long id, long uid, boolean seller) {
    var o = db.one("select * from orders where id=? for update", id);
    require(id(o, seller ? "sellerId" : "buyerId") == uid, 403, "订单不属于当前用户");
    return o;
  }

  @Transactional
  public void cancel(long oid, long uid, boolean seller, String reason) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var o = lockOrder(oid, uid, seller);
    if (str(o, "status").equals("CANCELLED")) return;
    require(str(o, "status").equals("PAID") && !reason.isBlank(), 409, "交付前可填写原因取消");
    var buyer = wallet(id(o, "buyerId"));
    var escrow = platform("ESCROW");
    for (var i : db.list("select * from order_items where order_id=?", oid)) {
      long iid = id(i, "id");
      entry(
          escrow,
          money(i, "paidAmount").negate(),
          BigDecimal.ZERO,
          "cancel:escrow:" + iid,
          "CANCEL",
          oid,
          uid);
      entry(
          buyer,
          money(i, "paidAmount"),
          BigDecimal.ZERO,
          "cancel:buyer:" + iid,
          "CANCEL",
          oid,
          uid);
      points(id(o, "buyerId"), num(i, "usedPoints"), 0, "cancel:" + iid, iid);
      db.update(
          "update products set stock=stock+? where id=?", num(i, "quantity"), id(i, "productId"));
    }
    db.update("update orders set status='CANCELLED',cancel_reason=? where id=?", reason, oid);
  }

  @Transactional
  public void delivery(long oid, long uid) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var o = lockOrder(oid, uid, true);
    require(str(o, "status").equals("PAID"), 409, "当前订单不可交付");
    db.update(
        "update orders set status='DELIVERED',delivered_at=? where id=?", LocalDateTime.now(), oid);
  }

  @Transactional
  public void confirm(long oid, long uid) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var o = lockOrder(oid, uid, false);
    if (str(o, "status").equals("CONFIRMED")) return;
    require(str(o, "status").equals("DELIVERED"), 409, "请先等待交付");
    var seller = wallet(id(o, "sellerId"));
    var escrow = platform("ESCROW");
    var fees = platform("FEE");
    for (var i : db.list("select * from order_items where order_id=?", oid)) {
      long iid = id(i, "id");
      BigDecimal paid = money(i, "paidAmount"), fee = cents(paid.multiply(money(o, "feeRate")));
      entry(escrow, paid.negate(), BigDecimal.ZERO, "confirm:escrow:" + iid, "SETTLE", oid, uid);
      entry(
          seller, BigDecimal.ZERO, paid.subtract(fee), "confirm:seller:" + iid, "SETTLE", oid, uid);
      entry(fees, fee, BigDecimal.ZERO, "confirm:fee:" + iid, "FEE", oid, uid);
      points(uid, 0, num(i, "rewardPoints"), "reward:" + iid, iid);
      db.update("update order_items set fee=?,settlement_status='FROZEN' where id=?", fee, iid);
    }
    db.update(
        "update orders set status='CONFIRMED',confirmed_at=? where id=?", LocalDateTime.now(), oid);
  }

  @Transactional
  public Object requestReturn(long iid, long uid, String reason) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var i = db.one("select * from order_items where id=? for update", iid);
    var o = lockOrder(id(i, "orderId"), uid, false);
    require(
        str(o, "status").equals("CONFIRMED")
            && LocalDateTime.parse(str(o, "confirmedAt")).plusHours(24).isAfter(LocalDateTime.now())
            && !reason.isBlank(),
        409,
        "退货须在确认收货24小时内申请");
    require(
        db.list("select id from return_requests where item_id=? and status!='REJECTED'", iid)
            .isEmpty(),
        409,
        "此商品已有售后申请");
    long rid =
        db.insert(
            "insert into return_requests(item_id,buyer_id,reason,status) values(?,?,?,'PENDING')",
            iid,
            uid,
            reason);
    return Map.of("id", rid, "status", "PENDING");
  }

  @Transactional
  public void reviewReturn(long rid, long uid, boolean approved, String reason) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var r = db.one("select * from return_requests where id=? for update", rid);
    var i = db.one("select * from order_items where id=? for update", id(r, "itemId"));
    var o = lockOrder(id(i, "orderId"), uid, true);
    if (!str(r, "status").equals("PENDING")) {
      require(str(r, "status").equals(approved ? "TRANSIT" : "REJECTED"), 409, "已处理");
      return;
    }
    require(approved || !reason.isBlank(), 400, "拒绝退货须说明原因");
    if (approved) {
      long iid = id(i, "id");
      var seller = wallet(uid);
      var buyer = wallet(id(o, "buyerId"));
      var fees = platform("FEE");
      entry(
          seller,
          BigDecimal.ZERO,
          money(i, "paidAmount").subtract(money(i, "fee")).negate(),
          "refund:seller:" + iid,
          "REFUND",
          id(o, "id"),
          uid);
      entry(
          fees,
          money(i, "fee").negate(),
          BigDecimal.ZERO,
          "refund:fee:" + iid,
          "FEE_REVERSAL",
          id(o, "id"),
          uid);
      entry(
          buyer,
          money(i, "paidAmount"),
          BigDecimal.ZERO,
          "refund:buyer:" + iid,
          "REFUND",
          id(o, "id"),
          uid);
      points(id(o, "buyerId"), num(i, "usedPoints"), -num(i, "rewardPoints"), "refund:" + iid, iid);
      db.update("update order_items set settlement_status='REFUNDED' where id=?", iid);
    }
    db.update(
        "update return_requests set status=?,review_reason=?,reviewed_at=?,refunded_at=? where"
            + " id=?",
        approved ? "TRANSIT" : "REJECTED",
        reason,
        LocalDateTime.now(),
        approved ? LocalDateTime.now() : null,
        rid);
    if(!approved) release(id(i,"id"));
  }

  @Transactional
  public void receipt(long rid, long uid) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var r = db.one("select * from return_requests where id=? for update", rid);
    var i = db.one("select * from order_items where id=?", id(r, "itemId"));
    lockOrder(id(i, "orderId"), uid, true);
    if (str(r, "status").equals("RETURNED")) return;
    require(str(r, "status").equals("TRANSIT"), 409, "没有待交回退货");
    db.update(
        "update products set stock=stock+? where id=?", num(i, "quantity"), id(i, "productId"));
    db.update(
        "update return_requests set status='RETURNED',received_at=? where id=?",
        LocalDateTime.now(),
        rid);
  }

  @Transactional
  public void release(long iid) {
    platform(
        "ESCROW"); // One platform row serializes money transitions in this single-node teaching
    // app.
    var i = db.one("select * from order_items where id=? for update", iid);
    if (!str(i, "settlementStatus").equals("FROZEN")) return;
    var o = db.one("select * from orders where id=?", id(i, "orderId"));
    if (LocalDateTime.parse(str(o, "confirmedAt")).plusHours(24).isAfter(LocalDateTime.now())
        || !db.list(
                "select id from return_requests where item_id=? and status in"
                    + " ('PENDING','TRANSIT')",
                iid)
            .isEmpty()) return;
    var seller = wallet(id(o, "sellerId"));
    BigDecimal income = money(i, "paidAmount").subtract(money(i, "fee"));
    entry(seller, income, income.negate(), "release:" + iid, "RELEASE", id(o, "id"), 0);
    points(
        id(o, "buyerId"), num(i, "rewardPoints"), -num(i, "rewardPoints"), "release:" + iid, iid);
    db.update("update order_items set settlement_status='RELEASED' where id=?", iid);
  }
}
