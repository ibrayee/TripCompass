let map;
let polylines = [];
let markers = [];
const PLACEHOLDER_IMG = 'https://via.placeholder.com/100x75?text=No+Image';

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
function normalizeHotel(hotel) {
    return {
        name: hotel.name || hotel.hotelName || hotel.hotel?.name || 'Unknown Hotel',
        address: hotel.address || hotel.vicinity || hotel.hotel?.address?.lines?.join(', ') || '',
        price: hotel.price || hotel.price?.total || hotel.offer?.price?.total || '',
        lat: hotel.lat || hotel.latitude || hotel.geoCode?.latitude || hotel.coordinates?.lat,
        lng: hotel.lng || hotel.longitude || hotel.geoCode?.longitude || hotel.coordinates?.lng,
        image: hotel.image || hotel.imageUrl || hotel.photo || (hotel.media && hotel.media[0]?.url) || ''
    };
}

function renderHotels(hotels) {
    let sidebar = document.getElementById('hotel-sidebar');
    if (!sidebar) {
        sidebar = document.createElement('div');
        sidebar.id = 'hotel-sidebar';
        sidebar.className = 'hotel-sidebar';
        document.querySelector('.container').appendChild(sidebar);
    }
    sidebar.innerHTML = '';

    hotels.forEach(h => {
        const hotel = normalizeHotel(h);

        const item = document.createElement('div');
        item.className = 'hotel-entry';

        const img = document.createElement('img');
        img.src = PLACEHOLDER_IMG;
        if (hotel.image) {
            const loader = new Image();
            loader.onload = () => { img.src = hotel.image; };
            loader.onerror = () => { img.src = PLACEHOLDER_IMG; };
            loader.src = hotel.image;
        }
        item.appendChild(img);

        const info = document.createElement('div');
        info.className = 'hotel-info';
        info.innerHTML = `
            <h3>${hotel.name}</h3>
            <p>${hotel.address}</p>
            ${hotel.price ? `<p class="price">${hotel.price}</p>` : ''}
            ${hotel.lat && hotel.lng ? `<p class="coords">${hotel.lat}, ${hotel.lng}</p>` : ''}
        `;
        item.appendChild(info);

        item.addEventListener('click', () => {
            if (hotel.lat && hotel.lng) {
                map.setCenter({ lat: parseFloat(hotel.lat), lng: parseFloat(hotel.lng) });
                map.setZoom(15);
            }
        });

        sidebar.appendChild(item);

        if (hotel.lat && hotel.lng) {
            const marker = new google.maps.Marker({
                position: { lat: parseFloat(hotel.lat), lng: parseFloat(hotel.lng) },
                map,
                title: hotel.name
            });
            markers.push(marker);
        }
    });
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
    const oldSidebar = document.getElementById('hotel-sidebar');
    if (oldSidebar) oldSidebar.remove();

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
                const data = await res.json();
                const hotels = Array.isArray(data) ? data : (data.hotels || []);
                if (hotels.length) {
                    renderHotels(hotels);
                }
                const sights = Array.isArray(data) ? [] : (data.sights || []);
                if (sights.length) {
                    const geocoder = new google.maps.Geocoder();
                    sights.forEach(place => {
                        const name = place['Name of place '] || place.name;
                        const address = place['adress '] || place.address;
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
                }
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
airportSelect.addEventListener('change', async (e) => {
    clearAirportRoute();
    const idx = e.target.value;
    if (idx === '') return;
    const airport = airportData[idx];
    if (!airport || !currentEnd) return;
    try {
        const res = await fetch(`/mashupJavalin/flightsAndPolyline?startPlace=${encodeURIComponent(airport.iata)}&endPlace=${encodeURIComponent(currentEnd)}`);
        if (res.ok) {
            const data = await res.json();
            const { poly, path } = drawPolyline(data.polyline, '#FFA500');
            airportRoutePolyline = poly;
            const mid = path[Math.floor(path.length / 2)];
            planeMarker = new google.maps.Marker({
                position: mid,
                map,
                label: '✈'
            });
            markers.push(planeMarker);
        }
    } catch (err) {
        console.error(err);
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
    return { poly, path };
}