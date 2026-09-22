package com.poppick.poppick.global.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

@Embeddable
class EntityStatus {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.ACTIVE

    @Column
    var deletedAt: LocalDateTime? = null

    fun delete() {
        status = Status.DELETED
        deletedAt = LocalDateTime.now()
    }

    fun toInactive() {
        status = Status.INACTIVE
    }
}
