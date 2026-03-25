package com.cz.common.util;



import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

/**
 * 本地雪花 ID 生成器。
 *
 * <p>当前实现使用 41 位时间戳、5 位机器标识、5 位服务标识和 12 位序列号，
 * 依赖 machineId/serviceId 配置保证多节点不冲突。</p>
 *
 * @author cz
 */
@Component
@Slf4j
public class SnowFlakeUtil {

    /**
     * 自定义起始时间，缩短时间戳跨度并保留更长的可用年限。
     */
    private static final long TIME_START = 1668096000000L;

    /**
     * 机器id占用的bit位数
     */
    private static final long MACHINE_ID_BITS = 5L;

    /**
     * 服务id占用的bit位数
     */
    private static final long SERVICE_ID_BITS = 5L;

    /**
     * 序列占用的bit位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 计算出机器id的最大值
     */
    private static final long MAX_MACHINE_ID = -1 ^ (-1 << MACHINE_ID_BITS);

    /**
     * 计算出服务id的最大值
     */
    private static final long MAX_SERVICE_ID = -1 ^ (-1 << SERVICE_ID_BITS);

    /**
     * 服务id需要位移的位数
     */
    private static final long SERVICE_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 机器id需要位移的位数
     */
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS + SERVICE_ID_BITS;

    /**
     * 时间戳需要位移的位数
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + SERVICE_ID_BITS + MACHINE_ID_BITS;

    /**
     * 序列的最大值
     */
    private static final long MAX_SEQUENCE_ID = -1 ^ (-1 << SEQUENCE_BITS);

    /**
     * 机器id
     */
    @Value("${snowflake.machineId:0}")
    private long machineId;

    /**
     * 服务id
     */
    @Value("${snowflake.serviceId:0}")
    private long serviceId;

    /**
     * 序列
     */
    private long sequence = 0L;

    @PostConstruct
    public void init(){
        if(machineId < 0 || machineId > MAX_MACHINE_ID || serviceId < 0 || serviceId > MAX_SERVICE_ID){
            log.error("snowflake config out of range, machineId={}, serviceId={}, maxMachineId={}, maxServiceId={}",
                    machineId, serviceId, MAX_MACHINE_ID, MAX_SERVICE_ID);
            throw new ApiException(ExceptionEnums.SNOWFLAKE_OUT_OF_RANGE);
        }
    }

    /** 最近一次生成 ID 时使用的毫秒时间。 */
    private long lastTimestamp = -1;

    private long timeGen(){
        return System.currentTimeMillis();
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    public synchronized long nextId(){
        long timestamp = timeGen();
        if(timestamp < lastTimestamp){
            // 时钟回拨直接失败，避免生成重复 ID。
            log.error("snowflake clock moved backwards, currentTimestamp={}, lastTimestamp={}", timestamp, lastTimestamp);
            throw new ApiException(ExceptionEnums.SNOWFLAKE_TIME_BACK);
        }

        if(timestamp == lastTimestamp){
            // 同一毫秒内递增序列；序列耗尽后等待下一毫秒。
            sequence = (sequence + 1) & MAX_SEQUENCE_ID;
            if(sequence == 0){
                timestamp = tilNextMillis(lastTimestamp);
            }
        }else{
            // 新毫秒内从 0 开始。
            sequence = 0;
        }
        lastTimestamp = timestamp;

        // 组合时间、机器、服务和序列位，并限制为正数。
        long id = ((timestamp - TIME_START) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | (serviceId << SERVICE_ID_SHIFT)
                | sequence;
        return id & Long.MAX_VALUE;
    }
}
