package com.team2.server.rollingpaper.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateRollingPaperRequestTest {
    @Test
    fun `작성자 닉네임과 내용은 trim해서 반환한다`() {
        val request =
            CreateRollingPaperRequest(
                writerNickname = "  축하요정  ",
                content = "  생일 축하해!  ",
                toppingId = 1L,
            )

        assertEquals("축하요정", request.trimmedWriterNickname())
        assertEquals("생일 축하해!", request.trimmedContent())
    }

    @Test
    fun `toppingId는 필수 값으로 반환한다`() {
        val request =
            CreateRollingPaperRequest(
                writerNickname = "축하요정",
                content = "생일 축하해!",
                toppingId = 10L,
            )

        assertEquals(10L, request.requiredToppingId())
    }

    @Test
    fun `작성자 닉네임이 null이면 trim 반환에 실패한다`() {
        val request =
            CreateRollingPaperRequest(
                writerNickname = null,
                content = "생일 축하해!",
                toppingId = 1L,
            )

        assertFailsWith<IllegalArgumentException> {
            request.trimmedWriterNickname()
        }
    }

    @Test
    fun `내용이 null이면 trim 반환에 실패한다`() {
        val request =
            CreateRollingPaperRequest(
                writerNickname = "축하요정",
                content = null,
                toppingId = 1L,
            )

        assertFailsWith<IllegalArgumentException> {
            request.trimmedContent()
        }
    }

    @Test
    fun `toppingId가 null이면 필수 값 반환에 실패한다`() {
        val request =
            CreateRollingPaperRequest(
                writerNickname = "축하요정",
                content = "생일 축하해!",
                toppingId = null,
            )

        assertFailsWith<IllegalArgumentException> {
            request.requiredToppingId()
        }
    }
}
