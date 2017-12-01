package io.pavelshackih.pushping

import io.pavelshackih.pushping.data.DeviceRepository
import io.pavelshackih.pushping.model.Device
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Component
class DatabaseFillerOnStartup : ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    lateinit var deviceRepository: DeviceRepository

    override fun onApplicationEvent(event: ContextRefreshedEvent?) {
    }
}