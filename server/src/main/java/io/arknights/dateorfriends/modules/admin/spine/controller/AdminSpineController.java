package io.arknights.dateorfriends.modules.admin.spine.controller;

import io.arknights.dateorfriends.modules.admin.spine.service.SpineAssetService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/spine")
public class AdminSpineController {
    private final SpineAssetService spineAssetService;

    public AdminSpineController(SpineAssetService spineAssetService) {
        this.spineAssetService = spineAssetService;
    }

    private JwtPrincipal requirePrincipal(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal;
    }

    // displayScale 绑原始 Part 而非 Double：客户端以非 text/plain 内容类型（如 octet-stream）发送时 Spring 找不到解码器会直接 415，这里自行按 UTF-8 读取解析
    private static Mono<Double> readScaleDouble(Part part) {
        if (part == null) return Mono.just(null);
        if (part instanceof FormFieldPart ffp) {
            return Mono.just(parseScale(ffp.value()));
        }
        return DataBufferUtils.join(part.content()).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return parseScale(new String(bytes, StandardCharsets.UTF_8));
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
    }

    private static Double parseScale(String raw) {
        if (raw == null) return null;
        var s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "displayScale 格式错误");
        }
    }

    @GetMapping("/list")
    public Mono<ApiResponse<SpineAssetService.PageResponse<SpineAssetService.AssetItem>>> list(
            @RequestParam(value = "keyword", required = false) @Size(max = 128) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") @Min(1) @Max(200) int size,
            ServerWebExchange exchange
    ) {
        requirePrincipal(exchange);
        return spineAssetService.list(keyword, page, size).map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<SpineAssetService.DetailResponse>> detail(@PathVariable("id") long id, ServerWebExchange exchange) {
        requirePrincipal(exchange);
        return spineAssetService.detail(id).map(ApiResponse::ok);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<SpineAssetService.DetailResponse>> importSpine(
            @RequestPart("atlas") FilePart atlas,
            @RequestPart("skel") FilePart skel,
            @RequestPart("png") List<FilePart> png,
            @RequestPart(value = "extra", required = false) List<FilePart> extra,
            @RequestPart(value = "name", required = false) @Size(max = 128) String name,
            @RequestPart(value = "type", required = false) String type,
            @RequestPart(value = "idleAnimation", required = false) String idleAnimation,
            @RequestPart(value = "moveAnimation", required = false) String moveAnimation,
            @RequestPart(value = "displayScale", required = false) Part displayScale,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        return readScaleDouble(displayScale)
                .flatMap(scale -> spineAssetService.create(principal.userId(), name, type, idleAnimation, moveAnimation, scale, atlas, skel, png, extra).map(ApiResponse::ok));
    }

    @PostMapping(value = "/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<SpineAssetService.DetailResponse>> importZip(
            @RequestPart("zip") FilePart zip,
            @RequestPart(value = "name", required = false) @Size(max = 128) String name,
            @RequestPart(value = "type", required = false) String type,
            @RequestPart(value = "idleAnimation", required = false) String idleAnimation,
            @RequestPart(value = "moveAnimation", required = false) String moveAnimation,
            @RequestPart(value = "displayScale", required = false) Part displayScale,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        return readScaleDouble(displayScale)
                .flatMap(scale -> spineAssetService.createFromZip(principal.userId(), name, type, idleAnimation, moveAnimation, scale, zip).map(ApiResponse::ok));
    }

    @PostMapping(value = "/{id}/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<SpineAssetService.DetailResponse>> update(
            @PathVariable("id") long id,
            @RequestPart(value = "zip", required = false) FilePart zip,
            @RequestPart(value = "atlas", required = false) FilePart atlas,
            @RequestPart(value = "skel", required = false) FilePart skel,
            @RequestPart(value = "png", required = false) List<FilePart> png,
            @RequestPart(value = "extra", required = false) List<FilePart> extra,
            @RequestPart(value = "name", required = false) @Size(max = 128) String name,
            @RequestPart(value = "type", required = false) String type,
            @RequestPart(value = "idleAnimation", required = false) String idleAnimation,
            @RequestPart(value = "moveAnimation", required = false) String moveAnimation,
            @RequestPart(value = "displayScale", required = false) Part displayScale,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        return readScaleDouble(displayScale).flatMap(scale -> {
            if (zip != null) {
                return spineAssetService.updateFromZip(principal.userId(), id, name, type, idleAnimation, moveAnimation, scale, zip);
            }
            return spineAssetService.update(principal.userId(), id, name, type, idleAnimation, moveAnimation, scale, atlas, skel, png, extra);
        }).map(ApiResponse::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable("id") long id, ServerWebExchange exchange) {
        requirePrincipal(exchange);
        return spineAssetService.delete(id).thenReturn(ApiResponse.ok(null));
    }
}
