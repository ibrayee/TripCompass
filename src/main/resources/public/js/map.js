let map, marker;
let currentMode = 'trip';
let infoWindow;
let airportMarkers = [];

function setMode(mode) {
    currentMode = mode;
    document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`mode-${mode}`);
    if (activeBtn) {
        activeBtn.classList.add('active');
    }
}

document.getElementById('mode-flights')?.addEventListener('click', () => setMode('flights'));
document.getElementById('mode-hotels')?.addEventListener('click', () => setMode('hotels'));
document.getElementById('mode-trip')?.addEventListener('click', () => setMode('trip'));

/* Called by Google Maps API */
function initMap() {
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome

    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
        mapId: "bf198408fe296ef1"
    });
    infoWindow = new google.maps.InfoWindow();

    const checkinInput = document.getElementById("checkin-input");
    if (checkinInput) {
        const today = new Date();
        today.setDate(today.getDate() + 1);
        const iso = today.toISOString().split('T')[0];
        checkinInput.min = iso;
        checkinInput.value = checkinInput.value || iso;
    }

    // Geolocate user on load
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            (position) => {
                appState.userCoords = {
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                };
                map.setCenter(appState.userCoords);
                map.setZoom(10);

                new google.maps.Marker({
                    position: appState.userCoords,
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
                });

                const geocoder = new google.maps.Geocoder();
                geocoder.geocode({ location: appState.userCoords }, (results, status) => {
                    if (status === "OK" && results[0]) {
                        const departureInput = document.getElementById("departure-input");
                        if (departureInput) {
                            departureInput.value = results[0].formatted_address;
                            appState.originAddress = departureInput.value;
                        }
                    }
                });
            },
            () => alert("Geolocation failed.")
        );
    }

    // Click on the map = destination
    map.addListener("click", (e) => {
        const lat = e.latLng.lat();
        const lng = e.latLng.lng();
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ location: { lat, lng } }, (results, status) => {
            if (status === "OK" && results[0]) {
                const destinationInput = document.getElementById("destination-input");
                if (destinationInput) {
                    destinationInput.value = results[0].formatted_address;
                }
                appState.destinationCoords = { lat, lng };
                requestTripInfo(lat, lng);
            } else {
                appState.showError("Location not found.");
            }
        });
    });
}

function placeDestinationMarker(lat, lng) {
    if (marker) marker.setMap(null);
    if (google.maps.marker && google.maps.marker.AdvancedMarkerElement) {
        marker = new google.maps.marker.AdvancedMarkerElement({
            position: { lat, lng },
            map: map
        });
    } else {
        marker = new google.maps.Marker({
            position: { lat, lng },
            map: map
        });
    }
}

