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
        var count = userMapper.countAdmin();
        if (count > 0) {
            return;
        }
        var account = properties.getAccount();
        var password = properties.getPassword();
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("admin 初始化失败：请配置 app.admin.bootstrap.account 与 app.admin.bootstrap.password");
        }
        var email = properties.getEmail();
        if (email == null || email.isBlank()) {
            email = account;
        }
        var nickname = properties.getNickname() == null || properties.getNickname().isBlank() ? "管理员" : properties.getNickname();
        var passwordHash = passwordEncoder.encode(password);
        userMapper.upsertAdmin(account, email, passwordHash, nickname);
        log.info("admin 初始化完成");
    }
}
