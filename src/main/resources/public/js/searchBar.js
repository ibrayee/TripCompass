function searchCity() {
    const query = document.getElementById("destination-input").value;    const geocoder = new google.maps.Geocoder();

    geocoder.geocode({ address: query }, (results, status) => {
        if (status === "OK") {
            const location = results[0].geometry.location;
            map.setCenter(location);
            if (userCoords) {
                requestTripInfo(location.lat(), location.lng(), userCoords.lat, userCoords.lng);
            } else {
                alert("User location not available.");
            }
        } else {
            alert("City not found. Please try again.");
        }
    });
}
