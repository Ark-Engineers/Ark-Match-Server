package io.arknights.dateorfriends.tools.startup;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupSuccessLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSuccessLogger.class);
    private final Environment environment;

    public StartupSuccessLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        var profiles = environment.getActiveProfiles();
        var profileText =
                profiles == null || profiles.length == 0
                        ? "default"
                        : Arrays.stream(profiles).collect(Collectors.joining(","));
        var port = environment.getProperty("local.server.port");
        if (port == null || port.isBlank()) {
            port = environment.getProperty("server.port", "8080");
        }
        var ip = getLocalIp();
        log.info("启动环境(Profiles)：{}", profileText);
        log.info("项目启动成功(Project started)：http://{}:{}", ip, port);
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

}
