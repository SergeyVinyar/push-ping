package io.pavelshackih.pushping.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
data class PushEvent(
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        var id: Long? = 0,
        @ManyToOne
        var device: Device? = null,
        var dateTime: LocalDateTime = LocalDateTime.MIN,
        var eventType: PushEventType = PushEventType.EMPTY,
        var messageId: String = ""
)