function requestTripInfo(destLat, destLng) {
    const departureInput = document.getElementById("departure-input");
    const originValue = departureInput ? departureInput.value.trim() : "";

    if (originValue && originValue !== appState.originAddress) {
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ address: originValue }, (results, status) => {
            if (status === "OK" && results[0]) {
                const loc = results[0].geometry.location;
                appState.userCoords = { lat: loc.lat(), lng: loc.lng() };
                if (departureInput) {
                    departureInput.value = results[0].formatted_address;
                    appState.originAddress = departureInput.value;
                }
                proceed();
            } else {
                appState.showError("Origin not found.");
            }
        });
        return;
    }

    if (!appState.userCoords) {
        appState.showError("User location not available.");
        return;
    }

    proceed();

    function proceed() {
        appState.clearError();
        appState.setLoading(true);
        const originLat = appState.userCoords.lat;
        const originLng = appState.userCoords.lng;
        appState.destinationCoords = { lat: destLat, lng: destLng };

        placeDestinationMarker(destLat, destLng);
        airportMarkers.forEach(m => m.setMap(null));
        airportMarkers = [];

        const checkInInput = document.getElementById("checkin-input");
        const checkInDate = checkInInput && checkInInput.value
            ? checkInInput.value
            : new Date().toISOString().split('T')[0];
        const adultsInput = document.getElementById("adults-input");
        const roomsInput = document.getElementById("rooms-input");
        const adults = adultsInput ? Math.max(1, Number(adultsInput.value) || 1) : 1;
        const roomQuantity = roomsInput ? Math.max(1, Number(roomsInput.value) || 1) : 1;
        const cacheKey = `${currentMode}-${destLat}-${destLng}-${originLat}-${originLng}-${checkInDate}-${adults}-${roomQuantity}`;

        if (appState.cache.has(cacheKey)) {
            renderSidebar(appState.cache.get(cacheKey), currentMode);
            showSidebar();
            appState.setLoading(false);
            return;
        }
        if (appState.inflight.has(cacheKey)) {
            appState.inflight.get(cacheKey)
                .then(data => {
                    renderSidebar(data, currentMode);
                    showSidebar();
                })
                .catch(err => appState.showError(err.message))
                .finally(() => appState.setLoading(false));
            return;
        }

        if (currentMode === 'flights') {
            const radiusSelect = document.getElementById('radius-input');
            const radius = radiusSelect && radiusSelect.value ? Number(radiusSelect.value) : 200;
            const flightsPromise = Promise.all([
                fetch(`/nearby-airports?lat=${originLat}&lng=${originLng}&limit=5&radius=${radius}`).then(res => {
                    if (!res.ok) throw new Error('Airport lookup failed');
                    return res.json();
                }),
                fetch(`/nearby-airports?lat=${destLat}&lng=${destLng}&limit=5&radius=${radius}`).then(res => {
                    if (!res.ok) throw new Error('Airport lookup failed');
                    return res.json();
                })
            ])
                .then(([origAirports, destAirports]) => {
                    if (origAirports.error) throw new Error(origAirports.error);
                    if (destAirports.error) throw new Error(destAirports.error);
                    if (!Array.isArray(origAirports) || !Array.isArray(destAirports) ||
                        !origAirports.length || !destAirports.length) {
                        throw new Error('No airports found within selected radius');
                    }

                    origAirports.forEach(ap => {
                        const m = new google.maps.Marker({
                            position: { lat: ap.lat, lng: ap.lng },
                            map: map,
                            label: ap.iata,
                            icon: {
                                path: google.maps.SymbolPath.CIRCLE,
                                scale: 6,
                                fillColor: '#4285F4',
                                fillOpacity: 1,
                                strokeWeight: 1
                            }
                        });
                        airportMarkers.push(m);
                    });

                    destAirports.forEach(ap => {
                        const m = new google.maps.Marker({
                            position: { lat: ap.lat, lng: ap.lng },
                            map: map,
                            label: ap.iata,
                            icon: {
                                path: google.maps.SymbolPath.CIRCLE,
                                scale: 6,
                                fillColor: '#DB4437',
                                fillOpacity: 1,
                                strokeWeight: 1
                            }
                        });
                        airportMarkers.push(m);
                    });

                    const origin = origAirports[0].iata;
                    const destination = destAirports[0].iata;
                    const url = `/search/flights?origin=${origin}&destination=${destination}&departureDate=${checkInDate}&adults=${adults}`;
                    return fetch(url).then(res => {
                        if (!res.ok) throw new Error('Flight search failed');
                        return res.json();
                    });
                })
                .then(data => {
                    const payload = { flights: data };
                    appState.cache.set(cacheKey, payload);
                    renderSidebar(payload, currentMode);
                    showSidebar();
                })
                .catch(err => {
                    console.error(err);
                    if (err.message === 'No airports found within selected radius') {
                        appState.showError("No airport found. Tests only in US/ES/UK/DE/IN. More info: https://example.com/dataset-coverage");
                    } else {
                        appState.showError("Oops! " + err.message);
                    }
                    hideSidebar();
                })
                .finally(() => {
                    appState.inflight.delete(cacheKey);
                    appState.setLoading(false);
                });
            appState.inflight.set(cacheKey, flightsPromise);
            return;
        }

        if (currentMode === 'hotels') {
            const url = `/search/nearby?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}`;
            const hotelPromise = fetch(url).then(res => {
                if (!res.ok) throw new Error('Hotel search failed');
                return res.json();
            })
                .then(data => {
                    const payload = { hotels: data.offers || [] };
                    appState.cache.set(cacheKey, payload);
                    renderSidebar(payload, currentMode);
                    showSidebar();
                })
                .catch(err => {
                    console.error(err);
                    appState.showError("Oops! " + err.message);
                    hideSidebar();
                })
                .finally(() => {
                    appState.inflight.delete(cacheKey);
                    appState.setLoading(false);
                });
            appState.inflight.set(cacheKey, hotelPromise);
            return;
        }

        const url = `/trip-info?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}&originLat=${originLat}&originLng=${originLng}`;
        const tripPromise = fetch(url).then(async res => {
            if (!res.ok) {
                const error = await res.json();
                const msg = error.error || error.message || "Unknown error";
                throw new Error(msg);
            }
            return res.json();
        })
            .then(data => {
                appState.cache.set(cacheKey, data);
                renderSidebar(data, currentMode);
                showSidebar();
            })
            .catch(err => {
                console.error("Trip info fetch failed:", err.message);
                appState.showError("Oops! " + err.message);
                hideSidebar();
            })
            .finally(() => {
                appState.inflight.delete(cacheKey);
                appState.setLoading(false);
            });
        appState.inflight.set(cacheKey, tripPromise);
    }
}