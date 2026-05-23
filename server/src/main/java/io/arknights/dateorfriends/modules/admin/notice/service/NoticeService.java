package io.arknights.dateorfriends.modules.admin.notice.service;

import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeDO;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeMapper;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeOperationLogDO;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeOperationLogMapper;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeReadMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class NoticeService {
    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final NoticeOperationLogMapper noticeOperationLogMapper;

    public NoticeService(NoticeMapper noticeMapper, NoticeReadMapper noticeReadMapper, NoticeOperationLogMapper noticeOperationLogMapper) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.noticeOperationLogMapper = noticeOperationLogMapper;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public Mono<NoticeDO> create(JwtPrincipal principal, String ip, String title, String content, String level, boolean pinned, LocalDateTime expireAt) {
        var safeTitle = title == null ? "" : title.trim();
        var safeContent = content == null ? "" : content.trim();
        var safeLevel = level == null ? "NORMAL" : level.trim().toUpperCase();
        if (safeTitle.isBlank() || safeTitle.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "标题不能为空且长度需<=128"));
        if (safeContent.isBlank()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "内容不能为空"));
        if (!Objects.equals(safeLevel, "NORMAL") && !Objects.equals(safeLevel, "IMPORTANT")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "level 必须为 NORMAL/IMPORTANT"));
        }

        var now = LocalDateTime.now();
        var notice = new NoticeDO();
        notice.setTitle(safeTitle);
        notice.setContent(safeContent);
        notice.setLevel(safeLevel);
        notice.setStatus("DRAFT");
        notice.setPinned(pinned ? 1 : 0);
        notice.setPublishAt(null);
        notice.setExpireAt(expireAt);
        notice.setCreatedBy(principal.userId());
        notice.setUpdatedBy(principal.userId());
        notice.setDeleted(0);

        return Mono.fromCallable(() -> {
                    noticeMapper.insert(notice);
                    insertLog(principal, ip, notice.getId(), "CREATE", "创建公告");
                    return notice;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> update(JwtPrincipal principal, String ip, long id, String title, String content, String level, boolean pinned, LocalDateTime expireAt) {
        var safeTitle = title == null ? "" : title.trim();
        var safeContent = content == null ? "" : content.trim();
        var safeLevel = level == null ? "NORMAL" : level.trim().toUpperCase();
        if (safeTitle.isBlank() || safeTitle.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "标题不能为空且长度需<=128"));
        if (safeContent.isBlank()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "内容不能为空"));
        if (!Objects.equals(safeLevel, "NORMAL") && !Objects.equals(safeLevel, "IMPORTANT")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "level 必须为 NORMAL/IMPORTANT"));
        }

        return Mono.fromCallable(() -> {
                    var existing = noticeMapper.selectById(id);
                    if (existing == null) throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
                    var updated = noticeMapper.updateContent(id, safeTitle, safeContent, safeLevel, pinned ? 1 : 0, expireAt, principal.userId());
                    if (updated <= 0) throw new BusinessException(ErrorCode.OP_FAILED, "更新失败");
                    insertLog(principal, ip, id, "UPDATE", "更新公告");
                    return noticeMapper.selectById(id);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> publish(JwtPrincipal principal, String ip, long id) {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> {
                    var existing = noticeMapper.selectById(id);
                    if (existing == null) throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
                    var updated = noticeMapper.updateStatus(id, "PUBLISHED", now, principal.userId());
                    if (updated <= 0) throw new BusinessException(ErrorCode.OP_FAILED, "发布失败");
                    insertLog(principal, ip, id, "PUBLISH", "发布公告");
                    return noticeMapper.selectById(id);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> offline(JwtPrincipal principal, String ip, long id) {
        return Mono.fromCallable(() -> {
                    var existing = noticeMapper.selectById(id);
                    if (existing == null) throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
                    var publishAt = existing.getPublishAt();
                    var updated = noticeMapper.updateStatus(id, "OFFLINE", publishAt, principal.userId());
                    if (updated <= 0) throw new BusinessException(ErrorCode.OP_FAILED, "下线失败");
                    insertLog(principal, ip, id, "OFFLINE", "下线公告");
                    return noticeMapper.selectById(id);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(JwtPrincipal principal, String ip, long id) {
        return Mono.fromCallable(() -> {
                    var existing = noticeMapper.selectById(id);
                    if (existing == null) throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
                    var updated = noticeMapper.softDelete(id, principal.userId());
                    if (updated <= 0) throw new BusinessException(ErrorCode.OP_FAILED, "删除失败");
                    insertLog(principal, ip, id, "DELETE", "删除公告");
                    return 1;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<PageResponse<NoticeDO>> listAdmin(String keyword, String status, String level, Integer pinned, LocalDateTime publishFrom, LocalDateTime publishTo, int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeKeyword = keyword == null ? null : keyword.trim();
        var safeStatus = status == null ? null : status.trim().toUpperCase();
        var safeLevel = level == null ? null : level.trim().toUpperCase();

        return Mono.fromCallable(() -> {
                    var total = noticeMapper.countAdminList(safeKeyword, safeStatus, safeLevel, pinned, publishFrom, publishTo);
                    var items = noticeMapper.selectAdminList(safeKeyword, safeStatus, safeLevel, pinned, publishFrom, publishTo, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> getAdmin(long id) {
        return Mono.fromCallable(() -> noticeMapper.selectById(id)).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PageResponse<NoticeDO>> listUser(String keyword, long userId, int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.min(50, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeKeyword = keyword == null ? null : keyword.trim();
        var now = LocalDateTime.now();

        return Mono.fromCallable(() -> {
                    var total = noticeMapper.countUserList(safeKeyword, now);
                    var items = noticeMapper.selectUserList(safeKeyword, now, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record UserNoticeItem(
            long id,
            String title,
            String content,
            String level,
            boolean pinned,
            LocalDateTime publishAt,
            LocalDateTime expireAt,
            boolean read
    ) {
    }

    public Mono<PageResponse<UserNoticeItem>> listUserItems(String keyword, long userId, int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.min(50, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeKeyword = keyword == null ? null : keyword.trim();
        var now = LocalDateTime.now();

        return Mono.fromCallable(() -> {
                    var total = noticeMapper.countUserList(safeKeyword, now);
                    var items = noticeMapper.selectUserList(safeKeyword, now, safeSize, offset);
                    var ids = items.stream().map(NoticeDO::getId).filter(Objects::nonNull).map(Long::longValue).toList();
                    var readIds = ids.isEmpty() ? List.<Long>of() : noticeReadMapper.selectReadNoticeIds(userId, ids);
                    var readSet = new java.util.HashSet<>(readIds);
                    var mapped = items.stream()
                            .map(n -> new UserNoticeItem(
                                    n.getId() == null ? 0 : n.getId(),
                                    n.getTitle(),
                                    n.getContent(),
                                    n.getLevel(),
                                    n.getPinned() != null && n.getPinned() == 1,
                                    n.getPublishAt(),
                                    n.getExpireAt(),
                                    n.getId() != null && readSet.contains(n.getId())
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, mapped);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> getUserDetail(long id) {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> {
                    var notice = noticeMapper.selectUserDetail(id, now);
                    if (notice == null) throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND, "公告不存在或已下线");
                    return notice;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NoticeDO> popupImportant(long userId) {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> noticeMapper.selectPopupImportantNotRead(userId, now))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> markRead(long noticeId, long userId) {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> noticeReadMapper.insertIgnore(noticeId, userId, now))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<PageResponse<NoticeOperationLogDO>> listLogs(Long noticeId, Long actorId, String actionType, LocalDateTime fromTime, LocalDateTime toTime, int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeActionType = actionType == null ? null : actionType.trim().toUpperCase();

        return Mono.fromCallable(() -> {
                    var total = noticeOperationLogMapper.count(noticeId, actorId, safeActionType, fromTime, toTime);
                    var items = noticeOperationLogMapper.selectList(noticeId, actorId, safeActionType, fromTime, toTime, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void insertLog(JwtPrincipal principal, String ip, long noticeId, String actionType, String detail) {
        var log = new NoticeOperationLogDO();
        log.setNoticeId(noticeId);
        log.setActorId(principal == null ? null : principal.userId());
        log.setActorRole(principal == null ? null : principal.role());
        log.setActionType(actionType);
        log.setIp(ip);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        noticeOperationLogMapper.insert(log);
    }
}
