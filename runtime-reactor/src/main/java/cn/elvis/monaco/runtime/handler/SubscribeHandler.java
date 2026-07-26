package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.port.BrokerClock;
import reactor.core.publisher.Mono;

public class SubscribeHandler {

    private final CommandDispatcher dispatcher;
    private final BrokerClock clock;

    public SubscribeHandler(CommandDispatcher dispatcher, BrokerClock clock) {
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    public Mono<Void> handle(String clientId, ClientPacket.Subscribe packet) {
        var ref = ConnectionRef.local(clientId, 0);
        var command = new Command.Subscribe(packet, clientId, clock.now());
        var sessionCommand = SessionCommand.of(clientId, ref, command);
        return dispatcher.dispatch(sessionCommand).then();
    }
}
