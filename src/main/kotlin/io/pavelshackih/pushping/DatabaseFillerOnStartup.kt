package io.pavelshackih.pushping

import io.pavelshackih.pushping.data.DeviceRepository
import io.pavelshackih.pushping.data.PushEventRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Suppress("unused")
@Component
class DatabaseFillerOnStartup : ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    lateinit var deviceRepository: DeviceRepository

    @Autowired
    lateinit var pushEventRepository: PushEventRepository

    override fun onApplicationEvent(event: ContextRefreshedEvent?) {
    }
}