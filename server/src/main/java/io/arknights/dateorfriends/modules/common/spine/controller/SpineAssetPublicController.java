package io.arknights.dateorfriends.modules.common.spine.controller;

import io.arknights.dateorfriends.tools.storage.SpineStorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/assets/spine")
public class SpineAssetPublicController {
    private final SpineStorageProperties properties;

    public SpineAssetPublicController(SpineStorageProperties properties) {
        this.properties = properties;
    }

    private Path baseDir() {
        var v = properties.getBaseDir();
        if (v == null || v.trim().isBlank()) return Path.of("./data/spine");
        return Path.of(v.trim());
    }

    private static MediaType guessType(String filename) {
        var name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".atlas")) return MediaType.TEXT_PLAIN;
        if (name.endsWith(".skel")) return MediaType.APPLICATION_OCTET_STREAM;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping("/{assetKey}/{fileName}")
    public Mono<ResponseEntity<ByteArrayResource>> get(@PathVariable("assetKey") String assetKey, @PathVariable("fileName") String fileName) {
        return Mono.<ResponseEntity<ByteArrayResource>>fromCallable(() -> {
                    var k = assetKey == null ? "" : assetKey.trim();
                    var f = fileName == null ? "" : fileName.trim();
                    if (k.isBlank() || f.isBlank()) return ResponseEntity.<ByteArrayResource>notFound().build();
                    if (k.contains("..") || k.contains("/") || k.contains("\\")) return ResponseEntity.<ByteArrayResource>notFound().build();
                    if (f.contains("..") || f.contains("/") || f.contains("\\")) return ResponseEntity.<ByteArrayResource>notFound().build();

                    var dir = baseDir().resolve(k).normalize();
                    var target = dir.resolve(f).normalize();
                    if (!target.startsWith(dir)) return ResponseEntity.<ByteArrayResource>notFound().build();
                    if (!Files.exists(target) || !Files.isRegularFile(target)) return ResponseEntity.<ByteArrayResource>notFound().build();

                    var bytes = Files.readAllBytes(target);
                    var type = guessType(f);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                            .contentType(type)
                            .body(new ByteArrayResource(bytes));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
