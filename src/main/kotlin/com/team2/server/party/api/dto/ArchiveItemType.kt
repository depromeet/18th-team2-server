package com.team2.server.party.api.dto

import com.team2.server.party.domain.entity.PartyOption

enum class ArchiveItemType {
    PARTY,
    PAPER,
    ;

    companion object {
        fun from(option: PartyOption): ArchiveItemType =
            when (option) {
                PartyOption.REALTIME -> PARTY
                PartyOption.PAPER_ONLY -> PAPER
            }
    }
}
