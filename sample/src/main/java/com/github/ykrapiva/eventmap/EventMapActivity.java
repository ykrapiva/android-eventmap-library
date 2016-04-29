package com.github.ykrapiva.eventmap;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykrapiva.eventmap.sample.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMapActivity extends Activity implements EventMapView.EventMapSeatTouchListener<Figure> {
    private static final String TAG = EventMapActivity.class.getSimpleName();
    private static final String BUNDLE_KEY_SELECTED_PLACES = "selected.place.ids";
    private static final int DEFAULT_SELECTED_PLACE_COLOR = 0xFF00FF00;
    private static final int DEFAULT_PRESSED_PLACE_COLOR = 0xFFFFFF00;
    private static final int DEFAULT_NOT_AVAILABLE_PLACE_COLOR = Color.TRANSPARENT;

    private EventMapView<Figure> mEventMapView;
    private EventMap<Figure> mEventMap;

    String mMapFileName = "sample_map.json";

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_map);

        mEventMap = loadEventMap();

        if (savedInstanceState != null) {
            List<Long> selectedPlaceIds = (List<Long>) savedInstanceState.getSerializable(BUNDLE_KEY_SELECTED_PLACES);
            for (Figure seat : mEventMap.getSeats()) {
                if (selectedPlaceIds.contains(seat.getPlaceId())) {
                    seat.setState(Figure.State.SELECTED);
                }
            }
        }

        mEventMapView = (EventMapView<Figure>) findViewById(R.id.event_map_view);
        mEventMapView.setBackgroundColor(Color.WHITE);
        mEventMapView.setEventMap(mEventMap);
        mEventMapView.setClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEventMapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEventMapView.onResume();
    }

    @Override
    public void onSeatClicked(Figure seat) {
        switch (seat.getState()) {
            case SELECTED:
                seat.resetState();
                break;
            case AVAILABLE:
            case PRESSED:
                seat.setState(Figure.State.SELECTED);
                break;
            case NOT_AVAILABLE:
                break;
        }

        mEventMapView.updateSeatColor(seat);
    }

    @Override
    public void onSeatPressed(Figure seat) {
        switch (seat.getState()) {
            case AVAILABLE:
                seat.setState(Figure.State.PRESSED);
                break;
        }
        mEventMapView.updateSeatColor(seat);
    }

    @Override
    public void onSeatUnPressed(Figure seat) {
        seat.resetState();
        mEventMapView.updateSeatColor(seat);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<Long> selectedPlaceIds = new ArrayList<Long>();
        for (Figure seat : mEventMap.getSeats()) {
            if (seat.getState() == Figure.State.SELECTED) {
                selectedPlaceIds.add(seat.getPlaceId());
            }
        }

        outState.putSerializable(BUNDLE_KEY_SELECTED_PLACES, selectedPlaceIds);
    }


    private EventMap<Figure> loadEventMap() {
        Hall hall;

        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getAssets().open(mMapFileName);
            List<Hall> halls = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, Hall.class));
            hall = halls.get(0);
            is.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        int mapWidth = hall.width;
        int mapHeight = hall.height;

        // No map dimensions provided ? calculate them
        if (mapWidth == 0 || mapHeight == 0) {
            for (Hall.Sector sector : hall.sectors) {
                for (Hall.Row row : sector.rows) {
                    for (Hall.Place place : row.places) {
                        Rect rect = new Rect(place.x, place.y, place.x + place.width, place.y + place.height);
                        mapWidth = Math.max(mapWidth, rect.right);
                        mapHeight = Math.max(mapHeight, rect.bottom);
                    }
                }
            }
        }

        EventMap<Figure> eventMap = new EventMap<Figure>(mapWidth, mapHeight);

        Map<Figure.State, Integer> colorMapBase = new HashMap<Figure.State, Integer>();
        colorMapBase.put(Figure.State.SELECTED, DEFAULT_SELECTED_PLACE_COLOR);
        colorMapBase.put(Figure.State.NOT_AVAILABLE, DEFAULT_NOT_AVAILABLE_PLACE_COLOR);

        for (Hall.Sector sector : hall.sectors) {
            for (Hall.Row row : sector.rows) {
                for (Hall.Place place : row.places) {
                    RectF rect = new RectF(place.x, place.y, place.x + place.width, place.y + place.height);

                    Map<Figure.State, Integer> colorMap = new HashMap<Figure.State, Integer>(colorMapBase);

                    if (place.prices == null || place.prices.isEmpty()) {
                        Log.w(TAG, "Place id " + place.placeId + " has no prices");
                        continue;
                    }
                    colorMap.put(Figure.State.AVAILABLE, 0xFF000000 | place.prices.get(0).color);
                    colorMap.put(Figure.State.PRESSED, DEFAULT_PRESSED_PLACE_COLOR);

                    Figure seat = new Figure(rect, colorMap, place.placeId, row.number, place.number, place.status == 0 ? Figure.State.AVAILABLE : Figure.State.NOT_AVAILABLE);
                    eventMap.add(seat);
                }
            }
        }


        // Load background image safely
        try {
            InputStream is = getAssets().open(hall.image);
            eventMap.setBackground(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return eventMap;
    }
}
