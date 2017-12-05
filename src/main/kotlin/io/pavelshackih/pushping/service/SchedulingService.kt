package io.pavelshackih.pushping.service

import io.pavelshackih.pushping.data.DeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SchedulingService {

    private val logger = LoggerFactory.getLogger(SchedulingService::class.java)

    @Autowired
    lateinit var cssClient: CcsClient

    @Autowired
    lateinit var deviceRepository: DeviceRepository

    @Scheduled(cron = "0 30 22,23,0-10 * * *")
    fun sendPushes() {
        val list = deviceRepository.findAll()
        if (list.isNotEmpty()) {
            logger.info("Sending device to $list")
            cssClient.sendPush(list)
        } else {
            logger.info("No devices, skip sending pushes...")
        }
    }
}