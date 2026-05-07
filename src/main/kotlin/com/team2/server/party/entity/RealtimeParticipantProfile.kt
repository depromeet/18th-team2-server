package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.security.SecureRandom

@Entity
@Table(
    name = "realtime_participant_profile",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_realtime_participant_profile_participant",
            columnNames = ["participant_id"],
        ),
        UniqueConstraint(
            name = "uk_realtime_participant_profile_token",
            columnNames = ["participant_token"],
        ),
    ],
)
class RealtimeParticipantProfile(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,
    @Column(name = "nickname", nullable = false, length = 20)
    var nickname: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    var character: Character? = null,
    @Column(name = "participant_token", nullable = false, length = 8)
    val participantToken: String = generateToken(),
) : BaseEntity() {
    companion object {
        private const val TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val random = SecureRandom()

        private const val TOKEN_LENGTH = 8

        private fun generateToken() = (1..TOKEN_LENGTH)
            .map { TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)] }
            .joinToString("")
    }
}
