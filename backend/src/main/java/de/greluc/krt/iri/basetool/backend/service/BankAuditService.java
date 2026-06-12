/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.BankAuditEventMapper;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEvent;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.BankAuditEventDto;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankAuditEventRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends rows to the immutable bank audit trail (epic #556, REQ-BANK-012). One row per bank
 * mutation, written in the <em>same transaction</em> as the business write — the {@code MANDATORY}
 * propagation makes calling this outside a transaction a programming error, and an audit insert
 * failure rolls the mutation back (no silent gaps).
 *
 * <p>The actor is resolved from the current security context and snapshotted: the row stores both
 * the user id (FK {@code ON DELETE SET NULL}) and the effective-name handle so the trail survives
 * user deletion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankAuditService {

  private final BankAuditEventRepository auditEventRepository;
  private final AuthHelperService authHelperService;
  private final UserRepository userRepository;
  private final BankAccountRepository accountRepository;
  private final BankAuditEventMapper bankAuditEventMapper;

  /**
   * Appends one audit event for the current caller within the surrounding business transaction.
   *
   * @param eventType what happened
   * @param accountId the affected account, or {@code null} for account-less events
   * @param transactionId the created ledger transaction, or {@code null} for non-booking events
   * @param targetUserId the affected user (grantee / holder's linked user), or {@code null}
   * @param details compact human-readable details payload, or {@code null}
   * @return the persisted audit row
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public BankAuditEvent record(
      @NotNull BankAuditEventType eventType,
      @Nullable UUID accountId,
      @Nullable UUID transactionId,
      @Nullable UUID targetUserId,
      @Nullable String details) {
    Optional<UUID> actorId = authHelperService.currentUserId();
    String actorHandle =
        actorId.flatMap(userRepository::findById).map(User::getEffectiveName).orElse("system");
    BankAuditEvent event =
        BankAuditEvent.builder()
            .occurredAt(Instant.now())
            .actorUserId(actorId.orElse(null))
            .actorHandle(actorHandle)
            .eventType(eventType)
            .accountId(accountId)
            .transactionId(transactionId)
            .targetUserId(targetUserId)
            .details(details)
            .build();
    return auditEventRepository.save(event);
  }

  /**
   * One filtered page of the audit log for the admin viewer (REQ-BANK-012, A2 mockup). The affected
   * accounts' display numbers are resolved with one batched lookup over the page — audit rows keep
   * plain UUID references so they outlive every aggregate.
   *
   * @param from period start (inclusive), or {@code null}
   * @param to period end (inclusive), or {@code null}
   * @param actorUserId filter on the acting user, or {@code null}
   * @param accountId filter on the affected account, or {@code null}
   * @param eventType filter on the event type, or {@code null}
   * @param pageable page, size and whitelisted sort
   * @return one page of audit events with resolved account numbers
   */
  @Transactional(readOnly = true)
  public Page<BankAuditEventDto> getEvents(
      @Nullable Instant from,
      @Nullable Instant to,
      @Nullable UUID actorUserId,
      @Nullable UUID accountId,
      @Nullable BankAuditEventType eventType,
      @NotNull Pageable pageable) {
    Page<BankAuditEvent> page =
        auditEventRepository.findFiltered(from, to, actorUserId, accountId, eventType, pageable);
    List<UUID> accountIds =
        page.getContent().stream()
            .map(BankAuditEvent::getAccountId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> accountNos =
        accountIds.isEmpty()
            ? Map.of()
            : accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(BankAccount::getId, BankAccount::getAccountNo));
    return page.map(
        event ->
            bankAuditEventMapper.toDto(
                event, event.getAccountId() == null ? null : accountNos.get(event.getAccountId())));
  }
}
