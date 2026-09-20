package io.arknights.dateorfriends.modules.user.lmd.service;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.UserWalletMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 龙门币账面校验：余额与流水之和逐账户比对，不一致即报告（不自动修复，由管理员介入） */
@Service
public class LmdVerifyService {

    private final UserWalletMapper walletMapper;
    private final LmdTransactionMapper txMapper;

    public LmdVerifyService(UserWalletMapper walletMapper, LmdTransactionMapper txMapper) {
        this.walletMapper = walletMapper;
        this.txMapper = txMapper;
    }

    public record Mismatch(long userId, long balance, long ledgerSum, long diff) {
    }

    public record VerifyResult(long totalAccounts, List<Mismatch> mismatches) {
    }

    public Mono<VerifyResult> verifyAll() {
        return Mono.fromCallable(() -> {
                    var total = walletMapper.countAll();
                    var mismatches = txMapper.selectMismatchWallets().stream()
                            .map(m -> new Mismatch(m.getUserId(), m.getBalance(), m.getLedgerSum(), m.getBalance() - m.getLedgerSum()))
                            .toList();
                    return new VerifyResult(total, mismatches);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<VerifyResult> verifyOne(long userId) {
        return Mono.fromCallable(() -> {
                    var row = walletMapper.selectByUserId(userId);
                    long balance = row == null || row.getBalance() == null ? 0L : row.getBalance();
                    long sum = txMapper.sumByUser(userId);
                    List<Mismatch> mismatches = balance == sum
                            ? List.of()
                            : List.of(new Mismatch(userId, balance, sum, balance - sum));
                    return new VerifyResult(mismatches.isEmpty() ? 1 : 0, mismatches);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
