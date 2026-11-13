package campus;

import static campus.BusinessException.require;
import static campus.mapper.Store.*;

import campus.mapper.Store;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

@org.springframework.stereotype.Service
public class FileService {
  final Store db;
  final Security sec;
  final Path directory;

  public FileService(Store d, Security s, @Value("${market.upload-dir}") String dir)
      throws IOException {
    db = d;
    sec = s;
    directory = Path.of(dir).toAbsolutePath().normalize();
    Files.createDirectories(directory);
  }

  Object upload(MultipartFile file, String purpose, HttpServletRequest r) throws Exception {
    require(
        Set.of("PRODUCT", "LICENSE", "IDENTITY").contains(purpose)
            && !file.isEmpty()
            && file.getSize() <= 5 * 1024 * 1024,
        400,
        "仅允许5MB以内图片");
    Object uid = r.getSession().getAttribute("uid");
    if (purpose.equals("PRODUCT")) sec.user(r);
    byte[] bytes = file.getBytes();
    var image = ImageIO.read(new ByteArrayInputStream(bytes));
    require(
        image != null && image.getWidth() <= 10000 && image.getHeight() <= 10000,
        400,
        "图片内容无效或尺寸过大");
    String name = UUID.randomUUID() + ".png";
    ImageIO.write(image, "png", directory.resolve(name).toFile());
    long id =
        db.insert(
            "insert into"
                + " uploaded_files(owner_id,session_key,purpose,storage_name,original_name,content_type,size_bytes)"
                + " values(?,?,?,?,?,'image/png',?)",
            uid,
            r.getSession().getId(),
            purpose,
            name,
            file.getOriginalFilename(),
            Files.size(directory.resolve(name)));
    return Map.of("data", Map.of("id", id, "url", "/api/files/" + id));
  }

  ResponseEntity<byte[]> download(long id, HttpServletRequest r) throws IOException {
    var f = db.one("select * from uploaded_files where id=?", id);
    if (!str(f, "purpose").equals("PRODUCT")) {
      Object uid = r.getSession().getAttribute("uid");
      boolean owner = uid != null && ((Number) uid).longValue() == id(f, "ownerId");
      boolean registrationOwner =
          f.get("ownerId") == null && r.getSession().getId().equals(str(f, "sessionKey"));
      require(
          owner || registrationOwner || uid != null && str(sec.user(r), "role").equals("ADMIN"),
          403,
          "无权读取证件");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-store")
        .body(Files.readAllBytes(directory.resolve(str(f, "storageName"))));
  }
}
