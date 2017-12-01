package com.wedevol.xmpp;

import com.wedevol.xmpp.bean.CcsOutMessage;
import com.wedevol.xmpp.server.CcsClient;
import com.wedevol.xmpp.server.MessageHelper;
import com.wedevol.xmpp.util.Util;
import org.jivesoftware.smack.XMPPException;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry Point class for the XMPP Server in dev mode for debugging and testing
 * purposes
 */
public class EntryPoint {
	public static void main(String[] args) {
		final String fcmProjectSenderId = "287332324256";
		final String fcmServerKey = "AAAAQuZXU6A:APA91bHfd7PY_8WnQngiYAvlogpj0cBwA4wO9NbjBwXhwZngLK1mEHNAE4nQs8EtYq6ww6SSqJL7SIW61NKHMrh2elPVjqZ5wR0K-QZfF4IdjtVvtwvm2EdLsgvNQj5Wc9btxpq5VyNy";
		final String toRegId = "eq7bgYS6_BY:APA91bF3lMcuGqYq0_ivJH49y-Idwu0q674LDkblPg6g-eHnQxU-5WYMw5_8daJ-Bv5C6lDxSwC5be6qkFezEez4wlmq4MVqe5SQbfCZYcen8WlzPr163WYdx59LH5FfsB3AjL5mj8Do";

		CcsClient ccsClient = CcsClient.prepareClient(fcmProjectSenderId, fcmServerKey, true);

		try {
			ccsClient.connect();
		} catch (XMPPException e) {
			e.printStackTrace();
		}

		// Send a sample downstream message to a device
		String messageId = Util.getUniqueMessageId();
		Map<String, String> dataPayload = new HashMap<>();
		dataPayload.put(Util.PAYLOAD_ATTRIBUTE_MESSAGE, "This is the simple sample message");
		CcsOutMessage message = new CcsOutMessage(toRegId, messageId, dataPayload);
		message.setDeliveryReceiptRequested(true);

		Map<String, String> notification = new HashMap<>();
		notification.put("body", "Сообщение, которое отрисовывают гугловые сервисы!");
		notification.put("title", "Заголовок сообщения!");
		notification.put("sound", "default");

		message.setNotificationPayload(notification);

		String jsonRequest = MessageHelper.createJsonOutMessage(message);
		ccsClient.send(jsonRequest);
	}
}
