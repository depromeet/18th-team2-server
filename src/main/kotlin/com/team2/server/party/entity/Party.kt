package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "party")
class Party(
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
    @Column(name = "background_image_url")
    var backgroundImageUrl: String? = null,
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null,
    @Column(name = "name")
    var name: String? = null,
    @Column(name = "celebrant_nickname")
    var celebrantNickname: String? = null,
    @Column(name = "is_chatting_allow")
    var isChattingAllow: Boolean = false,
    @Column(name = "paper_opened_at")
    var paperOpenedAt: LocalDateTime? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type")
    var option: PartyOption? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    var purpose: PartyPurpose? = null,
) : BaseEntity()

enum class PartyOption {
    REALTIME,
    PAPER_ONLY,
}

enum class PartyPurpose {
    BIRTHDAY,
    JOB_CHANGE,
    WEDDING,
}
