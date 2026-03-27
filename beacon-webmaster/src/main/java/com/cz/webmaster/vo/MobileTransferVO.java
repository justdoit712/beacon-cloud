package com.cz.webmaster.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MobileTransferVO {

    private Long id;

    @JsonProperty("transfernumber")
    private String transferNumber;

    @JsonProperty("areacode")
    private String areaCode;

    @JsonProperty("initisp")
    private Integer initIsp;

    @JsonProperty("nowisp")
    private Integer nowIsp;

    @JsonProperty("istransfer")
    private Byte isTransfer;

    private String extend1;
    private String extend2;
    private String extend3;
    private String extend4;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public void setTransferNumber(String transferNumber) {
        this.transferNumber = transferNumber;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public Integer getInitIsp() {
        return initIsp;
    }

    public void setInitIsp(Integer initIsp) {
        this.initIsp = initIsp;
    }

    public Integer getNowIsp() {
        return nowIsp;
    }

    public void setNowIsp(Integer nowIsp) {
        this.nowIsp = nowIsp;
    }

    public Byte getIsTransfer() {
        return isTransfer;
    }

    public void setIsTransfer(Byte isTransfer) {
        this.isTransfer = isTransfer;
    }

    public String getExtend1() {
        return extend1;
    }

    public void setExtend1(String extend1) {
        this.extend1 = extend1;
    }

    public String getExtend2() {
        return extend2;
    }

    public void setExtend2(String extend2) {
        this.extend2 = extend2;
    }

    public String getExtend3() {
        return extend3;
    }

    public void setExtend3(String extend3) {
        this.extend3 = extend3;
    }

    public String getExtend4() {
        return extend4;
    }

    public void setExtend4(String extend4) {
        this.extend4 = extend4;
    }
}
