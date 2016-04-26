package com.github.ykrapiva.eventmap;

import android.graphics.RectF;

public class EventMapFigure {
    private final FigureType figureType;
    private final RectF rect;
    private int color;
    private String title;

    public EventMapFigure(RectF coordinates) {
        this(FigureType.RECTANGLE, coordinates, 0);
    }

    public EventMapFigure(RectF coordinates, int color) {
        this(FigureType.RECTANGLE, coordinates, color);
    }

    public EventMapFigure(FigureType figureType, RectF coordinates, int color) {
        this.figureType = figureType;
        this.rect = coordinates;
        this.color = color;
    }

    public FigureType getFigureType() {
        return figureType;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}