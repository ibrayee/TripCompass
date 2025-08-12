let map, marker, userCoords = null;
let currentMode = 'trip';
let infoWindow;

function setMode(mode) {
    currentMode = mode;
    document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById(`mode-${mode}`).classList.add('active');
}

document.getElementById('mode-flights').addEventListener('click', () => setMode('flights'));
document.getElementById('mode-hotels').addEventListener('click', () => setMode('hotels'));
document.getElementById('mode-trip').addEventListener('click', () => setMode('trip'));
/* Called by Google Maps API */
function initMap() {
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome

    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
        mapId: "bf198408fe296ef1"
    });
    infoWindow = new google.maps.InfoWindow();

    // Geolocalizza utente al load
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            (position) => {
                userCoords = {
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                };
                console.log("User location:", userCoords);
                map.setCenter(userCoords);
                map.setZoom(10);

                new google.maps.Marker({
                    position: userCoords,
                    map: map,
                    title: "You are here!",
                    icon: {
                        path: google.maps.SymbolPath.CIRCLE,
                        scale: 8,
                        fillColor: "#4285F4",
                        fillOpacity: 1,
                        strokeWeight: 2,
                        strokeColor: "#fff"
                    }
                })
            },
            () => alert("Geolocation failed.")
        );
    }

    // Click sulla mappa = destinazione
    map.addListener("click", (e) => {
        if (!userCoords) {
            alert("User location not available yet.");
            return;
        }
        requestTripInfo(e.latLng.lat(), e.latLng.lng(), userCoords.lat, userCoords.lng);    });
}

function requestTripInfo(destLat, destLng, originLat, originLng) {    if (marker) marker.setMap(null);
    marker = new google.maps.marker.AdvancedMarkerElement({ position: { lat: destLat, lng: destLng }, map: map });

    const checkInDate = document.getElementById("checkin-input").value || new Date().toISOString().split('T')[0];
    const adults = 1;
    const roomQuantity = 1;
    if (currentMode === 'flights') {
        const origin = document.getElementById("origin-input").value.trim();
        const destination = document.getElementById("destination-input").value.trim();
        if (!origin || !destination) {
            alert("Please enter origin and destination IATA codes.");
            return;
        }
        const url = `/search/flights?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&departureDate=${checkInDate}&adults=${adults}`;
        fetch(url)
            .then(res => {
                if (!res.ok) throw new Error('Flight search failed');
                return res.json();
            })
            .then(data => {
                renderSidebar({ flights: data }, currentMode);
                showSidebar();
            })
            .catch(err => {
                console.error(err);
                alert("Oops! " + err.message);
                hideSidebar();
            });
        return;
    }
    function fetchNearbyAirports(lat, lng) {
        const radiusEl = document.getElementById('radius-input');
        const radius = radiusEl ? radiusEl.value : 200;
        fetch(`/nearby-airports?lat=${lat}&lng=${lng}&limit=5&radius=${radius}`)            .then(res => res.json())
            .then(airports => {
                airports.forEach(airport => {
                    const marker = new google.maps.Marker({
                        position: { lat: airport.lat, lng: airport.lng },
                        map: map,
                        icon: "airport_icon.png",
                        title: airport.name + " (" + airport.iata + ")"
                    });

                    marker.addListener("click", () => {
                        document.getElementById('origin-input').value = airport.iata;
                        infoWindow.setContent(`<strong>${airport.name}</strong> (${airport.iata})`);
                        infoWindow.open(map, marker);
                    });
                });
            });
    }
    if (currentMode === 'hotels') {
        const url = `/search/nearby?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}`;
        fetch(url)
            .then(res => {
                if (!res.ok) throw new Error('Hotel search failed');
                return res.json();
            })
            .then(data => {
                renderSidebar({ hotels: data.offers || [] }, currentMode);
                showSidebar();
            })
            .catch(err => {
                console.error(err);
                alert("Oops! " + err.message);
                hideSidebar();
            });
        return;
    }

    if (originLat == null || originLng == null) {
        alert("User location not available.");
    return;
    }
    const url = `/trip-info?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}&originLat=${originLat}&originLng=${originLng}`;

    fetch(url)
        .then(async res => {
            if (!res.ok) {
                const error = await res.json();
                const msg = error.error || error.message || "Unknown error";
                throw new Error(msg);            }
            return res.json();
        })
        .then(data => {
            console.log("Data received from backend:", data);
            renderSidebar(data, currentMode);
            showSidebar();
        })
        .catch(err => {
            console.error("Trip info fetch failed:", err.message);
            alert("Oops! " + err.message);
            hideSidebar();
        });
}
