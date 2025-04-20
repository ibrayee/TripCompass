let map, marker;

function initMap() {
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome

    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
    });

    // Make accessible globally
    window.map = map;
    window.initMap = initMap;

    map.addListener("click", (e) => {
        requestTripInfo(e.latLng.lat(), e.latLng.lng());
    });
}

function requestTripInfo(lat, lng) {
    if (marker) marker.setMap(null);
    marker = new google.maps.Marker({ position: { lat, lng }, map: map });

    const origin = document.getElementById("origin-input").value || "ARN";
    const destination = document.getElementById("destination-input").value || "FCO";
    const checkInDate = document.getElementById("checkin-input").value || "2025-06-01";
    const adults = 1;
    const roomQuantity = 1;

    const url = `/trip-info?lat=${lat}&lng=${lng}&origin=${origin}&destination=${destination}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}`;

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
