package com.team2.server.common.exception

import org.springframework.dao.DataIntegrityViolationException

fun DataIntegrityViolationException.isConstraintViolation(constraintName: String): Boolean {
    val message =
        listOfNotNull(
            message,
            rootCause?.message,
            mostSpecificCause.message,
        ).joinToString(" ")
    return message.contains(constraintName, ignoreCase = true)
}
