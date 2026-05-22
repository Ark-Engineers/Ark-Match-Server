package io.arknights.dateorfriends.tools.startup;

import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrap(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder, AdminBootstrapProperties properties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.countSuperAdmin() <= 0) {
            var superAccount = properties.getSuperAccount();
            var superPassword = properties.getSuperPassword();
            if (superAccount == null || superAccount.isBlank() || superPassword == null || superPassword.isBlank()) {
                throw new IllegalStateException("super admin 初始化失败：请配置 app.admin.bootstrap.super-account 与 app.admin.bootstrap.super-password（或旧配置 account/password）");
            }
            var superEmail = properties.getSuperEmail();
            if (superEmail == null || superEmail.isBlank()) {
                superEmail = superAccount;
            }
            var superNickname = properties.getSuperNickname() == null || properties.getSuperNickname().isBlank() ? "超级管理员" : properties.getSuperNickname();
            var superPasswordHash = passwordEncoder.encode(superPassword);
            userMapper.upsertSuperAdmin(superAccount, superEmail, superPasswordHash, superNickname);
            log.info("super admin 初始化完成");
        }

        if (userMapper.countAdmin() <= 0) {
            var adminAccount = properties.getAdminAccount();
            var adminPassword = properties.getAdminPassword();
            if (adminAccount == null || adminAccount.isBlank() || adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException("admin 初始化失败：请配置 app.admin.bootstrap.admin-account 与 app.admin.bootstrap.admin-password");
            }
            var adminEmail = properties.getAdminEmail();
            if (adminEmail == null || adminEmail.isBlank()) {
                adminEmail = adminAccount;
            }
            var adminNickname = properties.getAdminNickname() == null || properties.getAdminNickname().isBlank() ? "管理员" : properties.getAdminNickname();
            var adminPasswordHash = passwordEncoder.encode(adminPassword);
            userMapper.upsertAdmin(adminAccount, adminEmail, adminPasswordHash, adminNickname);
            log.info("admin 初始化完成");
        }
    }
}
