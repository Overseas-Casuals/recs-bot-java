package com.overseascasuals.recsbot.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class TCDay {
    private Date start;
    private Date updated;
    private boolean lock;
    private int popularity;
    private int predictedPopularity;
    private String appVersion;
    private List<ItemSupply> objects;

    public TCDay(){}

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getUpdated(){return updated; }

    public void setUpdated(Date updated) {this.updated = updated; }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public List<ItemSupply> getObjects() {
        return objects;
    }

    public void setObjects(List<ItemSupply> objects) {
        this.objects = objects;
    }

    public int getPredictedPopularity() {
        return predictedPopularity;
    }

    public void setPredictedPopularity(int predictedPopularity) {
        this.predictedPopularity = predictedPopularity;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("TCDay{").append("start=").append(start).append(", lock=").append(lock)
                .append(", popularity=").append(popularity)
                .append(", predictedPopularity=").append(predictedPopularity)
                .append(", appVersion='").append(appVersion).append('\'')
                .append(", objects=[");
        for(var obj : objects)
        {
            sb.append(obj).append(", ");
        }
        sb.setLength(sb.length()-2);
        sb.append("]}");
        return "TCDay{" +
                "start=" + start +
                ", lock=" + lock +
                ", popularity=" + popularity +
                ", predictedPopularity=" + predictedPopularity +
                ", appVersion='" + appVersion + '\'' +
                ", objects=" + objects +
                '}';
    }
}
