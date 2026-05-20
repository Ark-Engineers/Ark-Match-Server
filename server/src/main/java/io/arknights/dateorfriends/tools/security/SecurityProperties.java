package io.arknights.dateorfriends.tools.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @Min(1)
    private int loginFailLockThreshold = 5;

    @Min(1)
    private int loginFailLockMinutes = 15;

    public int getLoginFailLockThreshold() {
        return loginFailLockThreshold;
    }

    public void setLoginFailLockThreshold(int loginFailLockThreshold) {
        this.loginFailLockThreshold = loginFailLockThreshold;
    }

    public int getLoginFailLockMinutes() {
        return loginFailLockMinutes;
    }

    public void setLoginFailLockMinutes(int loginFailLockMinutes) {
        this.loginFailLockMinutes = loginFailLockMinutes;
    }
}

