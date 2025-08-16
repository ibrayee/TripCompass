let map;
let polylines = [];
let markers = [];

function initMap() {
    map = new google.maps.Map(document.getElementById('map'), {
        center: { lat: 48.8566, lng: 2.3522 },
        zoom: 4,
        mapId: 'tripcompass_map',
        streetViewControl: false,
        fullscreenControl: false
    });
}

function clearMap() {
    polylines.forEach(p => p.setMap(null));
    polylines = [];
    markers.forEach(m => m.setMap(null));
    markers = [];
}

document.getElementById('mashup-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    clearMap();
    const start = document.getElementById('start-city').value.trim();
    const end = document.getElementById('end-city').value.trim();
    const hotel = document.getElementById('hotel-name').value.trim();
    const placeType = document.getElementById('place-type').value.trim();
    const resultDiv = document.getElementById('results');
    resultDiv.innerHTML = '';

    if (start && end) {
        try {
            const res = await fetch(`/mashupJavalin/flightsAndPolyline?startPlace=${encodeURIComponent(start)}&endPlace=${encodeURIComponent(end)}`);
            if (res.ok) {
                const data = await res.json();
                drawPolyline(data.polyline, '#FF0000');
            } else {
                resultDiv.innerHTML += '<p>Failed to load flight route.</p>';
            }
        } catch (err) {
            console.error(err);
            resultDiv.innerHTML += '<p>Error loading flight route.</p>';
        }
    }

    if (placeType && (hotel || end)) {
        try {
            const query = hotel ? `hotelName=${encodeURIComponent(hotel)}` : `city=${encodeURIComponent(end)}`;
            const res = await fetch(`/mashupJavalin/hotelsAndSights?${query}&placeType=${encodeURIComponent(placeType)}`);
            if (res.ok) {
                const places = await res.json();
                const geocoder = new google.maps.Geocoder();
                places.forEach(place => {
                    const name = place['Name of place '];
                    const address = place['adress '];
                    geocoder.geocode({ address }, (results, status) => {
                        if (status === 'OK' && results[0]) {
                            const marker = new google.maps.Marker({
                                position: results[0].geometry.location,
                                map,
                                title: name
                            });
                            markers.push(marker);
                        }
                    });
                });
            } else {
                resultDiv.innerHTML += '<p>Failed to load nearby sights.</p>';
            }
        } catch (err) {
            console.error(err);
            resultDiv.innerHTML += '<p>Error loading nearby sights.</p>';
        }
    }

    if (start && hotel) {
        try {
            const res = await fetch(`/mashupJavalin/distToHotel?airport=${encodeURIComponent(start)}&hotel=${encodeURIComponent(hotel)}`);
            if (res.ok) {
                const data = await res.json();
                if (data.polyline) drawPolyline(data.polyline, '#0000FF');
                if (data.distance && data.duration) {
                    resultDiv.innerHTML += `<p>Airport to hotel: ${data.distance} (${data.duration})</p>`;
                }
            } else {
                resultDiv.innerHTML += '<p>Failed to load distance to hotel.</p>';
            }
        } catch (err) {
            console.error(err);
            resultDiv.innerHTML += '<p>Error loading distance to hotel.</p>';
        }
    }

    if (end) {
        try {
            const res = await fetch(`/mashupJavalin/distToAirport?city=${encodeURIComponent(end)}`);
            if (res.ok) {
                const data = await res.json();
                if (data.polyline) drawPolyline(data.polyline, '#00AA00');
                if (data.distance && data.duration) {
                    resultDiv.innerHTML += `<p>City to airport: ${data.distance} (${data.duration})</p>`;
                }
            } else {
                resultDiv.innerHTML += '<p>Failed to load distance to airport.</p>';
            }
        } catch (err) {
            console.error(err);
            resultDiv.innerHTML += '<p>Error loading distance to airport.</p>';
        }
    }
});

function decodePolyline(encoded) {
    let points = [];
    let index = 0, len = encoded.length;
    let lat = 0, lng = 0;

    while (index < len) {
        let b, shift = 0, result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlat = (result & 1) ? ~(result >> 1) : (result >> 1);
        lat += dlat;

        shift = 0;
        result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlng = (result & 1) ? ~(result >> 1) : (result >> 1);
        lng += dlng;

        points.push({ lat: lat / 1e5, lng: lng / 1e5 });
    }
    return points;
}

function drawPolyline(encoded, color) {
    const path = decodePolyline(encoded);
    const poly = new google.maps.Polyline({
        path,
        geodesic: true,
        strokeColor: color || '#FF0000',
        strokeOpacity: 0.7,
        strokeWeight: 4,
        map
    });
    polylines.push(poly);
}