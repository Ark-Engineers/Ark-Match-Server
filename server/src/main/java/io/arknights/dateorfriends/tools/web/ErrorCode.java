package io.arknights.dateorfriends.tools.web;

public enum ErrorCode {
    PARAM_INVALID(1000, "参数不合法"),
    UNAUTHORIZED(1001, "未登录或令牌无效"),
    FORBIDDEN(1002, "无权限访问"),

    USER_NOT_FOUND(2000, "账号不存在"),
    PASSWORD_INCORRECT(2001, "密码错误"),
    ACCOUNT_LOCKED(2002, "账号已锁定"),
    ACCOUNT_SUSPENDED(2003, "账号已暂停使用"),
    ACCOUNT_BANNED(2004, "账号已封禁"),
    ACCOUNT_ALREADY_EXISTS(2005, "账号已存在"),
    EMAIL_ALREADY_EXISTS(2006, "邮箱已存在"),
    IP_BANNED(2007, "IP已封禁"),
    EMAIL_BANNED(2008, "邮箱已封禁"),
    BATCH_CONFIRM_REQUIRED(2009, "批量操作需要二次确认"),
    BAN_TARGET_WHITELISTED(2010, "封禁目标在白名单中，禁止操作"),
    BAN_PERMISSION_REQUIRED(2011, "无权限执行封禁操作"),
    ALREADY_BANNED(2012, "目标已处于封禁状态"),
    NOTICE_NOT_FOUND(2013, "公告不存在"),
    OP_FAILED(2014, "操作失败"),

    TOKEN_EXPIRED(3000, "令牌已过期"),
    TOKEN_REVOKED(3001, "令牌已失效"),
    REFRESH_TOKEN_INVALID(3002, "刷新令牌无效"),

    INTERNAL_ERROR(9000, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
