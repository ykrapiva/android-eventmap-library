package com.github.ykrapiva.eventmap;

import android.graphics.RectF;
import com.github.ykrapiva.eventmap.EventMapSeat;

import java.util.Map;

public class Seat extends EventMapSeat {
    private final Map<State, Integer> colorMap;
    private final long placeId;
    private final int rowNumber;
    private final int placeNumber;
    private State state;

    public Seat(RectF coordinates, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        this(ViewType.RECTANGLE, coordinates, colorMap, placeId, rowNumber, placeNumber, state);
    }

    public Seat(float cx, float cy, float radius, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        this(ViewType.CIRCLE, new RectF(cx - radius, cy - radius, cx + radius, cy + radius), colorMap, placeId, rowNumber, placeNumber, state);
    }

    private Seat(ViewType viewType, RectF coordinates, Map<State, Integer> colorMap, long placeId, int rowNumber, int placeNumber, State state) {
        super(viewType, coordinates, colorMap.get(state));
        this.colorMap = colorMap;
        this.placeId = placeId;
        this.rowNumber = rowNumber;
        this.placeNumber = placeNumber;
        this.state = state;

        setCaption(String.valueOf(placeNumber));
    }

    public void setState(State state) {
        this.state = state;
        setColor(colorMap.get(state));
    }

    public State getState() {
        return state;
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
        NOT_AVAILABLE
    }
}
