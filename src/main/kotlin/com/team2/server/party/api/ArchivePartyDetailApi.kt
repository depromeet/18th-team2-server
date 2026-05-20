package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Archive", description = "보관함 API")
interface ArchivePartyDetailApi {
    @Operation(
        summary = "보관함 파티 상세 조회",
        description =
            """
            보관함에서 한 파티 상세를 조회한다. 본인이 작성한 롤페가 있으면 모달용 4개 필드가 함께 채워진다.
            PAPER_ONLY 파티는 participants/chatMessages 가 빈 배열, participantCount=0, chatHasMore=false 로 응답한다.
            """,
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "보관함 파티 상세 조회 성공")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun getArchivedPartyDetail(
        @Parameter(description = "파티 ID", required = true) partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse>
}
