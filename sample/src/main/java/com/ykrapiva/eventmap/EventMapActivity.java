package com.ykrapiva.eventmap;

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

public class EventMapActivity extends Activity implements EventMapView.EventMapClickListener<Seat> {
    private static final String TAG = EventMapActivity.class.getSimpleName();
    private static final String BUNDLE_KEY_SELECTED_PLACES = "selected.place.ids";
    private static final int DEFAULT_SELECTED_PLACE_COLOR = 0xFF00FF00;
    private static final int DEFAULT_NOT_AVAILABLE_PLACE_COLOR = Color.TRANSPARENT;

    private EventMapView<Seat> mEventMapView;
    private EventMap<Seat> mEventMap;

    String mMapFileName = "sample_map.json";

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_map);

        mEventMap = loadEventMap();

        if (savedInstanceState != null) {
            List<Long> selectedPlaceIds = (List<Long>) savedInstanceState.getSerializable(BUNDLE_KEY_SELECTED_PLACES);
            for (Seat seat : mEventMap.getSeats()) {
                if (selectedPlaceIds.contains(seat.getPlaceId())) {
                    seat.setState(Seat.State.SELECTED);
                }
            }
        }

        mEventMapView = (EventMapView<Seat>) findViewById(R.id.event_map_view);
        mEventMapView.setBackgroundColor(Color.WHITE);
        mEventMapView.setWorld(mEventMap);
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
    public void onSeatClicked(Seat seat) {
        switch (seat.getState()) {
            case SELECTED:
                seat.setState(Seat.State.AVAILABLE);
                break;
            case AVAILABLE:
                seat.setState(Seat.State.SELECTED);
            case NOT_AVAILABLE:
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<Long> selectedPlaceIds = new ArrayList<Long>();
        for (Seat seat : mEventMap.getSeats()) {
            if (seat.getState() == Seat.State.SELECTED) {
                selectedPlaceIds.add(seat.getPlaceId());
            }
        }

        outState.putSerializable(BUNDLE_KEY_SELECTED_PLACES, selectedPlaceIds);
    }


    private EventMap<Seat> loadEventMap() {
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

        EventMap<Seat> eventMap = new EventMap<Seat>(mapWidth, mapHeight);

        Map<Seat.State, Integer> colorMapBase = new HashMap<Seat.State, Integer>();
        colorMapBase.put(Seat.State.SELECTED, DEFAULT_SELECTED_PLACE_COLOR);
        colorMapBase.put(Seat.State.NOT_AVAILABLE, DEFAULT_NOT_AVAILABLE_PLACE_COLOR);

        for (Hall.Sector sector : hall.sectors) {
            for (Hall.Row row : sector.rows) {
                for (Hall.Place place : row.places) {
                    RectF rect = new RectF(place.x, place.y, place.x + place.width, place.y + place.height);

                    Map<Seat.State, Integer> colorMap = new HashMap<Seat.State, Integer>(colorMapBase);

                    if (place.prices == null || place.prices.isEmpty()) {
                        Log.w(TAG, "Place id " + place.placeId + " has no prices");
                        continue;
                    }
                    colorMap.put(Seat.State.AVAILABLE, 0xFF000000 | place.prices.get(0).color);

                    Seat seat = new Seat(rect, colorMap, place.placeId, row.number, place.number, place.status == 0 ? Seat.State.AVAILABLE : Seat.State.NOT_AVAILABLE);
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
