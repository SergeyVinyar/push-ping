package com.wedevol.xmpp.server;

import com.wedevol.xmpp.bean.CcsInMessage;
import com.wedevol.xmpp.service.PayloadProcessor;
import com.wedevol.xmpp.util.Util;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLSocketFactory;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sample Smack implementation of a client for FCM Cloud Connection Server. Most
 * of it has been taken more or less verbatim from Google's documentation:
 * https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref
 */
public class CcsClient implements PacketListener {

    private static final Logger logger = Logger.getLogger(CcsClient.class.getName());

    private static CcsClient sInstance = null;
    private XMPPConnection connection;
    private ConnectionConfiguration config;
    private String mApiKey = null;
    private String mProjectId = null;
    private boolean mDebuggable = false;
    private String fcmServerUsername = null;

    public static CcsClient getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("You have to prepare the client first");
        }
        return sInstance;
    }

    public static CcsClient prepareClient(String projectId, String apiKey, boolean debuggable) {
        synchronized (CcsClient.class) {
            if (sInstance == null) {
                sInstance = new CcsClient(projectId, apiKey, debuggable);
            }
        }
        return sInstance;
    }

    private CcsClient(String projectId, String apiKey, boolean debuggable) {
        this();
        mApiKey = apiKey;
        mProjectId = projectId;
        mDebuggable = debuggable;
        fcmServerUsername = mProjectId + "@" + Util.FCM_SERVER_CONNECTION;
    }

    private CcsClient() {
        // Add GcmPacketExtension
        ProviderManager.getInstance().addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
                (PacketExtensionProvider) parser -> {
                    String json = parser.nextText();
                    return new GcmPacketExtension(json);
                });
    }

    /**
     * Connects to FCM Cloud Connection Server using the supplied credentials
     */
    public void connect() throws XMPPException {
        config = new ConnectionConfiguration(Util.FCM_SERVER, Util.FCM_PORT);
        config.setSecurityMode(SecurityMode.enabled);
        config.setReconnectionAllowed(true);
        config.setRosterLoadedAtLogin(false);
        config.setSendPresence(false);
        config.setSocketFactory(SSLSocketFactory.getDefault());
        // Launch a window with info about packets sent and received
        config.setDebuggerEnabled(mDebuggable);

        connection = new XMPPConnection(config);
        connection.connect();

        connection.addConnectionListener(new ConnectionListener() {

            @Override
            public void reconnectionSuccessful() {
                logger.log(Level.INFO, "Reconnection successful ...");
                // TODO: handle the reconnecting successful
            }

            @Override
            public void reconnectionFailed(Exception e) {
                logger.log(Level.INFO, "Reconnection failed: ", e.getMessage());
                // TODO: handle the reconnection failed
            }

            @Override
            public void reconnectingIn(int seconds) {
                logger.log(Level.INFO, "Reconnecting in %d secs", seconds);
                // TODO: handle the reconnecting in
            }

            @Override
            public void connectionClosedOnError(Exception e) {
                logger.log(Level.INFO, "Connection closed on error");
                // TODO: handle the connection closed on error
            }

            @Override
            public void connectionClosed() {
                logger.log(Level.INFO, "Connection closed");
                // TODO: handle the connection closed
            }
        });

        // Handle incoming packets (the class implements the PacketListener)
        connection.addPacketListener(this, new PacketTypeFilter(Message.class));

        // Log all outgoing packets
        connection.addPacketInterceptor(packet ->
                logger.log(Level.INFO, "Sent: {0}", packet.toXML()), new PacketTypeFilter(Message.class));

        connection.login(fcmServerUsername, mApiKey);
        logger.log(Level.INFO, "Logged in: " + fcmServerUsername);
    }

    public void reconnect() {
        // Try to connect again using exponential back-off!
    }

    /**
     * Handles incoming messages
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processPacket(Packet packet) {
        logger.log(Level.INFO, "Received: " + packet.toXML());
        Message incomingMessage = (Message) packet;
        GcmPacketExtension gcmPacket = (GcmPacketExtension) incomingMessage.getExtension(Util.FCM_NAMESPACE);
        String json = gcmPacket.getJson();
        try {
            Map<String, Object> jsonMap = (Map<String, Object>) JSONValue.parseWithException(json);
            Object messageType = jsonMap.get("message_type");

            if (messageType == null) {
                CcsInMessage inMessage = MessageHelper.createCcsInMessage(jsonMap);
                handleUpstreamMessage(inMessage); // normal upstream message
                return;
            }

            switch (messageType.toString()) {
                case "ack":
                    handleAckReceipt(jsonMap);
                    break;
                case "nack":
                    handleNackReceipt(jsonMap);
                    break;
                case "receipt":
                    handleDeliveryReceipt(jsonMap);
                    break;
                case "control":
                    handleControlMessage(jsonMap);
                    break;
                default:
                    logger.log(Level.INFO, "Received unknown FCM message type: " + messageType.toString());
            }
        } catch (ParseException e) {
            logger.log(Level.INFO, "Error parsing JSON: " + json, e.getMessage());
        }

    }

    /**
     * Handles an upstream message from a device client through FCM
     */
    private void handleUpstreamMessage(CcsInMessage inMessage) {
        final String action = inMessage.getDataPayload().get(Util.PAYLOAD_ATTRIBUTE_ACTION);
        if (action != null) {
            PayloadProcessor processor = ProcessorFactory.getProcessor(action);
            processor.handleMessage(inMessage);
        }

        // Send ACK to FCM
        String ack = MessageHelper.createJsonAck(inMessage.getFrom(), inMessage.getMessageId());
        send(ack);
    }

    /**
     * Handles an ACK message from FCM
     */
    private void handleAckReceipt(Map<String, Object> jsonMap) {

    }

    /**
     * Handles a NACK message from FCM
     */
    private void handleNackReceipt(Map<String, Object> jsonMap) {
        String errorCode = (String) jsonMap.get("error");

        if (errorCode == null) {
            logger.log(Level.INFO, "Received null FCM Error Code");
            return;
        }

        switch (errorCode) {
            case "INVALID_JSON":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "BAD_REGISTRATION":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "DEVICE_UNREGISTERED":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "BAD_ACK":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "SERVICE_UNAVAILABLE":
                handleServerFailure(jsonMap);
                break;
            case "INTERNAL_SERVER_ERROR":
                handleServerFailure(jsonMap);
                break;
            case "DEVICE_MESSAGE_RATE_EXCEEDED":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "TOPICS_MESSAGE_RATE_EXCEEDED":
                handleUnrecoverableFailure(jsonMap);
                break;
            case "CONNECTION_DRAINING":
                handleConnectionDrainingFailure();
                break;
            default:
                logger.log(Level.INFO, "Received unknown FCM Error Code: " + errorCode);
        }
    }

    /**
     * Handles a Delivery Receipt message from FCM (when a device confirms that
     * it received a particular message)
     */
    private void handleDeliveryReceipt(Map<String, Object> jsonMap) {

    }

    /**
     * Handles a Control message from FCM
     */
    private void handleControlMessage(Map<String, Object> jsonMap) {
        String controlType = (String) jsonMap.get("control_type");

        if (controlType.equals("CONNECTION_DRAINING")) {
            handleConnectionDrainingFailure();
        } else {
            logger.log(Level.INFO, "Received unknown FCM Control message: " + controlType);
        }
    }

    private void handleServerFailure(Map<String, Object> jsonMap) {
        logger.log(Level.INFO, "Server error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));

    }

    private void handleUnrecoverableFailure(Map<String, Object> jsonMap) {
        logger.log(Level.INFO,
                "Unrecoverable error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));
    }

    private void handleConnectionDrainingFailure() {
        logger.log(Level.INFO, "FCM Connection is draining! Initiating reconnection ...");
    }

    /**
     * Sends a downstream message to FCM
     */
    public void send(String jsonRequest) {
        Packet request = new GcmPacketExtension(jsonRequest).toPacket();
        connection.sendPacket(request);
    }
}
