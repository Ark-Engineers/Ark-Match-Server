package io.arknights.dateorfriends.modules.admin.questionnaire.controller;

import io.arknights.dateorfriends.modules.admin.questionnaire.service.QuestionnaireService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/questionnaire")
public class AdminQuestionnaireController {

    private final QuestionnaireService questionnaireService;

    public AdminQuestionnaireController(QuestionnaireService questionnaireService) {
        this.questionnaireService = questionnaireService;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record ListItem(
            long id,
            String title,
            String subtitle,
            String status,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record QuestionItem(
            int seq,
            String question,
            String type,
            String options,
            Integer parentSeq,
            String triggerOption,
            String weight
    ) {
    }

    public record DetailResponse(
            long id,
            String title,
            String subtitle,
            String status,
            List<QuestionItem> questions
    ) {
    }

    public record CreateOrUpdateRequest(
            @Min(1) long id,
            String title,
            String subtitle,
            List<QuestionItem> questions
    ) {
    }

    public record IdRequest(@Min(1) long id) {
    }

    @GetMapping("/list")
    public Mono<ApiResponse<PageResponse<ListItem>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        return questionnaireService.list(page, size)
                .map(p -> new PageResponse<>(
                        p.total(),
                        p.page(),
                        p.size(),
                        p.items().stream().map(i -> new ListItem(
                                i.id(),
                                i.title(),
                                i.subtitle(),
                                i.status(),
                                i.createdBy(),
                                i.updatedBy(),
                                i.createdAt(),
                                i.updatedAt()
                        )).toList()
                ))
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<DetailResponse>> getDetail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        return questionnaireService.getDetail(id)
                .map(d -> new DetailResponse(
                        d.id(),
                        d.title(),
                        d.subtitle(),
                        d.status(),
                        d.questions().stream().map(q -> new QuestionItem(
                                q.seq(),
                                q.question(),
                                q.type(),
                                q.options(),
                                q.parentSeq(),
                                q.triggerOption(),
                                q.weight() == null ? null : q.weight().toPlainString()
                        )).toList()
                ))
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}/preview")
    public Mono<ApiResponse<QuestionnaireService.PreviewResponse>> preview(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        return questionnaireService.getPreview(id).map(ApiResponse::ok);
    }

    @GetMapping("/template")
    public Mono<ResponseEntity<byte[]>> exportTemplate(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);

        var name = "questionnaire-template-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".xlsx";
        return questionnaireService.exportTemplate()
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                        .body(bytes));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<DetailResponse>> importExcel(
            @RequestPart("file") FilePart file,
            @RequestPart("title") String title,
            @RequestPart(value = "subtitle", required = false) String subtitle,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);

        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    try {
                        var bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .flatMap(bytes -> questionnaireService.importExcel(title, subtitle, bytes, principal.userId()))
                .map(d -> new DetailResponse(
                        d.id(),
                        d.title(),
                        d.subtitle(),
                        d.status(),
                        d.questions().stream().map(q -> new QuestionItem(
                                q.seq(),
                                q.question(),
                                q.type(),
                                q.options(),
                                q.parentSeq(),
                                q.triggerOption(),
                                q.weight() == null ? null : q.weight().toPlainString()
                        )).toList()
                ))
                .map(ApiResponse::ok);
    }

    @PostMapping("/update")
    public Mono<ApiResponse<DetailResponse>> update(@Valid @RequestBody CreateOrUpdateRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);

        var list = req.questions() == null ? List.<QuestionnaireService.QuestionItem>of() : req.questions().stream()
                .map(q -> new QuestionnaireService.QuestionItem(
                        q.seq(),
                        q.question(),
                        q.type(),
                        q.options(),
                        q.parentSeq(),
                        q.triggerOption(),
                        parseDecimal(q.weight())
                ))
                .toList();

        return questionnaireService.update(req.id(), req.title(), req.subtitle(), list, principal.userId())
                .map(d -> new DetailResponse(
                        d.id(),
                        d.title(),
                        d.subtitle(),
                        d.status(),
                        d.questions().stream().map(q -> new QuestionItem(
                                q.seq(),
                                q.question(),
                                q.type(),
                                q.options(),
                                q.parentSeq(),
                                q.triggerOption(),
                                q.weight() == null ? null : q.weight().toPlainString()
                        )).toList()
                ))
                .map(ApiResponse::ok);
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<Void>> delete(@Valid @RequestBody IdRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        return questionnaireService.delete(req.id(), principal.userId()).thenReturn(ApiResponse.ok(null));
    }

    private static void assertAdmin(JwtPrincipal principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var role = String.valueOf(principal.role() == null ? "" : principal.role()).trim().toUpperCase();
        if (!Role.ADMIN.name().equals(role) && !Role.SUPER_ADMIN.name().equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static java.math.BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        var s = raw.trim();
        if (s.isBlank()) return null;
        try {
            return new java.math.BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "权重格式不正确");
        }
    }
}
