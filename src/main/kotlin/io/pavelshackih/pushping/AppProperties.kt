package io.pavelshackih.pushping

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Component

@Component
@PropertySource("classpath:application.properties")
@ConfigurationProperties
class AppProperties {

    lateinit var fcmProjectSenderId: String
    lateinit var fcmServerKey: String
}