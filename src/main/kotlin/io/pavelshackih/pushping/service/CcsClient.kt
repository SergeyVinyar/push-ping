package io.pavelshackih.pushping.service

import com.wedevol.xmpp.bean.CcsInMessage
import com.wedevol.xmpp.bean.CcsOutMessage
import com.wedevol.xmpp.server.GcmPacketExtension
import com.wedevol.xmpp.server.MessageHelper
import com.wedevol.xmpp.server.ProcessorFactory
import com.wedevol.xmpp.util.Util
import io.pavelshackih.pushping.AppProperties
import io.pavelshackih.pushping.data.PushEventRepository
import io.pavelshackih.pushping.model.Device
import org.jivesoftware.smack.*
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
import org.jivesoftware.smack.filter.PacketTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Packet
import org.jivesoftware.smack.provider.PacketExtensionProvider
import org.jivesoftware.smack.provider.ProviderManager
import org.json.simple.JSONValue
import org.json.simple.parser.ParseException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.net.ssl.SSLSocketFactory

/**
 * Sample Smack implementation of a client for FCM Cloud Connection Server. Most
 * of it has been taken more or less verbatim from Google's documentation:
 * https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref
 */
@Component
class CcsClient private constructor() : PacketListener {

    private val logger = LoggerFactory.getLogger(CcsClient::class.java)

    @Autowired
    lateinit var appProperties: AppProperties

    @Autowired
    lateinit var pushEventService: PushEventService

    private val connection: XMPPConnection by lazy { connect() }

