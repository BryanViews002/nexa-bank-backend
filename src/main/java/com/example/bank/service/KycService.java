// src/main/java/com/example/bank/service/KycService.java
package com.example.bank.service;

import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.repository.KycRepository;
import com.example.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class KycService {
    private final KycRepository kycRepository;
    private final UserRepository userRepository;
    @Value("${app.upload.dir}")
    private String uploadDir;

    public KycService(KycRepository kycRepository, UserRepository userRepository) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
    }

    public KycDocument upload(MultipartFile file, User user) throws IOException {
        String userDir = uploadDir + "/" + user.getId();
        Files.createDirectories(Paths.get(userDir));
        String uuid = UUID.randomUUID().toString();
        String fileName = uuid + "-" + file.getOriginalFilename();
        String path = userDir + "/" + fileName;
        file.transferTo(new File(path));
        KycDocument doc = new KycDocument();
        doc.setUser(user);
        doc.setFilename(fileName);
        doc.setPath(path);
        doc.setContentType(file.getContentType());
        return kycRepository.save(doc);
    }

    public void approve(Long id) {
        KycDocument doc = kycRepository.findById(id).orElseThrow();
        doc.setStatus(KycDocument.KycStatus.APPROVED);
        kycRepository.save(doc);
        User user = doc.getUser();
        user.setEnabled(true);
        userRepository.save(user);
    }

    public void reject(Long id) {
        KycDocument doc = kycRepository.findById(id).orElseThrow();
        doc.setStatus(KycDocument.KycStatus.REJECTED);
        kycRepository.save(doc);
    }

    public List<KycDocument> getPendingKyc() {
        return kycRepository.findByStatus(KycDocument.KycStatus.PENDING);
    }
}