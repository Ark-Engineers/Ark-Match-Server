package io.arknights.dateorfriends.tools.web;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String message;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.message = errorCode.defaultMessage();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
