package io.arknights.dateorfriends.tools.mail;

import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String KEY_FAIL_STREAK = "mail:fail:streak";

    private final JavaMailSender mailSender;
    private final ReactiveStringRedisTemplate redis;
    private final String from;
    private final String host;
    private final String username;
    private final String password;

    public MailService(
            JavaMailSender mailSender,
            ReactiveStringRedisTemplate redis,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password
    ) {
        this.mailSender = mailSender;
        this.redis = redis;
        this.from = from == null ? "" : from.trim();
        this.host = host == null ? "" : host.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();
    }

    public Mono<Void> sendTextWithRetry(String to, String subject, String content) {
        if (!isConfigured()) {
            return Mono.error(new BusinessException(
                    ErrorCode.EMAIL_SEND_FAILED,
                    "邮件服务未配置，请设置 MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM"
            ));
        }
        return sendAttempt(to, subject, content, 0);
    }

    public Mono<Void> sendTextWithFixedDelayRetry(String to, String subject, String content, int maxAttempts, Duration delay) {
        if (!isConfigured()) {
            return Mono.error(new BusinessException(
                    ErrorCode.EMAIL_SEND_FAILED,
                    "邮件服务未配置，请设置 MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM"
            ));
        }
        var attempts = Math.max(1, maxAttempts);
        var d = delay == null ? Duration.ZERO : delay;
        return sendAttemptFixedDelay(to, subject, content, 1, attempts, d);
    }

    private Mono<Void> sendAttempt(String to, String subject, String content, int attempt) {
        return Mono.fromRunnable(() -> {
                    var msg = new SimpleMailMessage();
                    var effectiveFrom = !from.isBlank() ? from : username;
                    if (!effectiveFrom.isBlank()) msg.setFrom(effectiveFrom);
                    msg.setTo(to);
                    msg.setSubject(subject);
                    msg.setText(content);
                    log.info("mail send attempt={} to={} subject={}", attempt + 1, to, subject);
                    mailSender.send(msg);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(redis.delete(KEY_FAIL_STREAK).then())
                .doOnSuccess(ignored -> log.info("mail send success attempt={} to={} subject={}", attempt + 1, to, subject))
                .onErrorResume(err -> {
                    log.warn("mail send failed attempt={} to={} subject={} reason={}", attempt + 1, to, subject, safeReason(err));
                    var delay = switch (attempt) {
                        case 0 -> Duration.ofSeconds(10);
                        case 1 -> Duration.ofSeconds(30);
                        default -> null;
                    };
                    return bumpFailStreak()
                            .then(Mono.defer(() -> {
                                if (delay == null) {
                                    return Mono.error(new BusinessException(ErrorCode.EMAIL_SEND_FAILED));
                                }
                                return Mono.delay(delay).then(sendAttempt(to, subject, content, attempt + 1));
                            }));
                });
    }

    private Mono<Void> sendAttemptFixedDelay(String to, String subject, String content, int attempt, int maxAttempts, Duration delay) {
        return Mono.fromRunnable(() -> {
                    var msg = new SimpleMailMessage();
                    var effectiveFrom = !from.isBlank() ? from : username;
                    if (!effectiveFrom.isBlank()) msg.setFrom(effectiveFrom);
                    msg.setTo(to);
                    msg.setSubject(subject);
                    msg.setText(content);
                    log.info("mail send attempt={} to={} subject={}", attempt, to, subject);
                    mailSender.send(msg);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(redis.delete(KEY_FAIL_STREAK).then())
                .doOnSuccess(ignored -> log.info("mail send success attempt={} to={} subject={}", attempt, to, subject))
                .onErrorResume(err -> {
                    log.warn("mail send failed attempt={} to={} subject={} reason={}", attempt, to, subject, safeReason(err));
                    return bumpFailStreak()
                            .then(Mono.defer(() -> {
                                if (attempt >= maxAttempts) {
                                    return Mono.error(new BusinessException(ErrorCode.EMAIL_SEND_FAILED));
                                }
                                return Mono.delay(delay).then(sendAttemptFixedDelay(to, subject, content, attempt + 1, maxAttempts, delay));
                            }));
                });
    }

    private boolean isConfigured() {
        if (host.isBlank()) return false;
        if (username.isBlank()) return false;
        if (password.isBlank()) return false;
        return true;
    }

    private Mono<Void> bumpFailStreak() {
        return redis.opsForValue()
                .increment(KEY_FAIL_STREAK)
                .flatMap(v -> {
                    if (v != null && v == 1L) {
                        return redis.expire(KEY_FAIL_STREAK, Duration.ofMinutes(10)).thenReturn(v);
                    }
                    return Mono.just(v);
                })
                .flatMap(v -> {
                    if (v != null && v >= 3) {
                        log.error("mail send failed streak={}", v);
                    }
                    return Mono.empty();
                });
    }

    private String safeReason(Throwable e) {
        if (e == null) return "unknown";
        var msg = e.getMessage();
        if (msg == null) msg = "";
        msg = msg.replace("\r", " ").replace("\n", " ").trim();
        if (msg.length() > 200) msg = msg.substring(0, 200);
        return e.getClass().getSimpleName() + (msg.isBlank() ? "" : (": " + msg));
    }
}
