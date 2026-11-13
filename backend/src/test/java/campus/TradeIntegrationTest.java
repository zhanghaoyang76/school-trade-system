package campus;

import static campus.mapper.Store.*;
import static org.junit.jupiter.api.Assertions.*;

import campus.mapper.Store;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trade;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.sql.init.mode=always",
      "spring.config.import="
    })
class TradeIntegrationTest {
  @Autowired Store db;
  @Autowired TradeService trade;
  @Autowired AdminService admin;
  long buyer, seller, product, shop;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "reviews",
            "return_requests",
            "order_items",
            "orders",
            "checkout_batches",
            "wallet_entries",
            "point_entries",
            "cart_items",
            "product_images",
            "products",
            "blacklists",
            "shops",
            "wallets",
            "users")) db.update("delete from " + table);
    buyer = user("buyer", "USER");
    seller = user("seller", "MERCHANT");
    shop = db.insert("insert into shops(seller_id,name,level) values(?,'店',5)", seller);
    product = product(shop, 10, "100.00");
    db.insert("insert into wallets(account_type) values('ESCROW')");
    db.insert("insert into wallets(account_type) values('FEE')");
    db.update("update wallets set available=1000 where user_id=?", buyer);
    db.update("update users set points=1000 where id=?", buyer);
  }

  long user(String name, String role) {
    long id =
        db.insert(
            "insert into users(username,password_hash,role,status) values(?,'hash',?,'APPROVED')",
            name,
            role);
    db.insert("insert into wallets(user_id,account_type) values(?,'USER')", id);
    return id;
  }

  long product(long sid, int stock, String price) {
    return db.insert(
        "insert into products(shop_id,name,price,stock,status) values(?,'物品',?,?,'ON_SHELF')",
        sid,
        new BigDecimal(price),
        stock);
  }

  Map<String, Object> checkout(
      String key, int points, List<Map<String, Object>> items, List<Map<String, Object>> aps) {
    return Map.of("requestKey", key, "points", points, "items", items, "appointments", aps);
  }

  Map<String, Object> request(String key, int points) {
    return checkout(
        key,
        points,
        List.of(Map.of("productId", product, "quantity", 1)),
        List.of(Map.of("shopId", shop, "appointmentTime", "明天", "appointmentPlace", "校门")));
  }

  long orderId(Object batch) {
    return id(
        ((List<Map<String, Object>>) ((Map<String, Object>) batch).get("orders")).getFirst(), "id");
  }

  @Test
  void idempotentCheckoutDebitsOnce() {
    long first = orderId(trade.checkout(buyer, request("once", 100)));
    assertEquals(first, orderId(trade.checkout(buyer, request("once", 100))));
    assertEquals(
        0,
        new BigDecimal("901.00")
            .compareTo(money(db.one("select * from wallets where user_id=?", buyer), "available")));
    assertEquals(9, num(db.one("select * from products where id=?", product), "stock"));
  }

  @Test
  void crossSellerFailureRollsBackEverything() {
    long other = user("other", "USER"),
        otherShop = db.insert("insert into shops(seller_id,name) values(?,'其他')", other),
        bad = product(otherShop, 0, "20");
    var req =
        checkout(
            "rollback",
            0,
            List.of(
                Map.of("productId", product, "quantity", 1),
                Map.of("productId", bad, "quantity", 1)),
            List.of(
                Map.of("shopId", shop, "appointmentTime", "明天", "appointmentPlace", "校门"),
                Map.of("shopId", otherShop, "appointmentTime", "明天", "appointmentPlace", "校门")));
    assertThrows(BusinessException.class, () -> trade.checkout(buyer, req));
    assertEquals(10, num(db.one("select * from products where id=?", product), "stock"));
    assertEquals(
        0,
        new BigDecimal("1000")
            .compareTo(money(db.one("select * from wallets where user_id=?", buyer), "available")));
    assertTrue(db.list("select * from checkout_batches").isEmpty());
  }

  @Test
  void refundReversesFeeAndPointsButInventoryWaitsForReceipt() {
    long oid = orderId(trade.checkout(buyer, request("refund", 100)));
    trade.delivery(oid, seller);
    trade.confirm(oid, buyer);
    long iid = id(db.one("select * from order_items where order_id=?", oid), "id");
    long rid = id((Map<String, Object>) trade.requestReturn(iid, buyer, "不适用"), "id");
    trade.reviewReturn(rid, seller, true, "");
    trade.reviewReturn(rid, seller, true, "");
    assertEquals(9, num(db.one("select * from products where id=?", product), "stock"));
    assertEquals(1000, num(db.one("select * from users where id=?", buyer), "points"));
    assertEquals(0, num(db.one("select * from users where id=?", buyer), "frozenPoints"));
    assertEquals(
        0, money(db.one("select * from wallets where account_type='FEE'"), "available").signum());
    trade.receipt(rid, seller);
    trade.receipt(rid, seller);
    assertEquals(10, num(db.one("select * from products where id=?", product), "stock"));
  }

  @Test
  void schedulerHonorsSevenDaysAndTwentyFourHoursAndIsIdempotent() {
    long oid = orderId(trade.checkout(buyer, request("scheduled", 0)));
    trade.delivery(oid, seller);
    db.update(
        "update orders set delivered_at=? where id=?",
        LocalDateTime.now().minusDays(7).plusMinutes(1),
        oid);
    admin.tasks();
    assertEquals("DELIVERED", str(trade.order(oid), "status"));
    db.update(
        "update orders set delivered_at=? where id=?",
        LocalDateTime.now().minusDays(7).minusSeconds(1),
        oid);
    admin.tasks();
    assertEquals("CONFIRMED", str(trade.order(oid), "status"));
    db.update(
        "update orders set confirmed_at=? where id=?",
        LocalDateTime.now().minusHours(24).minusSeconds(1),
        oid);
    admin.tasks();
    admin.tasks();
    assertEquals(100, num(db.one("select * from users where id=?", buyer), "points") - 1000);
    assertEquals(
        0, money(db.one("select * from wallets where user_id=?", seller), "frozen").signum());
  }

  @Test
  void lastUnitCannotBeOversold() throws Exception {
    db.update("update products set stock=1 where id=?", product);
    long buyer2 = user("buyer2", "USER");
    db.update("update wallets set available=1000 where user_id=?", buyer2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    for (long uid : List.of(buyer, buyer2))
      results.add(
          executor.submit(
              () -> {
                start.await();
                try {
                  trade.checkout(uid, request("race", 0));
                  return true;
                } catch (RuntimeException e) {
                  return false;
                }
              }));
    start.countDown();
    int successes = 0;
    for (var f : results) if (f.get(20, TimeUnit.SECONDS)) successes++;
    executor.shutdownNow();
    assertEquals(1, successes);
    assertEquals(0, num(db.one("select * from products where id=?", product), "stock"));
  }

  @Test
  void blacklistAndClosedShopBlockNewPurchases() {
    db.insert("insert into blacklists(scope,buyer_id,active) values('PLATFORM',?,true)", buyer);
    assertThrows(BusinessException.class, () -> trade.checkout(buyer, request("blocked", 0)));
    db.update("delete from blacklists");
    admin.closure(shop, true);
    assertThrows(BusinessException.class, () -> trade.checkout(buyer, request("closed", 0)));
  }

  @Test
  void pointsAllocationPreservesEveryCentAndRefundsOriginalAllocation() {
    db.update("update products set price=0.33 where id=?", product);
    long second = product(shop, 2, "0.33"), third = product(shop, 2, "0.34");
    var b =
        checkout(
            "allocation",
            100,
            List.of(
                Map.of("productId", product, "quantity", 1),
                Map.of("productId", second, "quantity", 1),
                Map.of("productId", third, "quantity", 1)),
            List.of(Map.of("shopId", shop, "appointmentTime", "明天", "appointmentPlace", "校门")));
    long oid = orderId(trade.checkout(buyer, b));
    assertEquals(
        100,
        db.list("select * from order_items where order_id=?", oid).stream()
            .mapToInt(i -> num(i, "usedPoints"))
            .sum());
    assertEquals(
        0,
        db.list("select * from order_items where order_id=?", oid).stream()
            .map(i -> money(i, "paidAmount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());
    trade.cancel(oid, buyer, false, "取消");
    assertEquals(1000, num(db.one("select * from users where id=?", buyer), "points"));
  }

  @Test
  void rejectedReturnCanReapplyInsideWindowButCannotStartAfterDeadline() {
    long oid = orderId(trade.checkout(buyer, request("deadline", 0)));
    trade.delivery(oid, seller);
    trade.confirm(oid, buyer);
    long iid = id(db.one("select * from order_items where order_id=?", oid), "id");
    long rid = id((Map<String, Object>) trade.requestReturn(iid, buyer, "退货"), "id");
    trade.reviewReturn(rid, seller, false, "原因不足");
    assertNotNull(trade.requestReturn(iid, buyer, "补充原因"));
    db.update("update return_requests set status='REJECTED' where item_id=?", iid);
    db.update(
        "update orders set confirmed_at=? where id=?",
        LocalDateTime.now().minusHours(24).minusSeconds(1),
        oid);
    assertThrows(BusinessException.class, () -> trade.requestReturn(iid, buyer, "超时"));
    trade.release(iid);
    assertEquals(
        "RELEASED", str(db.one("select * from order_items where id=?", iid), "settlementStatus"));
  }

  @Test
  void blacklistAndClosureDoNotRemoveExistingOrderRights() {
    long oid = orderId(trade.checkout(buyer, request("existing", 0)));
    admin.closure(shop, true);
    db.insert("insert into blacklists(scope,buyer_id,active) values('PLATFORM',?,true)", buyer);
    trade.delivery(oid, seller);
    trade.confirm(oid, buyer);
    long iid = id(db.one("select * from order_items where order_id=?", oid), "id");
    assertNotNull(trade.requestReturn(iid, buyer, "仍可售后"));
  }

  @Test
  void duplicateQuantitiesCannotOverflow() {
    var b =
        checkout(
            "overflow",
            0,
            List.of(
                Map.of("productId", product, "quantity", 2147483647),
                Map.of("productId", product, "quantity", 1)),
            List.of(Map.of("shopId", shop, "appointmentTime", "明天", "appointmentPlace", "校门")));
    assertThrows(BusinessException.class, () -> trade.checkout(buyer, b));
    assertEquals(10, num(db.one("select * from products where id=?", product), "stock"));
  }

  @Test
  void deletedSellerCannotBeReopenedApprovedOrPurchased() {
    db.update("update users set deleted=true where id=?", seller);
    admin.closure(shop, true);
    assertThrows(BusinessException.class, () -> admin.closure(shop, false));
    db.update("update shops set status='OPEN' where id=?", shop);
    db.update("update products set status='ON_SHELF' where id=?", product);
    assertThrows(BusinessException.class, () -> admin.reviewProduct(product, true, ""));
    assertThrows(
        BusinessException.class, () -> trade.checkout(buyer, request("deleted-seller", 0)));
  }

  @Test
  void historicalOrderUsesNameSnapshots() {
    db.update("update users set name='原买家' where id=?", buyer);
    db.update("update users set name='原卖家' where id=?", seller);
    long oid = orderId(trade.checkout(buyer, request("snapshot", 0)));
    db.update("update users set name='更名' where id in (?,?)", buyer, seller);
    db.update("update shops set name='改店名' where id=?", shop);
    var o = trade.order(oid);
    assertEquals("原买家", o.get("buyerName"));
    assertEquals("原卖家", o.get("sellerName"));
    assertEquals("店", o.get("shopName"));
  }

  @Test
  void manualLevelSurvivesConcurrentAutomaticAdjustment() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int attempt = 0; attempt < 10; attempt++) {
        admin.level(shop, Map.of("mode", "AUTO", "level", 1));
        CountDownLatch start = new CountDownLatch(1);
        Future<?> automatic =
            pool.submit(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  admin.recalculate(shop);
                });
        Future<?> manual =
            pool.submit(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  admin.level(shop, Map.of("mode", "MANUAL", "level", 5));
                });
        start.countDown();
        automatic.get(10, TimeUnit.SECONDS);
        manual.get(10, TimeUnit.SECONDS);
        var current = db.one("select * from shops where id=?", shop);
        assertEquals("MANUAL", str(current, "levelMode"));
        assertEquals(5, num(current, "level"));
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
