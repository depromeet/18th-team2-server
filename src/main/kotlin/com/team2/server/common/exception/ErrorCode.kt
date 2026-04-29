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
    INVITE_LINK_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 초대링크입니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),

    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
    AUTH_OAUTH_FAILURE(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다"),
    AUTH_USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다"),

    PARTY_ENDED(HttpStatus.BAD_REQUEST, "이미 종료된 파티입니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 파티입니다"),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다"),
    CHARACTER_REQUIRED(HttpStatus.BAD_REQUEST, "채팅 허용 파티는 캐릭터 선택이 필수입니다"),
    CHARACTER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "채팅 비허용 파티는 캐릭터를 선택할 수 없습니다"),
}
