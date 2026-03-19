package com.cz.webmaster.entity;

import java.util.Date;

/**
 * 客户余额实体。
 *
 * <p>对应表：{@code client_balance}</p>
 */
public class ClientBalance {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 客户 id，对应 {@code client_business.id}。
     */
    private Long clientId;

    /**
     * 余额，单位：厘。
     */
    private Long balance;

    /**
     * 创建时间。
     */
    private Date created;

    /**
     * 创建人 id。
     */
    private Long createId;

    /**
     * 更新时间。
     */
    private Date updated;

    /**
     * 更新人 id。
     */
    private Long updateId;

    /**
     * 删除标记，0 表示未删除，1 表示已删除。
     */
    private Byte isDelete;

    /**
     * 备用字段 1。
     */
    private String extend1;

    /**
     * 备用字段 2。
     */
    private String extend2;

    /**
     * 备用字段 3。
     */
    private String extend3;

    /**
     * 备用字段 4。
     */
    private String extend4;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
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
