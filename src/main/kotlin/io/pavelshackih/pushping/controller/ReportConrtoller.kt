package io.pavelshackih.pushping.controller

import io.pavelshackih.pushping.model.Device
import io.pavelshackih.pushping.model.PushEventType
import io.pavelshackih.pushping.service.PushEventService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class ReportConrtoller {

    @Autowired
    lateinit var pushEventService: PushEventService

    @RequestMapping(path = arrayOf("/report"), method = arrayOf(RequestMethod.GET))
    @ResponseStatus(HttpStatus.OK)
    fun pushReport(): PushReport {
        val sent = pushEventService.findByPushEventType(PushEventType.SENT).size
        val received = pushEventService.findByPushEventType(PushEventType.RECEIVED).size
        val percent: Double = if (sent == 0 || received == 0) 0.toDouble() else ((100 * received) / sent).toDouble()
        return PushReport(sent, received, percent)
    }

    data class PushReport(val sent: Int, val received: Int, val percent: Double)

    @RequestMapping(path = arrayOf("/save"), method = arrayOf(RequestMethod.POST))
    @ResponseStatus(HttpStatus.OK)
    fun addDevice(@RequestParam("name") name: String,
                  @RequestParam("token") token: String,
                  @RequestParam("system") system: String): Device {
        return pushEventService.saveDevice(Device(0, name, token, system))
    }
}