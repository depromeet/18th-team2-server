package com.team2.server.party.domain.entity

import com.team2.server.common.persistence.BaseEntity
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

    abstract fun hostViewableAt(): LocalDateTime

    fun canHostViewRollingPapers(now: LocalDateTime): Boolean = !hostViewableAt().isAfter(now)

    /**
     * 주최자가 롤링페이퍼 오픈 안내를 확인한 시각. 아직 확인하지 않았으면 null.
     * "롤링페이퍼를 읽었다"가 아니라 "오픈 안내를 소진했다"는 뜻이라, 안내를 보지 않고 닫아도 기록된다.
     */
    @Column(name = "host_rolling_paper_notice_seen_at")
    var hostRollingPaperNoticeSeenAt: LocalDateTime? = null
        protected set

    /** 최초 확인 시각을 유지한다. */
    fun markHostRollingPaperNoticeSeen(now: LocalDateTime) {
        if (hostRollingPaperNoticeSeenAt == null) {
            hostRollingPaperNoticeSeenAt = now
        }
    }

    fun needsHostRollingPaperNotice(now: LocalDateTime): Boolean =
        canHostViewRollingPapers(now) && hostRollingPaperNoticeSeenAt == null

    fun endedAt(): LocalDateTime = startedAt.plusDays(ENDED_AFTER_DAYS)

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
