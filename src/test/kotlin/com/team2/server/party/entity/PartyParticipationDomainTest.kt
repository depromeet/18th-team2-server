package com.team2.server.party.entity

import com.team2.server.party.entity.RealtimeParty
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PartyParticipationDomainTest {
    @Test
    fun `participant는 회원 또는 파티 내 비회원 참여 관계를 표현한다`() {
        val party = newParty()
        val user = newUser()
        val memberParticipant = Participant(party = party, user = user, isCelebrant = true)
        val anonymousParticipant = Participant(party = party)

        memberParticipant.hasWrittenPaper = true

        assertSame(party, memberParticipant.party)
        assertSame(user, memberParticipant.user)
        assertTrue(memberParticipant.isCelebrant)
        assertTrue(memberParticipant.hasWrittenPaper)
        assertSame(party, anonymousParticipant.party)
        assertNull(anonymousParticipant.user)
        assertFalse(anonymousParticipant.isCelebrant)
    }

    @Test
    fun `실시간 참여 프로필은 닉네임과 캐릭터를 참여 관계와 분리해 가진다`() {
        val participant = Participant(party = newParty(), user = newUser())
        val character = Character(name = "character1")
        val profile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "입장닉네임",
            )

        profile.character = character
        profile.nickname = "변경닉네임"

        assertSame(participant, profile.participant)
        assertEquals("변경닉네임", profile.nickname)
        assertSame(character, profile.character)
    }

    @Test
    fun `롤링페이퍼는 작성자 참여 관계와 작성 당시 닉네임 스냅샷을 가진다`() {
        val party = newParty()
        val writer = Participant(party = party, user = newUser())
        val wrapper = RollingPaperWrapper(name = "기본테마")
        val rollingPaper =
            RollingPaper(
                theme = wrapper,
                writer = writer,
                party = party,
                writerNickname = "작성당시닉네임",
                content = "축하해요",
            )

        rollingPaper.isRead = true

        assertSame(wrapper, rollingPaper.theme)
        assertSame(writer, rollingPaper.writer)
        assertSame(party, rollingPaper.party)
        assertEquals("작성당시닉네임", rollingPaper.writerNickname)
        assertEquals("축하해요", rollingPaper.content)
        assertTrue(rollingPaper.isRead)
    }

    private fun newParty(): Party =
        RealtimeParty(
            ownerId = 1L,
            startedAt =
                java.time.LocalDateTime
                    .now()
                    .plusDays(1),
        )

    private fun newUser(): User =
        User(
            name = "유저",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "kakao-user",
            email = "user@kakao.local",
        )
}
