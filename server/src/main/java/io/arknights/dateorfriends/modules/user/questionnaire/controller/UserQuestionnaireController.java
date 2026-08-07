package io.arknights.dateorfriends.modules.user.questionnaire.controller;

import io.arknights.dateorfriends.modules.user.questionnaire.service.UserQuestionnaireService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/questionnaire")
public class UserQuestionnaireController {

    private final UserQuestionnaireService questionnaireService;

    public UserQuestionnaireController(UserQuestionnaireService questionnaireService) {
        this.questionnaireService = questionnaireService;
    }

    public record SubmitRequest(
            @Min(1) long questionnaireId,
            List<AnswerItem> answers
    ) {
    }

    public record AnswerItem(int parentSeq, int seq, String answerText) {
    }

    @GetMapping("/current")
    public Mono<ApiResponse<UserQuestionnaireService.CurrentResponse>> current(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return questionnaireService.getCurrent(principal.userId()).map(ApiResponse::ok);
    }

    @GetMapping("/ready-list")
    public Mono<ApiResponse<List<UserQuestionnaireService.ReadyItem>>> readyList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return questionnaireService.listReady(page, size).map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<UserQuestionnaireService.CurrentResponse>> getById(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "参数不合法"));
        return questionnaireService.getById(principal.userId(), id).map(ApiResponse::ok);
    }

    @GetMapping("/my-active")
    public Mono<ApiResponse<UserQuestionnaireService.MyActiveResponse>> myActive(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return questionnaireService.getMyActive(principal.userId()).map(ApiResponse::ok);
    }

    @PostMapping("/submit")
    public Mono<ApiResponse<Void>> submit(@Valid @RequestBody SubmitRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var items = req.answers() == null ? List.<UserQuestionnaireService.SubmitAnswerItem>of() : req.answers().stream()
                .map(a -> new UserQuestionnaireService.SubmitAnswerItem(a.parentSeq(), a.seq(), a.answerText()))
                .toList();
        return questionnaireService.submit(principal.userId(), req.questionnaireId(), items).thenReturn(ApiResponse.ok(null));
    }
}
