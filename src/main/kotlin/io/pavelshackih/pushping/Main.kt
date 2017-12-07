package io.pavelshackih.pushping

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.pavelshackih.pushping.model.PushEvent
import io.pavelshackih.pushping.model.PushEventType
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime

fun main(args: Array<String>) {
    val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val result = mapper.readValue(URL("http://push-ping.us-west-2.elasticbeanstalk.com/push?size=400"), Embedded::class.java)
    result._embedded?.push?.let { list ->
        list.forEach {
            val local = LocalDateTime.parse(it.dateTimeUi)
            it.dateTime = local
        }

        // Времена отправки и подтверждения в разрезе messageId
        val sentTimeMap = list.filter { it.eventType == PushEventType.SENT }.associateBy({ it.messageId }, { it.dateTime })
        val receivedTimeMap = list.filter { it.eventType == PushEventType.RECEIVED }.associateBy({ it.messageId }, { it.dateTime })

        var max: Duration = Duration.ZERO
        var min: Duration = Duration.ZERO
        val times = ArrayList<Duration>()

        sentTimeMap.forEach { messageId, sentTime ->
            val receivedTime = receivedTimeMap[messageId]
            receivedTime?.let {
                val tmp = Duration.between(sentTime, receivedTime)
                if (tmp > max) {
                    max = tmp
                }
                if (tmp < min) {
                    min = tmp
                }
                times.add(tmp)
            }
        }

        println("Max ${formatDuration(max)}")
        println("Min ${formatDuration(min)}")
        val middle = times.reduce({ acc, duration -> acc.plus(duration) }).dividedBy(times.size.toLong())
        println("Mid ${formatDuration(middle)}")
        times.forEach { println(formatDuration(it)) }
    }
}

fun formatDuration(duration: Duration): String {
    val seconds = duration.seconds
    val absSeconds = Math.abs(seconds)
    val positive = String.format(
            "%d:%02d:%02d",
            absSeconds / 3600,
            absSeconds % 3600 / 60,
            absSeconds % 60)
    return if (seconds < 0) "-" + positive else positive
}

data class Embedded(val _embedded: PushList? = null)

data class PushList(val push: List<PushEvent> = ArrayList())