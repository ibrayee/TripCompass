let map, marker, userCoords = null;

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

    const url = `/trip-info?lat=${destLat}&lng=${destLng}&originLat=${userCoords.lat}&originLng=${userCoords.lng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}`;

    fetch(url)
        .then(async res => {
            if (!res.ok) {
                const error = await res.json();
                throw new Error(error.message || "Unknown error");
            }
            return res.json();
        })
        .then(data => {
            renderSidebar(data);
            showSidebar();
        })
        .catch(err => {
            console.error("Trip info fetch failed:", err.message);
            alert("Oops! " + err.message);
            hideSidebar();
        });
}
