const destinationInput = document.getElementById("destination-input");
const suggestionsEl = document.getElementById("suggestions");

destinationInput.addEventListener("input", () => {
    const query = destinationInput.value.trim();
    if (query.length >= 1) {
        fetch(`/search/locations?keyword=${encodeURIComponent(query)}`)
            .then(res => res.json())
            .then(data => {
                suggestionsEl.innerHTML = "";
                data.forEach(loc => {
                    const li = document.createElement("li");
                    li.textContent = `${loc.name}${loc.iataCode ? ` (${loc.iataCode})` : ""}`;
                    li.addEventListener("click", () => {
                        suggestionsEl.innerHTML = "";
                        destinationInput.value = loc.name;
                        if (userCoords) {
                            requestTripInfo(loc.lat, loc.lng, userCoords.lat, userCoords.lng);
                        } else {
                            alert("User location not available.");
                        }
                    });
                    suggestionsEl.appendChild(li);
                });
            })
            .catch(err => console.error(err));
    } else {
        suggestionsEl.innerHTML = "";
    }
});

function searchCity() {
    const query = destinationInput.value;
    const geocoder = new google.maps.Geocoder();
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
