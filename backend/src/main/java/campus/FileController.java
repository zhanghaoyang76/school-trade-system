package campus;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
  final FileService files;

  public FileController(FileService files) {
    this.files = files;
  }

  @PostMapping
  Object upload(
      @RequestParam MultipartFile file, @RequestParam String purpose, HttpServletRequest request)
      throws Exception {
    return files.upload(file, purpose, request);
  }

  @GetMapping("/{id}")
  ResponseEntity<byte[]> download(@PathVariable long id, HttpServletRequest request)
      throws java.io.IOException {
    return files.download(id, request);
  }
}
