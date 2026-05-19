package com.cz.webmaster.entity;

import java.util.Date;

/**
 * Mobile area entity mapped to {@code mobile_area}.
 */
public class MobileArea {

    private Long id;
    private String mobileNumber;
    private String mobileArea;
    private String mobileType;
    private String areaCode;
    private String postCode;
    private String provinceCode;
    private Date created;
    private Long createId;
    private Date updated;
    private Long updateId;
    private Byte isDelete;
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

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber == null ? null : mobileNumber.trim();
    }

    public String getMobileArea() {
        return mobileArea;
    }

    public void setMobileArea(String mobileArea) {
        this.mobileArea = mobileArea == null ? null : mobileArea.trim();
    }

    public String getMobileType() {
        return mobileType;
    }

    public void setMobileType(String mobileType) {
        this.mobileType = mobileType == null ? null : mobileType.trim();
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode == null ? null : areaCode.trim();
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        this.postCode = postCode == null ? null : postCode.trim();
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode == null ? null : provinceCode.trim();
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Long getCreateId() {
        return createId;
    }

    public void setCreateId(Long createId) {
        this.createId = createId;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public Byte getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Byte isDelete) {
        this.isDelete = isDelete;
    }

    public String getExtend1() {
        return extend1;
    }

    public void setExtend1(String extend1) {
        this.extend1 = extend1 == null ? null : extend1.trim();
    }

    public String getExtend2() {
        return extend2;
    }

    public void setExtend2(String extend2) {
        this.extend2 = extend2 == null ? null : extend2.trim();
    }

    public String getExtend3() {
        return extend3;
    }

    public void setExtend3(String extend3) {
        this.extend3 = extend3 == null ? null : extend3.trim();
    }

    public String getExtend4() {
        return extend4;
    }

    public void setExtend4(String extend4) {
        this.extend4 = extend4 == null ? null : extend4.trim();
    }
}
