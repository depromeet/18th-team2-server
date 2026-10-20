package com.team2.server.calendar.application.dto

/**
 * 동의 콜백에서 확보한 1회용 자격증명.
 * 비회원 경로는 이 값을 저장하지 않고 일정 생성에 쓴 뒤 버린다.
 */
data class KakaoConsentCredentials(
    val accessToken: String,
    val kakaoProviderId: String,
)
