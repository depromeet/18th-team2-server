package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.common.web.swagger.ValidationErrorResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Archive", description = "보관함 API")
interface ArchiveApi {
    @Operation(
        summary = "보관함 리스트 조회",
        description = "사용자가 호스트로 만들었거나 참여한 파티 목록을 최신순으로 조회한다. 비로그인은 200 빈 응답을 반환한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "보관함 조회 성공")
    @ValidationErrorResponse
    @InternalServerErrorResponse
    fun getArchive(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "마지막으로 받은 항목의 id. 첫 페이지면 생략") cursor: Long?,
        @Parameter(description = "페이지 크기. 1~50, 기본 20") size: Int,
    ): ApiResponse<ArchiveListResponse>
}
