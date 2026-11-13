package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
public class AccountService {
  final Store db;
  final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public AccountService(Store d) {
    db = d;
  }

  public void captcha(HttpServletRequest r, String code) {
    Object value = r.getSession().getAttribute("captcha");
    Object expires = r.getSession().getAttribute("captchaExpires");
    r.getSession().removeAttribute("captcha");
    require(
        value != null
            && value.toString().equalsIgnoreCase(code)
            && expires != null
            && (long) expires > System.currentTimeMillis(),
        400,
        "验证码错误或已过期");
  }

  @Transactional
  public Object register(Map<String, Object> b, HttpServletRequest r) {
    captcha(r, str(b, "captcha"));
    require(
        str(b, "username").matches("[A-Za-z0-9_]{4,32}") && str(b, "password").length() >= 8,
        400,
        "账号须4至32位字母数字下划线，密码至少8位");
    require(
        str(b, "bankAccount").matches("[0-9]{16}")
            && str(b, "phone").matches("1[0-9]{10}")
            && str(b, "email").matches("[^@ ]+@[^@ ]+\\.[^@ ]+"),
        400,
        "请检查银行账号、手机号和邮箱");
    require(
        !str(b, "name").isBlank() && !str(b, "city").isBlank() && !str(b, "gender").isBlank(),
        400,
        "请填写姓名、城市和性别");
    String role = str(b, "role");
    require(Set.of("USER", "MERCHANT").contains(role), 400, "用户类型错误");
    if (role.equals("MERCHANT")) {
      ownedFile(id(b, "licenseFileId"), "LICENSE", r);
      ownedFile(id(b, "identityFileId"), "IDENTITY", r);
    }
    var existing = db.list("select * from users where username=?", str(b, "username"));
    long userId;
    if (!existing.isEmpty()) {
      var u = existing.getFirst();
      require(
          str(u, "status").equals("REJECTED")
              && encoder.matches(str(b, "password"), str(u, "passwordHash")),
          409,
          "账号已存在");
      userId = id(u, "id");
      db.update(
          "update users set"
              + " role=?,status='PENDING',name=?,phone=?,email=?,city=?,gender=?,bank_account=?,license_file_id=?,identity_file_id=?,review_reason=null"
              + " where id=?",
          role,
          b.get("name"),
          b.get("phone"),
          b.get("email"),
          b.get("city"),
          b.get("gender"),
          b.get("bankAccount"),
          b.get("licenseFileId"),
          b.get("identityFileId"),
          userId);
    } else {
      userId =
          db.insert(
              "insert into"
                  + " users(username,password_hash,role,status,name,phone,email,city,gender,bank_account,license_file_id,identity_file_id)"
                  + " values(?,?,?,'PENDING',?,?,?,?,?,?,?,?)",
              b.get("username"),
              encoder.encode(str(b, "password")),
              role,
              b.get("name"),
              b.get("phone"),
              b.get("email"),
              b.get("city"),
              b.get("gender"),
              b.get("bankAccount"),
              b.get("licenseFileId"),
              b.get("identityFileId"));
      db.insert(
          "insert into shops(seller_id,name) values(?,?)",
          userId,
          str(b, "shopName").isBlank() ? str(b, "name") + "的店铺" : str(b, "shopName"));
      db.insert("insert into wallets(user_id,account_type) values(?,'USER')", userId);
    }
    db.update(
        "update uploaded_files set owner_id=? where session_key=? and owner_id is null",
        userId,
        r.getSession().getId());
    return Map.of("id", userId, "status", "PENDING");
  }

  void ownedFile(long id, String purpose, HttpServletRequest r) {
    require(
        !db.list(
                "select id from uploaded_files where id=? and purpose=? and session_key=? and"
                    + " owner_id is null",
                id,
                purpose,
                r.getSession().getId())
            .isEmpty(),
        400,
        "请上传本人证件");
  }

  public Object login(Map<String, Object> b, HttpServletRequest r) {
    captcha(r, str(b, "captcha"));
    var rows = db.list("select * from users where username=?", b.get("username"));
    require(
        !rows.isEmpty()
            && encoder.matches(str(b, "password"), str(rows.getFirst(), "passwordHash")),
        401,
        "账号或密码错误");
    var u = rows.getFirst();
    require(!Boolean.TRUE.equals(u.get("deleted")), 403, "账号已停用");
    require(
        str(u, "status").equals("APPROVED"),
        403,
        "注册" + str(u, "status") + "：" + str(u, "reviewReason"));
    r.changeSessionId();
    r.getSession().setAttribute("uid", id(u, "id"));
    return new Security(db).safe(u);
  }

  @Transactional
  public Object profile(long uid, Map<String, Object> b) {
    require(
        !b.containsKey("bankAccount") || str(b, "bankAccount").matches("[0-9]{16}"),
        400,
        "银行账号须16位数字");
    for (String field : List.of("name", "city", "gender"))
      require(!b.containsKey(field) || !str(b, field).isBlank(), 400, "姓名城市性别不能为空");
    require(!b.containsKey("phone") || str(b, "phone").matches("1[0-9]{10}"), 400, "手机号格式错误");
    require(
        !b.containsKey("email") || str(b, "email").matches("[^@ ]+@[^@ ]+\\.[^@ ]+"),
        400,
        "邮箱格式错误");
    for (String f : List.of("name", "phone", "email", "city", "gender", "bankAccount")) {
      if (b.containsKey(f))
        db.update(
            "update users set " + (f.equals("bankAccount") ? "bank_account" : f) + "=? where id=?",
            b.get(f),
            uid);
    }
    return new Security(db).safe(db.one("select * from users where id=?", uid));
  }
}
