package com.team2.server.rollingpaper.entity

import com.team2.server.common.entity.BaseEntity
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "rolling_paper")
class RollingPaper(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_id", nullable = false)
    var theme: RollingPaperWrapper,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_participant_id", nullable = false)
    var writer: Participant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    @Column(name = "writer_nickname", length = 20)
    var writerNickname: String? = null,
    @Column(nullable = false, length = 1000)
    var content: String,
    @Column(nullable = false, length = 32)
    var color: String,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
) : BaseEntity()
