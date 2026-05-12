package com.team2.server.party.domain.entity

import com.team2.server.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "party_invite")
class PartyInvite(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    val party: Party,
    @Column(name = "token", nullable = false, length = 16, unique = true)
    val token: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,
) : BaseEntity()
