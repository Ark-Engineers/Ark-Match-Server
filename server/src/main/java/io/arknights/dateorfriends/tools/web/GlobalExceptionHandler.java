package io.arknights.dateorfriends.tools.web;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import io.arknights.dateorfriends.tools.web.BusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e, ServerWebExchange exchange) {
        log.warn(
                "[{} {}] BusinessException code={} msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                e.getErrorCode().code(),
                e.getMessage());
        var status = switch (e.getErrorCode()) {
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_REVOKED, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, IP_BANNED, EMAIL_BANNED, ACCOUNT_BANNED, ACCOUNT_SUSPENDED, ACCOUNT_LOCKED, BAN_TARGET_WHITELISTED, BAN_PERMISSION_REQUIRED -> HttpStatus.FORBIDDEN;
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getErrorCode().code(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler({WebExchangeBindException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception e, ServerWebExchange exchange) {
        var msg = resolveValidationMessage(e);
        log.warn(
                "[{} {}] ValidationError msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ErrorCode.PARAM_INVALID.code(), msg));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtException(JwtException e, ServerWebExchange exchange) {
        log.warn(
                "[{} {}] JwtException msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, ServerWebExchange exchange) {
        log.error(
                "[{} {}] UnhandledException",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private String resolveValidationMessage(Exception e) {
        if (e instanceof WebExchangeBindException ex) {
            var fe = ex.getFieldError();
            if (fe != null && fe.getDefaultMessage() != null && !fe.getDefaultMessage().isBlank()) {
                return fe.getDefaultMessage();
            }
            var oe = ex.getGlobalError();
            if (oe != null && oe.getDefaultMessage() != null && !oe.getDefaultMessage().isBlank()) {
                return oe.getDefaultMessage();
            }
            return ErrorCode.PARAM_INVALID.defaultMessage();
        }
        if (e instanceof BindException ex) {
            var fe = ex.getFieldError();
            if (fe != null && fe.getDefaultMessage() != null && !fe.getDefaultMessage().isBlank()) {
                return fe.getDefaultMessage();
            }
            var oe = ex.getGlobalError();
            if (oe != null && oe.getDefaultMessage() != null && !oe.getDefaultMessage().isBlank()) {
                return oe.getDefaultMessage();
            }
            return ErrorCode.PARAM_INVALID.defaultMessage();
        }
        return ErrorCode.PARAM_INVALID.defaultMessage();
    }
}
