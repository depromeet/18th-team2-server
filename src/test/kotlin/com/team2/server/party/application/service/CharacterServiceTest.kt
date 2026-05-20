package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.infrastructure.CharacterImageResolver
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@ExtendWith(MockitoExtension::class)
class CharacterServiceTest {
    @Mock
    lateinit var characterRepository: CharacterRepository

    @Mock
    lateinit var characterImageResolver: CharacterImageResolver

    @InjectMocks
    lateinit var service: CharacterService

    @Test
    fun `requireCharacter throws INVALID_INPUT when id is null`() {
        val e =
            assertThrows<BusinessException> {
                service.requireCharacter(null)
            }
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode)
    }

    @Test
    fun `requireCharacter throws CHARACTER_NOT_FOUND when id does not exist`() {
        whenever(characterRepository.findById(99L)).thenReturn(Optional.empty())

        val e =
            assertThrows<BusinessException> {
                service.requireCharacter(99L)
            }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `requireCharacter returns the character when found`() {
        val character = Character(name = "octopus")
        whenever(characterRepository.findById(1L)).thenReturn(Optional.of(character))

        val result = service.requireCharacter(1L)

        assertSame(character, result)
    }

    @Test
    fun `toResult populates image url from resolver and leaves thumbnail null`() {
        val character = Character(name = "octopus")
        whenever(characterImageResolver.resolve(character)).thenReturn("https://cdn/char.png")

        val result = service.toResult(character)

        assertEquals(character.id, result.characterId)
        assertEquals("octopus", result.name)
        assertEquals("https://cdn/char.png", result.characterImageUrl)
        assertNull(result.characterThumbnailImageUrl)
    }
}
