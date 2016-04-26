package com.github.ykrapiva.eventmap;

import android.graphics.RectF;

import java.util.Map;

public class Figure extends EventMapFigure {
    private final Map<State, Integer> colorMap;
    private final long placeId;
    private final int rowNumber;
    private final int placeNumber;
    private final State originalState;
    private State currentState;

    public Figure(RectF coordinates, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        this(FigureType.RECTANGLE, coordinates, colorMap, placeId, rowNumber, placeNumber, state);
    }

    public Figure(float cx, float cy, float radius, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        this(FigureType.CIRCLE, new RectF(cx - radius, cy - radius, cx + radius, cy + radius), colorMap, placeId, rowNumber, placeNumber, state);
    }

    private Figure(FigureType viewType, RectF coordinates, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        super(viewType, coordinates, colorMap.get(state));
        this.colorMap = colorMap;
        this.placeId = placeId;
        this.rowNumber = rowNumber;
        this.placeNumber = placeNumber;
        this.currentState = state;
        this.originalState = state;

        setTitle(String.valueOf(placeNumber));
    }

    public void setState(State state) {
        this.currentState = state;
        setColor(colorMap.get(state));
    }

    public void resetState() {
        setState(originalState);
    }

    public State getState() {
        return currentState;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public int getPlaceNumber() {
        return placeNumber;
    }

    public long getPlaceId() {
        return placeId;
    }

    public enum State {
        AVAILABLE,
        SELECTED,
        NOT_AVAILABLE,
        PRESSED
    }
}
