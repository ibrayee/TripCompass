const departureInput = document.getElementById("departure-input");
const departureSuggestionsEl = document.getElementById("departure-suggestions");
const destinationInput = document.getElementById("destination-input");
const destinationSuggestionsEl = document.getElementById("destination-suggestions");
let suggestionController = null;

function positionSuggestions(inputEl, listEl) {
    listEl.style.left = "0px";
    listEl.style.top = "100%";
    listEl.style.width = "100%";
}

function fetchLocations(query, targetEl, onSelect) {
    if (suggestionController) {
        suggestionController.abort();
    }
    suggestionController = new AbortController();
    const signal = suggestionController.signal;
    const cacheKey = `loc-${query}`;
    if (appState.cache.has(cacheKey)) {
        renderSuggestions(appState.cache.get(cacheKey), targetEl, onSelect);
        return;
    }
    fetch(`/search/locations?keyword=${encodeURIComponent(query)}`, { signal })
        .then(res => {
            if (!res.ok) {
                throw new Error("Location search failed");
            }
            return res.json();
        })
        .then(data => {
            appState.cache.set(cacheKey, data);
            renderSuggestions(data, targetEl, onSelect);
        })
        .catch(err => {
            if (err.name === "AbortError") return;
            console.error(err);
            targetEl.innerHTML = `<li class="error">Error fetching suggestions</li>`;
        });
}

function renderSuggestions(data, listEl, onSelect) {
    listEl.innerHTML = "";
    data.forEach(loc => {
        const li = document.createElement("li");
        li.textContent = `${loc.name || ""}${loc.iataCode ? ` (${loc.iataCode})` : ""}`;
        li.addEventListener("click", () => onSelect(loc));
        listEl.appendChild(li);
    });
    if (!data.length) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No matches";
        listEl.appendChild(li);
    }
}

departureInput.addEventListener("input", () => {
    const query = departureInput.value.trim();
    if (query.length >= 1) {
        positionSuggestions(departureInput, departureSuggestionsEl);
        fetchLocations(query, departureSuggestionsEl, (loc) => {
            departureSuggestionsEl.innerHTML = "";
            departureInput.value = loc.name;
            appState.userCoords = { lat: loc.lat, lng: loc.lng };
            if (appState.destinationCoords) {
                requestTripInfo(appState.destinationCoords.lat, appState.destinationCoords.lng);
            }
        });
    } else {
        departureSuggestionsEl.innerHTML = "";
    }
});

destinationInput.addEventListener("input", () => {
    const query = destinationInput.value.trim();
    if (query.length >= 1) {
        positionSuggestions(destinationInput, destinationSuggestionsEl);
        fetchLocations(query, destinationSuggestionsEl, (loc) => {
            destinationSuggestionsEl.innerHTML = "";
            destinationInput.value = loc.name;
            appState.destinationCoords = { lat: loc.lat, lng: loc.lng };
            if (appState.userCoords) {
                requestTripInfo(appState.destinationCoords.lat, appState.destinationCoords.lng);
            } else {
                appState.showError("User location not available.");
            }
        });
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
            appState.showError("City not found. Please try again.");
        }
    });
}