package com.team2.server.chat.entity

import com.team2.server.common.persistence.BaseEntity
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.RealtimeParticipantProfile
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "chat_message")
class ChatMessage(
    @Column(nullable = false, length = 1000)
    var content: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    var profile: RealtimeParticipantProfile,
) : BaseEntity()
