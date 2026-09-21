package io.arknights.dateorfriends.modules.user.lmd.service;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdMailClaimDO;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdMailClaimMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionDO;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.UserWalletMapper;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationUserMapper;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.LocalDateTime;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;

/**
 * 龙门币核心记账服务：所有余额变动必须走这里。
 * 余额更新 + 流水落库在同一 SqlSession 事务内完成，保证「余额 = 流水之和」恒成立；
 * 邮件领取额外在同一事务内写入领取记录（唯一键防重）并回写领取标记。
 */
@Service
public class LmdWalletService {

    private final SqlSessionFactory sqlSessionFactory;
    private final UserWalletMapper walletMapper;

    public LmdWalletService(SqlSessionFactory sqlSessionFactory, UserWalletMapper walletMapper) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.walletMapper = walletMapper;
    }

    public record ChangeResult(long amount, long balanceAfter, long transactionId) {
    }

    public long getBalance(long userId) {
        var row = walletMapper.selectByUserId(userId);
        return row == null || row.getBalance() == null ? 0L : row.getBalance();
    }

    /** 管理员调整（正=发放入账，负=扣除出账；扣除时余额不足抛 LMD_INSUFFICIENT_BALANCE） */
    public ChangeResult adjustBalance(long userId, long amount, String description, String traceId, String ip, long adminId) {
        if (amount == 0) throw new BusinessException(ErrorCode.LMD_AMOUNT_INVALID);
        try (var session = sqlSessionFactory.openSession(false)) {
            var result = applyCredit(session, userId, amount, "ADMIN_ADJUST", null, null, description, traceId, ip, adminId);
            session.commit();
            return result;
        }
    }

    /** 管理员设置余额：锁定钱包并校验预期余额，以差额记账，避免覆盖并发资金变动。 */
    public ChangeResult setBalance(long userId, long balance, long expectedBalance, String description, String traceId, String ip, long adminId) {
        if (balance < 0 || expectedBalance < 0) throw new BusinessException(ErrorCode.LMD_AMOUNT_INVALID);
        try (var session = sqlSessionFactory.openSession(false)) {
            var wm = session.getMapper(UserWalletMapper.class);
            wm.ensureRow(userId);
            var row = wm.selectByUserIdForUpdate(userId);
            long actualBalance = row == null || row.getBalance() == null ? 0L : row.getBalance();
            if (actualBalance != expectedBalance) {
                throw new BusinessException(ErrorCode.OP_FAILED, "余额已变化，请刷新后重新确认");
            }
            long amount = balance - actualBalance;
            if (amount == 0 || amount < -10_000_000L || amount > 10_000_000L) {
                throw new BusinessException(ErrorCode.LMD_AMOUNT_INVALID, "额度必须为非零且绝对值不超过 10000000");
            }
            var result = applyCredit(session, userId, amount, "ADMIN_ADJUST", null, null, description, traceId, ip, adminId);
            session.commit();
            return result;
        }
    }

    /** 邮件领取：领取记录（防重） + 入账 + 流水 + 领取标记回写，同一事务完成 */
    public ChangeResult claimMailReward(long userId, long notificationId, long amount, String traceId, String ip) {
        try (var session = sqlSessionFactory.openSession(false)) {
            var claimMapper = session.getMapper(LmdMailClaimMapper.class);
            var claim = new LmdMailClaimDO();
            claim.setNotificationId(notificationId);
            claim.setUserId(userId);
            claim.setAmount(amount);
            claim.setTraceId(traceId);
            claim.setRequestIp(ip);
            claim.setCreatedAt(LocalDateTime.now());
            try {
                claimMapper.insert(claim);
            } catch (Exception e) {
                if (isDuplicateKey(e)) throw new BusinessException(ErrorCode.LMD_ALREADY_CLAIMED);
                throw e;
            }

            var result = applyCredit(session, userId, amount, "MAIL_CLAIM", "NOTIFICATION", notificationId, null, traceId, ip, userId);
            session.getMapper(SiteNotificationUserMapper.class).markClaimed(userId, notificationId, LocalDateTime.now());
            session.commit();
            return result;
        }
    }

    /** 供其他模块在自有 SqlSession 事务内记账（如赛马下注扣款/中奖入账/关闭退款）；amount 正=入账，负=出账 */
    public ChangeResult applyCredit(
            SqlSession session,
            long userId,
            long amount,
            String type,
            String refType,
            Long refId,
            String description,
            String traceId,
            String ip,
            Long createdBy
    ) {
        var wm = session.getMapper(UserWalletMapper.class);
        var tm = session.getMapper(LmdTransactionMapper.class);
        wm.ensureRow(userId);
        int affected = wm.addBalance(userId, amount);
        if (affected == 0) throw new BusinessException(ErrorCode.LMD_INSUFFICIENT_BALANCE);
        var row = wm.selectByUserId(userId);
        long balanceAfter = row == null || row.getBalance() == null ? 0L : row.getBalance();

        var tx = new LmdTransactionDO();
        tx.setUserId(userId);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setType(type);
        tx.setRefType(refType);
        tx.setRefId(refId);
        tx.setDescription(description);
        tx.setTraceId(traceId);
        tx.setRequestIp(ip);
        tx.setCreatedBy(createdBy);
        tx.setCreatedAt(LocalDateTime.now());
        tm.insert(tx);
        return new ChangeResult(amount, balanceAfter, tx.getId() == null ? 0L : tx.getId());
    }

    private boolean isDuplicateKey(Throwable t) {
        for (var cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof java.sql.SQLIntegrityConstraintViolationException sql && sql.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }
}
