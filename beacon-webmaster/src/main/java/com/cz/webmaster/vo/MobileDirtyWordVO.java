package com.cz.webmaster.vo;

public class MobileDirtyWordVO {

    private Long id;
    private String dirtyword;
    private Integer owntype;
    private String creater;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDirtyword() {
        return dirtyword;
    }

    public void setDirtyword(String dirtyword) {
        this.dirtyword = dirtyword;
    }

    public Integer getOwntype() {
        return owntype;
    }

    public void setOwntype(Integer owntype) {
        this.owntype = owntype;
    }

    public String getCreater() {
        return creater;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }
}
