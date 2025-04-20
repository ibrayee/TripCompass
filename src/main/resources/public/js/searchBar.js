function searchCity() {
    const query = document.getElementById("city-input").value;
    const geocoder = new google.maps.Geocoder();

    geocoder.geocode({ address: query }, (results, status) => {
        if (status === "OK") {
            const location = results[0].geometry.location;
            map.setCenter(location);
            requestTripInfo(location.lat(), location.lng());
        } else {
            alert("City not found. Please try again.");
        }
    });
}
