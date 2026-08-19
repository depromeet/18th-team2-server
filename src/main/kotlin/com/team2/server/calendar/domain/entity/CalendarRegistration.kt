package com.team2.server.calendar.domain.entity

import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "calendar_registration",
    uniqueConstraints = [
        UniqueConstraint(
            name = CalendarRegistration.UK_USER_PARTY_PROVIDER,
            columnNames = ["user_id", "party_id", "provider"],
        ),
    ],
)
class CalendarRegistration(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "party_id", nullable = false)
    val partyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: CalendarProvider,
    eventId: String? = null,
) : BaseEntity() {
    @Column(name = "event_id", length = 100)
    final var eventId: String? = eventId
        private set

    fun linkEvent(eventId: String) {
        this.eventId = eventId
    }

    companion object {
        const val UK_USER_PARTY_PROVIDER = "uk_calendar_registration_user_party_provider"
    }
}
