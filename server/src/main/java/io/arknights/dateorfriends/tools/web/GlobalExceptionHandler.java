package io.arknights.dateorfriends.tools.web;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        var status = switch (e.getErrorCode()) {
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_REVOKED, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, IP_BANNED, EMAIL_BANNED, ACCOUNT_BANNED, ACCOUNT_SUSPENDED, ACCOUNT_LOCKED, BAN_TARGET_WHITELISTED, BAN_PERMISSION_REQUIRED -> HttpStatus.FORBIDDEN;
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getErrorCode().code(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler({WebExchangeBindException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception e) {
        var msg = resolveValidationMessage(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ErrorCode.PARAM_INVALID.code(), msg));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtException(JwtException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
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
