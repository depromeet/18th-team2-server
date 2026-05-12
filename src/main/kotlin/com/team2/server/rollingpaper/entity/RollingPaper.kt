package com.team2.server.rollingpaper.entity

import com.team2.server.common.persistence.BaseEntity
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.Locale

@Entity
@Table(
    name = "rolling_paper",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_rolling_paper_party_writer_nickname",
            columnNames = ["party_id", "writer_nickname_key"],
        ),
        UniqueConstraint(
            name = "uk_rolling_paper_writer_participant",
            columnNames = ["writer_participant_id"],
        ),
    ],
)
class RollingPaper(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wrapper_id", nullable = false)
    var wrapper: RollingPaperWrapper,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_participant_id", nullable = false)
    var writer: Participant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    writerNickname: String,
    @Column(nullable = false, length = 100)
    var content: String,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
) : BaseEntity() {
    @Column(name = "writer_nickname", nullable = false, length = 10)
    var writerNickname: String = writerNickname.trim()
        protected set

    @Column(name = "writer_nickname_key", nullable = false, length = 10)
    var writerNicknameKey: String = this.writerNickname.toWriterNicknameKey()
        protected set
}

fun String.toWriterNicknameKey(): String = trim().lowercase(Locale.ROOT)
