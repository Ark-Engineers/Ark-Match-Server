package io.arknights.dateorfriends.modules.user.arknights.service.impl;

import io.arknights.dateorfriends.modules.user.arknights.mapper.ArknightsBindingDO;
import io.arknights.dateorfriends.modules.user.arknights.mapper.ArknightsBindingMapper;
import io.arknights.dateorfriends.modules.user.arknights.service.ArknightsBindingService;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service("userArknightsBindingService")
public class ArknightsBindingServiceImpl implements ArknightsBindingService {

    private final ArknightsBindingMapper arknightsBindingMapper;

    public ArknightsBindingServiceImpl(ArknightsBindingMapper arknightsBindingMapper) {
        this.arknightsBindingMapper = arknightsBindingMapper;
    }

    @Override
    public Mono<BindingStatus> getStatus(long userId) {
        return Mono.fromCallable(() -> toStatus(arknightsBindingMapper.selectByUserId(userId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BindingStatus> bind(long userId, BindRequest request) {
        return Mono.fromCallable(() -> {
                    var basic = request.basic();
                    var accountBinding = request.accountBinding();
                    var hgId = requiredText(basic.hgId(), 64, "森空岛ID不正确");
                    var uid = requiredText(accountBinding.uid(), 64, "明日方舟UID不正确");
                    var nickName = requiredText(accountBinding.nickName(), 128, "明日方舟昵称不正确");
                    var channelName = requiredText(accountBinding.channelName(), 64, "明日方舟区服不正确");
                    if (basic.isMinor() == null) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID, "未成年状态不能为空");
                    }

                    var boundByUid = arknightsBindingMapper.selectBoundByUid(uid);
                    if (boundByUid != null && !Objects.equals(boundByUid.getUserId(), userId)) {
                        throw new BusinessException(ErrorCode.ARKNIGHTS_ACCOUNT_ALREADY_BOUND);
                    }

                    var binding = new ArknightsBindingDO();
                    binding.setUserId(userId);
                    binding.setArknightsBound(1);
                    binding.setArknightsIsMinor(basic.isMinor() ? 1 : 0);
                    binding.setArknightsHgId(hgId);
                    binding.setArknightsUid(uid);
                    binding.setArknightsNickname(nickName);
                    binding.setArknightsChannelName(channelName);
                    binding.setArknightsBoundAt(LocalDateTime.now());
                    persistBinding(binding);
                    return toStatus(binding);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> unbind(long userId) {
        return Mono.fromRunnable(() -> arknightsBindingMapper.unbind(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void persistBinding(ArknightsBindingDO binding) {
        try {
            if (arknightsBindingMapper.update(binding) > 0) {
                return;
            }
            try {
                arknightsBindingMapper.insert(binding);
            } catch (DuplicateKeyException e) {
                if (arknightsBindingMapper.update(binding) == 0) {
                    throw e;
                }
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ARKNIGHTS_ACCOUNT_ALREADY_BOUND);
        }
    }

    private BindingStatus toStatus(ArknightsBindingDO binding) {
        var bound = binding != null && Integer.valueOf(1).equals(binding.getArknightsBound());
        if (!bound) {
            return new BindingStatus(false, null, null, null, null, null, null, null);
        }
        var isMinor = Integer.valueOf(1).equals(binding.getArknightsIsMinor());
        return new BindingStatus(
                true,
                isMinor,
                !isMinor,
                binding.getArknightsHgId(),
                binding.getArknightsUid(),
                binding.getArknightsNickname(),
                binding.getArknightsChannelName(),
                binding.getArknightsBoundAt()
        );
    }

    private String requiredText(String value, int maxLength, String message) {
        var text = value == null ? "" : value.trim();
        if (text.isBlank() || text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return text;
    }
}
