package io.pavelshackih.pushping.service

import io.pavelshackih.pushping.data.DeviceRepository
import io.pavelshackih.pushping.data.PushEventRepository
import io.pavelshackih.pushping.model.Device
import io.pavelshackih.pushping.model.PushEvent
import io.pavelshackih.pushping.model.PushEventType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PushEventService {

    @Autowired
    private lateinit var repository: PushEventRepository

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    fun proceedSent(messageId: String, device: Device) {
        val event = PushEvent(0, device, LocalDateTime.now(), PushEventType.SENT, messageId)
        repository.save(event)
    }

    fun proceedReceipt(messageId: String) {
        messageId.split(":").lastOrNull()?.let {
            repository.findByMessageId(it).firstOrNull()?.let {
                val event = PushEvent(null, it.device, LocalDateTime.now(), PushEventType.RECEIVED, messageId)
                repository.save(event)
            }
        }
    }

    fun findByPushEventType(pushEventType: PushEventType): List<PushEvent> = repository.findByEventType(pushEventType)

    fun saveDevice(device: Device): Device {
        val list = deviceRepository.findByToken(device.token)
        return if (list.isEmpty()) {
            deviceRepository.save(device)
        } else {
            list.first()
        }
    }
}