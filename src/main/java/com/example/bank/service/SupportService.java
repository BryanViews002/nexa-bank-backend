package com.example.bank.service;

import com.example.bank.dto.SupportDto;
import com.example.bank.entity.Notification;
import com.example.bank.entity.SupportMessage;
import com.example.bank.entity.SupportTicket;
import com.example.bank.entity.User;
import com.example.bank.repository.SupportMessageRepository;
import com.example.bank.repository.SupportTicketRepository;
import com.example.bank.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class SupportService {

    private static final List<SupportTicket.TicketStatus> CLOSED_STATUSES = List.of(
            SupportTicket.TicketStatus.RESOLVED,
            SupportTicket.TicketStatus.CLOSED
    );

    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SupportService(
            SupportTicketRepository supportTicketRepository,
            SupportMessageRepository supportMessageRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public SupportDto.TicketResponse create(SupportDto.CreateTicketRequest request, User user) {
        SupportTicket ticket = new SupportTicket();
        ticket.setUser(user);
        ticket.setSubject(request.subject().trim());
        ticket.setCategory(parse(
                request.category(),
                SupportTicket.TicketCategory.class,
                SupportTicket.TicketCategory.OTHER
        ));
        ticket.setPriority(parse(
                request.priority(),
                SupportTicket.TicketPriority.class,
                SupportTicket.TicketPriority.NORMAL
        ));
        SupportTicket saved = supportTicketRepository.save(ticket);
        appendMessage(saved, user, request.message(), false);
        return toResponse(saved, false);
    }

    public Page<SupportDto.TicketResponse> listMine(User user, int page, int size) {
        return supportTicketRepository
                .findByUserIdOrderByUpdatedAtDesc(user.getId(), pageable(page, size))
                .map(ticket -> toSummary(ticket, false));
    }

    public SupportDto.TicketResponse get(Long id, User user) {
        return toResponse(owned(id, user), false);
    }

    @Transactional
    public SupportDto.TicketResponse reply(Long id, SupportDto.MessageRequest request, User user) {
        SupportTicket ticket = owned(id, user);
        if (CLOSED_STATUSES.contains(ticket.getStatus())) {
            throw new IllegalStateException("This ticket is closed. Please open a new ticket.");
        }
        appendMessage(ticket, user, request.body(), false);
        if (ticket.getStatus() == SupportTicket.TicketStatus.WAITING_FOR_CUSTOMER) {
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
            supportTicketRepository.save(ticket);
        }
        return toResponse(ticket, false);
    }

    @Transactional
    public SupportDto.TicketResponse close(Long id, User user) {
        SupportTicket ticket = owned(id, user);
        ticket.setStatus(SupportTicket.TicketStatus.CLOSED);
        return toResponse(supportTicketRepository.save(ticket), false);
    }

    public Page<SupportDto.TicketResponse> listAllForAdmin(int page, int size) {
        return supportTicketRepository.findAllByOrderByUpdatedAtDesc(pageable(page, size))
                .map(ticket -> toSummary(ticket, true));
    }

    public SupportDto.TicketResponse getForAdmin(Long id) {
        return toResponse(byId(id), true);
    }

    @Transactional
    public SupportDto.TicketResponse replyAsAdmin(Long id, SupportDto.MessageRequest request, User admin) {
        SupportTicket ticket = byId(id);
        boolean internal = Boolean.TRUE.equals(request.internalNote());
        appendMessage(ticket, admin, request.body(), internal);
        if (!internal) {
            if (ticket.getStatus() == SupportTicket.TicketStatus.OPEN) {
                ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
            }
            supportTicketRepository.save(ticket);
            notificationService.notify(
                    ticket.getUser(),
                    Notification.NotificationType.SUPPORT,
                    "Support replied to your ticket",
                    "There is a new reply on \"" + ticket.getSubject() + "\".",
                    "SUPPORT_TICKET",
                    ticket.getId()
            );
        }
        return toResponse(ticket, true);
    }

    @Transactional
    public SupportDto.TicketResponse updateAsAdmin(Long id, SupportDto.AdminUpdateRequest request) {
        SupportTicket ticket = byId(id);
        if (request.status() != null) {
            ticket.setStatus(parse(
                    request.status(),
                    SupportTicket.TicketStatus.class,
                    ticket.getStatus()
            ));
        }
        if (request.priority() != null) {
            ticket.setPriority(parse(
                    request.priority(),
                    SupportTicket.TicketPriority.class,
                    ticket.getPriority()
            ));
        }
        if (request.assignedAdminId() != null) {
            ticket.setAssignedAdmin(userRepository.findById(request.assignedAdminId())
                    .orElseThrow(() -> new IllegalArgumentException("Assigned administrator not found")));
        }
        if (request.resolution() != null) {
            ticket.setResolution(request.resolution());
        }
        SupportTicket saved = supportTicketRepository.save(ticket);
        if (CLOSED_STATUSES.contains(saved.getStatus())) {
            notificationService.notify(
                    saved.getUser(),
                    Notification.NotificationType.SUPPORT,
                    "Support ticket " + saved.getStatus().name().toLowerCase(),
                    "Your ticket \"" + saved.getSubject() + "\" was "
                            + saved.getStatus().name().toLowerCase() + ".",
                    "SUPPORT_TICKET",
                    saved.getId()
            );
        }
        return toResponse(saved, true);
    }

    private void appendMessage(SupportTicket ticket, User author, String body, boolean internal) {
        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setBody(body.trim());
        message.setInternalNote(internal);
        supportMessageRepository.save(message);
    }

    private SupportTicket owned(Long id, User user) {
        return supportTicketRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Support ticket not found"));
    }

    private SupportTicket byId(Long id) {
        return supportTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Support ticket not found"));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private SupportDto.TicketResponse toSummary(SupportTicket ticket, boolean includeInternal) {
        List<SupportMessage> messages = visibleMessages(ticket, includeInternal);
        return buildResponse(ticket, messages.size(), List.of());
    }

    private SupportDto.TicketResponse toResponse(SupportTicket ticket, boolean includeInternal) {
        List<SupportMessage> messages = visibleMessages(ticket, includeInternal);
        return buildResponse(
                ticket,
                messages.size(),
                messages.stream().map(this::toMessageResponse).toList()
        );
    }

    private List<SupportMessage> visibleMessages(SupportTicket ticket, boolean includeInternal) {
        return supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .filter(message -> includeInternal || !message.isInternalNote())
                .toList();
    }

    private SupportDto.TicketResponse buildResponse(
            SupportTicket ticket,
            int messageCount,
            List<SupportDto.MessageResponse> messages
    ) {
        return new SupportDto.TicketResponse(
                ticket.getId(),
                ticket.getUser().getId(),
                ticket.getUser().getFullName(),
                ticket.getSubject(),
                ticket.getCategory().name(),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getResolution(),
                ticket.getAssignedAdmin() == null ? null : ticket.getAssignedAdmin().getId(),
                ticket.getAssignedAdmin() == null ? null : ticket.getAssignedAdmin().getFullName(),
                messageCount,
                messages,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    private SupportDto.MessageResponse toMessageResponse(SupportMessage message) {
        boolean fromSupport = !message.getAuthor().getId().equals(message.getTicket().getUser().getId());
        return new SupportDto.MessageResponse(
                message.getId(),
                message.getAuthor().getId(),
                fromSupport ? "Nexa Support" : message.getAuthor().getFullName(),
                fromSupport,
                message.isInternalNote(),
                message.getBody(),
                message.getCreatedAt()
        );
    }

    private <E extends Enum<E>> E parse(String value, Class<E> type, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value for " + type.getSimpleName() + ": " + value);
        }
    }
}
