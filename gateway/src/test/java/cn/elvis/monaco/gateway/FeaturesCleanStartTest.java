package cn.elvis.monaco.gateway;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ExtendWith(VertxExtension.class)
public class FeaturesCleanStartTest {

    @BeforeAll
    public static void init() {
        GatewayApplication.main(new String[0]);
    }

    @Test
    public void testCleanStartEnable(Vertx vertx, VertxTestContext testContext) throws TimeoutException {
        var previousOptions = new MqttClientOptions().setClientId("test").setCleanSession(false);
        MqttClient previous = MqttClient.create(vertx, previousOptions);
        var ack1 = previous.connect(18083, "localhost").await(1, TimeUnit.SECONDS);
        if (ack1.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            var options = new MqttClientOptions().setClientId("test");
            MqttClient client = MqttClient.create(vertx, options);
            var ack2 = client.connect(18083, "localhost").await(1, TimeUnit.SECONDS);
            if (ack2.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                previous.closeHandler(v -> {
                    testContext.completeNow();
                }).exceptionHandler(t -> {
                    System.out.println("abc");
                    t.printStackTrace();
                });
            } else {
                testContext.failNow(new AssertionError(ack2.code()));
            }
        } else {
            testContext.failNow(new AssertionError(ack1.code()));
        }

    }
}
