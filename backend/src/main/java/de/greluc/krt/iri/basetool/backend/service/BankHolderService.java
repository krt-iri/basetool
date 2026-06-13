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

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.BankHolderMapper;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.BankHolderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.request.RegisterBankHolderRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateBankHolderRequest;
import de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance;
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bank-local holder registry (epic #556, REQ-BANK-003): registration via the user lookup with a
 * deletion-proof handle snapshot, activity toggling, and the registry listing enriched with
 * batch-computed custody totals. Holders are never hard-deleted — the ledger references them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankHolderService {

  private final BankHolderRepository holderRepository;
  private final BankPostingRepository postingRepository;
  private final UserRepository userRepository;
  private final BankHolderMapper bankHolderMapper;
  private final BankAuditService bankAuditService;

  /**
   * The full registry, ordered by handle, with the cross-account custody totals and account counts
   * joined in from two grouped statements (W1 "Halter" tab, no N+1).
   *
   * @return every holder row as DTO
   */
  public List<BankHolderDto> getHolders() {
    Map<UUID, BigDecimal> totals =
        postingRepository.holderTotals().stream()
            .collect(Collectors.toMap(BankHolderBalance::holderId, BankHolderBalance::amount));
    Map<UUID, Long> accountCounts =
        postingRepository.holderAccountCounts().stream()
            .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    return holderRepository.findAllByOrderByHandleAsc().stream()
        .map(
            holder ->
                bankHolderMapper.toDto(
                    holder,
                    totals.getOrDefault(holder.getId(), BigDecimal.ZERO),
                    accountCounts.getOrDefault(holder.getId(), 0L)))
        .toList();
  }

  /**
   * Registers a basetool user as holder (REQ-BANK-003), snapshotting the effective name as the
   * deletion-proof handle. One holder row per user.
   *
   * @param request the user to register
   * @return the created holder row
   * @throws NotFoundException when the user does not exist
   * @throws DuplicateEntityException when the user is already registered
   */
  @Transactional
  public BankHolderDto registerHolder(@NotNull RegisterBankHolderRequest request) {
    User user =
        userRepository
            .findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    if (holderRepository.existsByUserId(user.getId())) {
      throw new DuplicateEntityException("The user is already registered as a bank holder");
    }
    BankHolder holder = new BankHolder();
    holder.setUser(user);
    holder.setHandle(user.getEffectiveName());
    holder.setActive(true);
    BankHolder saved = holderRepository.save(holder);
    bankAuditService.record(
        BankAuditEventType.HOLDER_REGISTERED, null, null, user.getId(), saved.getHandle());
    return bankHolderMapper.toDto(saved, BigDecimal.ZERO, 0L);
  }

  /**
   * Toggles a holder's active flag (REQ-BANK-003). Deactivation blocks new incoming postings; the
   * recorded custody and the history stay untouched.
   *
   * @param holderId the holder row
   * @param request the new flag plus the echoed optimistic-locking version
   * @return the updated holder row incl. fresh custody totals
   * @throws NotFoundException when the holder does not exist
   * @throws ObjectOptimisticLockingFailureException on a version mismatch (409)
   */
  @Transactional
  public BankHolderDto updateHolder(
      @NotNull UUID holderId, @NotNull UpdateBankHolderRequest request) {
    BankHolder holder =
        holderRepository
            .findById(holderId)
            .orElseThrow(() -> new NotFoundException("Bank holder not found"));
    if (holder.getVersion() != null && !holder.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(BankHolder.class, holderId);
    }
    boolean wasActive = holder.isActive();
    holder.setActive(request.active());
    BankHolder saved = holderRepository.save(holder);
    if (wasActive != saved.isActive()) {
      bankAuditService.record(
          saved.isActive()
              ? BankAuditEventType.HOLDER_REACTIVATED
              : BankAuditEventType.HOLDER_DEACTIVATED,
          null,
          null,
          saved.getUser() != null ? saved.getUser().getId() : null,
          saved.getHandle());
    }
    BigDecimal total = postingRepository.holderTotal(holderId);
    return bankHolderMapper.toDto(saved, total, 0L);
  }
}
