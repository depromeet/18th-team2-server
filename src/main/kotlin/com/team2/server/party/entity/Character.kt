package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "avatar",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_avatar_name",
            columnNames = ["name"],
        ),
    ],
)
class Character(
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
