package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "guest",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_guest_token_hash",
            columnNames = ["token_hash"],
        ),
    ],
)
class Guest(
    @Column(name = "token_hash", nullable = false, length = 128)
    var tokenHash: String,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: LocalDateTime,
) : BaseEntity()
