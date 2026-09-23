package io.arknights.dateorfriends.modules.user.questionnaire.service;

import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireMapper;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionMapper;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerDO;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerItemDO;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerItemMapper;
import io.arknights.dateorfriends.modules.user.questionnaire.mapper.UserQuestionnaireAnswerMapper;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserQuestionnaireService {

    private static final String STATUS_READY = "READY";

    private static final String TYPE_SINGLE = "单选";
    private static final String TYPE_MULTI_PREFIX = "多选_";
    private static final String TYPE_FILL = "填空";
    private static final String TYPE_JUDGE = "判断";

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireQuestionMapper questionMapper;
    private final UserQuestionnaireAnswerMapper answerMapper;
    private final UserQuestionnaireAnswerItemMapper answerItemMapper;
    private final TransactionTemplate tx;

    public UserQuestionnaireService(
            QuestionnaireMapper questionnaireMapper,
            QuestionnaireQuestionMapper questionMapper,
            UserQuestionnaireAnswerMapper answerMapper,
            UserQuestionnaireAnswerItemMapper answerItemMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.questionnaireMapper = questionnaireMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.answerItemMapper = answerItemMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record QuestionnaireMeta(long id, String title, String subtitle) {
    }

    public record QuestionItem(
            int seq,
            String question,
            String type,
            String options,
            Integer parentSeq,
            String triggerOption,
            String weight,
            Integer isSuitable,
            Integer isExcluded
    ) {
    }

    public record State(
            boolean hasActiveAnswer,
            Long activeAnswerQuestionnaireId,
            boolean needReSubmit,
            String activeSubmittedAt
    ) {
    }

    public record CurrentResponse(
            QuestionnaireMeta questionnaire,
            List<QuestionItem> questions,
            State state
    ) {
    }

    public record MyActiveItem(int parentSeq, int seq, String answerText) {
    }

    public record MyActiveResponse(long questionnaireId, String submittedAt, List<MyActiveItem> answers) {
    }

    public record SubmitAnswerItem(int parentSeq, int seq, String answerText) {
    }

    public record ReadyItem(long id, String title, String subtitle, String updatedAt) {
    }

    public Mono<CurrentResponse> getCurrent(long userId) {
        return Mono.fromCallable(() -> {
                    var current = questionnaireMapper.selectCurrentReady();
                    var active = answerMapper.selectActiveByUserId(userId);

                    QuestionnaireMeta meta = null;
                    List<QuestionItem> questions = List.of();
                    if (current != null) {
                        meta = new QuestionnaireMeta(current.getId(), current.getTitle(), current.getSubtitle());
                        questions = questionMapper.selectByQuestionnaireId(current.getId()).stream()
                                .map(q -> new QuestionItem(
                                        q.getSeq(),
                                        q.getQuestionText(),
                                        q.getQuestionType(),
                                        q.getOptionsText(),
                                        q.getParentSeq(),
                                        q.getTriggerOption(),
                                        q.getWeight() == null ? null : q.getWeight().toPlainString(),
                                        q.getIsSuitable(),
                                        q.getIsExcluded()
                                ))
                                .toList();
                    }

                    var hasActive = active != null && active.getActiveFlag() != null && active.getActiveFlag() == 1;
                    var activeQuestionnaireId = hasActive ? active.getQuestionnaireId() : null;
                    var needReSubmit = hasActive && current != null && activeQuestionnaireId != null && activeQuestionnaireId != current.getId();
                    var submittedAt = hasActive && active.getSubmittedAt() != null ? active.getSubmittedAt().toString() : null;

                    return new CurrentResponse(meta, questions, new State(hasActive, activeQuestionnaireId, needReSubmit, submittedAt));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<CurrentResponse> getById(long userId, long questionnaireId) {
        return Mono.fromCallable(() -> {
                    var qn = questionnaireMapper.selectReadyById(questionnaireId);
                    if (qn == null) {
                        throw new BusinessException(ErrorCode.OP_FAILED, "问卷不存在或不可填写");
                    }
                    var active = answerMapper.selectActiveByUserId(userId);

                    var meta = new QuestionnaireMeta(qn.getId(), qn.getTitle(), qn.getSubtitle());
                    var questions = questionMapper.selectByQuestionnaireId(qn.getId()).stream()
                            .map(q -> new QuestionItem(
                                    q.getSeq(),
                                    q.getQuestionText(),
                                    q.getQuestionType(),
                                    q.getOptionsText(),
                                    q.getParentSeq(),
                                    q.getTriggerOption(),
                                    q.getWeight() == null ? null : q.getWeight().toPlainString(),
                                    q.getIsSuitable(),
                                    q.getIsExcluded()
                            ))
                            .toList();

                    var hasActive = active != null && active.getActiveFlag() != null && active.getActiveFlag() == 1;
                    var activeQuestionnaireId = hasActive ? active.getQuestionnaireId() : null;
                    var needReSubmit = hasActive && activeQuestionnaireId != null && activeQuestionnaireId != qn.getId();
                    var submittedAt = hasActive && active.getSubmittedAt() != null ? active.getSubmittedAt().toString() : null;
                    return new CurrentResponse(meta, questions, new State(hasActive, activeQuestionnaireId, needReSubmit, submittedAt));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<ReadyItem>> listReady(int page, int size) {
        return Mono.fromCallable(() -> {
                    var safePage = Math.max(1, page);
                    var safeSize = Math.min(100, Math.max(1, size));
                    var offset = (safePage - 1) * safeSize;
                    return questionnaireMapper.selectReadyList(safeSize, offset).stream()
                            .map(q -> new ReadyItem(
                                    q.getId(),
                                    q.getTitle(),
                                    q.getSubtitle(),
                                    q.getUpdatedAt() == null ? null : q.getUpdatedAt().toString()
                            ))
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<MyActiveResponse> getMyActive(long userId) {
        return Mono.fromCallable(() -> {
                    var active = answerMapper.selectActiveByUserId(userId);
                    if (active == null) {
                        throw new BusinessException(ErrorCode.OP_FAILED, "未提交问卷");
                    }
                    var items = answerItemMapper.selectByAnswerId(active.getId()).stream()
                            .map(i -> new MyActiveItem(i.getParentSeq(), i.getQuestionSeq(), i.getAnswerText()))
                            .toList();
                    return new MyActiveResponse(
                            active.getQuestionnaireId(),
                            active.getSubmittedAt() == null ? null : active.getSubmittedAt().toString(),
                            items
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> submit(long userId, long questionnaireId, List<SubmitAnswerItem> answers) {
        return Mono.fromRunnable(() -> tx.executeWithoutResult(status -> doSubmit(userId, questionnaireId, answers)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doSubmit(long userId, long questionnaireId, List<SubmitAnswerItem> answers) {
        var qn = questionnaireMapper.selectReadyById(questionnaireId);
        if (qn == null) {
            throw new BusinessException(ErrorCode.OP_FAILED, "问卷不存在或不可填写");
        }
        if (!STATUS_READY.equalsIgnoreCase(String.valueOf(qn.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷不可提交");
        }

        var questions = questionMapper.selectByQuestionnaireId(questionnaireId);
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷题目为空");
        }

        var questionMap = new HashMap<String, io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionDO>();
        var mainSeqs = new HashSet<Integer>();
        var children = new ArrayList<io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionDO>();
        for (var q : questions) {
            var parentSeq = q.getParentSeq() == null ? 0 : q.getParentSeq();
            var key = key(parentSeq, q.getSeq());
            questionMap.put(key, q);
            if (parentSeq == 0) {
                mainSeqs.add(q.getSeq());
            } else {
                children.add(q);
            }
        }

        var answerMap = new HashMap<String, SubmitAnswerItem>();
        if (answers != null) {
            for (var a : answers) {
                var k = key(a.parentSeq(), a.seq());
                if (answerMap.containsKey(k)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "答案重复");
                }
                answerMap.put(k, a);
            }
        }

        for (var seq : mainSeqs) {
            if (!answerMap.containsKey(key(0, seq))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "存在未填写的题目");
            }
        }

        for (var k : answerMap.keySet()) {
            if (!questionMap.containsKey(k)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "存在无效题目答案");
            }
        }

        var normalized = new ArrayList<UserQuestionnaireAnswerItemDO>();
        var parentSelections = new HashMap<Integer, Set<String>>();

        for (var seq : mainSeqs) {
            var q = questionMap.get(key(0, seq));
            var a = answerMap.get(key(0, seq));
            var normalizedText = normalizeAnswer(q.getQuestionType(), q.getOptionsText(), a.answerText());
            normalized.add(toItem(0, seq, normalizedText));

            var selected = selections(q.getQuestionType(), normalizedText);
            parentSelections.put(seq, selected);
        }

        var expectedChildKeys = new HashSet<String>();
        for (var q : children) {
            int parentSeq = q.getParentSeq() == null ? 0 : q.getParentSeq();
            var parentSelected = parentSelections.get(parentSeq);
            var triggered = parentSelected != null && q.getTriggerOption() != null && parentSelected.contains(q.getTriggerOption());
            var k = key(parentSeq, q.getSeq());
            if (triggered) {
                expectedChildKeys.add(k);
                var a = answerMap.get(k);
                if (a == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "存在未填写的题目");
                }
                var normalizedText = normalizeAnswer(q.getQuestionType(), q.getOptionsText(), a.answerText());
                normalized.add(toItem(parentSeq, q.getSeq(), normalizedText));
            } else {
                if (answerMap.containsKey(k)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "存在无效题目答案");
                }
            }
        }

        for (var k : answerMap.keySet()) {
            if (k.startsWith("0:")) continue;
            if (!expectedChildKeys.contains(k)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "存在无效题目答案");
            }
        }

        answerMapper.discardActiveByUserId(userId);

        var now = LocalDateTime.now();
        var answer = new UserQuestionnaireAnswerDO();
        answer.setUserId(userId);
        answer.setQuestionnaireId(questionnaireId);
        answer.setStatus("ACTIVE");
        answer.setActiveFlag(1);
        answer.setSubmittedAt(now);
        answerMapper.insert(answer);

        for (var i : normalized) {
            i.setAnswerId(answer.getId());
        }
        answerItemMapper.insertBatch(normalized);
    }

    private UserQuestionnaireAnswerItemDO toItem(int parentSeq, int seq, String answerText) {
        var i = new UserQuestionnaireAnswerItemDO();
        i.setParentSeq(parentSeq);
        i.setQuestionSeq(seq);
        i.setAnswerText(answerText);
        return i;
    }

    private Set<String> selections(String typeRaw, String normalizedAnswerText) {
        var type = typeRaw == null ? "" : typeRaw.trim();
        if (type.startsWith(TYPE_MULTI_PREFIX)) {
            var out = new HashSet<String>();
            for (var p : normalizedAnswerText.split("\\|")) {
                var s = p.trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        var out = new HashSet<String>();
        if (normalizedAnswerText != null && !normalizedAnswerText.isBlank()) out.add(normalizedAnswerText);
        return out;
    }

    private String normalizeAnswer(String typeRaw, String optionsRaw, String answerRaw) {
        var type = typeRaw == null ? "" : typeRaw.trim();
        var answer = answerRaw == null ? "" : answerRaw.trim();
        if (answer.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "存在未填写的题目");

        if (TYPE_SINGLE.equals(type)) {
            var options = parseOptions(optionsRaw);
            if (!options.contains(answer)) throw new BusinessException(ErrorCode.PARAM_INVALID, "答案不合法");
            return answer;
        }

        if (TYPE_JUDGE.equals(type)) {
            var v = answer.toLowerCase();
            if (!"true".equals(v) && !"false".equals(v)) throw new BusinessException(ErrorCode.PARAM_INVALID, "答案不合法");
            return v;
        }

        if (TYPE_FILL.equals(type)) {
            return answer;
        }

        if (type.startsWith(TYPE_MULTI_PREFIX)) {
            var max = parseMultiMax(type);
            var options = parseOptions(optionsRaw);
            var parts = answer.split("\\|");
            var picked = new HashSet<String>();
            for (var p : parts) {
                var s = p == null ? "" : p.trim();
                if (s.isBlank()) continue;
                if (!options.contains(s)) throw new BusinessException(ErrorCode.PARAM_INVALID, "答案不合法");
                picked.add(s);
            }
            if (picked.isEmpty()) throw new BusinessException(ErrorCode.PARAM_INVALID, "存在未填写的题目");
            if (picked.size() > max) throw new BusinessException(ErrorCode.PARAM_INVALID, "多选超过最大可选数量");
            var ordered = new ArrayList<String>();
            for (var opt : options) {
                if (picked.contains(opt)) ordered.add(opt);
            }
            return String.join("|", ordered);
        }

        throw new BusinessException(ErrorCode.PARAM_INVALID, "题型不支持");
    }

    private int parseMultiMax(String type) {
        try {
            var parts = type.split("_");
            var v = Integer.parseInt(parts[1]);
            if (v < 2) throw new BusinessException(ErrorCode.PARAM_INVALID, "多选最大可选数量必须>=2");
            return v;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "多选格式必须为 多选_X");
        }
    }

    private List<String> parseOptions(String raw) {
        var v = raw == null ? "" : raw.trim();
        if (v.isBlank()) return List.of();
        var parts = v.split("\\|");
        var out = new ArrayList<String>();
        for (var p : parts) {
            var s = p == null ? "" : p.trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private String key(int parentSeq, int seq) {
        return parentSeq + ":" + seq;
    }
}
