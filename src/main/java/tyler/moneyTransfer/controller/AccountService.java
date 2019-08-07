package tyler.moneyTransfer.controller;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tyler.moneyTransfer.dao.AccountRepository;
import tyler.moneyTransfer.dao.LogRepository;
import tyler.moneyTransfer.model.Account;
import tyler.moneyTransfer.model.Log;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccountService {
    private final AccountRepository accRepository;
    private final LogRepository logRepository;

    public List<Account> getAccounts() {
        log.debug("get all accounts");
        return accRepository.findAll();
    }

    public List<Log> getLogRecords() {
        log.debug("get log records");
        return logRepository.findAll();
    }

    public Optional<Account> getAccount(long accNumber) {
        log.debug("get account: {}", accNumber);
        return accRepository.findByAccNumber(accNumber);
    }

    @Transactional(rollbackFor = Exception.class)
    public Account withdraw(long number, BigDecimal amount) {
        checkAmount(amount);
        Account account = accRepository.findByAccNumberAndLock(number).orElseThrow(() -> new IllegalArgumentException("not found id: " + number));
        decreaseAmount(account, amount);

        log.debug("withdraw account: {}, amount: {}", number, amount);
        logRepository.save(Log.builder().fromNumber(number).amount(amount).description("withdraw").date(Instant.now()).build());
        return accRepository.save(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public Account deposit(long number, BigDecimal amount) {
        checkAmount(amount);
        Account account = accRepository.findByAccNumberAndLock(number).orElseThrow(() -> new IllegalArgumentException("not found id: " + number));
        increaseAmount(account, amount);

        log.debug("deposit account: {}, amount: {}", number, amount);
        logRepository.save(Log.builder().toNumber(number).amount(amount).description("deposit").date(Instant.now()).build());
        return accRepository.save(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Account> transfer(long from, long to, BigDecimal amount) {
        checkAmount(amount);
        long first = from < to ? from : to;
        long second = from < to ? to : from;

        Account firstAccount = accRepository.findByAccNumberAndLock(first)
                .orElseThrow(() -> new IllegalArgumentException("not found id: " + first));
        Account secondAccount = accRepository.findByAccNumberAndLock(second)
                .orElseThrow(() -> new IllegalArgumentException("not found id: " + second));
        decreaseAmount(firstAccount.getAccNumber().equals(from) ? firstAccount : secondAccount, amount);
        increaseAmount(firstAccount.getAccNumber().equals(to) ? firstAccount : secondAccount, amount);

        AccountService.log.debug("transfer amount: {}, accounts: {} -> {} ", amount, from, to);
        logRepository.save(Log.builder().fromNumber(from).toNumber(to).amount(amount).description("transfer").date(Instant.now()).build());

        List<Account> list = new ArrayList<>();
        list.add(firstAccount);
        list.add(secondAccount);
//        return accRepository.saveAll(List.of(firstAccount, secondAccount));
        return accRepository.saveAll(list);
    }

    private void increaseAmount(Account account, BigDecimal amount) {
        account.setAmount(account.getAmount().add(amount));
        updateDate(account);
    }

    private void decreaseAmount(Account account, BigDecimal amount) {
        account.setAmount(account.getAmount().subtract(amount));
        updateDate(account);
    }

    @VisibleForTesting
    void updateDate(Account account) {
        account.setLastUpdate(Instant.now());
    }

    private static void checkAmount(BigDecimal amount) {
        if (BigDecimal.ZERO.compareTo(amount) > 0) throw new IllegalArgumentException("not positive amount: " + amount);
    }
}
