package com.github.ykrapiva.eventmap;

import android.graphics.RectF;

public class EventMapSeat {
    private final ViewType viewType;
    private final RectF rect;
    private int color;
    private String caption;

    public EventMapSeat(RectF coordinates) {
        this(ViewType.RECTANGLE, coordinates, 0);
    }

    public EventMapSeat(RectF coordinates, int color) {
        this(ViewType.RECTANGLE, coordinates, color);
    }

    public EventMapSeat(ViewType viewType, RectF coordinates, int color) {
        this.viewType = viewType;
        this.rect = coordinates;
        this.color = color;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public RectF getRect() {
        return rect;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }

    public enum ViewType {
        RECTANGLE,
        CIRCLE
    }
}