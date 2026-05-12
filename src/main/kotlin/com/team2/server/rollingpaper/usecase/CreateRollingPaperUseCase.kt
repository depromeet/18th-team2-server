package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.rollingpaper.dto.CreateRollingPaperRequest
import com.team2.server.rollingpaper.dto.CreateRollingPaperResponse
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.rollingpaper.entity.toWriterNicknameKey
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CreateRollingPaperUseCase(
    private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val userRepository: UserRepository,
    private val participantService: ParticipantService,
    private val partyInviteService: PartyInviteService,
) {
    @Transactional
    fun create(
        inviteToken: String,
        userId: Long?,
        request: CreateRollingPaperRequest,
    ): CreateRollingPaperResponse {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = invite.party
        validatePartyWritable(party, now)

        val wrapper = findWrapper(request.requiredWrapperId())
        val participant = findOrCreateParticipant(party, userId)
        validateParticipantWritable(participant)

        val writerNickname = request.trimmedWriterNickname()
        val content = request.trimmedContent()
        validateWriterNicknameAvailable(party, writerNickname)

        val rollingPaper = saveRollingPaper(wrapper, participant, party, writerNickname, content)
        participant.hasWrittenPaper = true
        return CreateRollingPaperResponse(rollingPaperId = rollingPaper.id)
    }

    private fun validatePartyWritable(
        party: Party,
        now: LocalDateTime,
    ) {
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
    }

    private fun findWrapper(wrapperId: Long): RollingPaperWrapper =
        rollingPaperWrapperRepository.findByIdOrNull(wrapperId)
            ?: throw BusinessException(ErrorCode.ROLLING_PAPER_WRAPPER_NOT_FOUND)

    private fun validateParticipantWritable(participant: Participant) {
        if (participant.hasWrittenPaper) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_ALREADY_WRITTEN)
        }
    }

    private fun findOrCreateParticipant(
        party: Party,
        userId: Long?,
    ): Participant {
        if (userId == null) {
            return participantService.joinAnonymous(party)
        }

        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        return participantService.joinMember(party, user)
    }

    private fun validateWriterNicknameAvailable(
        party: Party,
        writerNickname: String,
    ) {
        if (rollingPaperRepository.existsByPartyAndWriterNicknameKey(party, writerNickname.toWriterNicknameKey())) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_NICKNAME_DUPLICATED)
        }
    }

    private fun saveRollingPaper(
        wrapper: RollingPaperWrapper,
        participant: Participant,
        party: Party,
        writerNickname: String,
        content: String,
    ): RollingPaper =
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
            when {
                e.isConstraintViolation("uk_rolling_paper_party_writer_nickname") ->
                    throw BusinessException(ErrorCode.ROLLING_PAPER_NICKNAME_DUPLICATED)
                e.isConstraintViolation("uk_rolling_paper_writer_participant") ->
                    throw BusinessException(ErrorCode.ROLLING_PAPER_ALREADY_WRITTEN)
                else -> throw e
            }
        }
}
