// inputs for autocomplete suggestions
const departureInput = document.getElementById("departure-input");
const departureSuggestionsEl = document.getElementById("departure-suggestions");
const destinationInput = document.getElementById("destination-input");
const destinationSuggestionsEl = document.getElementById("destination-suggestions");

// abort controller is used to cancel old requests while user is typing
let suggestionController = null;

function positionSuggestions(inputEl, listEl) {
    // simple positioning: put suggestion list below input and full width
    // (inputEl is not used here but maybe you keep it for future)
    listEl.style.left = "0px";
    listEl.style.top = "100%";
    listEl.style.width = "100%";
}

function fetchLocations(query, targetEl, onSelect) {
    // cancel previous request if user typed again
    if (suggestionController) {
        suggestionController.abort();
    }

    suggestionController = new AbortController();
    const signal = suggestionController.signal;

    // cache suggestions per query to avoid calling backend too often
    const cacheKey = `loc-${query}`;
    if (appState.cache.has(cacheKey)) {
        renderSuggestions(appState.cache.get(cacheKey), targetEl, onSelect);
        return;
    }

    // call backend endpoint to get locations list (airports/cities)
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
            // AbortError is normal when typing fast
            if (err.name === "AbortError") return;

            console.error(err);
            targetEl.innerHTML = `<li class="error">Error fetching suggestions</li>`;
        });
}

function renderSuggestions(data, listEl, onSelect) {
    // rebuild the list items every time
    listEl.innerHTML = "";

    data.forEach(loc => {
        const li = document.createElement("li");

        // show location name and iata code if available
        li.textContent = `${loc.name || ""}${loc.iataCode ? ` (${loc.iataCode})` : ""}`;

        // when user clicks, run callback to fill inputs and update coords
        li.addEventListener("click", () => onSelect(loc));

        listEl.appendChild(li);
    });

    // if backend returns empty list, show "no matches"
    if (!data.length) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No matches";
        listEl.appendChild(li);
    }
}

// ---------------- departure autocomplete ----------------
departureInput.addEventListener("input", () => {
    const query = departureInput.value.trim();

    // start searching even with 1 character
    if (query.length >= 1) {
        positionSuggestions(departureInput, departureSuggestionsEl);

        fetchLocations(query, departureSuggestionsEl, (loc) => {
            // clear suggestions after selecting
            departureSuggestionsEl.innerHTML = "";

            // fill input with selected location name
            departureInput.value = loc.name;

            // update origin coords in appState
            appState.userCoords = { lat: loc.lat, lng: loc.lng };

            // if destination already selected, refresh search automatically
            if (appState.destinationCoords) {
                requestTripInfo(appState.destinationCoords.lat, appState.destinationCoords.lng);
            }
        });
    } else {
        // if user deletes text, clear suggestions
        departureSuggestionsEl.innerHTML = "";
    }
});

// ---------------- destination autocomplete ----------------
destinationInput.addEventListener("input", () => {
    const query = destinationInput.value.trim();

    if (query.length >= 1) {
        positionSuggestions(destinationInput, destinationSuggestionsEl);

        fetchLocations(query, destinationSuggestionsEl, (loc) => {
            // reminder: when selecting suggestion, we clear list
            destinationSuggestionsEl.innerHTML = "";


            destinationSuggestionsEl.innerHTML = "";

            destinationInput.value = loc.name;

            // update destination coords
            appState.destinationCoords = { lat: loc.lat, lng: loc.lng };

            // if origin exists, trigger search, else show error
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
    // fallback manual search using geocoder, if autocomplete is not used
    const query = destinationInput.value;

    const geocoder = new google.maps.Geocoder();
    geocoder.geocode({ address: query }, (results, status) => {
        if (status === "OK") {
            const location = results[0].geometry.location;

            // center map on found city and request trip info
            map.setCenter(location);
            requestTripInfo(location.lat(), location.lng());
        } else {
            appState.showError("City not found. Please try again.");
        }
    });
}