package com.overseascasuals.recsbot.mysql;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CycleCraft
{
    @EmbeddedId
    private CraftID craftID;
    private String craft1;
    private String craft2;
    private String craft3;
    private String craft4;
    private String craft5;

    public CraftID getCraftID() {
        return craftID;
    }

    public void setCraftID(CraftID craftID) {
        this.craftID = craftID;
    }

    public String getCraft1() {
        return craft1;
    }

    public void setCraft1(String craft1) {
        this.craft1 = craft1;
    }

    public String getCraft2() {
        return craft2;
    }

    public void setCraft2(String craft2) {
        this.craft2 = craft2;
    }

    public String getCraft3() {
        return craft3;
    }

    public void setCraft3(String craft3) {
        this.craft3 = craft3;
    }

    public String getCraft4() {
        return craft4;
    }

    public void setCraft4(String craft4) {
        this.craft4 = craft4;
    }

    public String getCraft5() {
        return craft5;
    }

    public void setCraft5(String craft5) {
        this.craft5 = craft5;
    }
}
