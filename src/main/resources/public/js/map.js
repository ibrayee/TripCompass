let map, marker, userCoords = null;

/* Called by Google Maps API */
function initMap() {
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome

    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
        mapId: "bf198408fe296ef1"
    });

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
        requestTripInfo(e.latLng.lat(), e.latLng.lng());
    });
}

function requestTripInfo(destLat, destLng) {
    if (marker) marker.setMap(null);
    marker = new google.maps.marker.AdvancedMarkerElement({ position: { lat: destLat, lng: destLng }, map: map });

    const checkInDate = document.getElementById("checkin-input").value || "2025-06-03";
    const adults = 1;
    const roomQuantity = 1;

    const originText = document.getElementById("origin-input").value.trim();

    let url = `/trip-info?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}`;

    if (originText) {
        url += `&origin=${encodeURIComponent(originText)}`;
    } else if (userCoords) {
        url += `&originLat=${userCoords.lat}&originLng=${userCoords.lng}`;
    } else {
        alert("Utent position not available and no origin insert!");
        return;
    }
    function fetchNearbyAirports(lat, lng) {
        fetch(`/nearby-airports?lat=${lat}&lng=${lng}&limit=5`)
            .then(res => res.json())
            .then(airports => {
                airports.forEach(airport => {
                    const marker = new google.maps.Marker({
                        position: { lat: airport.lat, lng: airport.lng },
                        map: map,
                        icon: "airport_icon.png", // icona diversa per gli aeroporti
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

    if (userCoords) {
        fetchNearbyAirports(userCoords.lat, userCoords.lng);
    } else if (originText) {
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ address: originText }, (results, status) => {
            if (status === "OK" && results[0]) {
                const loc = results[0].geometry.location;
                fetchNearbyAirports(loc.lat(), loc.lng());
            } else {
                console.warn("Could not geocode origin, skipping nearby airport fetch.");
            }
        });
    }

    fetch(url)
        .then(async res => {
            if (!res.ok) {
                const error = await res.json();
                throw new Error(error.message || "Unknown error");
            }
            return res.json();
        })
        // In map.js
        .then(data => {
            console.log("Data received from backend:", data);
            renderSidebar(data);
            showSidebar();
        })
        .catch(err => {
            console.error("Trip info fetch failed:", err.message);
            alert("Oops! " + err.message);
            hideSidebar();
        });
}
