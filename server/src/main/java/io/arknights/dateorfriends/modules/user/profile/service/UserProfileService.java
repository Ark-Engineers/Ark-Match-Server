package io.arknights.dateorfriends.modules.user.profile.service;

import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.ActionLogMapper;
import io.arknights.dateorfriends.modules.user.profile.controller.UserProfileController.UpdateProfileRequest;
import io.arknights.dateorfriends.modules.user.profile.mapper.UserProfileDO;
import io.arknights.dateorfriends.modules.user.profile.mapper.UserProfileMapper;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final ActionLogMapper actionLogMapper;
    private final UserProfileMapper userProfileMapper;
    private final GeoIpService geoIpService;
    private final ContactAesService contactAesService;

    public UserProfileService(
            UserMapper userMapper,
            ActionLogMapper actionLogMapper,
            UserProfileMapper userProfileMapper,
            GeoIpService geoIpService,
            ContactAesService contactAesService
    ) {
        this.userMapper = userMapper;
        this.actionLogMapper = actionLogMapper;
        this.userProfileMapper = userProfileMapper;
        this.geoIpService = geoIpService;
        this.contactAesService = contactAesService;
    }

    public record ProfileResponse(
            long userId,
            String account,
            String nickname,
            String avatarUrl,
            String avatarCharId,
            String avatarCharName,
            String featuredRole,
            String signature,
            String region,
            Integer age,
            String birthday,
            Boolean birthdayVisible,
            List<String> tags,
            String qq,
            String wechat,
            String email
    ) {
    }

    public Mono<ProfileResponse> getProfile(long viewerUserId, long targetUserId) {
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(targetUserId);
                    if (user == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    }
                    var profile = userProfileMapper.selectByUserId(targetUserId);
                    var recentIps = actionLogMapper.selectDistinctIpsByUserId(targetUserId);
                    var regionIp = profile == null ? null : profile.getRegionIp();
                    var ipForGeo = chooseIpForGeo(regionIp, user.getLastLoginIp(), recentIps);
                    return new Object[]{user, profile, ipForGeo};
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(arr -> {
                    var user = (io.arknights.dateorfriends.modules.user.auth.mapper.UserDO) arr[0];
                    var profile = (UserProfileDO) arr[1];
                    var ipForGeo = (String) arr[2];
                    var isOwner = viewerUserId == targetUserId;
                    return geoIpService.resolveProvinceCityByIp(ipForGeo)
                            .map(region -> toResponse(user, profile, region, isOwner));
                });
    }

    public Mono<ProfileResponse> updateProfile(long userId, String ip, UpdateProfileRequest req) {
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(userId);
                    if (user == null) {
                        throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    }
                    var existed = userProfileMapper.selectByUserId(userId);
                    var profile = existed == null ? new UserProfileDO() : existed;
                    profile.setUserId(userId);
                    if (IpUtils.isPublicIp(ip)) {
                        profile.setRegionIp(ip);
                    }

                    if (req.featuredRole() != null) profile.setFeaturedRole(trimToNull(req.featuredRole()));

                    if (req.signature() != null) profile.setSignature(trimToNull(req.signature()));

                    var birthday = parseDate(req.birthday());
                    if (req.birthday() != null) profile.setBirthday(birthday);

                    if (req.birthdayVisible() != null) profile.setBirthdayVisible(req.birthdayVisible() ? 1 : 0);

                    if (req.tags() != null) {
                        var tags = normalizeTags(req.tags());
                        profile.setTagsJson(toTagsJson(tags));
                    }

                    var hasAvatarUpdate = req.avatarCharId() != null || req.avatarCharName() != null;
                    if (hasAvatarUpdate) {
                        var avatarCharId = trimToNull(req.avatarCharId());
                        if (avatarCharId == null) {
                            userMapper.updateAvatar(userId, null, null, null);
                            user.setAvatarUrl(null);
                            user.setAvatarCharId(null);
                            user.setAvatarCharName(null);
                        } else {
                            if (!avatarCharId.matches("^char_[0-9a-zA-Z_]+$")) {
                                throw new BusinessException(ErrorCode.PARAM_INVALID, "头像角色ID不正确");
                            }
                            var avatarCharName = trimToNull(req.avatarCharName());
                            if (avatarCharName == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "头像角色名称不能为空");
                            if (avatarCharName.length() > 64) throw new BusinessException(ErrorCode.PARAM_INVALID, "头像角色名称过长");
                            var avatarUrl = buildAvatarUrl(avatarCharId);
                            userMapper.updateAvatar(userId, avatarUrl, avatarCharId, avatarCharName);
                            user.setAvatarUrl(avatarUrl);
                            user.setAvatarCharId(avatarCharId);
                            user.setAvatarCharName(avatarCharName);
                        }
                    }

                    var hasContactUpdate = req.qq() != null || req.wechat() != null || req.email() != null;
                    if (hasContactUpdate) {
                        if (req.qq() != null) profile.setQqEnc(encryptContact(req.qq()));
                        if (req.wechat() != null) profile.setWechatEnc(encryptContact(req.wechat()));
                        if (req.email() != null) profile.setEmailEnc(encryptContact(req.email()));
                    }

                    userProfileMapper.upsert(profile);
                    var recentIps = actionLogMapper.selectDistinctIpsByUserId(userId);
                    var ipForGeo = chooseIpForGeo(profile.getRegionIp(), user.getLastLoginIp(), recentIps);
                    var region = geoIpService.resolveProvinceCityByIp(ipForGeo).block();
                    if (region == null || region.isBlank()) region = "未知";
                    return toResponse(user, profile, region, true);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ProfileResponse toResponse(
            io.arknights.dateorfriends.modules.user.auth.mapper.UserDO user,
            UserProfileDO profile,
            String region,
            boolean isOwner
    ) {
        var featuredRole = profile == null ? null : profile.getFeaturedRole();
        var signature = profile == null ? null : profile.getSignature();
        var birthdayVisible = profile != null && profile.getBirthdayVisible() != null && profile.getBirthdayVisible() == 1;
        var birthday = profile == null ? null : profile.getBirthday();
        var tags = parseTags(profile == null ? null : profile.getTagsJson());

        var showBirthday = isOwner || birthdayVisible;
        var showAge = showBirthday;
        var age = showAge ? calcAge(birthday) : null;
        var birthdayText = showBirthday && birthday != null ? birthday.toString() : null;

        var qq = isOwner && profile != null ? contactAesService.decryptFromBase64(profile.getQqEnc()) : null;
        var wechat = isOwner && profile != null ? contactAesService.decryptFromBase64(profile.getWechatEnc()) : null;
        var email = isOwner && profile != null ? contactAesService.decryptFromBase64(profile.getEmailEnc()) : null;

        return new ProfileResponse(
                user.getId(),
                user.getAccount(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getAvatarCharId(),
                user.getAvatarCharName(),
                featuredRole,
                signature,
                region,
                age,
                birthdayText,
                birthdayVisible,
                tags,
                qq,
                wechat,
                email
        );
    }

    private Integer calcAge(LocalDate birthday) {
        if (birthday == null) return null;
        var now = LocalDate.now();
        if (birthday.isAfter(now)) return null;
        return Period.between(birthday, now).getYears();
    }

    private String chooseIpForGeo(String regionIp, String lastLoginIp, List<String> recentIps) {
        if (IpUtils.isPublicIp(lastLoginIp)) return lastLoginIp;
        if (IpUtils.isPublicIp(regionIp)) return regionIp;
        if (recentIps != null) {
            for (var ip : recentIps) {
                if (IpUtils.isPublicIp(ip)) return ip;
            }
        }
        return null;
    }

    private String trimToNull(String v) {
        if (v == null) return null;
        var s = v.trim();
        return s.isBlank() ? null : s;
    }

    private LocalDate parseDate(String v) {
        if (v == null) return null;
        var s = v.trim();
        if (s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "生日格式不正确");
        }
    }

    private List<String> normalizeTags(List<String> tagsRaw) {
        var tags = new ArrayList<String>();
        for (var t : tagsRaw) {
            if (t == null) continue;
            var s = t.trim();
            if (s.isBlank()) continue;
            if (s.length() > 16) throw new BusinessException(ErrorCode.PARAM_INVALID, "Tag长度不能超过16");
            tags.add(s);
        }
        if (tags.size() > 3) throw new BusinessException(ErrorCode.PARAM_INVALID, "Tag最多3个");
        return tags;
    }

    private String toTagsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        var sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escapeJson(tags.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildAvatarUrl(String id) {
        return "https://web.hycdn.cn/arknights/game/assets/char/avatar/" + id + ".png";
    }

    private List<String> parseTags(String json) {
        var raw = json == null ? "" : json.trim();
        if (raw.isBlank() || "[]".equals(raw)) return List.of();
        if (!raw.startsWith("[") || !raw.endsWith("]")) return List.of();
        var inner = raw.substring(1, raw.length() - 1).trim();
        if (inner.isBlank()) return List.of();
        var out = new ArrayList<String>();
        var parts = inner.split(",");
        for (var p : parts) {
            var s = p.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            s = s.replace("\\\"", "\"").replace("\\\\", "\\");
            if (!s.isBlank()) out.add(s);
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String encryptContact(String v) {
        var raw = v == null ? null : v.trim();
        if (raw == null || raw.isBlank()) return null;
        return contactAesService.encryptToBase64(raw);
    }
}
