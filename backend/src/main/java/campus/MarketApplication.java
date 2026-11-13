package campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.*;

@SpringBootApplication
@EnableScheduling
@MapperScan("campus.mapper")
public class MarketApplication {
  public static void main(String[] args) {
    SpringApplication.run(MarketApplication.class, args);
  }
}
