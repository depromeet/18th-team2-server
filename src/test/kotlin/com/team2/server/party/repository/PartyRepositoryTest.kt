package com.team2.server.party.repository

import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
class PartyRepositoryTest
    @Autowired
    constructor(
        private val partyRepository: PartyRepository,
    ) {
        private fun newParty(
            shareLink: String = "abc123",
            endedAt: LocalDateTime = LocalDateTime.now().plusDays(7),
        ) = Party(
            shareLink = shareLink,
            name = "생일파티",
            celebrantNickname = "홍길동",
            purpose = PartyPurpose.BIRTHDAY,
            option = PartyOption.REALTIME,
            startedAt = LocalDateTime.now(),
            endedAt = endedAt,
            isChattingAllow = true,
        )

        @Test
        fun `findByShareLink 매칭 파티 반환`() {
            val saved = partyRepository.save(newParty("link-1"))

            val found = partyRepository.findByShareLink("link-1")

            assertNotNull(found)
            assertEquals(saved.id, found.id)
        }

        @Test
        fun `findByShareLink 미매칭 시 null`() {
            val found = partyRepository.findByShareLink("no-such-link")
            assertNull(found)
        }
    }
