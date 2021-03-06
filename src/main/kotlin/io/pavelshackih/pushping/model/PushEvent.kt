package io.pavelshackih.pushping.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDateTime
import javax.persistence.*

@Entity
data class PushEvent(
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        var id: Long? = 0,
        @ManyToOne
        var device: Device? = null,
        @get:JsonIgnore
        var dateTime: LocalDateTime = LocalDateTime.MIN,
        var dateTimeUi: String = "",
        var eventType: PushEventType = PushEventType.EMPTY,
        var messageId: String = ""
)