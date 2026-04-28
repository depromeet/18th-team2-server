package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import com.team2.server.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "participant")
class Participant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    var character: Character? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "nickname")
    var nickname: String? = null,
    @Column(name = "is_celebrant", nullable = false)
    var isCelebrant: Boolean = false,
    @Column(name = "has_written_paper", nullable = false)
    var hasWrittenPaper: Boolean = false,
) : BaseEntity()
