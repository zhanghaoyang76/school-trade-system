package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.entity.*;
import campus.mapper.*;
import campus.mapper.Store;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
public class CatalogueService {
  final Store db;
  final CatalogueMapper search;
  final ProductsMapper products;

  public CatalogueService(Store d, CatalogueMapper s, ProductsMapper p) {
    db = d;
    search = s;
    products = p;
  }

  public Map<String, Object> product(long id) {
    var p =
        db.one(
            "select p.*,s.name shop_name,u.name seller_name,s.seller_id from products p join shops"
                + " s on s.id=p.shop_id join users u on u.id=s.seller_id where p.id=?",
            id);
    p.put(
        "imageIds",
        db
            .list("select file_id from product_images where product_id=? order by sort_order", id)
            .stream()
            .map(i -> i.get("fileId"))
            .toList());
    p.put(
        "imageUrls",
        db
            .list("select file_id from product_images where product_id=? order by sort_order", id)
            .stream()
            .map(i -> "/api/files/" + i.get("fileId"))
            .toList());
    p.put(
        "soldCount",
        db.one(
                "select coalesce(sum(oi.quantity),0) metric from order_items oi join orders o on"
                    + " o.id=oi.order_id where oi.product_id=? and o.status='CONFIRMED' and"
                    + " oi.settlement_status!='REFUNDED'",
                id)
            .get("metric"));
    p.put(
        "positiveRate",
        db.one(
                "select avg(case when r.product_stars>=4 then 100.0 else 0 end) metric from reviews"
                    + " r join order_items oi on oi.id=r.item_id where oi.product_id=? and"
                    + " r.direction='BUYER'",
                id)
            .get("metric"));
    return p;
  }

  public Object search(
      String keyword, Long shopId, String sort, Long categoryId, int page, int size) {
    var all = search.search(keyword, shopId, sort, categoryId);
    int start = Math.min(Math.max(0, page - 1) * Math.min(100, Math.max(1, size)), all.size());
    return Map.of(
        "records",
        all.subList(start, Math.min(start + Math.max(1, Math.min(100, size)), all.size())).stream()
            .map(
                m ->
                    product(
                        ((Number) (m.containsKey("id") ? m.get("id") : m.get("ID"))).longValue()))
            .toList(),
        "total",
        all.size());
  }

  public Map<String, Object> shop(long seller) {
    return db.one("select * from shops where seller_id=?", seller);
  }

  public void own(long uid, long product) {
    require(id(product(product), "sellerId") == uid, 403, "不能操作他人商品");
  }

  @Transactional
  public Object save(long uid, Long pid, Map<String, Object> b) {
    db.one("select * from wallets where account_type=\'ESCROW\' for update");
    var shop = shop(uid);
    require(str(shop, "status").equals("OPEN"), 409, "店铺已关闭");
    if (pid != null) own(uid, pid);
    require(
        !str(b, "name").isBlank()
            && money(b, "originalPrice").signum() > 0
            && money(b, "discount").signum() > 0
            && money(b, "discount").compareTo(java.math.BigDecimal.ONE) <= 0
            && num(b, "stock") >= 0,
        400,
        "商品名称、售价和库存不正确");
    require(
        !db.list("select id from categories where id=?", id(b, "categoryId")).isEmpty(),
        400,
        "请选择类别");
    var imgs = (List<?>) b.getOrDefault("imageIds", List.of());
    require(!imgs.isEmpty() && imgs.size() <= 9, 400, "请上传1至9张图片");
    for (var image : imgs)
      require(
          !db.list(
                  "select id from uploaded_files where id=? and owner_id=? and purpose='PRODUCT'",
                  image,
                  uid)
              .isEmpty(),
          403,
          "图片不属于当前用户");
    Products p = new Products();
    p.id = pid;
    p.shopId = id(shop, "id");
    p.categoryId = id(b, "categoryId");
    p.name = str(b, "name");
    p.originalPrice = money(b, "originalPrice");
    p.discount = money(b, "discount");
    p.price =
        money(b, "originalPrice")
            .multiply(money(b, "discount"))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    require(p.price.signum() > 0, 400, "折后售价须至少0.01元");
    p.description = str(b, "description");
    p.size = str(b, "size");
    p.instructions = str(b, "instructions");
    p.negotiable = Boolean.TRUE.equals(b.get("negotiable"));
    p.conditionText = str(b, "conditionText");
    p.stock = num(b, "stock");
    p.status = "PENDING";
    if (pid == null) products.insert(p);
    else products.updateById(p);
    db.update("delete from product_images where product_id=?", p.id);
    int i = 0;
    for (var image : imgs)
      db.insert(
          "insert into product_images(product_id,file_id,sort_order) values(?,?,?)",
          p.id,
          image,
          i++);
    return product(p.id);
  }

  public Object sellerProducts(long uid) {
    var rows =
        db.list(
            "select p.id from products p join shops s on s.id=p.shop_id where s.seller_id=? order"
                + " by p.id desc",
            uid);
    return db.page(rows.stream().map(p -> product(id(p, "id"))).toList());
  }

  public Object cart(long uid) {
    var rows = db.list("select * from cart_items where buyer_id=? order by id", uid);
    rows.forEach(i -> i.put("product", product(id(i, "productId"))));
    return rows;
  }

  public void addCart(long uid, Map<String, Object> b) {
    long pid = id(b, "productId");
    var p = product(pid);
    require(
        id(p, "sellerId") != uid && str(p, "status").equals("ON_SHELF") && num(b, "quantity") > 0,
        409,
        "商品不可加入购物车");
    var old = db.list("select * from cart_items where buyer_id=? and product_id=?", uid, pid);
    if (old.isEmpty())
      db.insert(
          "insert into cart_items(buyer_id,product_id,quantity) values(?,?,?)",
          uid,
          pid,
          num(b, "quantity"));
    else
      db.update(
          "update cart_items set quantity=quantity+? where id=?",
          num(b, "quantity"),
          id(old.getFirst(), "id"));
  }
}
