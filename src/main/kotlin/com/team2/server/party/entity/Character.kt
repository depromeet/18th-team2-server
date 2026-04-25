package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "avatar")
class Character(
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
