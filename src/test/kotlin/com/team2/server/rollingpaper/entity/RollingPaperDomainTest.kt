package com.team2.server.rollingpaper.entity

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RollingPaperDomainTest {
    @Test
    fun `롤링페이퍼는 작성자 닉네임과 읽음 상태를 가진다`() {
        val party = newParty()
        val writer = Participant(party = party)
        val wrapper = RollingPaperWrapper(name = "기본테마")
        val rollingPaper =
            RollingPaper(
                wrapper = wrapper,
                writer = writer,
                party = party,
                writerNickname = "작성자",
                content = "축하해요",
            )

        assertSame(wrapper, rollingPaper.wrapper)
        assertSame(writer, rollingPaper.writer)
        assertSame(party, rollingPaper.party)
        assertEquals("작성자", rollingPaper.writerNickname)
        assertEquals("작성자", rollingPaper.writerNicknameKey)
        assertEquals("축하해요", rollingPaper.content)
        assertFalse(rollingPaper.isRead)
    }

    @Test
    fun `롤링페이퍼 작성 정보는 닉네임 스냅샷을 제외하고 변경할 수 있다`() {
        val originalParty = newParty()
        val changedParty = newParty()
        val originalWriter = Participant(party = originalParty)
        val changedWriter = Participant(party = changedParty)
        val originalWrapper = RollingPaperWrapper(name = "기본테마")
        val changedWrapper = RollingPaperWrapper(name = "변경테마")
        val rollingPaper =
            RollingPaper(
                wrapper = originalWrapper,
                writer = originalWriter,
                party = originalParty,
                writerNickname = "작성자",
                content = "축하해요",
            )

        rollingPaper.wrapper = changedWrapper
        rollingPaper.writer = changedWriter
        rollingPaper.party = changedParty
        rollingPaper.content = "다시 축하해요"
        rollingPaper.isRead = true

        assertSame(changedWrapper, rollingPaper.wrapper)
        assertSame(changedWriter, rollingPaper.writer)
        assertSame(changedParty, rollingPaper.party)
        assertEquals("작성자", rollingPaper.writerNickname)
        assertEquals("작성자", rollingPaper.writerNicknameKey)
        assertEquals("다시 축하해요", rollingPaper.content)
        assertTrue(rollingPaper.isRead)
    }

    @Test
    fun `작성자 닉네임은 trim 후 정규화 키와 함께 저장된다`() {
        val party = newParty()
        val rollingPaper =
            RollingPaper(
                wrapper = RollingPaperWrapper(name = "기본테마"),
                writer = Participant(party = party),
                party = party,
                writerNickname = " ABC ",
                content = "축하해요",
            )

        assertEquals("ABC", rollingPaper.writerNickname)
        assertEquals("abc", rollingPaper.writerNicknameKey)
    }

    private fun newParty(): Party =
        RealtimeParty(
            ownerId = 1L,
            startedAt = LocalDateTime.now().plusDays(1),
        )
}
