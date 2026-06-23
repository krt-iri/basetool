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

package de.greluc.krt.profit.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Join entity linking a {@link User} to an {@link OrgUnit} they belong to. Persisted in the {@code
 * org_unit_membership} table created by Flyway migration V95.
 *
 * <p>Why a dedicated entity rather than a plain {@code @ManyToMany} on {@link User}: the membership
 * carries its own per-link state (the role flags {@code is_logistician}, {@code
 * is_mission_manager}, {@code is_lead}, plus the {@code joined_at} timestamp and an optimistic-
 * lock {@code @Version} counter) that a pure join table cannot express. Once R2.b switches the
 * scoped-role authorisation onto these flags, the membership row becomes the source of truth for
 * "what may this user do in this org unit" — far more expressive than a flat {@code Set<OrgUnit>}.
 *
 * <p>Composite-key pattern: the primary key is the {@code (user_id, org_unit_id)} pair, expressed
 * via the {@link OrgUnitMembershipId} embeddable. The {@code @ManyToOne user} reference uses
 * {@code @MapsId("userId")} so Hibernate derives the {@code user_id} value from the related entity,
 * sparing the service layer from having to mirror the id into the embedded key by hand. The {@code
 * org_unit_id} half of the key is intentionally NOT a JPA relation — it is a plain UUID column —
 * because the {@code org_unit} table currently holds {@code kind = 'SQUADRON'} rows that have no
 * Java subclass in the inheritance hierarchy yet (Squadron still maps to the legacy {@code
 * squadron} table during the R2.a soak window). Resolving a SQUADRON-discriminated org-unit id
 * through Hibernate's polymorphic load would raise {@code WrongClassException}; treating it as an
 * opaque UUID keeps the membership row readable regardless of which kind it references. R2.b will
 * promote this to a proper {@code @ManyToOne OrgUnit} once Squadron joins the hierarchy.
 *
 * <p>The denormalised {@link #kind} column mirrors {@code org_unit.kind} so the partial unique
 * index "at most one Staffel membership per user" (V95: {@code uq_org_unit_membership_one_squadron}
 * on {@code (user_id) WHERE kind = 'SQUADRON'}) and the CHECK constraint "{@code is_lead} only on
 * SK memberships" (V95: {@code chk_org_unit_membership_lead_only_on_special_command}) can be
 * expressed without crossing tables. The BEFORE INSERT/UPDATE trigger {@code
 * sync_org_unit_membership_kind} keeps the value aligned with the referenced {@code org_unit.kind}
 * — application code MUST NOT write to this column directly, so it is mapped as {@code insertable =
 * false, updatable = false}. Hibernate reads it back from the trigger output via the RETURNING
 * clause Spring Data emits on insert.
 *
 * <p>This entity does not extend {@link AbstractEntity} because it owns a composite key rather than
 * a single UUID surrogate: {@link AbstractEntity}'s {@code @Id} contract is fundamentally
 * single-column. The audit columns ({@link #version}, {@link #createdAt}, {@link #updatedAt}) are
 * reproduced on this class directly. The Spring Data {@code Persistable#isNew} contract is left at
 * the JPA default (Hibernate determines persisted-vs-transient by checking whether the
 * {@code @Version} field is {@code null}).
 */
@Entity
@Table(name = "org_unit_membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrgUnitMembership {

  /**
   * Composite primary key combining {@link OrgUnitMembershipId#getUserId()} and {@link
   * OrgUnitMembershipId#getOrgUnitId()}. Created on insert (the service layer constructs the
   * embeddable with both UUIDs filled in); the {@code @ManyToOne user} reference's
   * {@code @MapsId("userId")} synchronises the {@code userId} half from the {@link User} entity, so
   * service code only has to set {@link #user} and the {@link #id}'s {@code orgUnitId} half.
   */
  @EmbeddedId private OrgUnitMembershipId id;

  /**
   * The user holding this membership. {@code @MapsId("userId")} ties the {@code user_id} column to
   * the {@link OrgUnitMembershipId#getUserId()} half of the composite key — Hibernate writes {@code
   * user_id} from {@link User#getId()} on insert and the embedded key picks it up automatically.
   * Lazy-fetched so listing memberships does not eagerly hydrate every user row.
   */
  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @ToString.Exclude
  private User user;

  /**
   * Denormalised discriminator of the referenced {@code org_unit} row, kept in sync by the V95
   * {@code sync_org_unit_membership_kind} trigger. Read-only at the JPA layer ({@code insertable =
   * false, updatable = false}) so application code cannot drift it out of sync with {@code
   * org_unit.kind}. Stored as a string ({@link EnumType#STRING}) so the column value reads the same
   * as the matching {@link OrgUnit#getKind()} discriminator across Flyway scripts, JPA, and the
   * CHECK constraints.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 32, insertable = false, updatable = false)
  private OrgUnitKind kind;

  /**
   * {@code true} when the membership grants the Logistician role within the referenced org unit.
   * Replaces the previous global {@code app_user.is_logistician} flag once R2.b switches the
   * authorisation layer onto scoped roles — until then, both columns coexist (the V95 backfill
   * copied the user-level flag onto every Staffel membership) and the global flag remains
   * authoritative.
   */
  @Column(name = "is_logistician", nullable = false)
  private boolean isLogistician = false;

  /**
   * {@code true} when the membership grants the Mission Manager role within the referenced org
   * unit. Same dual-write semantics as {@link #isLogistician}: copied from the global {@code
   * app_user.is_mission_manager} flag at V95 backfill time, but the global flag stays the source of
   * truth until R2.b.
   */
  @Column(name = "is_mission_manager", nullable = false)
  private boolean isMissionManager = false;

  /**
   * {@code true} when the membership grants the Spezialkommando Lead capability (managing the SK's
   * member list without requiring global ADMIN). The V95 CHECK constraint {@code
   * chk_org_unit_membership_lead_only_on_special_command} forbids this flag on a Squadron
   * membership — Staffeln already use the global {@code OFFICER} / {@code ADMIN} roles for member
   * administration, so a separate per-Staffel Lead would be redundant. Always {@code false} for
   * {@code kind = 'SQUADRON'} memberships.
   */
  @Column(name = "is_lead", nullable = false)
  private boolean isLead = false;

  /**
   * {@code true} when this membership makes the user the <em>Bereichsleiter</em> (area lead) of the
   * referenced Bereich (epic #692, REQ-ORG-017). Valid only on a {@code kind = 'BEREICH'}
   * membership (V164 CHECK {@code chk_org_unit_membership_bereich_flags_only_on_bereich}). Together
   * with {@link #isBereichskoordinator} / {@link #isBereichsoperator} it confers
   * officer-equivalent, cascading reach over every Staffel/SK of that Bereich (REQ-ORG-015) — never
   * admin rights. Always {@code false} for non-Bereich memberships.
   */
  @Column(name = "is_bereichsleiter", nullable = false)
  private boolean isBereichsleiter = false;

  /**
   * {@code true} when this membership makes the user a <em>Bereichskoordinator</em> of the
   * referenced Bereich (epic #692, REQ-ORG-017). Same Bereich-only CHECK and same cascading,
   * officer-equivalent reach over the Bereich's Staffeln/SKs as {@link #isBereichsleiter}. Always
   * {@code false} for non-Bereich memberships.
   */
  @Column(name = "is_bereichskoordinator", nullable = false)
  private boolean isBereichskoordinator = false;

  /**
   * {@code true} when this membership makes the user a <em>Bereichsoperator</em> of the referenced
   * Bereich (epic #692, REQ-ORG-017). Same Bereich-only CHECK and same cascading,
   * officer-equivalent reach over the Bereich's Staffeln/SKs as {@link #isBereichsleiter}. Always
   * {@code false} for non-Bereich memberships.
   */
  @Column(name = "is_bereichsoperator", nullable = false)
  private boolean isBereichsoperator = false;

  /**
   * {@code true} when this membership makes the user a member of the <em>Organisationsleitung</em>
   * (epic #692, REQ-ORG-017). Valid only on a {@code kind = 'ORGANISATIONSLEITUNG'} membership
   * (V164 CHECK {@code chk_org_unit_membership_ol_flag_only_on_ol}). Confers officer-equivalent,
   * cascading reach over <em>every</em> org unit (REQ-ORG-015) — never admin rights. Always {@code
   * false} for non-OL memberships.
   */
  @Column(name = "is_ol_member", nullable = false)
  private boolean isOlMember = false;

  /**
   * Unified leadership rank of this membership (epic #800, REQ-ROLE-001) — the eventual single
   * source of truth that supersedes the five mutually-exclusive boolean flags above. Kind-scoped by
   * the V184 {@code chk_org_unit_membership_role_kind} CHECK (squadron ranks only on {@code
   * SQUADRON}, area ranks only on {@code BEREICH}, {@link MembershipRole#OL_MEMBER} only on {@code
   * ORGANISATIONSLEITUNG}, {@link MembershipRole#SK_LEAD} only on {@code SPECIAL_COMMAND}).
   *
   * <p>During the additive soak (epic #800 Phase 1) the boolean flags remain authoritative and this
   * column is written in lockstep by {@code OrgUnitMembershipService}; it is consumed by the
   * authorisation layer only from Phase 2 onward. Defaults to {@link MembershipRole#MEMBER}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 40)
  private MembershipRole role = MembershipRole.MEMBER;

  /**
   * The Kommandogruppe this membership is assigned to (epic #800, REQ-ROLE-003), or {@code null}.
   * Non-null only for the squadron ranks {@link MembershipRole#KOMMANDOLEITER} / {@link
   * MembershipRole#STELLV_KOMMANDOLEITER} / {@link MembershipRole#ENSIGN}; an Ensign with a {@code
   * null} group is "allgemein der Staffelleitung". The {@code
   * chk_org_unit_membership_kommando_group_role} CHECK (V185) enforces this pairing. Lazy-fetched.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "kommando_group_id")
  @ToString.Exclude
  private KommandoGroup kommandoGroup;

  /**
   * Timestamp when the membership was granted. Backfilled by V95 from {@link User#getJoinDate()}
   * for pre-existing Staffel memberships; defaults to {@code now()} for new memberships at the DB
   * layer (the V95 column carries {@code DEFAULT NOW()}). Stored as {@link Instant} (UTC) per the
   * project convention "all times in UTC" from CLAUDE.md.
   */
  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  /**
   * Optimistic-lock counter so two admins concurrently editing the same membership row (e.g. one
   * flipping {@code is_logistician}, another flipping {@code is_mission_manager}) surface a 409
   * Conflict at flush time rather than silently losing one of the writes. Initialised to {@code 0L}
   * by the V95 backfill; Hibernate bumps it on every UPDATE.
   */
  @Version private Long version;

  /**
   * Insert-time audit timestamp populated by Hibernate's {@code @CreationTimestamp}. Mirrors the
   * pattern from {@link AbstractEntity#getCreatedAt()}; not inherited because this entity uses a
   * composite key rather than a single-UUID id.
   */
  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  /**
   * Last-update audit timestamp populated by Hibernate's {@code @UpdateTimestamp}. Mirrors the
   * pattern from {@link AbstractEntity#getUpdatedAt()}; not inherited because this entity uses a
   * composite key rather than a single-UUID id.
   */
  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
