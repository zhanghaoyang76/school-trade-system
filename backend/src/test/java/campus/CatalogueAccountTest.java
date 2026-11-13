package campus;

import static campus.mapper.Store.*;
import static org.junit.jupiter.api.Assertions.*;

import campus.mapper.Store;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.mock.web.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:catalogue;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.config.import="
    })
class CatalogueAccountTest {
  @Autowired Store db;
  @Autowired CatalogueService catalogue;
  @Autowired AccountService accounts;
  @Autowired AdminService admin;

  @Test
  void registrationNeedsApprovalAndRejectedApplicantCanResubmit() {
    var r = new MockHttpServletRequest();
    r.getSession().setAttribute("captcha", "1234");
    r.getSession().setAttribute("captchaExpires", System.currentTimeMillis() + 60000);
    Map<String, Object> b =
        new HashMap<>(
            Map.of(
                "username",
                "newbuyer",
                "password",
                "password123",
                "role",
                "USER",
                "name",
                "同学",
                "phone",
                "13800138000",
                "email",
                "student@example.com",
                "city",
                "北京",
                "gender",
                "女",
                "bankAccount",
                "0123456789012345"));
    b.put("captcha", "1234");
    long id = id((Map<String, Object>) accounts.register(b, r), "id");
    r.getSession().setAttribute("captcha", "5678");
    r.getSession().setAttribute("captchaExpires", System.currentTimeMillis() + 60000);
    b.put("captcha", "5678");
    assertThrows(BusinessException.class, () -> accounts.login(b, r));
    admin.reviewUser(id, false, "修改资料");
    r.getSession().setAttribute("captcha", "1234");
    b.put("captcha", "1234");
    assertEquals(id, id((Map<String, Object>) accounts.register(b, r), "id"));
    admin.reviewUser(id, true, "");
    assertThrows(BusinessException.class,()->admin.reviewUser(id,false,"不能撤销已审核账户"));
    r.getSession().setAttribute("captcha", "1234");
    assertNotNull(accounts.login(b, r));
    assertNull(((Map<String, Object>) accounts.login(captcha(r, b), r)).get("passwordHash"));
  }

  Map<String, Object> captcha(MockHttpServletRequest r, Map<String, Object> b) {
    r.getSession().setAttribute("captcha", "1234");
    r.getSession().setAttribute("captchaExpires", System.currentTimeMillis() + 60000);
    return b;
  }

  @Test
  void baseMapperPersistsCalculatedPriceAndXmlFiltersCategory() {
    long seller =
        db.insert(
            "insert into users(username,password_hash,role,status)"
                + " values('publisher','hash','USER','APPROVED')");
    long shop = db.insert("insert into shops(seller_id,name) values(?,'测试店铺')", seller);
    long category = id(db.one("select * from categories order by id limit 1"), "id"),
        file =
            db.insert("insert into uploaded_files(owner_id,purpose) values(?,'PRODUCT')", seller);
    Map<String, Object> b =
        new HashMap<>(
            Map.of(
                "name",
                "教材",
                "categoryId",
                category,
                "originalPrice",
                new BigDecimal("99.99"),
                "discount",
                new BigDecimal("0.8"),
                "price",
                1,
                "stock",
                2,
                "imageIds",
                List.of(file)));
    long pid = id((Map<String, Object>) catalogue.save(seller, null, b), "id");
    assertEquals(0, new BigDecimal("79.99").compareTo(money(catalogue.product(pid), "price")));
    assertEquals(
        List.of(file),
        ((List<?>) catalogue.product(pid).get("imageIds"))
            .stream().map(x -> ((Number) x).longValue()).toList());
    admin.reviewProduct(pid, true, "");
    assertThrows(BusinessException.class,()->admin.reviewProduct(pid,false,"不能撤销已审核商品"));
    assertEquals(
        1,
        ((Number)
                ((Map<?, ?>) catalogue.search("教材", null, "priceAsc", category, 1, 12))
                    .get("total"))
            .intValue());
    assertEquals(
        0,
        ((Number) ((Map<?, ?>) catalogue.search("教材", null, "sales", 999999L, 1, 12)).get("total"))
            .intValue());
  }

  @Test
  void profileRejectsInvalidContactFields() {
    long uid =
        db.insert(
            "insert into users(username,password_hash,role,status,name,phone)"
                + " values('profile','hash','USER','APPROVED','原名','13800138000')");
    assertThrows(BusinessException.class, () -> accounts.profile(uid, Map.of("phone", "bad")));
    assertThrows(BusinessException.class, () -> accounts.profile(uid, Map.of("email", "bad")));
    assertThrows(BusinessException.class, () -> accounts.profile(uid, Map.of("name", "")));
    assertEquals("13800138000", db.one("select * from users where id=?", uid).get("phone"));
  }
}
