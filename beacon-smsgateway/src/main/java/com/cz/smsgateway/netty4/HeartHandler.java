package com.cz.smsgateway.netty4;


import com.cz.smsgateway.netty4.entity.CmppActiveTest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;


/**
 * 心跳Handler
 */
@Slf4j
public class HeartHandler extends ChannelInboundHandlerAdapter {

    private final NettyClient client;
    public HeartHandler(NettyClient client){
        this.client=client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("cmpp channel active, channelId={}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            IdleState state = event.state();
            if (state == IdleState.WRITER_IDLE || state == IdleState.ALL_IDLE) {
                client.submit(new CmppActiveTest());
                log.debug("cmpp heartbeat sent, channelId={}, state={}",
                        ctx.channel().id().asShortText(), state);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.warn("cmpp channel inactive, channelId={}, reconnecting...", ctx.channel().id().asShortText());
        client.reConnect(10);
    }
}
