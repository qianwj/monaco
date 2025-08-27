package cn.elvis.monaco.entity.request;

import io.vertx.core.buffer.Buffer;

public record Request (

    /*
      A UTF-8 Encoded String which is used as the Topic Name for a response message.
     */
    String responseTopic,

    /*
      The Correlation Data is used by the sender of the Request Message to identify
      which request the Response Message is for when it is received.
     */
    Buffer correlationData
) {}
