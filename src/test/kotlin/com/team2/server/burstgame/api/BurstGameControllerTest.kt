package com.team2.server.burstgame.api

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.domain.BurstGameSession
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class BurstGameControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val sessionService: BurstGameSessionService,
        private val sessionStore: BurstGameSessionStore,
        private val candleBlowSessionStore: CandleBlowSessionStore,
        private val phaseStore: PartyPhaseStore,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        private val candleBlowDurationSeconds = 300L

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            sessionStore.clear()
            candleBlowSessionStore.clear()
            phaseStore.clear()
        }

        @Test
        fun `participantToken으로 촛불끄기 상태 조회 성공`() {
            val fixture = saveRealtimeParticipant(startedAt = activeCandleBlowStartedAt())

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/candle-blow") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value(200) }
                    jsonPath("$.data.partyId") { value(fixture.partyId) }
                    jsonPath("$.data.status") { value("ACTIVE") }
                    jsonPath("$.data.remainingCount") { doesNotExist() }
                    jsonPath("$.data.finishedReason") { doesNotExist() }
                    jsonPath("$.data.candles.length()") { value(9) }
                    jsonPath("$.data.candles[0].candleId") { value(1) }
                    jsonPath("$.data.candles[0].extinguished") { value(false) }
                }
        }

        @Test
        fun `participantToken으로 촛불 끄기 성공`() {
            val fixture = saveRealtimeParticipant(startedAt = activeCandleBlowStartedAt())

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/3") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value(200) }
                    jsonPath("$.data.partyId") { value(fixture.partyId) }
                    jsonPath("$.data.status") { value("ACTIVE") }
                    jsonPath("$.data.remainingCount") { doesNotExist() }
                    jsonPath("$.data.candles[2].candleId") { value(3) }
                    jsonPath("$.data.candles[2].extinguished") { value(true) }
                }
        }

        @Test
        fun `허용 범위 밖 candleId 요청은 400을 반환한다`() {
            val fixture = saveRealtimeParticipant(startedAt = activeCandleBlowStartedAt())

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/0") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("VALIDATION_ERROR") }
                }
        }

        @Test
        fun `이미 꺼진 촛불을 다시 꺼도 200 응답으로 현재 상태를 반환한다`() {
            val fixture = saveRealtimeParticipant(startedAt = activeCandleBlowStartedAt())

            blowCandle(fixture, candleId = 3)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/3") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.status") { value("ACTIVE") }
                    jsonPath("$.data.remainingCount") { doesNotExist() }
                    jsonPath("$.data.candles[2].extinguished") { value(true) }
                }
        }

        @Test
        fun `촛불끄기 시작 전 촛불 끄기 요청은 400을 반환한다`() {
            val fixture = saveRealtimeParticipant(startedAt = LocalDateTime.now().minusSeconds(10))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/1") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("CANDLE_BLOW_NOT_STARTED") }
                }
        }

        @Test
        fun `모든 촛불이 꺼진 뒤 촛불 끄기 요청은 200 응답으로 종료 상태를 반환한다`() {
            val fixture = saveRealtimeParticipant(startedAt = activeCandleBlowStartedAt())

            (1..CandleBlowPolicy.CANDLE_COUNT).forEach { candleId ->
                blowCandle(fixture, candleId)
            }

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/1") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.status") { value("FINISHED") }
                    jsonPath("$.data.remainingCount") { doesNotExist() }
                    jsonPath("$.data.finishedReason") { value("ALL_EXTINGUISHED") }
                }
        }

        @Test
        fun `종료 시각이 지난 촛불끄기 조회는 TIMEOUT 종료 상태를 반환한다`() {
            val fixture =
                saveRealtimeParticipant(
                    startedAt =
                        LocalDateTime
                            .now()
                            .minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + candleBlowDurationSeconds + 5),
                )

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/candle-blow") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.status") { value("FINISHED") }
                    jsonPath("$.data.remainingCount") { doesNotExist() }
                    jsonPath("$.data.finishedReason") { value("TIMEOUT") }
                }
        }

        @Test
        fun `CANDLE에서 BURST phase 전환 시 박터뜨리기 시작 성공`() {
            val fixture = saveRealtimeParticipant()

            prepareCandleBlowFinished(fixture)
            moveToCandle(fixture.partyId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"currentPhase":"CANDLE"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("BURST") }
                }

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/burst-game") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(fixture.partyId) }
                    jsonPath("$.data.myParticipantId") { value(fixture.participantId) }
                    jsonPath("$.data.totalTapCount") { doesNotExist() }
                    jsonPath("$.data.colorChanged") { value(false) }
                }
        }

        @Test
        fun `카운트다운 중 상태 조회는 실제 플레이 시간 기준 remainingSeconds를 반환한다`() {
            val fixture = saveRealtimeParticipant()
            startCountdownGame(fixture)

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/burst-game") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.ended") { value(false) }
                    jsonPath("$.data.remainingSeconds") { value(BurstGamePolicy.ROUND_DURATION_SECONDS) }
                }
        }

        @Test
        fun `카운트다운 중 터치 batch는 ROUND_NOT_STARTED로 무시한다`() {
            val fixture = saveRealtimeParticipant()
            startCountdownGame(fixture)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/burst-game/taps") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"tapCount":7,"clientSequence":1}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.accepted") { value(false) }
                    jsonPath("$.data.ignoredReason") { value("ROUND_NOT_STARTED") }
                    jsonPath("$.data.myTapCount") { value(0) }
                    jsonPath("$.data.stateVersion") { value(0) }
                }
        }

        @Test
        fun `촛불끄기가 끝나기 전 CANDLE에서 BURST phase 전환은 400을 반환한다`() {
            val fixture = saveRealtimeParticipant()
            moveToCandle(fixture.partyId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"currentPhase":"CANDLE"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("BURST_GAME_NOT_READY") }
                }
        }

        @Test
        fun `촛불 세션이 없어도 촛불 종료 시각이 지난 파티는 BURST phase 전환 시 박터뜨리기 시작 성공`() {
            val fixture =
                saveRealtimeParticipant(
                    startedAt =
                        LocalDateTime
                            .now()
                            .minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + candleBlowDurationSeconds + 5),
                )
            moveToCandle(fixture.partyId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"currentPhase":"CANDLE"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("BURST") }
                }

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/burst-game") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.myParticipantId") { value(fixture.participantId) }
                }
        }

        @Test
        fun `active 라운드가 있으면 start 재호출은 같은 party 상태를 반환한다`() {
            val fixture = saveRealtimeParticipant()

            prepareCandleBlowFinished(fixture)
            startGame(fixture)
            startGame(fixture)

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/burst-game") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(fixture.partyId) }
                    jsonPath("$.data.ended") { value(false) }
                    jsonPath("$.data.totalTapCount") { doesNotExist() }
                }
        }

        @Test
        fun `터치 batch 제출 후 진행 상태에 rankings를 반환한다`() {
            val fixture = saveRealtimeParticipant()
            prepareCandleBlowFinished(fixture)
            startPlayableGame(fixture)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/burst-game/taps") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"tapCount":7,"clientSequence":1}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.accepted") { value(true) }
                    jsonPath("$.data.ignoredReason") { doesNotExist() }
                    jsonPath("$.data.totalTapCount") { doesNotExist() }
                    jsonPath("$.data.myTapCount") { value(7) }
                    jsonPath("$.data.rankings[0].rank") { value(1) }
                    jsonPath("$.data.rankings[0].participantId") { value(fixture.participantId) }
                }
        }

        @Test
        fun `중복 clientSequence는 200 응답에서 DUPLICATE_SEQUENCE로 무시한다`() {
            val fixture = saveRealtimeParticipant()
            prepareCandleBlowFinished(fixture)
            startPlayableGame(fixture)
            submitTap(fixture, clientSequence = 1)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/burst-game/taps") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"tapCount":7,"clientSequence":1}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.accepted") { value(false) }
                    jsonPath("$.data.ignoredReason") { value("DUPLICATE_SEQUENCE") }
                    jsonPath("$.data.totalTapCount") { doesNotExist() }
                    jsonPath("$.data.myTapCount") { value(7) }
                }
        }

        @Test
        fun `종료 라운드 submit은 ROUND_ENDED와 빈 rankings를 반환한다`() {
            val fixture = saveRealtimeParticipant()
            prepareCandleBlowFinished(fixture)
            startPlayableGame(fixture)
            submitTap(fixture, clientSequence = 1)
            sessionService.end(fixture.partyId, LocalDateTime.now().plusSeconds(21))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/burst-game/taps") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"tapCount":7,"clientSequence":2}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.accepted") { value(false) }
                    jsonPath("$.data.ignoredReason") { value("ROUND_ENDED") }
                    jsonPath("$.data.totalTapCount") { doesNotExist() }
                    jsonPath("$.data.rankings") { isEmpty() }
                }
        }

        @Test
        fun `종료 상태 조회는 터치한 참가자 전체 rankings와 totalTapCount를 반환한다`() {
            val fixture = saveRealtimeParticipant()
            prepareCandleBlowFinished(fixture)
            startPlayableGame(fixture)
            submitTap(fixture, clientSequence = 1)

            sessionService.end(fixture.partyId, LocalDateTime.now().plusSeconds(21))

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/burst-game") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.ended") { value(true) }
                    jsonPath("$.data.totalTapCount") { value(7) }
                    jsonPath("$.data.rankings.length()") { value(1) }
                    jsonPath("$.data.rankings[0].participantId") { value(fixture.participantId) }
                    jsonPath("$.data.rankings[0].tapCount") { value(7) }
                    jsonPath("$.data.winners") { doesNotExist() }
                }
        }

        @Test
        fun `여러 참여자가 탭한 뒤 종료 결과는 터치한 참가자 전체 rankings를 반환한다`() {
            val fixtures = saveRealtimeParticipants()
            val firstParticipant = fixtures[0]
            val secondParticipant = fixtures[1]
            prepareCandleBlowFinished(firstParticipant)
            startPlayableGame(firstParticipant)
            submitTap(firstParticipant, tapCount = 7, clientSequence = 1)
            submitTap(secondParticipant, tapCount = 14, clientSequence = 1)

            sessionService.end(firstParticipant.partyId, LocalDateTime.now().plusSeconds(21))

            mockMvc
                .get("/api/v1/parties/${firstParticipant.partyId}/burst-game") {
                    header("X-Participant-Token", firstParticipant.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.ended") { value(true) }
                    jsonPath("$.data.totalTapCount") { value(21) }
                    jsonPath("$.data.rankings.length()") { value(2) }
                    jsonPath("$.data.rankings[0].participantId") { value(secondParticipant.participantId) }
                    jsonPath("$.data.rankings[0].tapCount") { value(14) }
                    jsonPath("$.data.rankings[1].participantId") { value(firstParticipant.participantId) }
                    jsonPath("$.data.rankings[1].tapCount") { value(7) }
                    jsonPath("$.data.winners") { doesNotExist() }
                }
        }

        private fun prepareCandleBlowFinished(fixture: BurstGameFixture) {
            (1..CandleBlowPolicy.CANDLE_COUNT).forEach { candleId ->
                blowCandle(fixture, candleId)
            }
        }

        private fun startGame(fixture: BurstGameFixture) {
            moveToCandle(fixture.partyId)
            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"currentPhase":"CANDLE"}"""
                }.andExpect {
                    status { isOk() }
                }
        }

        private fun moveToCandle(partyId: Long) {
            val now = LocalDateTime.now()
            phaseStore.advance(partyId, PartyPhase.ENTRY, PartyPhase.MUSIC, now)
            phaseStore.advance(partyId, PartyPhase.MUSIC, PartyPhase.CANDLE, now)
        }

        private fun startPlayableGame(fixture: BurstGameFixture) {
            val now = LocalDateTime.now()
            val startedAt = now.minusSeconds(1)
            sessionStore.start(fixture.partyId, now) {
                BurstGameSession(
                    partyId = fixture.partyId,
                    startedAt = startedAt,
                    endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
                )
            }
        }

        private fun startCountdownGame(fixture: BurstGameFixture) {
            val now = LocalDateTime.now()
            val startedAt = now.plusMinutes(1)
            sessionStore.start(fixture.partyId, now) {
                BurstGameSession(
                    partyId = fixture.partyId,
                    startedAt = startedAt,
                    endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
                )
            }
        }

        private fun submitTap(
            fixture: BurstGameFixture,
            tapCount: Int = 7,
            clientSequence: Long,
        ) {
            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/burst-game/taps") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-Participant-Token", fixture.participantToken)
                    content = """{"tapCount":$tapCount,"clientSequence":$clientSequence}"""
                }.andExpect {
                    status { isOk() }
                }
        }

        private fun blowCandle(
            fixture: BurstGameFixture,
            candleId: Int,
        ) {
            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/candle-blow/candles/$candleId") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                }
        }

        private fun saveRealtimeParticipant(
            startedAt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
        ): BurstGameFixture {
            val party =
                partyRepository.saveAndFlush(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "실시간 파티",
                        celebrantNickname = "주인공",
                        startedAt = startedAt,
                        hostEnteredAt = startedAt,
                    ),
                )
            val participant = participantRepository.saveAndFlush(Participant(party = party, isCelebrant = true))
            val profile =
                profileRepository.saveAndFlush(
                    RealtimeParticipantProfile(
                        participant = participant,
                        nickname = "토끼왕",
                        participantToken = "tokn0001",
                    ),
                )
            return BurstGameFixture(
                partyId = party.id,
                participantId = participant.id,
                participantToken = profile.participantToken,
            )
        }

        private fun activeCandleBlowStartedAt(): LocalDateTime =
            LocalDateTime.now().minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + 1L)

        private fun saveRealtimeParticipants(): List<BurstGameFixture> {
            val party =
                partyRepository.saveAndFlush(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "실시간 파티",
                        celebrantNickname = "주인공",
                        startedAt = LocalDateTime.now().minusMinutes(1),
                        hostEnteredAt = LocalDateTime.now().minusMinutes(1),
                    ),
                )
            return listOf(
                saveParticipant(party, nickname = "토끼왕", participantToken = "tokn0001"),
                saveParticipant(party, nickname = "곰돌이", participantToken = "tokn0002"),
            )
        }

        private fun saveParticipant(
            party: RealtimeParty,
            nickname: String,
            participantToken: String,
        ): BurstGameFixture {
            val participant = participantRepository.saveAndFlush(Participant(party = party))
            val profile =
                profileRepository.saveAndFlush(
                    RealtimeParticipantProfile(
                        participant = participant,
                        nickname = nickname,
                        participantToken = participantToken,
                    ),
                )
            return BurstGameFixture(
                partyId = party.id,
                participantId = participant.id,
                participantToken = profile.participantToken,
            )
        }

        private data class BurstGameFixture(
            val partyId: Long,
            val participantId: Long,
            val participantToken: String,
        )
    }
