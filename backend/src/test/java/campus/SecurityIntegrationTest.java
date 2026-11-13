package campus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import campus.mapper.Store;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.sql.init.mode=always",
      "spring.config.import="
    })
@AutoConfigureMockMvc
class SecurityIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Store db;

  @Test
  void deletedSessionCannotAccessPrivateApi() throws Exception {
    long id =
        db.insert(
            "insert into users(username,password_hash,role,status,deleted)"
                + " values('deleted','hash','USER','APPROVED',true)");
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("uid", id);
    mvc.perform(get("/api/points").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void privateDocumentCannotBeReadByAnotherUser() throws Exception {
    long id =
        db.insert(
            "insert into uploaded_files(owner_id,purpose,storage_name)"
                + " values(999,'IDENTITY','not-found.png')");
    mvc.perform(get("/api/files/" + id)).andExpect(status().isForbidden());
  }

  @Test
  void pendingUserCannotUseExistingSession() throws Exception {
    long id =
        db.insert(
            "insert into users(username,password_hash,role,status)"
                + " values('pending','hash','MERCHANT','PENDING')");
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("uid", id);
    mvc.perform(get("/api/points").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void ordinaryUserCannotCallAdmin() throws Exception {
    long id =
        db.insert(
            "insert into users(username,password_hash,role,status)"
                + " values('ordinary','hash','USER','APPROVED')");
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("uid", id);
    mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void adminUsersPaginationReturnsDifferentPages() throws Exception {
    long id =
        db.insert(
            "insert into users(username,password_hash,role,status)"
                + " values('paging-admin','hash','ADMIN','APPROVED')");
    db.insert(
        "insert into users(username,password_hash,role,status)"
            + " values('paging-user','hash','USER','APPROVED')");
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("uid", id);
    String first =
        mvc.perform(get("/api/admin/users?page=1&size=1").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mvc.perform(get("/api/admin/users?page=2&size=1").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
  }
}
