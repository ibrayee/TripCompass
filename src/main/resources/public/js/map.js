let map, marker, userCoords = null;
let currentMode = 'trip';
let infoWindow;
let airportMarkers = [];
let originAddress = "";



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
                const geocoder = new google.maps.Geocoder();
                geocoder.geocode({ location: userCoords }, (results, status) => {
                    if (status === "OK" && results[0]) {
                        const departureInput = document.getElementById("departure-input");
                        departureInput.value = results[0].formatted_address;
                        originAddress = departureInput.value;
                    }
                });
            },
            () => alert("Geolocation failed.")
        );
    }

    // Click sulla mappa = destinazione
    map.addListener("click", (e) => {
        const lat = e.latLng.lat();
        const lng = e.latLng.lng();
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ location: { lat, lng } }, async (results, status) => {
            if (status === "OK" && results[0]) {
                const departureInput = document.getElementById("departure-input");
                if (departureInput) {
                    departureInput.value = results[0].formatted_address;
                }
                userCoords = { lat, lng };

                const radiusSelect = document.getElementById('radius-input');
                let radius = radiusSelect && radiusSelect.value ? Number(radiusSelect.value) : 200;
                let airports = [];
                const maxRadius = 5000;
                while (radius <= maxRadius) {
                    try {
                        const res = await fetch(`/nearby-airports?lat=${lat}&lng=${lng}&limit=1&radius=${radius}`);
                        if (!res.ok) throw new Error('Airport lookup failed');
                        airports = await res.json();
                        if (Array.isArray(airports) && airports.length > 0 && !airports.error) {
                            break;
                        }
                    } catch (err) {
                        console.error(err);
                        break;
                    }
                    radius *= 2;
                }

                if (departureInput && departureInput.value.trim() && typeof destinationCoords !== "undefined" && destinationCoords) {
                    requestTripInfo(destinationCoords.lat, destinationCoords.lng);
                }
            } else {
                alert("Location not found.");
            }
        });    });
}

function requestTripInfo(destLat, destLng) {
    const departureInput = document.getElementById("departure-input");
    const originValue = departureInput ? departureInput.value.trim() : "";

    if (originValue && originValue !== originAddress) {
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ address: originValue }, (results, status) => {
            if (status === "OK" && results[0]) {
                const loc = results[0].geometry.location;
                userCoords = { lat: loc.lat(), lng: loc.lng() };
                departureInput.value = results[0].formatted_address;
                originAddress = departureInput.value;
                proceed();
            } else {
                alert("Origin not found.");
            }
        });
        return;
    }
    if (!userCoords) {
        alert("User location not available.");
        return;
    }

    proceed();

    function proceed() {
        const originLat = userCoords.lat;
        const originLng = userCoords.lng;
        if (marker) marker.setMap(null);
        marker = new google.maps.marker.AdvancedMarkerElement({ position: { lat: destLat, lng: destLng }, map: map });
        airportMarkers.forEach(m => m.setMap(null));
        airportMarkers = [];


        const checkInInput = document.getElementById("checkin-input");
        const checkInDate = checkInInput && checkInInput.value
            ? checkInInput.value
            : new Date().toISOString().split('T')[0];
        const adults = 1;
        const roomQuantity = 1;
    if (currentMode === 'flights') {
        const radiusSelect = document.getElementById('radius-input');
        const radius = radiusSelect && radiusSelect.value ? radiusSelect.value : 200;
        Promise.all([
            fetch(`/nearby-airports?lat=${originLat}&lng=${originLng}&limit=5&radius=${radius}`).then(res => {
                if (!res.ok) throw new Error('Airport lookup failed');
                return res.json();
            }),
            fetch(`/nearby-airports?lat=${destLat}&lng=${destLng}&limit=5&radius=${radius}`).then(res => {
                if (!res.ok) throw new Error('Airport lookup failed');
                return res.json();
            })])
            .then(([origAirports, destAirports]) => {
                if (origAirports.error) throw new Error(origAirports.error);
                if (destAirports.error) throw new Error(destAirports.error);
                if (!Array.isArray(origAirports) || !Array.isArray(destAirports) ||
                    !origAirports.length || !destAirports.length) {                    throw new Error('No airports found within selected radius');
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
                renderSidebar({ flights: data }, currentMode);
                showSidebar();
            })
            .catch(err => {
                console.error(err);
                if (err.message === 'No airports found within selected radius') {
                    alert("No airport found. Tests only in US/ES/UK/DE/IN. More info: https://example.com/dataset-coverage");
                } else {
                    alert("Oops! " + err.message);
                }
                hideSidebar();
            });
        return;
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
}
