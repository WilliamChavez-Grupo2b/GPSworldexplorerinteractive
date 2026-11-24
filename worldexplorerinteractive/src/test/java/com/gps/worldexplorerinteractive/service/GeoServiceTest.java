package com.gps.worldexplorerinteractive.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeoServiceTest {

    @Test
    void autocompleteUsesGooglePlacesAndBiasesTowardOrigin() {
        RestClient.Builder placesBuilder = placesClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(placesBuilder).build();
        server.expect(requestTo("https://places.test/v1/places:autocomplete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "suggestions.placePrediction.placeId,suggestions.placePrediction.text.text"))
                .andExpect(content().json("""
                        {
                          "input":"Pasto",
                          "languageCode":"es",
                          "sessionToken":"session-1",
                          "locationBias":{"circle":{"center":{"latitude":4.711,"longitude":-74.0721},"radius":50000.0}}
                        }
                        """))
                .andRespond(withSuccess("""
                        {"suggestions":[
                          {"placePrediction":{"placeId":"place-id",
                           "text":{"text":"Pasto, Nariño, Colombia"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        GeoService geoService = geoService(placesBuilder, routesClientBuilder());

        var results = geoService.searchPlaces("Pasto", 4.711, -74.0721, "session-1");

        assertEquals(1, results.size());
        assertEquals("place-id", results.get(0).placeId());
        assertEquals("Pasto, Nariño, Colombia", results.get(0).label());
        server.verify();
    }

    @Test
    void placeDetailsResolveExactCoordinatesWithinAutocompleteSession() {
        RestClient.Builder placesBuilder = placesClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(placesBuilder).build();
        server.expect(requestTo("https://places.test/v1/places/place-id?sessionToken=session-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "id,displayName,formattedAddress,location"))
                .andRespond(withSuccess("""
                        {
                          "id":"place-id",
                          "displayName":{"text":"Pasto"},
                          "formattedAddress":"Pasto, Nariño, Colombia",
                          "location":{"latitude":1.2136,"longitude":-77.2811}
                        }
                        """, MediaType.APPLICATION_JSON));
        GeoService geoService = geoService(placesBuilder, routesClientBuilder());

        GeoService.GeoPoint result = geoService.resolvePlace("place-id", "session-1");

        assertEquals(1.2136, result.lat(), 0.000001);
        assertEquals(-77.2811, result.lon(), 0.000001);
        assertEquals("Pasto, Nariño, Colombia", result.label());
        server.verify();
    }

    @Test
    void routesApiDecodesGooglePolylineAndUsesTollPreference() {
        RestClient.Builder routesBuilder = routesClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(routesBuilder).build();
        server.expect(requestTo("https://routes.test/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"))
                .andExpect(content().json("""
                        {
                          "origin":{"location":{"latLng":{"latitude":1.2136,"longitude":-77.2811}}},
                          "destination":{"location":{"latLng":{"latitude":4.711,"longitude":-74.0721}}},
                          "travelMode":"DRIVE",
                          "routingPreference":"TRAFFIC_AWARE",
                          "routeModifiers":{"avoidTolls":true},
                          "languageCode":"es",
                          "units":"METRIC"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"routes":[{
                          "distanceMeters":120000,
                          "duration":"3600s",
                          "polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}
                        }]}
                        """, MediaType.APPLICATION_JSON));
        GeoService geoService = geoService(placesClientBuilder(), routesBuilder);

        GeoService.GeoRoute route = geoService.directions(
                new GeoService.GeoPoint(1.2136, -77.2811, "origin"),
                new GeoService.GeoPoint(4.711, -74.0721, "destination"),
                true);

        assertEquals(120, route.distanceKm(), 0.001);
        assertEquals(60, route.durationMin(), 0.001);
        assertEquals(3, route.geometry().size());
        assertEquals(-120.2, route.geometry().get(0).get(0), 0.00001);
        assertEquals(38.5, route.geometry().get(0).get(1), 0.00001);
        assertEquals(-126.453, route.geometry().get(2).get(0), 0.00001);
        assertEquals(43.252, route.geometry().get(2).get(1), 0.00001);
        server.verify();
    }

    @Test
    void placesPermissionErrorsExplainGoogleConfigurationWithoutExposingKey() {
        RestClient.Builder placesBuilder = placesClientBuilder();
        MockRestServiceServer.bindTo(placesBuilder).build()
                .expect(requestTo("https://places.test/v1/places:autocomplete"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        GeoService geoService = geoService(placesBuilder, routesClientBuilder());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> geoService.searchPlaces("Pasto", null, null, "session-1"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("API esté habilitada"));
        org.junit.jupiter.api.Assertions.assertFalse(error.getReason().contains("test-key"));
    }

    private RestClient.Builder placesClientBuilder() {
        return RestClient.builder()
                .baseUrl("https://places.test")
                .defaultHeader("X-Goog-Api-Key", "test-key");
    }

    private RestClient.Builder routesClientBuilder() {
        return RestClient.builder()
                .baseUrl("https://routes.test")
                .defaultHeader("X-Goog-Api-Key", "test-key");
    }

    private GeoService geoService(RestClient.Builder placesBuilder, RestClient.Builder routesBuilder) {
        return new GeoService(placesBuilder.build(), routesBuilder.build());
    }
}
