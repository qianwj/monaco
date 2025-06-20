package cn.elvis.monaco.transport;

import io.vertx.core.AbstractVerticle;
import io.vertx.mqtt.MqttServer;

import java.util.Map;

public class MqttServerDemo extends AbstractVerticle {

    @Override
    public void start() throws Exception {

        MqttServer mqttServer = MqttServer.create(vertx);
        mqttServer.endpointHandler(endpoint -> {

                    // shows main connect info
                    System.out.println("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

                    if (endpoint.auth() != null) {
                        System.out.println("[username = " + endpoint.auth().getUsername() + ", password = " + endpoint.auth().getPassword() + "]");
                    }
                    System.out.println("[properties = " + endpoint.connectProperties() + "]");

//                    if (endpoint.will() != null) {
//                        System.out.println("[will topic = " + endpoint.will().getWillTopic() + " msg = " + new String(endpoint.will().getWillMessageBytes()) +
//                                " QoS = " + endpoint.will().getWillQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
//                    }

                    System.out.println("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");


                    // accept connection from the remote client
                    endpoint.accept(false);

                })
                .listen(18083)
                .onComplete(ar -> {

                    if (ar.succeeded()) {

                        System.out.println("MQTT server is listening on port " + ar.result().actualPort());
                    } else {

                        System.out.println("Error on starting the server");
                        ar.cause().printStackTrace();
                    }
                });
    }
}
