package io.arknights.dateorfriends.modules.admin.questionnaire.service;

import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireDO;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireMapper;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionDO;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireQuestionMapper;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class QuestionnaireService {

    private static final String TYPE_SINGLE = "单选";
    private static final String TYPE_MULTI_PREFIX = "多选_";
    private static final String TYPE_FILL = "填空";
    private static final String TYPE_JUDGE = "判断";

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireQuestionMapper questionMapper;

    public QuestionnaireService(QuestionnaireMapper questionnaireMapper, QuestionnaireQuestionMapper questionMapper) {
        this.questionnaireMapper = questionnaireMapper;
        this.questionMapper = questionMapper;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record QuestionnaireItem(
            long id,
            String title,
            String subtitle,
            String status,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record QuestionItem(
            int seq,
            String question,
            String type,
            String options,
            Integer parentSeq,
            String triggerOption,
            BigDecimal weight
    ) {
    }

    public record QuestionnaireDetail(
            long id,
            String title,
            String subtitle,
            String status,
            List<QuestionItem> questions
    ) {
    }

    public record PreviewQuestion(
            int seq,
            String question,
            String type,
            Integer multiMax,
            List<String> options,
            Integer parentSeq,
            String triggerOption,
            BigDecimal weight
    ) {
    }

    public record PreviewResponse(
            long id,
            String title,
            String subtitle,
            List<PreviewQuestion> questions
    ) {
    }

    private record ParsedRow(
            int seq,
            int excelRow,
            String question,
            String type,
            String options,
            Integer parentSeq,
            String triggerOption,
            BigDecimal weight
    ) {
    }

    public Mono<PageResponse<QuestionnaireItem>> list(int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        return Mono.fromCallable(() -> {
                    var total = questionnaireMapper.countAll();
                    var list = questionnaireMapper.selectList(safeSize, offset);
                    var items = list.stream().map(this::toItem).toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<QuestionnaireDetail> getDetail(long id) {
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        return Mono.fromCallable(() -> {
                    var q = questionnaireMapper.selectById(id);
                    if (q == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷不存在");
                    var qs = questionMapper.selectByQuestionnaireId(id);
                    return new QuestionnaireDetail(
                            q.getId(),
                            q.getTitle(),
                            q.getSubtitle(),
                            q.getStatus(),
                            qs.stream().map(this::toQuestionItem).toList()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PreviewResponse> getPreview(long id) {
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        return Mono.fromCallable(() -> {
                    var q = questionnaireMapper.selectById(id);
                    if (q == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷不存在");
                    var qs = questionMapper.selectByQuestionnaireId(id);
                    var out = new ArrayList<PreviewQuestion>();
                    for (var item : qs) {
                        Integer parentSeq = item.getParentSeq();
                        if (parentSeq != null && parentSeq <= 0) parentSeq = null;
                        var parsed = parseType(item.getQuestionType());
                        out.add(new PreviewQuestion(
                                item.getSeq(),
                                item.getQuestionText(),
                                parsed.type(),
                                parsed.multiMax(),
                                splitOptions(item.getOptionsText()),
                                parentSeq,
                                item.getTriggerOption(),
                                item.getWeight()
                        ));
                    }
                    return new PreviewResponse(q.getId(), q.getTitle(), q.getSubtitle(), out);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<QuestionnaireDetail> importExcel(String titleRaw, String subtitleRaw, byte[] bytes, long actorId) {
        return Mono.fromCallable(() -> {
                    var title = normalize(titleRaw);
                    if (title == null || title.length() < 2) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷标题至少2个字符");
                    if (title.length() > 128) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷标题过长");
                    var subtitle = normalize(subtitleRaw);
                    if (subtitle != null && subtitle.length() > 255) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷副标题过长");

                    var parsedRows = parseExcel(bytes);
                    var validated = validateImport(parsedRows);

                    var q = new QuestionnaireDO();
                    q.setTitle(title);
                    q.setSubtitle(subtitle);
                    q.setStatus("READY");
                    q.setCreatedBy(actorId);
                    q.setUpdatedBy(actorId);
                    q.setDeleted(0);
                    questionnaireMapper.insert(q);

                    var items = new ArrayList<QuestionnaireQuestionDO>();
                    for (var qi : validated) {
                        var row = new QuestionnaireQuestionDO();
                        row.setQuestionnaireId(q.getId());
                        row.setSeq(qi.seq());
                        row.setQuestionText(qi.question());
                        row.setQuestionType(qi.type());
                        row.setOptionsText(normalizeOptionsForStorage(qi.type(), qi.options(), qi.seq()));
                        row.setParentSeq(qi.parentSeq() == null ? 0 : qi.parentSeq());
                        row.setTriggerOption(normalize(qi.triggerOption()));
                        row.setWeight(qi.weight());
                        items.add(row);
                    }
                    if (!items.isEmpty()) {
                        try {
                            questionMapper.insertBatch(items);
                        } catch (Exception e) {
                            throw resolveQuestionInsertException(e);
                        }
                    }

                    return new QuestionnaireDetail(q.getId(), q.getTitle(), q.getSubtitle(), q.getStatus(), validated);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<QuestionnaireDetail> update(long id, String titleRaw, String subtitleRaw, List<QuestionItem> questions, long actorId) {
        return Mono.fromCallable(() -> {
                    if (id <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID);
                    var existed = questionnaireMapper.selectById(id);
                    if (existed == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷不存在");

                    var title = normalize(titleRaw);
                    if (title == null || title.length() < 2) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷标题至少2个字符");
                    if (title.length() > 128) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷标题过长");
                    var subtitle = normalize(subtitleRaw);
                    if (subtitle != null && subtitle.length() > 255) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷副标题过长");

                    var list = questions == null ? List.<QuestionItem>of() : questions;
                    validateUpdate(list);

                    questionnaireMapper.updateMeta(id, title, subtitle, "READY", actorId);
                    questionMapper.deleteByQuestionnaireId(id);

                    var items = new ArrayList<QuestionnaireQuestionDO>();
                    for (var qi : list) {
                        var row = new QuestionnaireQuestionDO();
                        row.setQuestionnaireId(id);
                        row.setSeq(qi.seq());
                        row.setQuestionText(qi.question());
                        row.setQuestionType(qi.type());
                        row.setOptionsText(normalizeOptionsForStorage(qi.type(), qi.options(), qi.seq()));
                        row.setParentSeq(qi.parentSeq() == null ? 0 : qi.parentSeq());
                        row.setTriggerOption(normalize(qi.triggerOption()));
                        row.setWeight(qi.weight());
                        items.add(row);
                    }
                    if (!items.isEmpty()) {
                        try {
                            questionMapper.insertBatch(items);
                        } catch (Exception e) {
                            throw resolveQuestionInsertException(e);
                        }
                    }

                    return new QuestionnaireDetail(id, title, subtitle, "READY", list);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private BusinessException resolveQuestionInsertException(Exception e) {
        var msg = e == null ? "" : String.valueOf(e.getMessage());
        var causeMsg = e != null && e.getCause() != null ? String.valueOf(e.getCause().getMessage()) : "";
        var combined = (msg + "\n" + causeMsg).toLowerCase();
        if (combined.contains("questionnaire_question") && combined.contains("duplicate")
                && (combined.contains("uk_questionnaire_seq") || combined.contains("questionnaire_id") && combined.contains("seq"))) {
            return new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "问卷问题表索引不兼容：当前已支持“子问题序号从1开始”，需要将唯一索引从 (questionnaire_id, seq) 调整为 (questionnaire_id, parent_seq, seq)，并将 parent_seq 设为 NOT NULL DEFAULT 0。请执行最新 questionnaire.sql。"
            );
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    public Mono<Void> delete(long id, long actorId) {
        return Mono.fromCallable(() -> {
                    if (id <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID);
                    var existed = questionnaireMapper.selectById(id);
                    if (existed == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "问卷不存在");
                    questionnaireMapper.softDelete(id, actorId);
                    questionMapper.deleteByQuestionnaireId(id);
                    return 0;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<byte[]> exportTemplate() {
        return Mono.fromCallable(() -> {
                    try (var wb = new XSSFWorkbook()) {
                        var sheet = wb.createSheet("问题");
                        var header = sheet.createRow(0);
                        var titles = List.of(
                                "序号",
                                "问题*",
                                "题型*",
                                "选项答案",
                                "主问题序号",
                                "触发子问题选项",
                                "权重*"
                        );
                        for (int i = 0; i < titles.size(); i++) {
                            header.createCell(i, CellType.STRING).setCellValue(titles.get(i));
                            sheet.setColumnWidth(i, 20 * 256);
                        }
                        addHeaderComments(wb, sheet, header);

                        try (var out = new ByteArrayOutputStream()) {
                            wb.write(out);
                            return out.toByteArray();
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private QuestionnaireItem toItem(QuestionnaireDO q) {
        return new QuestionnaireItem(
                q.getId(),
                q.getTitle(),
                q.getSubtitle(),
                q.getStatus(),
                q.getCreatedBy(),
                q.getUpdatedBy(),
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }

    private QuestionItem toQuestionItem(QuestionnaireQuestionDO q) {
        Integer parentSeq = q.getParentSeq();
        if (parentSeq != null && parentSeq <= 0) parentSeq = null;
        return new QuestionItem(
                q.getSeq() == null ? 0 : q.getSeq(),
                q.getQuestionText(),
                q.getQuestionType(),
                q.getOptionsText(),
                parentSeq,
                q.getTriggerOption(),
                q.getWeight()
        );
    }

    private void addHeaderComments(Workbook wb, Sheet sheet, Row header) {
        CreationHelper factory = wb.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        addComment(factory, drawing, header.getCell(0), "系统自动生成的连续正整数；只读，不可编辑；主问题从1开始递增；同一主问题下子问题从1开始递增");
        addComment(factory, drawing, header.getCell(1), "必填；最少2个字符");
        addComment(factory, drawing, header.getCell(2), "固定可选：单选 / 多选_X / 填空 / 判断；多选_X 中 X 为>=2的正整数");
        addComment(factory, drawing, header.getCell(3), "仅单选/多选可填；用英文半角 | 分隔；禁止空选项或连续 ||");
        addComment(factory, drawing, header.getCell(4), "仅子问题填写：绑定的主问题序号；必须为已存在的主问题序号；禁止绑定自身或子问题");
        addComment(factory, drawing, header.getCell(5), "仅子问题填写：从主问题选项中选择一个值；填写问卷时选择该选项才展示子问题");
        addComment(factory, drawing, header.getCell(6), "必填；最多2位小数；所有问题权重总和必须=100（误差>0.01视为不合法）");
    }

    private void addComment(CreationHelper factory, Drawing<?> drawing, Cell cell, String text) {
        if (cell == null) return;
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 4);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(factory.createRichTextString(text));
        cell.setCellComment(comment);
    }

    private List<ParsedRow> parseExcel(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "文件为空");
        try (var in = new ByteArrayInputStream(bytes); var wb = WorkbookFactory.create(in)) {
            var sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "模板缺少Sheet");
            var out = new ArrayList<ParsedRow>();
            int mainSeq = 0;
            var childSeq = new HashMap<Integer, Integer>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                if (row == null) continue;
                var question = readString(row.getCell(1));
                var type = readString(row.getCell(2));
                var options = readString(row.getCell(3));
                var parentSeq = readInteger(row.getCell(4));
                var trigger = readString(row.getCell(5));
                var weight = readDecimal(row.getCell(6));

                var blank = (question == null || question.isBlank())
                        && (type == null || type.isBlank())
                        && (options == null || options.isBlank())
                        && parentSeq == null
                        && (trigger == null || trigger.isBlank())
                        && weight == null;
                if (blank) continue;

                int seq;
                if (parentSeq == null) {
                    mainSeq++;
                    seq = mainSeq;
                } else {
                    var next = childSeq.getOrDefault(parentSeq, 0) + 1;
                    childSeq.put(parentSeq, next);
                    seq = next;
                }
                out.add(new ParsedRow(seq, r + 1, question, type, options, parentSeq, trigger, weight));
            }
            if (out.isEmpty()) throw new BusinessException(ErrorCode.PARAM_INVALID, "模板未填写任何题目");
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "解析Excel失败");
        }
    }

    private String readString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return normalize(cell.getStringCellValue());
        if (cell.getCellType() == CellType.NUMERIC) {
            var v = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            return normalize(v);
        }
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        return normalize(cell.toString());
    }

    private Integer readInteger(Cell cell) {
        var s = readString(cell);
        if (s == null) return null;
        try {
            var n = Integer.parseInt(s.trim());
            return n > 0 ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal readDecimal(Cell cell) {
        var s = readString(cell);
        if (s == null) return null;
        try {
            return new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private List<QuestionItem> validateImport(List<ParsedRow> rows) {
        if (rows == null || rows.isEmpty()) throw new BusinessException(ErrorCode.PARAM_INVALID, "题目不能为空");
        var errors = new ArrayList<String>();

        var mainRows = new ArrayList<ParsedRow>();
        for (var r : rows) {
            if (r == null) {
                errors.add("存在空行数据");
                continue;
            }
            if (r.parentSeq() == null) mainRows.add(r);
        }
        if (mainRows.isEmpty()) errors.add("至少需要1个主问题");

        var mainSeqSet = new HashSet<Integer>();
        for (var r : mainRows) mainSeqSet.add(r.seq());
        if (mainSeqSet.size() != mainRows.size()) errors.add("主问题序号不能重复");
        for (int s = 1; s <= mainRows.size(); s++) {
            if (!mainSeqSet.contains(s)) errors.add("主问题序号必须从1开始连续递增");
        }

        var mainMap = new HashMap<Integer, ParsedRow>();
        for (var r : mainRows) mainMap.put(r.seq(), r);

        var childrenByParent = new HashMap<Integer, List<ParsedRow>>();
        for (var r : rows) {
            if (r == null || r.parentSeq() == null) continue;
            var p = r.parentSeq();
            if (p <= 0) {
                errors.add("第" + r.excelRow() + "行：主问题序号不合法");
                continue;
            }
            if (!mainMap.containsKey(p)) {
                errors.add("第" + r.excelRow() + "行：主问题序号不存在");
                continue;
            }
            childrenByParent.computeIfAbsent(p, k -> new ArrayList<>()).add(r);
        }
        for (var e : childrenByParent.entrySet()) {
            var list = e.getValue();
            var childSeqSet = new HashSet<Integer>();
            for (var r : list) childSeqSet.add(r.seq());
            if (childSeqSet.size() != list.size()) errors.add("主问题" + e.getKey() + "：子问题序号不能重复");
            for (int s = 1; s <= list.size(); s++) {
                if (!childSeqSet.contains(s)) errors.add("主问题" + e.getKey() + "：子问题序号必须从1开始连续递增");
            }
        }

        var allWeightsOk = true;
        for (var i : rows) {
            if (i == null) continue;
            var rowNo = i.excelRow();
            var q = normalize(i.question());
            if (q == null || q.length() < 2) errors.add("第" + rowNo + "行：问题至少2个字符");
            if (q != null && q.length() > 255) errors.add("第" + rowNo + "行：问题过长");

            var t = normalize(i.type());
            ParsedType parsed = null;
            if (t == null) {
                errors.add("第" + rowNo + "行：题型必填");
            } else {
                try {
                    parsed = parseType(t);
                    if (parsed.multiMax() != null && parsed.multiMax() < 2) {
                        errors.add("第" + rowNo + "行：多选最大可选数量必须>=2");
                    }
                } catch (BusinessException e) {
                    errors.add("第" + rowNo + "行：" + e.getMessage());
                }
            }

            var parent = i.parentSeq();
            if (parent != null) {
                if (parent <= 0) errors.add("第" + rowNo + "行：主问题序号不合法");
                if (!mainMap.containsKey(parent)) errors.add("第" + rowNo + "行：主问题序号不存在");
            }

            var w = i.weight();
            if (w == null) {
                errors.add("第" + rowNo + "行：权重必填");
                allWeightsOk = false;
            } else if (w.scale() > 2) {
                errors.add("第" + rowNo + "行：权重最多2位小数");
                allWeightsOk = false;
            }

            var isChoice = parsed != null && (TYPE_SINGLE.equals(parsed.type()) || parsed.multiMax() != null);
            if (isChoice) {
                var parts = validateOptionsMaybe(i.options(), "第" + rowNo + "行：", i.excelRow(), errors);
                if (parts != null) {
                    if (parts.size() < 2) errors.add("第" + rowNo + "行：选项至少2个");
                    if (parsed != null && parsed.multiMax() != null && parsed.multiMax() > parts.size()) {
                        errors.add("第" + rowNo + "行：多选最大可选数量不能超过选项数量");
                    }
                }
            } else {
                var opt = normalize(i.options());
                if (opt != null) errors.add("第" + rowNo + "行：填空/判断题型选项答案必须为空");
            }
        }

        for (var i : rows) {
            if (i == null) continue;
            if (i.parentSeq() == null) {
                if (normalize(i.triggerOption()) != null) errors.add("第" + i.excelRow() + "行：未绑定子问题时触发选项必须为空");
                continue;
            }
            var parent = mainMap.get(i.parentSeq());
            if (parent == null) continue;
            try {
                var parentParsed = parseType(normalize(parent.type()));
                if (!(TYPE_SINGLE.equals(parentParsed.type()) || parentParsed.multiMax() != null)) {
                    errors.add("第" + i.excelRow() + "行：主问题必须为单选或多选");
                    continue;
                }
            } catch (BusinessException e) {
                continue;
            }
            var trigger = normalize(i.triggerOption());
            if (trigger == null) {
                errors.add("第" + i.excelRow() + "行：触发子问题选项必填");
                continue;
            }
            var parentOptions = validateOptionsMaybe(parent.options(), "第" + parent.excelRow() + "行：", parent.excelRow(), errors);
            if (parentOptions != null && !parentOptions.contains(trigger)) {
                errors.add("第" + i.excelRow() + "行：触发子问题选项必须来自主问题选项");
            }
        }

        if (allWeightsOk) {
            var sum = BigDecimal.ZERO;
            for (var i : rows) sum = sum.add(i.weight());
            sum = sum.setScale(2, RoundingMode.HALF_UP);
            var diff = sum.subtract(new BigDecimal("100.00")).abs();
            if (diff.compareTo(new BigDecimal("0.01")) > 0) {
                errors.add("权重总和必须等于100（当前=" + sum.toPlainString() + "）");
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, String.join("\n", errors));
        }

        return rows.stream()
                .map(r -> new QuestionItem(r.seq(), r.question(), r.type(), r.options(), r.parentSeq(), r.triggerOption(), r.weight()))
                .toList();
    }

    private void validateUpdate(List<QuestionItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException(ErrorCode.PARAM_INVALID, "题目不能为空");
        var errors = new ArrayList<String>();
        var mainItems = new ArrayList<QuestionItem>();
        for (var i : items) {
            if (i == null) {
                errors.add("存在空行数据");
                continue;
            }
            Integer parent = i.parentSeq();
            if (parent != null && parent <= 0) parent = null;
            if (parent == null) mainItems.add(i);
        }
        if (mainItems.isEmpty()) errors.add("至少需要1个主问题");

        var mainSeqSet = new HashSet<Integer>();
        for (var i : mainItems) mainSeqSet.add(i.seq());
        if (mainSeqSet.size() != mainItems.size()) errors.add("主问题序号不能重复");
        for (int s = 1; s <= mainItems.size(); s++) {
            if (!mainSeqSet.contains(s)) errors.add("主问题序号必须从1开始连续递增");
        }

        var mainMap = new HashMap<Integer, QuestionItem>();
        for (var i : mainItems) mainMap.put(i.seq(), i);

        var childrenByParent = new HashMap<Integer, List<QuestionItem>>();
        for (var i : items) {
            if (i == null) continue;
            Integer parent = i.parentSeq();
            if (parent != null && parent <= 0) parent = null;
            if (parent == null) continue;
            if (parent <= 0) {
                errors.add("主" + parent + "-子" + i.seq() + "：主问题序号不合法");
                continue;
            }
            if (!mainMap.containsKey(parent)) {
                errors.add("主" + parent + "-子" + i.seq() + "：主问题序号不存在");
                continue;
            }
            childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(i);
        }
        for (var e : childrenByParent.entrySet()) {
            var list = e.getValue();
            var childSeqSet = new HashSet<Integer>();
            for (var r : list) childSeqSet.add(r.seq());
            if (childSeqSet.size() != list.size()) errors.add("主问题" + e.getKey() + "：子问题序号不能重复");
            for (int s = 1; s <= list.size(); s++) {
                if (!childSeqSet.contains(s)) errors.add("主问题" + e.getKey() + "：子问题序号必须从1开始连续递增");
            }
        }

        var allWeightsOk = true;
        for (var i : items) {
            if (i == null) continue;
            var rowNo = (i.parentSeq() == null || i.parentSeq() <= 0) ? ("主" + i.seq()) : ("主" + i.parentSeq() + "-子" + i.seq());
            var q = normalize(i.question());
            if (q == null || q.length() < 2) errors.add(rowNo + "：问题至少2个字符");
            if (q != null && q.length() > 255) errors.add(rowNo + "：问题过长");
            ParsedType parsed = null;
            try {
                parsed = parseType(normalize(i.type()));
            } catch (BusinessException e) {
                errors.add(rowNo + "：" + e.getMessage());
            }
            var w = i.weight();
            if (w == null) {
                errors.add(rowNo + "：权重必填");
                allWeightsOk = false;
            } else if (w.scale() > 2) {
                errors.add(rowNo + "：权重最多2位小数");
                allWeightsOk = false;
            }
            var isChoice = parsed != null && (TYPE_SINGLE.equals(parsed.type()) || parsed.multiMax() != null);
            if (isChoice) {
                var parts = validateOptionsMaybe(i.options(), rowNo + "：", i.seq(), errors);
                if (parts != null) {
                    if (parts.size() < 2) errors.add(rowNo + "：选项至少2个");
                    if (parsed != null && parsed.multiMax() != null && parsed.multiMax() > parts.size()) {
                        errors.add(rowNo + "：多选最大可选数量不能超过选项数量");
                    }
                }
            } else {
                var opt = normalize(i.options());
                if (opt != null) errors.add(rowNo + "：填空/判断题型选项答案必须为空");
            }
        }

        for (var i : items) {
            if (i == null) continue;
            Integer parentSeq = i.parentSeq();
            if (parentSeq != null && parentSeq <= 0) parentSeq = null;
            if (parentSeq == null) {
                if (normalize(i.triggerOption()) != null) errors.add("主" + i.seq() + "：未绑定子问题时触发选项必须为空");
                continue;
            }
            var parent = mainMap.get(parentSeq);
            if (parent == null) {
                errors.add("主" + parentSeq + "-子" + i.seq() + "：主问题序号不存在");
                continue;
            }
            try {
                var parentParsed = parseType(normalize(parent.type()));
                if (!(TYPE_SINGLE.equals(parentParsed.type()) || parentParsed.multiMax() != null)) {
                    errors.add("主" + parentSeq + "-子" + i.seq() + "：主问题必须为单选或多选");
                    continue;
                }
            } catch (BusinessException e) {
                continue;
            }
            var trigger = normalize(i.triggerOption());
            if (trigger == null) {
                errors.add("主" + parentSeq + "-子" + i.seq() + "：触发子问题选项必填");
                continue;
            }
            var parentOptions = validateOptionsMaybe(parent.options(), "主" + parent.seq() + "：", parent.seq(), errors);
            if (parentOptions != null && !parentOptions.contains(trigger)) {
                errors.add("主" + parentSeq + "-子" + i.seq() + "：触发子问题选项必须来自主问题选项");
            }
        }

        if (allWeightsOk) {
            var sum = BigDecimal.ZERO;
            for (var i : items) sum = sum.add(i.weight());
            sum = sum.setScale(2, RoundingMode.HALF_UP);
            var diff = sum.subtract(new BigDecimal("100.00")).abs();
            if (diff.compareTo(new BigDecimal("0.01")) > 0) {
                errors.add("权重总和必须等于100（当前=" + sum.toPlainString() + "）");
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, String.join("\n", errors));
        }
    }

    private record ParsedType(String type, Integer multiMax) {
    }

    private ParsedType parseType(String raw) {
        var t = normalize(raw);
        if (t == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "题型不合法");
        if (TYPE_SINGLE.equals(t)) return new ParsedType(TYPE_SINGLE, null);
        if (TYPE_FILL.equals(t)) return new ParsedType(TYPE_FILL, null);
        if (TYPE_JUDGE.equals(t)) return new ParsedType(TYPE_JUDGE, null);
        if (t.startsWith(TYPE_MULTI_PREFIX)) {
            var n = t.substring(TYPE_MULTI_PREFIX.length()).trim();
            try {
                var max = Integer.parseInt(n);
                if (max < 2) throw new BusinessException(ErrorCode.PARAM_INVALID, "多选最大可选数量必须>=2");
                return new ParsedType("多选", max);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "多选格式必须为 多选_X");
            }
        }
        if ("多选".equals(t)) throw new BusinessException(ErrorCode.PARAM_INVALID, "多选题型必须为 多选_X");
        throw new BusinessException(ErrorCode.PARAM_INVALID, "题型不合法");
    }

    private List<String> validateOptions(String raw, int seq) {
        var s = normalize(raw);
        if (s == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案必填（序号" + seq + "）");
        if (s.contains("||")) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案禁止连续分隔符（序号" + seq + "）");
        var parts = s.split("\\|", -1);
        var out = new ArrayList<String>();
        for (var p : parts) {
            var item = p == null ? "" : p.trim();
            if (item.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案禁止空选项（序号" + seq + "）");
            out.add(item);
        }
        return out;
    }

    private List<String> validateOptionsNoSeq(String raw) {
        var s = normalize(raw);
        if (s == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案必填");
        if (s.contains("||")) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案禁止连续分隔符");
        var parts = s.split("\\|", -1);
        var out = new ArrayList<String>();
        for (var p : parts) {
            var item = p == null ? "" : p.trim();
            if (item.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "选项答案禁止空选项");
            out.add(item);
        }
        return out;
    }

    private List<String> validateOptionsMaybe(String raw, String prefix, int id, List<String> errors) {
        try {
            return validateOptionsNoSeq(raw);
        } catch (BusinessException e) {
            errors.add((prefix == null ? "" : prefix) + e.getMessage());
            return null;
        }
    }

    private List<String> splitOptions(String raw) {
        var s = normalize(raw);
        if (s == null) return List.of();
        var parts = s.split("\\|", -1);
        var out = new ArrayList<String>();
        for (var p : parts) {
            var item = p == null ? "" : p.trim();
            if (!item.isBlank()) out.add(item);
        }
        return out;
    }

    private String normalizeOptionsForStorage(String typeRaw, String optionsRaw, int seq) {
        var parsed = parseType(normalize(typeRaw));
        var isChoice = TYPE_SINGLE.equals(parsed.type()) || parsed.multiMax() != null;
        if (!isChoice) return null;
        var parts = validateOptions(optionsRaw, seq);
        return String.join("|", parts);
    }

    private String normalize(String v) {
        if (v == null) return null;
        var s = v.trim();
        return s.isBlank() ? null : s;
    }
}
