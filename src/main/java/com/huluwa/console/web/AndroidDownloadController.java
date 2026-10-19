package com.huluwa.console.web;

import com.huluwa.console.service.AndroidDownloadService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/public/android-downloads")
public class AndroidDownloadController {
    private final AndroidDownloadService downloads;

    public AndroidDownloadController(AndroidDownloadService downloads) { this.downloads = downloads; }

    @GetMapping("/{ticket}")
    public ResponseEntity<Resource> download(@PathVariable String ticket) throws IOException {
        AndroidDownloadService.Download file = downloads.resolve(ticket);
        MediaType type = file.logFile() ? MediaType.TEXT_PLAIN : MediaType.parseMediaType("application/vnd.android.package-archive");
        return ResponseEntity.ok().contentType(type).contentLength(Files.size(file.path()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Accel-Buffering", "no")
                .body(new FileSystemResource(file.path()));
    }
}
