package com.team2.server.party.domain.entity

import com.team2.server.common.persistence.BaseEntity
import com.team2.server.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "participant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_participant_party_user",
            columnNames = ["party_id", "user_id"],
        ),
    ],
)
class Participant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "is_celebrant", nullable = false)
    var isCelebrant: Boolean = false,
    @Column(name = "has_written_paper", nullable = false)
    var hasWrittenPaper: Boolean = false,
    hasLeft: Boolean = false,
) : BaseEntity() {
    @Column(name = "has_left", nullable = false)
    final var hasLeft: Boolean = hasLeft
        private set

    fun leave() {
        hasLeft = true
    }

    fun rejoin() {
        hasLeft = false
    }
}
