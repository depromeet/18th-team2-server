package com.team2.server.common.exception

class BusinessException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
