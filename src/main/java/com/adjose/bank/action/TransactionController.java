package com.adjose.bank.action;

import com.adjose.bank.dao.AccountRepository;
import com.adjose.bank.dao.UserProfileRepository;
import com.adjose.bank.entity.Account;
import com.adjose.bank.entity.UserProfile;
import com.adjose.bank.entity.transaction.Deposit;
import com.adjose.bank.entity.transaction.TransferOut;
import com.adjose.bank.entity.transaction.Withdrawal;
import com.adjose.bank.exception.BadRequestException;
import com.adjose.bank.exception.ResourceNotFoundException;
import com.adjose.bank.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;

@RestController
public class TransactionController {

    private UserProfileRepository userProfileRepository;
    private AccountRepository accountRepository;
    private TransactionService transactionService;

    @Autowired
    public TransactionController(final UserProfileRepository userProfileRepository,
                                 final AccountRepository accountRepository,
                                 final TransactionService transactionService) {
        this.userProfileRepository = userProfileRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping("/v1/transactions/deposit")
    @ResponseStatus(code = HttpStatus.OK)
    public Deposit deposit(@RequestParam String accountNumber, @RequestParam BigDecimal amount,
                           Principal principal) {

        return userProfileRepository.findById(principal.getName())
                .map(userProfile -> deposit(userProfile, accountNumber, amount))
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found"));
    }

    private Deposit deposit(UserProfile userProfile, String accountNumber, BigDecimal amount) {

        return accountRepository.findByAccountNumberAndUserProfile(accountNumber, userProfile)
                .map(account -> transactionService.deposit(account, amount))
                .orElseThrow(() -> new BadRequestException("Account not found"));
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping("/v1/transactions/withdraw")
    @ResponseStatus(code = HttpStatus.OK)
    public Withdrawal withdraw(@RequestParam String accountNumber, @RequestParam BigDecimal amount,
                               Principal principal) {

        final Optional<UserProfile> userProfileOptional = userProfileRepository.findById(principal.getName());
        if (!userProfileOptional.isPresent()) {
            throw new ResourceNotFoundException("User profile not found");
        }

        final Optional<Account> accountOptional =
                accountRepository.findByAccountNumberAndUserProfile(accountNumber, userProfileOptional.get());
        if (!accountOptional.isPresent()) {
            throw new BadRequestException("Account not found");
        }

        final Account account = accountOptional.get();
        if (account.getBalance().compareTo(amount) >= 0) {
            return transactionService.withdraw(account, amount);
        } else {
            throw new BadRequestException("Insufficient balance");
        }
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping("/v1/transactions/transfer")
    @ResponseStatus(code = HttpStatus.OK)
    public TransferOut transfer(@RequestParam String fromAccountNumber, @RequestParam String toAccountNumber,
                                @RequestParam BigDecimal amount, Principal principal) {

        final Optional<UserProfile> userProfileOptional = userProfileRepository.findById(principal.getName());
        if (!userProfileOptional.isPresent()) {
            throw new ResourceNotFoundException("User profile not found");
        }

        final Optional<Account> fromAccountOptional =
                accountRepository.findByAccountNumberAndUserProfile(fromAccountNumber, userProfileOptional.get());
        if (!fromAccountOptional.isPresent()) {
            throw new BadRequestException(String.format("Account not found with id: %s", fromAccountNumber));
        }

        final Optional<Account> toAccountOptional = accountRepository.findById(toAccountNumber);
        if (!toAccountOptional.isPresent()) {
            throw new BadRequestException(String.format("Account not found with id: %s", toAccountNumber));
        }

        final Account fromAccount = fromAccountOptional.get();
        if (fromAccount.getBalance().compareTo(amount) >= 0) {
            return transactionService.transfer(fromAccount, toAccountOptional.get(), amount);
        } else {
            throw new BadRequestException("Insufficient balance");
        }
    }

}
