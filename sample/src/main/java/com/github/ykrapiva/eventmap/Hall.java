package com.github.ykrapiva.eventmap;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class Hall implements Serializable {
    @JsonProperty("siteId")
    public long siteId;
    @JsonProperty("hallId")
    public long hallId;
    @JsonProperty("name")
    public String name;
    @JsonProperty("image")
    public String image;
    @JsonProperty("width")
    public int width;
    @JsonProperty("height")
    public int height;
    @JsonProperty("placeShape")
    public String placeShape;
    @JsonProperty("sectors")
    public List<Sector> sectors;

    @Override
    public String toString() {
        return "Hall{" +
                "siteId=" + siteId +
                ", hallId=" + hallId +
                ", name='" + name + '\'' +
                ", image='" + image + '\'' +
                ", width='" + width + '\'' +
                ", height='" + height + '\'' +
                ", numSectors=" + (sectors != null ? sectors.size() : 0) +
                '}';
    }

    public static class Sector implements Serializable {
        @JsonProperty("sectorId")
        public long sectorId;
        @JsonProperty("name")
        public String name;
        @JsonProperty("x")
        public int x;
        @JsonProperty("y")
        public int y;
        @JsonProperty("width")
        public int width;
        @JsonProperty("height")
        public int height;
        @JsonProperty("angle")
        public int angle;
        @JsonProperty("rows")
        public List<Row> rows;

        @Override
        public String toString() {
            return "Sector{" +
                    "sectorId=" + sectorId +
                    ", name='" + name + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    ", angle=" + angle +
                    ", numRows=" + (rows != null ? rows.size() : 0) +
                    '}';
        }
    }

    public static class Row implements Serializable {
        @JsonProperty("rowId")
        public long rowId;
        @JsonProperty("number")
        public int number;
        @JsonProperty("places")
        public List<Place> places;
    }

    public static class Place implements Serializable {
        @JsonProperty("placeId")
        public long placeId;
        @JsonProperty("number")
        public int number;
        @JsonProperty("seatCount")
        public int seatCount;
        @JsonProperty("status")
        public int status;
        @JsonProperty("x")
        public int x;
        @JsonProperty("y")
        public int y;
        @JsonProperty("width")
        public int width;
        @JsonProperty("height")
        public int height;
        @JsonProperty("prices")
        public List<Price> prices;

        @Override
        public String toString() {
            return "Place{" +
                    "placeId=" + placeId +
                    ", number=" + number +
                    ", seatCount=" + seatCount +
                    ", status=" + status +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    ", prices=" + prices +
                    '}';
        }
    }

    public static class Price implements Serializable {
        @JsonProperty("priceId")
        public int priceId;
        @JsonProperty("price")
        public BigDecimal price;
        @JsonProperty("color")
        public int color;
        @JsonProperty("name")
        public String name;

        @Override
        public String toString() {
            return "Price{" +
                    "priceId=" + priceId +
                    ", price=" + price +
                    ", color=" + color +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
