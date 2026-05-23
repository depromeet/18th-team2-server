package com.team2.server.common.exception

class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
) : RuntimeException(message)
