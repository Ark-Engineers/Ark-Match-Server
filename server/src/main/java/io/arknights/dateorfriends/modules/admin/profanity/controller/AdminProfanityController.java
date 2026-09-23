    package io.arknights.dateorfriends.modules.admin.profanity.controller;

    import io.arknights.dateorfriends.modules.user.online.mapper.ProfanityWordDO;
    import io.arknights.dateorfriends.modules.user.online.mapper.ProfanityWordMapper;
    import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
    import io.arknights.dateorfriends.tools.security.AuthWebFilter;
    import io.arknights.dateorfriends.tools.web.ApiResponse;
    import io.arknights.dateorfriends.tools.web.BusinessException;
    import io.arknights.dateorfriends.tools.web.ErrorCode;
    import jakarta.validation.constraints.NotBlank;
    import jakarta.validation.constraints.Size;
    import java.util.List;
    import org.springframework.web.bind.annotation.DeleteMapping;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RequestParam;
    import org.springframework.web.bind.annotation.RestController;
    import org.springframework.web.server.ServerWebExchange;
    import reactor.core.publisher.Mono;
    import reactor.core.scheduler.Schedulers;

    @RestController
    @RequestMapping("/admin/profanity")
    public class AdminProfanityController {

        private final ProfanityWordMapper profanityWordMapper;

        public AdminProfanityController(ProfanityWordMapper profanityWordMapper) {
            this.profanityWordMapper = profanityWordMapper;
        }

        public record ProfanityPageResponse(
                int total,
                int page,
                int size,
                List<ProfanityWordDO> items
        ) {
        }

        public record AddWordRequest(
                @NotBlank @Size(max = 64) String word
        ) {
        }

        @GetMapping("/list")
        public Mono<ApiResponse<ProfanityPageResponse>> list(
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "20") int size,
                ServerWebExchange exchange
        ) {
            var principal = requireAdmin(exchange);
            final int p = page < 1 ? 1 : page;
            final int s = (size < 1 || size > 100) ? 20 : size;
            final int offset = (p - 1) * s;
            return Mono.fromCallable(() -> {
                        var total = profanityWordMapper.countAll();
                        var items = profanityWordMapper.selectPage(offset, s);
                        return ApiResponse.ok(new ProfanityPageResponse(total, p, s, items));
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }

        @PostMapping
        public Mono<ApiResponse<Void>> add(
                @RequestBody AddWordRequest req,
                ServerWebExchange exchange
        ) {
            var principal = requireAdmin(exchange);
            var word = req.word().trim();
            if (word.isEmpty()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "屏蔽词不能为空"));
            return Mono.fromCallable(() -> {
                        var existing = profanityWordMapper.selectIdByWord(word);
                        if (existing != null) throw new BusinessException(ErrorCode.OP_FAILED, "屏蔽词已存在");
                        profanityWordMapper.insert(word);
                        return ApiResponse.<Void>ok(null);
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }

        @DeleteMapping("/{id}")
        public Mono<ApiResponse<Void>> delete(
                @PathVariable("id") int id,
                ServerWebExchange exchange
        ) {
            var principal = requireAdmin(exchange);
            return Mono.fromCallable(() -> {
                        var existed = profanityWordMapper.selectById(id);
                        if (existed == null) throw new BusinessException(ErrorCode.OP_FAILED, "屏蔽词不存在");
                        profanityWordMapper.deleteById(id);
                        return ApiResponse.<Void>ok(null);
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }

        private JwtPrincipal requireAdmin(ServerWebExchange exchange) {
            var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
            if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            var role = String.valueOf(principal.role() == null ? "" : principal.role()).toUpperCase();
            if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw new BusinessException(ErrorCode.FORBIDDEN);
            return principal;
        }
    }
