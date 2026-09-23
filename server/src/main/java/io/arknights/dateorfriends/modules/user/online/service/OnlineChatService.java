package io.arknights.dateorfriends.modules.user.online.service;

import io.arknights.dateorfriends.modules.user.online.mapper.ChatMessageDO;
import io.arknights.dateorfriends.modules.user.online.mapper.ChatMessageMapper;
import io.arknights.dateorfriends.modules.user.online.mapper.ProfanityWordMapper;
import io.arknights.dateorfriends.tools.profanity.ProfanityFilter;
import io.arknights.dateorfriends.tools.web.IpUtils;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OnlineChatService {

    private static final Logger log = LoggerFactory.getLogger(OnlineChatService.class);

    private final ProfanityFilter profanityFilter;
    private final ProfanityWordMapper profanityWordMapper;
    private final ChatMessageMapper chatMessageMapper;

    public OnlineChatService(
            ProfanityFilter profanityFilter,
            ProfanityWordMapper profanityWordMapper,
            ChatMessageMapper chatMessageMapper
    ) {
        this.profanityFilter = profanityFilter;
        this.profanityWordMapper = profanityWordMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        reloadProfanityWords();
    }

    @Scheduled(fixedDelay = 60_000)
    public void reloadProfanityWords() {
        try {
            var words = profanityWordMapper.selectAllWords();
            profanityFilter.refresh(words);
            log.debug("已加载 {} 个屏蔽词", words.size());
        } catch (Exception e) {
            log.warn("加载屏蔽词失败", e);
        }
    }

    public String filter(String text) {
        return profanityFilter.filter(text);
    }

    public void saveChatMessage(String roomId, long userId, String nickname, String content, String rawIp) {
        try {
            var msg = new ChatMessageDO();
            msg.setRoomId(roomId);
            msg.setUserId(userId);
            msg.setNickname(nickname);
            msg.setContent(content);
            msg.setSenderIp(IpUtils.mask(rawIp));
            msg.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(msg);
        } catch (Exception e) {
            log.warn("保存聊天记录失败 room={} user={}", roomId, userId, e);
        }
    }
}
