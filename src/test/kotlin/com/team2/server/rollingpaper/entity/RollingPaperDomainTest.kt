package com.team2.server.rollingpaper.entity

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.RealtimeParty
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
        assertEquals("축하해요", rollingPaper.content)
        assertFalse(rollingPaper.isRead)
    }

    @Test
    fun `롤링페이퍼 작성 정보는 변경할 수 있다`() {
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
        rollingPaper.writerNickname = "변경작성자"
        rollingPaper.content = "다시 축하해요"
        rollingPaper.isRead = true

        assertSame(changedWrapper, rollingPaper.wrapper)
        assertSame(changedWriter, rollingPaper.writer)
        assertSame(changedParty, rollingPaper.party)
        assertEquals("변경작성자", rollingPaper.writerNickname)
        assertEquals("다시 축하해요", rollingPaper.content)
        assertTrue(rollingPaper.isRead)
    }

    private fun newParty(): Party =
        RealtimeParty(
            ownerId = 1L,
            startedAt = LocalDateTime.now().plusDays(1),
        )
}
