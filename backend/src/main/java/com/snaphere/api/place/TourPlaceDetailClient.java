package com.snaphere.api.place;

public interface TourPlaceDetailClient {
    Detail load(String contentId, String languageCode);

    record Detail(String overview, String tel, String homepage, String useTime, String restDate) { }
}
