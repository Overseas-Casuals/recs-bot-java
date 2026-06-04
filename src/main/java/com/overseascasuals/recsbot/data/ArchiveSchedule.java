package com.overseascasuals.recsbot.data;

import java.util.List;

public class ArchiveSchedule
{
    private List<Item> items;
    private List<Item> subItems;
    private int groovelessValue;
    private int value;
    private int startingGroove;

    public ArchiveSchedule(List<Item> items, List<Item> subItems, int groovelessValue, int value, int startingGroove) {
        this.items = items;
        this.subItems = subItems;
        this.groovelessValue = groovelessValue;
        this.value = value;
        this.startingGroove = startingGroove;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Item> getSubItems() {
        return subItems;
    }

    public void setSubItems(List<Item> subItems) {
        this.subItems = subItems;
    }

    public int getGroovelessValue() {
        return groovelessValue;
    }

    public void setGroovelessValue(int groovelessValue) {
        this.groovelessValue = groovelessValue;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getStartingGroove() {
        return startingGroove;
    }

    public void setStartingGroove(int startingGroove) {
        this.startingGroove = startingGroove;
    }
}
