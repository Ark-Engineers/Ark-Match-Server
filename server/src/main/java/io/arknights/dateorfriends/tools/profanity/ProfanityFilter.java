package io.arknights.dateorfriends.tools.profanity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * 敏感词过滤工具类。
 * <p>
 * 内部维护按长度降序排列的词表，对输入文本逐词匹配并替换为 '*'。
 * 检测时会跳过符号，防止通过插入符号绕过过滤。
 * 词表通过 {@link #refresh(List)} 整体替换，线程安全。
 */
@Component
public class ProfanityFilter {

    private volatile List<String> words = new CopyOnWriteArrayList<>();

    public void refresh(List<String> newWords) {
        var sorted = new ArrayList<>(newWords);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        this.words = new CopyOnWriteArrayList<>(sorted);
    }

    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        var current = this.words;
        if (current.isEmpty()) return text;

        var chars = text.toCharArray();
        for (var word : current) {
            if (word.isEmpty()) continue;
            replaceAll(chars, word);
        }
        return new String(chars);
    }

    private static void replaceAll(char[] chars, String word) {
        var cLen = chars.length;
        var wLen = word.length();
        if (wLen > cLen) return;

        var wChars = word.toCharArray();

        for (int i = 0; i < cLen; i++) {
                if (chars[i] == '\0' || !isContentChar(chars[i])) continue;

                if (matchSkippingSymbols(chars, i, wChars)) {
                    var endPos = findMatchEnd(chars, i, wChars);
                    for (int j = i; j <= endPos; j++) {
                        if (chars[j] != '\0') {
                            chars[j] = '*';
                        }
                    }
                    i = endPos;
                }
        }
    }

    private static boolean matchSkippingSymbols(char[] chars, int start, char[] word) {
        var cLen = chars.length;
        var wIdx = 0;
        var wLen = word.length;

        for (int i = start; i < cLen && wIdx < wLen; i++) {
            if (chars[i] == '\0') continue;
            if (!isContentChar(chars[i])) continue;
            if (Character.toLowerCase(chars[i]) != Character.toLowerCase(word[wIdx])) {
                return false;
            }
            wIdx++;
        }
        return wIdx == wLen;
    }

    private static int findMatchEnd(char[] chars, int start, char[] word) {
        var cLen = chars.length;
        var wIdx = 0;
        var wLen = word.length;
        var lastPos = start;

        for (int i = start; i < cLen && wIdx < wLen; i++) {
            if (chars[i] == '\0') continue;
            if (!isContentChar(chars[i])) continue;
            wIdx++;
            lastPos = i;
        }
        return lastPos;
    }

    private static boolean isContentChar(char c) {
        return Character.isLetterOrDigit(c) || Character.getType(c) == Character.OTHER_LETTER;
    }
}