    init {
        // Add GcmPacketExtension
        ProviderManager.getInstance().addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
                PacketExtensionProvider { parser ->
                    val json = parser.nextText()
                    GcmPacketExtension(json)
                })
    }

    fun sendPush(list: List<Device>) {
        list.forEach {
            send(buildMessage(it))
        }
    }

    private fun buildMessage(device: Device): String {
        val localDateTime = LocalDateTime.now()
        val messageId = Util.getUniqueMessageId()
        val dataPayload = HashMap<String, String>()
        dataPayload.put(Util.PAYLOAD_ATTRIBUTE_MESSAGE, "Application push: $localDateTime")
        val message = CcsOutMessage(device.token, messageId, dataPayload)
        message.isDeliveryReceiptRequested = true

        val notification = mapOf(
                "body" to "System service notification: $localDateTime",
                "title" to "System service notification",
                "sound" to "default")

        message.notificationPayload = notification

        message.priority = "high"
        message.timeToLive = 90

        pushEventService.proceedSent(messageId, device)

        return MessageHelper.createJsonOutMessage(message)
    }

    /**
     * Connects to FCM Cloud Connection Server using the supplied credentials
     */
    @Throws(XMPPException::class)
    fun connect() : XMPPConnection {
        val config = ConnectionConfiguration(Util.FCM_SERVER, Util.FCM_PORT)
        config.securityMode = SecurityMode.enabled
        config.isReconnectionAllowed = true
        config.isRosterLoadedAtLogin = false
        config.setSendPresence(false)
        config.socketFactory = SSLSocketFactory.getDefault()

        val con = XMPPConnection(config)
        con.connect()

        con.addConnectionListener(object : ConnectionListener {

            override fun reconnectionSuccessful() {
                logger.info("Reconnection successful ...")
            }

            override fun reconnectionFailed(e: Exception) {
                logger.info("Reconnection failed: ", e.message)
            }

            override fun reconnectingIn(seconds: Int) {
                logger.info("Reconnecting in %d secs", seconds)
            }

            override fun connectionClosedOnError(e: Exception) {
                logger.info("Connection closed on error")
            }

            override fun connectionClosed() {
                logger.info("Connection closed")
            }
        })

        con.addPacketListener(this, PacketTypeFilter(Message::class.java))

        con.addPacketInterceptor(
                PacketInterceptor { packet -> logger.info("Sent: {}", packet.toXML()) },
                PacketTypeFilter(Message::class.java))

        con.login("${appProperties.fcmProjectSenderId}@${Util.FCM_SERVER_CONNECTION}",
                appProperties.fcmServerKey)

        return con
    }

    /**
     * Handles incoming messages
     */
    @Suppress("UNCHECKED_CAST")
    override fun processPacket(packet: Packet) {
        logger.info("Received: " + packet.toXML())
        val incomingMessage = packet as Message
        val gcmPacket = incomingMessage.getExtension(Util.FCM_NAMESPACE) as GcmPacketExtension
        val json = gcmPacket.json
        try {
            val jsonMap = JSONValue.parseWithException(json) as Map<String, Any>
            val messageType = jsonMap["message_type"]

            if (messageType == null) {
                val inMessage = MessageHelper.createCcsInMessage(jsonMap)
                handleUpstreamMessage(inMessage) // normal upstream message
                return
            }

            when (messageType.toString()) {
                "ack" -> handleAckReceipt(jsonMap)
                "nack" -> handleNackReceipt(jsonMap)
                "receipt" -> handleDeliveryReceipt(jsonMap)
                "control" -> handleControlMessage(jsonMap)
                else -> logger.info("Received unknown FCM message type: " + messageType.toString())
            }
        } catch (e: ParseException) {
            logger.info("Error parsing JSON: " + json, e.message)
        }

    }

    /**
     * Handles an upstream message from a device client through FCM
     */
    private fun handleUpstreamMessage(inMessage: CcsInMessage) {
        val action = inMessage.dataPayload[Util.PAYLOAD_ATTRIBUTE_ACTION]
        if (action != null) {
            val processor = ProcessorFactory.getProcessor(action)
            processor.handleMessage(inMessage)
        }

        // Send ACK to FCM
        val ack = MessageHelper.createJsonAck(inMessage.from, inMessage.messageId)
        send(ack)
    }

    /**
     * Handles an ACK message from FCM
     */
    private fun handleAckReceipt(jsonMap: Map<String, Any>) {

    }

    /**
     * Handles a NACK message from FCM
     */
    private fun handleNackReceipt(jsonMap: Map<String, Any>) {
        val errorCode = jsonMap["error"] as String?

        if (errorCode == null) {
            logger.info("Received null FCM Error Code")
            return
        }

        when (errorCode) {
            "INVALID_JSON" -> handleUnrecoverableFailure(jsonMap)
            "BAD_REGISTRATION" -> handleUnrecoverableFailure(jsonMap)
            "DEVICE_UNREGISTERED" -> handleUnrecoverableFailure(jsonMap)
            "BAD_ACK" -> handleUnrecoverableFailure(jsonMap)
            "SERVICE_UNAVAILABLE" -> handleServerFailure(jsonMap)
            "INTERNAL_SERVER_ERROR" -> handleServerFailure(jsonMap)
            "DEVICE_MESSAGE_RATE_EXCEEDED" -> handleUnrecoverableFailure(jsonMap)
            "TOPICS_MESSAGE_RATE_EXCEEDED" -> handleUnrecoverableFailure(jsonMap)
            "CONNECTION_DRAINING" -> handleConnectionDrainingFailure()
            else -> logger.info("Received unknown FCM Error Code: " + errorCode)
        }
    }

    /**
     * Handles a Delivery Receipt message from FCM (when a device confirms that
     * it received a particular message)
     */
    private fun handleDeliveryReceipt(jsonMap: Map<String, Any>) {
        pushEventService.proceedReceipt(jsonMap["message_id"] as String)
    }

    /**
     * Handles a Control message from FCM
     */
    private fun handleControlMessage(jsonMap: Map<String, Any>) {
        val controlType = jsonMap["control_type"] as String

        if (controlType == "CONNECTION_DRAINING") {
            handleConnectionDrainingFailure()
        } else {
            logger.info("Received unknown FCM Control message: " + controlType)
        }
    }

    private fun handleServerFailure(jsonMap: Map<String, Any>) {
        logger.info("Server error: " + jsonMap["error"] + " -> " + jsonMap["error_description"])

    }

    private fun handleUnrecoverableFailure(jsonMap: Map<String, Any>) {
        logger.info("Unrecoverable error: " + jsonMap["error"] + " -> " + jsonMap["error_description"])
    }

    private fun handleConnectionDrainingFailure() {
        logger.info("FCM Connection is draining! Initiating reconnection ...")
    }

    /**
     * Sends a downstream message to FCM
     */
    fun send(jsonRequest: String) {
        val request = GcmPacketExtension(jsonRequest).toPacket()
        connection.sendPacket(request)
    }
}
