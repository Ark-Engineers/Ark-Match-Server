package io.arknights.dateorfriends.tools.mail;

import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RegistrationMailService {

    private final MailService mailService;

    public RegistrationMailService(MailService mailService) {
        this.mailService = mailService;
    }

    public Mono<Void> sendRegisterAccountInfo(String toEmail, String account, String nickname) {
        var to = toEmail == null ? "" : toEmail.trim();
        var acc = account == null ? "" : account.trim();
        var name = nickname == null ? "" : nickname.trim();
        if (to.isBlank() || acc.isBlank()) {
            return Mono.empty();
        }
        var subject = "注册成功 - 账号信息";
        var content = "注册成功！\n\n账号：" + acc + "\n昵称：" + name + "\n\n请妥善保管账号信息。";
        return mailService.sendTextWithFixedDelayRetry(to, subject, content, 3, Duration.ofSeconds(60));
    }
}

