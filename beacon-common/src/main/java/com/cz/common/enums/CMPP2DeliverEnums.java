package com.cz.common.enums;

import lombok.Getter;

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


    CMPP2DeliverEnums(String stat, String description) {
        this.stat = stat;
        this.description = description;
    }
}
