package com.team2.server.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val httpStatus: HttpStatus,
    val message: String,
) {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "파티를 찾을 수 없습니다"),
    PARTY_FORBIDDEN(HttpStatus.FORBIDDEN, "파티에 대한 권한이 없습니다"),
    PARTY_INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대링크를 찾을 수 없습니다"),
    INVITE_LINK_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 초대링크입니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),

    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
    AUTH_OAUTH_FAILURE(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다"),
    AUTH_USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다"),

    PARTY_ENDED(HttpStatus.BAD_REQUEST, "이미 종료된 파티입니다"),
    PARTY_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 파티는 삭제할 수 없습니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 파티입니다"),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다"),
    CHARACTER_REQUIRED(HttpStatus.BAD_REQUEST, "채팅 허용 파티는 캐릭터 선택이 필수입니다"),
    CHARACTER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "채팅 비허용 파티는 캐릭터를 선택할 수 없습니다"),
    ROLLING_PAPER_TOPPING_NOT_FOUND(HttpStatus.NOT_FOUND, "롤링페이퍼 토핑을 찾을 수 없습니다"),
    ROLLING_PAPER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 롤링페이퍼 닉네임입니다"),
    ROLLING_PAPER_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 롤링페이퍼를 작성했습니다"),
    ROLLING_PAPER_NOT_VIEWABLE(HttpStatus.FORBIDDEN, "아직 롤링페이퍼를 확인할 수 없습니다"),
    ROLLING_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "롤링페이퍼를 찾을 수 없습니다"),
    PARTY_HOST_NICKNAME_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "주최자 닉네임은 변경할 수 없습니다"),
    PARTY_NOT_REALTIME(HttpStatus.BAD_REQUEST, "실시간 파티가 아닙니다"),
    PARTY_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
    CHAT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "채팅을 지원하지 않는 파티입니다"),
    CHAT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "현재 채팅이 활성화된 시간이 아닙니다"),
    REALTIME_PARTY_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 실시간 파티 상태에서는 요청할 수 없습니다"),
    REALTIME_PARTY_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 실시간 파티입니다"),
    BURST_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "박터뜨리기 라운드를 찾을 수 없습니다"),
    BURST_GAME_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 박터뜨리기 라운드입니다"),
    BURST_GAME_NOT_READY(HttpStatus.BAD_REQUEST, "박터뜨리기를 시작할 수 있는 상태가 아닙니다"),
    BURST_GAME_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "박터뜨리기 터치 요청이 너무 많습니다"),
    CANDLE_BLOW_NOT_STARTED(HttpStatus.BAD_REQUEST, "아직 촛불끄기를 시작할 수 없습니다"),

    KAKAO_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "카카오 재로그인이 필요합니다"),
    KAKAO_CALENDAR_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "톡캘린더 사용 동의가 필요합니다"),
    KAKAO_CALENDAR_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 톡캘린더 연동에 실패했습니다"),
    TALK_CALENDAR_PARTY_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 파티는 캘린더에 등록할 수 없습니다"),
    CALENDAR_REGISTRATION_IN_PROGRESS(HttpStatus.CONFLICT, "캘린더 등록이 이미 진행 중입니다"),
}
