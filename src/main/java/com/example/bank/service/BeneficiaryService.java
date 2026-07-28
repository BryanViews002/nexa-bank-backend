package com.example.bank.service;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.BeneficiaryDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Beneficiary;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.BeneficiaryRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final KycGuardService kycGuardService;
    private final BankMapper bankMapper;

    public BeneficiaryService(
            BeneficiaryRepository beneficiaryRepository,
            AccountRepository accountRepository,
            KycGuardService kycGuardService,
            BankMapper bankMapper
    ) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountRepository = accountRepository;
        this.kycGuardService = kycGuardService;
        this.bankMapper = bankMapper;
    }

    @Transactional
    public BeneficiaryDto.Response create(BeneficiaryDto.CreateRequest request, User user) {
        kycGuardService.requireApproved(user);
        Account recipient = accountRepository.findByAccountNumber(request.recipientIdentifier())
                .or(() -> accountRepository.findFirstByUserUsernameAndStatusOrderByCreatedAtAsc(
                        request.recipientIdentifier(),
                        Account.AccountStatus.ACTIVE
                ))
                .orElseThrow(() -> new IllegalArgumentException("Recipient account not found"));
        if (recipient.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You cannot add one of your own accounts as a beneficiary");
        }
        if (recipient.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Recipient account is not active");
        }

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(user);
        beneficiary.setName(recipient.getUser().getFullName());
        beneficiary.setAccountNumber(recipient.getAccountNumber());
        beneficiary.setRecipientUsername(recipient.getUser().getUsername());
        beneficiary.setNickname(request.nickname());
        beneficiary.setVerifiedAt(Instant.now());
        return toResponse(beneficiaryRepository.save(beneficiary));
    }

    public List<BeneficiaryDto.Response> list(User user) {
        return beneficiaryRepository.findByUserIdOrderByLastUsedAtDescCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BeneficiaryDto.Response updateNickname(Long id, String nickname, User user) {
        Beneficiary beneficiary = owned(id, user);
        beneficiary.setNickname(nickname == null ? null : nickname.trim());
        return toResponse(beneficiaryRepository.save(beneficiary));
    }

    @Transactional
    public void remove(Long id, User user) {
        beneficiaryRepository.delete(owned(id, user));
    }

    @Transactional
    public void markUsed(User user, String accountNumber) {
        beneficiaryRepository.findByUserIdOrderByLastUsedAtDescCreatedAtDesc(user.getId()).stream()
                .filter(beneficiary -> beneficiary.getAccountNumber().equals(accountNumber))
                .findFirst()
                .ifPresent(beneficiary -> beneficiary.setLastUsedAt(Instant.now()));
    }

    private Beneficiary owned(Long id, User user) {
        return beneficiaryRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Beneficiary not found"));
    }

    private BeneficiaryDto.Response toResponse(Beneficiary beneficiary) {
        return new BeneficiaryDto.Response(
                beneficiary.getId(),
                beneficiary.getName(),
                beneficiary.getRecipientUsername(),
                bankMapper.maskAccountNumber(beneficiary.getAccountNumber()),
                beneficiary.getNickname(),
                beneficiary.isActive(),
                beneficiary.getVerifiedAt(),
                beneficiary.getLastUsedAt(),
                beneficiary.getCreatedAt()
        );
    }
}
