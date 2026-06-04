package com.overseascasuals.recsbot.mysql;

import com.overseascasuals.recsbot.data.Item;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cycle_crafts")
public class CycleCraft
{
    @EmbeddedId
    private CraftID craftID;

    private String craft1;
    private String craft2;
    private String craft3;
    private String craft4;
    private String craft5;
    private String craft6;

    private String subcraft1;
    private String subcraft2;
    private String subcraft3;
    private String subcraft4;
    private String subcraft5;
    private String subcraft6;


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

    public String getCraft6() {
        return craft6;
    }

    public void setCraft6(String craft6) {
        this.craft6 = craft6;
    }

    public String getSubcraft1() {
        return subcraft1;
    }

    public void setSubcraft1(String subcraft1) {
        this.subcraft1 = subcraft1;
    }

    public String getSubcraft2() {
        return subcraft2;
    }

    public void setSubcraft2(String subcraft2) {
        this.subcraft2 = subcraft2;
    }

    public String getSubcraft3() {
        return subcraft3;
    }

    public void setSubcraft3(String subcraft3) {
        this.subcraft3 = subcraft3;
    }

    public String getSubcraft4() {
        return subcraft4;
    }

    public void setSubcraft4(String subcraft4) {
        this.subcraft4 = subcraft4;
    }

    public String getSubcraft5() {
        return subcraft5;
    }

    public void setSubcraft5(String subcraft5) {
        this.subcraft5 = subcraft5;
    }

    public String getSubcraft6() {
        return subcraft6;
    }

    public void setSubcraft6(String subcraft6) {
        this.subcraft6 = subcraft6;
    }

    public List<Item> getCrafts()
    {
        List<Item> items = new ArrayList<>();
        var array = new String[]{craft1,craft2,craft3,craft4,craft5,craft6};
        for(int c=0; c<array.length; c++) {
            String name = array[c];
            if (name.isEmpty())
                break;

            Item item = Item.getEnum(name);

            items.add(item);
        }
        return items;
    }

    public List<Item> getSubcrafts()
    {
        List<Item> items = new ArrayList<>();
        var array = new String[]{subcraft1,subcraft2,subcraft3,subcraft4,subcraft5,subcraft6};
        for(int c=0; c<array.length; c++) {
            String name = array[c];
            if (name.isEmpty())
                break;
            Item item = Item.getEnum(name);
            items.add(item);
        }
        return items;
    }
    public void setCrafts(List<Item> crafts)
    {
        if(crafts.size()>0)
            craft1=crafts.get(0).toString();
        else
            craft1="";
        if(crafts.size()>1)
            craft2=crafts.get(1).toString();
        else
            craft2="";
        if(crafts.size()>2)
            craft3=crafts.get(2).toString();
        else
            craft3="";
        if(crafts.size()>3)
            craft4=crafts.get(3).toString();
        else
            craft4="";
        if(crafts.size()>4)
            craft5=crafts.get(4).toString();
        else
            craft5="";
        if(crafts.size()>5)
            craft6=crafts.get(5).toString();
        else
            craft6="";
    }

    public void setSubcrafts(List<Item> crafts)
    {
        if(crafts.size()>0)
            subcraft1=crafts.get(0).toString();
        else
            subcraft1="";
        if(crafts.size()>1)
            subcraft2=crafts.get(1).toString();
        else
            subcraft2="";
        if(crafts.size()>2)
            subcraft3=crafts.get(2).toString();
        else
            subcraft3="";
        if(crafts.size()>3)
            subcraft4=crafts.get(3).toString();
        else
            subcraft4="";
        if(crafts.size()>4)
            subcraft5=crafts.get(4).toString();
        else
            subcraft5="";
        if(crafts.size()>5)
            subcraft6=crafts.get(5).toString();
        else
            subcraft6="";
    }

    @Override
    public String toString() {
        return "CycleCraft{" +
                "craftID=" + craftID +
                ", crafts='" + getCrafts() +
                ", subcrafts='" + getSubcrafts() +
                '}';
    }
}
