package com.cz.test.entity;

import java.util.Date;
import java.io.Serializable;

/**
 * 客户信息表(ClientBusiness)实体类
 *
 * @author makejava
 * @since 2025-12-11 14:56:19
 */
public class ClientBusiness implements Serializable {
    private static final long serialVersionUID = 231527973811464450L;

    private Object id;
/**
     * 公司名
     */
    private String corpname;
/**
     * HTTP接入的密码
     */
    private String apikey;
/**
     * HTTP客户端的IP白名单（多个用,隔开）
     */
    private String ipAddress;
/**
     * 状态报告是否返回：0 不返回 1 返回
     */
    private Integer isCallback;
/**
     * 客户接收状态报告的URL地址
     */
    private String callbackUrl;
/**
     * 联系人
     */
    private String clientLinkname;
/**
     * 密保手机
     */
    private String clientPhone;
/**
     * 策略校验顺序动态实现规则
     */
    private String clientFilters;
/**
     * 创建时间，默认系统时间
     */
    private Date created;
/**
     * 创建人id
     */
    private Long createId;
/**
     * 修改时间，默认系统时间
     */
    private Date updated;
/**
     * 修改人id
     */
    private Long updateId;
/**
     * 是否删除 0-未删除 ， 1-已删除
     */
    private Integer isDelete;
/**
     * 备用字段1
     */
    private String extend1;
/**
     * 备用字段2
     */
    private String extend2;
/**
     * 备用字段3
     */
    private String extend3;
/**
     * 备用字段4
     */
    private String extend4;


    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getCorpname() {
        return corpname;
    }

    public void setCorpname(String corpname) {
        this.corpname = corpname;
    }

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getIsCallback() {
        return isCallback;
    }

    public void setIsCallback(Integer isCallback) {
        this.isCallback = isCallback;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getClientLinkname() {
        return clientLinkname;
    }

    public void setClientLinkname(String clientLinkname) {
        this.clientLinkname = clientLinkname;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public String getClientFilters() {
        return clientFilters;
    }

    public void setClientFilters(String clientFilters) {
        this.clientFilters = clientFilters;
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

    public Integer getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Integer isDelete) {
        this.isDelete = isDelete;
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

