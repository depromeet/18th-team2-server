package com.team2.server.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 카카오 API 두 호스트를 흉내내는 루프백 스텁.
 *
 * 테스트 프로파일의 `kakao.talk-calendar.base-url`(19595) 과 `kakao.auth.base-url`(19596) 이
 * 이 포트를 가리킨다. 여러 테스트 클래스가 공유하므로 참조 계수로 한 번만 열고 마지막에 닫는다.
 */
object KakaoStubServers {
    const val CALENDAR_PORT = 19595
    const val AUTH_PORT = 19596
    const val CREATE_EVENT_PATH = "/v2/api/calendar/create/event"
    const val UPDATE_EVENT_PATH = "/v2/api/calendar/update/event/host"

    /** 경로별 마지막 요청 바디. 카카오로 나간 페이로드를 검증할 때 쓴다. */
    val requests = ConcurrentHashMap<String, String>()

    /** 테스트가 카카오 회원번호를 바꿔 계정 불일치를 만들 수 있게 한다. 숫자여야 한다. */
    @Volatile
    var kakaoUserId: Long = 1L

    private var refCount = 0
    private var calendar: HttpServer? = null
    private var auth: HttpServer? = null

    @Synchronized
    fun start() {
        if (refCount++ > 0) return
        calendar =
            HttpServer.create(InetSocketAddress("127.0.0.1", CALENDAR_PORT), 0).apply {
                createContext(CREATE_EVENT_PATH) { respond(it, EVENT_BODY) }
                createContext(UPDATE_EVENT_PATH) { respond(it, EVENT_BODY) }
                createContext("/v1/user/access_token_info") { respond(it, "{\"id\":$kakaoUserId}") }
                start()
            }
        auth =
            HttpServer.create(InetSocketAddress("127.0.0.1", AUTH_PORT), 0).apply {
                createContext("/oauth/token") { respond(it, TOKEN_BODY) }
                start()
            }
    }

    @Synchronized
    fun stop() {
        if (--refCount > 0) return
        calendar?.stop(0)
        auth?.stop(0)
        calendar = null
        auth = null
    }

    fun reset() {
        requests.clear()
        kakaoUserId = 1L
    }

    private fun respond(
        exchange: HttpExchange,
        body: String,
    ) {
        requests[exchange.requestURI.path] = exchange.requestBody.readBytes().decodeToString()
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private const val EVENT_BODY = "{\"event_id\":\"stub-event-1\"}"
    private const val TOKEN_BODY =
        "{\"access_token\":\"stub-access\",\"expires_in\":21599," +
            "\"refresh_token\":\"stub-refresh\",\"refresh_token_expires_in\":5183999}"
}
