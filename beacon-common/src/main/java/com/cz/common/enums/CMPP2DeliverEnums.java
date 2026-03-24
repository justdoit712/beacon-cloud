package com.cz.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author cz
 * @description
 */
@Getter
public enum CMPP2DeliverEnums {

    DELIVERED("DELIVRD","Message is delivered to destination"),
    EXPIRED("EXPIRED","Message validity period has expired"),
    DELETED("DELETED","Message has been deleted."),
    UNDELIVERABLE("UNDELIV","Message is undeliverable"),
    ACCEPTED("ACCEPTD","Message is in accepted state"),
    UNKNOWN("UNKNOWN","Message is in invalid state"),
    REJECTED("REJECTD","Message is in a rejected state"),
    ;

    private String stat;

    private String description;

    private static final Map<String, CMPP2DeliverEnums> BY_STAT = Collections.unmodifiableMap(
            Arrays.stream(values()).collect(Collectors.toMap(CMPP2DeliverEnums::getStat, Function.identity()))
    );


    CMPP2DeliverEnums(String stat, String description) {
        this.stat = stat;
        this.description = description;
    }

    public static String descriptionOf(String stat) {
        CMPP2DeliverEnums deliverEnum = BY_STAT.get(stat);
        return deliverEnum == null ? null : deliverEnum.description;
    }
}
