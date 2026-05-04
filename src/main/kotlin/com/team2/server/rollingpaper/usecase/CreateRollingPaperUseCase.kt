package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.rollingpaper.dto.CreateRollingPaperRequest
import com.team2.server.rollingpaper.dto.CreateRollingPaperResponse
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CreateRollingPaperUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
    private val participantRepository: ParticipantRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun create(
        inviteToken: String,
        userId: Long?,
        request: CreateRollingPaperRequest,
    ): CreateRollingPaperResponse {
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val now = LocalDateTime.now()
        if (!invite.expiresAt.isAfter(now)) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }

        val party = invite.party
        if (!now.isBefore(party.createdAt.plusDays(Party.ENDED_AFTER_DAYS))) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }

        val wrapper =
            rollingPaperWrapperRepository.findByIdOrNull(request.requiredWrapperId())
                ?: throw BusinessException(ErrorCode.ROLLING_PAPER_WRAPPER_NOT_FOUND)
        val participant = findOrCreateParticipant(party, userId)
        if (participant.hasWrittenPaper) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_ALREADY_WRITTEN)
        }

        val writerNickname = request.trimmedWriterNickname()
        val content = request.trimmedContent()
        if (isWriterNicknameDuplicated(party, writerNickname)) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_NICKNAME_DUPLICATED)
        }

        val rollingPaper =
            try {
                rollingPaperRepository.saveAndFlush(
                    RollingPaper(
                        wrapper = wrapper,
                        writer = participant,
                        party = party,
                        writerNickname = writerNickname,
                        content = content,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw e.toRollingPaperBusinessException()
            }

        participant.hasWrittenPaper = true
        return CreateRollingPaperResponse(rollingPaperId = rollingPaper.id)
    }

    private fun findOrCreateParticipant(
        party: Party,
        userId: Long?,
    ): Participant {
        if (userId == null) {
            return participantRepository.save(Participant(party = party))
        }

        val user =
            userRepository
                .findById(userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return participantRepository.findByPartyAndUser(party, user) ?: createMemberParticipant(party, user)
    }

    private fun createMemberParticipant(
        party: Party,
        user: User,
    ): Participant =
        try {
            participantRepository.saveAndFlush(
                Participant(
                    party = party,
                    user = user,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (!e.isConstraintViolation(PARTICIPANT_PARTY_USER_UNIQUE_CONSTRAINT)) {
                throw e
            }
            participantRepository.findByPartyAndUser(party, user) ?: throw e
        }

    private fun isWriterNicknameDuplicated(
        party: Party,
        writerNickname: String,
    ): Boolean = rollingPaperRepository.existsByPartyAndWriterNickname(party, writerNickname)

    private fun DataIntegrityViolationException.toRollingPaperBusinessException(): BusinessException =
        when {
            isConstraintViolation(ROLLING_PAPER_PARTY_WRITER_NICKNAME_UNIQUE_CONSTRAINT) ->
                BusinessException(ErrorCode.ROLLING_PAPER_NICKNAME_DUPLICATED)
            isConstraintViolation(ROLLING_PAPER_WRITER_PARTICIPANT_UNIQUE_CONSTRAINT) ->
                BusinessException(ErrorCode.ROLLING_PAPER_ALREADY_WRITTEN)
            else -> throw this
        }

    companion object {
        private const val PARTICIPANT_PARTY_USER_UNIQUE_CONSTRAINT = "uk_participant_party_user"
        private const val ROLLING_PAPER_PARTY_WRITER_NICKNAME_UNIQUE_CONSTRAINT =
            "uk_rolling_paper_party_writer_nickname"
        private const val ROLLING_PAPER_WRITER_PARTICIPANT_UNIQUE_CONSTRAINT =
            "uk_rolling_paper_writer_participant"
    }
}
