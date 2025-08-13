const departureInput = document.getElementById("departure-input");
const departureSuggestionsEl = document.getElementById("departure-suggestions");
const destinationInput = document.getElementById("destination-input");
const destinationSuggestionsEl = document.getElementById("destination-suggestions");
let destinationCoords = null;

function positionSuggestions(inputEl, listEl) {
    const rect = inputEl.getBoundingClientRect();
    listEl.style.left = `${rect.left}px`;
    listEl.style.top = `${rect.bottom + window.scrollY}px`;
    listEl.style.width = `${rect.width}px`;
}

departureInput.addEventListener("input", () => {
    const query = departureInput.value.trim();
    if (query.length >= 1) {
        positionSuggestions(departureInput, departureSuggestionsEl);
        fetch(`/search/locations?keyword=${encodeURIComponent(query)}`)
            .then(res => res.json())
            .then(data => {
                departureSuggestionsEl.innerHTML = "";
                data.forEach(loc => {
                    const li = document.createElement("li");
                    li.textContent = `${loc.name}${loc.iataCode ? ` (${loc.iataCode})` : ""}`;
                    li.addEventListener("click", () => {
                        departureSuggestionsEl.innerHTML = "";
                        departureInput.value = loc.name;
                        userCoords = { lat: loc.lat, lng: loc.lng };
                        if (destinationCoords) {
                            requestTripInfo(destinationCoords.lat, destinationCoords.lng, userCoords.lat, userCoords.lng);
                        }
                    });
                    departureSuggestionsEl.appendChild(li);
                });
            })
            .catch(err => console.error(err));
    } else {
        departureSuggestionsEl.innerHTML = "";
    }
});

destinationInput.addEventListener("input", () => {
    const query = destinationInput.value.trim();
    if (query.length >= 1) {
        positionSuggestions(destinationInput, destinationSuggestionsEl);
        fetch(`/search/locations?keyword=${encodeURIComponent(query)}`)
            .then(res => res.json())
            .then(data => {
                destinationSuggestionsEl.innerHTML = "";
                data.forEach(loc => {
                    const li = document.createElement("li");
                    li.textContent = `${loc.name}${loc.iataCode ? ` (${loc.iataCode})` : ""}`;
                    li.addEventListener("click", () => {
                        destinationSuggestionsEl.innerHTML = "";
                        destinationInput.value = loc.name;
                        destinationCoords = { lat: loc.lat, lng: loc.lng };
                        if (userCoords) {
                            requestTripInfo(destinationCoords.lat, destinationCoords.lng, userCoords.lat, userCoords.lng);
                        } else {
                            alert("User location not available.");
                        }
                    });
                    destinationSuggestionsEl.appendChild(li);
                });
            })
            .catch(err => console.error(err));
    } else {
        destinationSuggestionsEl.innerHTML = "";
    }
});

function searchCity() {
    const query = destinationInput.value;
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
