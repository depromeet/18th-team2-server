package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.rollingpaper.dto.CreateRollingPaperRequest
import com.team2.server.rollingpaper.dto.CreateRollingPaperResponse
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
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
        val invite = findInvite(inviteToken)
        val now = LocalDateTime.now()
        validateInvite(invite, now)
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

    private fun findInvite(inviteToken: String): PartyInvite =
        partyInviteRepository.findByToken(inviteToken)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    private fun validateInvite(
        invite: PartyInvite,
        now: LocalDateTime,
    ) {
        if (!invite.expiresAt.isAfter(now)) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
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
            if (!e.isConstraintViolation("uk_participant_party_user")) {
                throw e
            }
            participantRepository.findByPartyAndUser(party, user) ?: throw e
        }

    private fun validateWriterNicknameAvailable(
        party: Party,
        writerNickname: String,
    ) {
        if (rollingPaperRepository.existsByPartyAndWriterNicknameIgnoreCase(party, writerNickname)) {
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
            throw e.toRollingPaperBusinessException()
        }

    private fun DataIntegrityViolationException.toRollingPaperBusinessException(): BusinessException =
        when {
            isConstraintViolation("uk_rolling_paper_party_writer_nickname") ->
                BusinessException(ErrorCode.ROLLING_PAPER_NICKNAME_DUPLICATED)
            isConstraintViolation("uk_rolling_paper_writer_participant") ->
                BusinessException(ErrorCode.ROLLING_PAPER_ALREADY_WRITTEN)
            else -> throw this
        }
}
