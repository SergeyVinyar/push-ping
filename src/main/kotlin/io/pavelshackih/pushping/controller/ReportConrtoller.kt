package io.pavelshackih.pushping.controller

import io.pavelshackih.pushping.model.Device
import io.pavelshackih.pushping.model.PushEvent
import io.pavelshackih.pushping.model.PushEventType
import io.pavelshackih.pushping.service.PushEventService
import org.h2.util.DateTimeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.collections.ArrayList

@RestController
class ReportConrtoller {

    @Autowired
    lateinit var pushEventService: PushEventService

    @RequestMapping(path = arrayOf("/report"), method = arrayOf(RequestMethod.GET))
    @ResponseStatus(HttpStatus.OK)
    fun pushReport(): PushReport {
        val sentList = pushEventService.findByPushEventType(PushEventType.SENT)
        val sentCount = sentList.size

        val receivedList = pushEventService.findByPushEventType(PushEventType.RECEIVED)
        val receivedCount = receivedList.size

        // Процент подтверждений
        val percent: Double = if (sentCount == 0 || receivedCount == 0) 0.toDouble() else ((100 * receivedCount) / sentCount).toDouble()

        // Времена отправки и подтверждения в разрезе messageId
        val sentTimeMap = sentList.stream().collect(Collectors.toMap({event: PushEvent -> event.messageId}, {event: PushEvent -> event.dateTime}))
        val receivedTimeMap = receivedList.stream().collect(Collectors.toMap({event: PushEvent -> event.messageId}, {event: PushEvent -> event.dateTime}))

        // Считаем время до подтверждения (в секундах)
        val periodList = ArrayList<Double>()
        receivedTimeMap.forEach({
            if (sentTimeMap.containsKey(it.key)) {
                periodList.add(Duration.between(sentTimeMap[it.key], it.value).toMillis().toDouble() / 1000)
            }
        })

        // Считаем среднее время до подтверждения (в секундах)
        val averageSecs = (periodList.sum() / periodList.count())

        // Считаем среднеквадратичное отклоние
        val sigma = Math.sqrt(periodList.map { Math.pow((it - averageSecs), 2.toDouble()) }.sum() / periodList.count() )

        // Считаем диапазон времени доставки, в который значения попадают с 90% вероятностью в соотв-ии с распределением Гаусса (+/- 1.64 сигмы)
        val minSecs = averageSecs - 1.64 * sigma;
        val maxSecs = averageSecs + 1.64 * sigma;

        return PushReport(sentCount, receivedCount, percent, averageSecs, minSecs, maxSecs)
    }

    data class PushReport(val sent: Int, val received: Int, val percent: Double, val avrgSecs: Double, val minSecs: Double, val maxSecs: Double)

    @RequestMapping(path = arrayOf("/save"), method = arrayOf(RequestMethod.POST))
    @ResponseStatus(HttpStatus.OK)
    fun addDevice(@RequestParam("name") name: String,
                  @RequestParam("token") token: String,
                  @RequestParam("system") system: String): Device {
        return pushEventService.saveDevice(Device(0, name, token, system))
    }
}