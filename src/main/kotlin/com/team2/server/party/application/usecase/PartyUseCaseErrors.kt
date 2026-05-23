package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode

internal fun throwPartyBusiness(errorCode: ErrorCode): Nothing = throw BusinessException(errorCode)
