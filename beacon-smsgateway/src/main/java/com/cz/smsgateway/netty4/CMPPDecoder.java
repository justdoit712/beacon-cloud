package com.cz.smsgateway.netty4;

import com.cz.smsgateway.netty4.entity.CmppActiveTestResp;
import com.cz.smsgateway.netty4.entity.CmppDeliver;
import com.cz.smsgateway.netty4.entity.CmppSubmitResp;
import com.cz.smsgateway.netty4.utils.Command;
import com.cz.smsgateway.netty4.utils.MsgUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.List;

/**
 * 中国移动给咱们响应信息时，通过当前Decoder接收并做数据的解析
 */
@Slf4j
public class CMPPDecoder extends ByteToMessageDecoder {



    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list){
        //字节数组
        byte[] buf = new byte[byteBuf.readableBytes()];
        //读取数据到字节数组
        byteBuf.readBytes(buf);

        //开始解析数据,先提取出长度字段标识长度的数据,也就是该条消息
        //4位 消息长度
        int totalLength = MsgUtils.bytesToInt(ArrayUtils.subarray(buf, 0, 4));
        //获取到该长度的字节数组
        byte[] bytes = ArrayUtils.subarray(buf, 0, totalLength);

        //获取到响应类型,也就是哪个接口的响应,4位
        int commandId = MsgUtils.bytesToInt(ArrayUtils.subarray(bytes, 4, 8));

        //连接请求响应
        switch (commandId) {
            case Command.CMPP_ACTIVE_TEST:
                log.debug("cmpp active test received, channelId={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), commandId);
                channelHandlerContext.writeAndFlush(new CmppActiveTestResp());
                break;
            case Command.CMPP_ACTIVE_TEST_RESP:
                log.debug("cmpp active test response received, channelId={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), commandId);
                break;
            case Command.CMPP_DELIVER:
                log.info("cmpp deliver received, channelId={}, totalLength={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), totalLength, commandId);
                CmppDeliver deliver=new CmppDeliver(bytes);
                list.add(deliver);
                break;
            case Command.CMPP_SUBMIT_RESP:
                log.info("cmpp submit response received, channelId={}, totalLength={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), totalLength, commandId);
                CmppSubmitResp submitResp=new CmppSubmitResp(bytes);
                list.add(submitResp);
                break;
            case Command.CMPP_QUERY_RESP:
                log.info("cmpp query response received, channelId={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), commandId);
                break;
            case Command.CMPP_CONNECT_RESP:
                log.info("cmpp connect response received, channelId={}, commandId={}",
                        channelHandlerContext.channel().id().asShortText(), commandId);
                //服务器端告诉客户端已接受你的连接
                break;
            default:
                log.warn("unsupported cmpp command, channelId={}, commandId={}, totalLength={}",
                        channelHandlerContext.channel().id().asShortText(), commandId, totalLength);
                break;
        }
        //list.add(commandId);
    }
}
