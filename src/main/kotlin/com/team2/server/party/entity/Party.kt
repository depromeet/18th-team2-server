package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.LocalDateTime

@Entity
@Table(name = "party")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "party_option",
    discriminatorType = DiscriminatorType.STRING,
    columnDefinition = "VARCHAR(31)",
)
abstract class Party(
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
    @Column(name = "name")
    var name: String? = null,
    @Column(name = "celebrant_nickname")
    var celebrantNickname: String? = null,
    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    var purpose: PartyPurpose = PartyPurpose.BIRTHDAY,
) : BaseEntity() {
    @get:Transient
    abstract val partyOption: PartyOption

    fun endedAt(): LocalDateTime = createdAt.plusDays(ENDED_AFTER_DAYS)

    fun isEnded(now: LocalDateTime = LocalDateTime.now()): Boolean = !endedAt().isAfter(now)

    companion object {
        const val ENDED_AFTER_DAYS: Long = 7
    }
}

enum class PartyOption {
    REALTIME,
    PAPER_ONLY,
}

enum class PartyPurpose {
    BIRTHDAY,
    JOB_CHANGE,
    WEDDING,
}
