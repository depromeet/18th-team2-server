package com.team2.server.calendar.application.port

/**
 * 액세스 토큰이 어느 카카오 계정의 것인지 확인한다.
 * 동의를 시작한 사용자와 실제로 동의한 계정이 같은지 대조하는 데 쓴다.
 */
interface KakaoAccountPort {
    /** 카카오 회원번호. 확인할 수 없으면 null. */
    fun fetchProviderId(accessToken: String): String?
}
