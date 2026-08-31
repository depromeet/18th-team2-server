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
    // 파티 생성 시점에 미리 만들어지는 호스트 참가자 row(PartyService.createRealtimeParty)만
    // false로 시작한다. 그 외에는 row 생성 자체가 곧 실제 입장이라 기본값이 true다.
    hasEntered: Boolean = true,
) : BaseEntity() {
    @Column(name = "has_left", nullable = false)
    final var hasLeft: Boolean = hasLeft
        private set

    @Column(name = "has_entered", nullable = false)
    final var hasEntered: Boolean = hasEntered
        private set

    fun leave() {
        hasLeft = true
    }

    fun rejoin() {
        hasLeft = false
    }

    fun enter() {
        hasEntered = true
    }
}
