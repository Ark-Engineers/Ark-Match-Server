package io.arknights.dateorfriends.modules.user.match.service;

import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireMapper;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionMapper;
import io.arknights.dateorfriends.modules.user.match.mapper.MatchCandidateMapper;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerItemMapper;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerMapper;
import io.arknights.dateorfriends.modules.user.profile.service.GeoIpService;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserMatchService {

    private static final String TYPE_MULTI_PREFIX = "多选_";
    private static final String TYPE_FILL = "填空";

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireQuestionMapper questionMapper;
    private final UserQuestionnaireAnswerMapper answerMapper;
    private final UserQuestionnaireAnswerItemMapper answerItemMapper;
    private final MatchCandidateMapper candidateMapper;
    private final GeoIpService geoIpService;

    public UserMatchService(
            QuestionnaireMapper questionnaireMapper,
            QuestionnaireQuestionMapper questionMapper,
            UserQuestionnaireAnswerMapper answerMapper,
            UserQuestionnaireAnswerItemMapper answerItemMapper,
            MatchCandidateMapper candidateMapper,
            GeoIpService geoIpService
    ) {
        this.questionnaireMapper = questionnaireMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.answerItemMapper = answerItemMapper;
        this.candidateMapper = candidateMapper;
        this.geoIpService = geoIpService;
    }

    public record RecommendationItem(
            long userId,
            String nickname,
            String avatarUrl,
            String region,
            Integer age,
            List<String> tags,
            double score,
            List<String> highlights
    ) {
    }

    public Mono<List<RecommendationItem>> recommend(long selfUserId, int limit, int offset) {
        return Mono.fromCallable(() -> doRecommend(selfUserId, limit, offset))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<RecommendationItem> doRecommend(long selfUserId, int limit, int offset) {
        var selfAnswer = answerMapper.selectActiveByUserId(selfUserId);
        if (selfAnswer == null || selfAnswer.getQuestionnaireId() == null) {
            throw new BusinessException(ErrorCode.OP_FAILED, "请先填写问卷");
        }
        var questionnaireId = selfAnswer.getQuestionnaireId();
        var qn = questionnaireMapper.selectReadyById(questionnaireId);
        if (qn == null) {
            throw new BusinessException(ErrorCode.OP_FAILED, "问卷不存在或不可匹配");
        }

        var questions = questionMapper.selectByQuestionnaireId(questionnaireId);
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.OP_FAILED, "问卷题目为空");
        }

        var weights = new HashMap<String, Double>();
        var enabledWeightSum = 0.0;
        for (var q : questions) {
            var key = key(q.getParentSeq(), q.getSeq());
            var type = q.getQuestionType() == null ? "" : q.getQuestionType().trim();
            if (TYPE_FILL.equals(type)) continue;
            var w = q.getWeight() == null ? 0.0 : q.getWeight().doubleValue();
            weights.put(key, w);
            enabledWeightSum += w;
        }
        if (enabledWeightSum <= 0) {
            throw new BusinessException(ErrorCode.OP_FAILED, "问卷权重配置不正确");
        }

        var selfItems = answerItemMapper.selectByAnswerId(selfAnswer.getId());
        var selfMap = toAnswerMap(selfItems);

        var candidates = candidateMapper.selectCandidates(selfUserId, Math.min(200, Math.max(1, limit) * 10), 0);
        if (candidates.isEmpty()) return List.of();

        var candidateIds = candidates.stream().map(c -> c.getUserId()).filter(x -> x != null && x > 0).distinct().toList();
        if (candidateIds.isEmpty()) return List.of();

        var candidateAnswers = answerMapper.selectActiveByUserIds(questionnaireId, candidateIds);
        var answerIdByUserId = new HashMap<Long, Long>();
        for (var a : candidateAnswers) {
            if (a.getUserId() == null || a.getId() == null) continue;
            answerIdByUserId.put(a.getUserId(), a.getId());
        }

        var answerIds = answerIdByUserId.values().stream().distinct().toList();
        if (answerIds.isEmpty()) return List.of();

        var allItems = answerItemMapper.selectByAnswerIds(answerIds);
        var itemsByAnswerId = new HashMap<Long, List<io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerItemDO>>();
        for (var i : allItems) {
            if (i.getAnswerId() == null) continue;
            itemsByAnswerId.computeIfAbsent(i.getAnswerId(), k -> new ArrayList<>()).add(i);
        }

        var scored = new ArrayList<Scored>();
        for (var c : candidates) {
            var uid = c.getUserId();
            if (uid == null || uid <= 0) continue;
            var aid = answerIdByUserId.get(uid);
            if (aid == null) continue;

            var map = toAnswerMap(itemsByAnswerId.getOrDefault(aid, List.of()));
            var score = score(weights, enabledWeightSum, selfMap, map);
            scored.add(new Scored(c, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        var start = Math.max(0, offset);
        var end = Math.min(scored.size(), start + Math.max(1, limit));
        var page = scored.subList(start, end);

        var out = new ArrayList<RecommendationItem>();
        for (var s : page) {
            var c = s.candidate;
            var regionIp = pickIp(c.getLastLoginIp(), c.getRegionIp());
            var region = geoIpService.resolveProvinceCityByIp(regionIp).block();
            if (region == null || region.isBlank()) region = "未知";
            out.add(new RecommendationItem(
                    c.getUserId(),
                    c.getNickname(),
                    c.getAvatarUrl(),
                    region,
                    calcAge(c.getBirthday(), c.getBirthdayVisible()),
                    parseTags(c.getTagsJson()),
                    round2(s.score),
                    List.of()
            ));
        }
        return out;
    }

    private record Scored(io.arknights.dateorfriends.modules.user.match.mapper.MatchCandidateDO candidate, double score) {
    }

    private Map<String, String> toAnswerMap(List<io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerItemDO> items) {
        var map = new HashMap<String, String>();
        for (var i : items) {
            var key = key(i.getParentSeq(), i.getQuestionSeq());
            map.put(key, i.getAnswerText() == null ? "" : i.getAnswerText().trim());
        }
        return map;
    }

    private double score(Map<String, Double> weights, double sumWeight, Map<String, String> a, Map<String, String> b) {
        var total = 0.0;
        for (var e : weights.entrySet()) {
            var k = e.getKey();
            var w = e.getValue() == null ? 0.0 : e.getValue();
            if (w <= 0) continue;
            var av = a.getOrDefault(k, "");
            var bv = b.getOrDefault(k, "");
            var sim = similarity(av, bv);
            total += (w / sumWeight) * sim * 100.0;
        }
        return total;
    }

    private double similarity(String av, String bv) {
        var a = av == null ? "" : av.trim();
        var b = bv == null ? "" : bv.trim();
        if (a.isBlank() || b.isBlank()) return 0.0;
        if (!a.contains("|") && !b.contains("|")) {
            return a.equals(b) ? 1.0 : 0.0;
        }
        var as = splitSet(a);
        var bs = splitSet(b);
        if (as.isEmpty() || bs.isEmpty()) return 0.0;
        var inter = 0;
        for (var x : as) {
            if (bs.contains(x)) inter++;
        }
        var uni = as.size() + bs.size() - inter;
        return uni <= 0 ? 0.0 : ((double) inter) / ((double) uni);
    }

    private Set<String> splitSet(String s) {
        var out = new HashSet<String>();
        for (var p : s.split("\\|")) {
            var x = p == null ? "" : p.trim();
            if (!x.isBlank()) out.add(x);
        }
        return out;
    }

    private Integer calcAge(LocalDate birthday, Integer birthdayVisible) {
        var visible = birthdayVisible != null && birthdayVisible == 1;
        if (!visible) return null;
        if (birthday == null) return null;
        var now = LocalDate.now();
        if (birthday.isAfter(now)) return null;
        return Period.between(birthday, now).getYears();
    }

    private String pickIp(String lastLoginIp, String regionIp) {
        var ip = lastLoginIp == null ? "" : lastLoginIp.trim();
        if (!ip.isBlank()) return ip;
        return regionIp;
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

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String key(Integer parentSeq, Integer seq) {
        var p = parentSeq == null ? 0 : parentSeq;
        var s = seq == null ? 0 : seq;
        return p + ":" + s;
    }
}
