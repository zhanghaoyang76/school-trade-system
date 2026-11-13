package campus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.sql.init.mode=always",
      "spring.config.import="
    })
@AutoConfigureMockMvc
class MarketTest {
  @Autowired MockMvc mvc;

  @Test
  void anonymousCannotReadWallet() throws Exception {
    mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
  }

  @Test
  void csrfTokenAvailable() throws Exception {
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.token").isNotEmpty());
  }

  @Test
  void mutationsRequireCsrf() throws Exception {
    mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
  }

  @Test
  void catalogueAvailable() throws Exception {
    mvc.perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records").isArray());
  }
}
