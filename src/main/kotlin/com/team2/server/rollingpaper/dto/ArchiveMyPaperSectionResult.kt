package com.team2.server.rollingpaper.dto

data class ArchiveMyPaperSectionResult(
    val paperCount: Long,
    val myPaperWritten: Boolean,
    val myPaperContent: String?,
    val myPaperWriterNickname: String?,
    val myPaperToppingImageUrl: String?,
)
