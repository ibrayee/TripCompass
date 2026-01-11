// global variables for the map and UI state
let map, marker;
let currentMode = 'trip'; // can be: 'trip' | 'flights' | 'hotels'
let airportMarkers = [];  // markers for nearby airports
let routePolyline = null; // line between origin and destination (simple straight line)
let cachedCheckinMin = null; // remember minimum check-in date
let hotelMarkers = []; // markers for hotels on map

function setMode(mode) {
    // update current mode and refresh UI
    currentMode = mode;

    // remove "active" css from all mode buttons
    document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));

    // add active class only to selected button
    const activeBtn = document.getElementById(`mode-${mode}`);
    if (activeBtn) {
        activeBtn.classList.add('active');
    }

    // if we switch to flights mode, hotels markers are not relevant, so clear them
    if (mode === 'flights') {
        clearHotelMarkers();
    }

    // update labels, tooltips, placeholders, etc
    updateModeUI(mode);
}

// attach click events for the mode buttons (optional chaining because maybe button not exists in some pages)
document.getElementById('mode-flights')?.addEventListener('click', () => setMode('flights'));
document.getElementById('mode-hotels')?.addEventListener('click', () => setMode('hotels'));
document.getElementById('mode-trip')?.addEventListener('click', () => setMode('trip'));

document.addEventListener('DOMContentLoaded', () => {
    // when page is loaded, apply UI mode and tooltip logic
    updateModeUI(currentMode);
    attachTooltipDelays();

    // adjust app height based on header height
    syncAppHeightWithHeader();
    window.addEventListener('resize', syncAppHeightWithHeader);
});

/* Called by Google Maps API (callback=initMap) */
function initMap() {
    // default center if geolocation not available
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome

    // create google map instance
    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
        mapId: "bf198408fe296ef1" // custom map style id from google cloud console
    });

    // date inputs handling (check-in / check-out)
    const checkinInput = document.getElementById("checkin-input");
    const checkoutInput = document.getElementById("checkout-input");

    // set min check-in date = tomorrow (so user cannot pick today)
    if (checkinInput) {
        const today = new Date();
        today.setDate(today.getDate() + 1);

        const iso = today.toISOString().split('T')[0]; // yyyy-mm-dd
        cachedCheckinMin = iso;

        checkinInput.min = iso;

        // if user has no value, set default to tomorrow
        checkinInput.value = checkinInput.value || iso;
    }

    // set checkout min date = checkin + 1 day
    if (checkoutInput) {
        const updateCheckoutMin = () => {
            const checkinVal = checkinInput?.value || cachedCheckinMin;
            if (!checkinVal) return;

            const checkoutDate = new Date(checkinVal);
            checkoutDate.setDate(checkoutDate.getDate() + 1);

            const checkoutIso = checkoutDate.toISOString().split("T")[0];
            checkoutInput.min = checkoutIso;

            // if checkout value is empty or smaller than min, force it to min
            if (!checkoutInput.value || checkoutInput.value < checkoutIso) {
                checkoutInput.value = checkoutIso;
            }
        };

        updateCheckoutMin();
        // when check-in changes, update check-out minimum
        checkinInput?.addEventListener("change", updateCheckoutMin);
    }

    // Geolocate user on load
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            (position) => {
                // store user coords in global app state
                appState.userCoords = {
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                };

                // center map on user
                map.setCenter(appState.userCoords);
                map.setZoom(10);

                // show a blue circle marker for user location
                new google.maps.Marker({
                    position: appState.userCoords,
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
                });

                // reverse geocode to fill the departure input with a real address
                const geocoder = new google.maps.Geocoder();
                geocoder.geocode({ location: appState.userCoords }, (results, status) => {
                    if (status === "OK" && results[0]) {
                        const departureInput = document.getElementById("departure-input");
                        if (departureInput) {
                            departureInput.value = results[0].formatted_address;
                            appState.originAddress = departureInput.value; // save original address
                        }
                    }
                });
            },
            () => alert("Geolocation failed.")
        );
    }

    // Click on the map = destination selection
    map.addListener("click", (e) => {
        const lat = e.latLng.lat();
        const lng = e.latLng.lng();

        // reverse geocode clicked coordinates to get formatted address
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ location: { lat, lng } }, (results, status) => {
            if (status === "OK" && results[0]) {
                const destinationInput = document.getElementById("destination-input");
                if (destinationInput) {
                    destinationInput.value = results[0].formatted_address;
                }

                // store destination coords and call backend for trip data
                appState.destinationCoords = { lat, lng };
                requestTripInfo(lat, lng);
            } else {
                appState.showError("Location not found.");
            }
        });
    });
}

function placeDestinationMarker(lat, lng) {
    // keep only one destination marker
    if (marker) marker.setMap(null);

    // use AdvancedMarkerElement if available, else fallback to normal marker
    if (google.maps.marker && google.maps.marker.AdvancedMarkerElement) {
        marker = new google.maps.marker.AdvancedMarkerElement({
            position: { lat, lng },
            map: map
        });
    } else {
        marker = new google.maps.Marker({
            position: { lat, lng },
            map: map
        });
    }
}

function clearRouteLine() {
    // remove the polyline if it exists
    if (routePolyline) {
        routePolyline.setMap(null);
        routePolyline = null;
    }
}

function clearHotelMarkers() {
    // remove hotel markers from map
    hotelMarkers.forEach(m => m.setMap(null));
    hotelMarkers = [];
}

function drawHotelMarkers(hotels) {
    // draw markers for hotels list
    if (!map || !Array.isArray(hotels)) return;

    clearHotelMarkers();

    // bounds is used to auto-zoom/fit all markers
    const bounds = new google.maps.LatLngBounds();

    hotels.forEach(h => {
        // looks like your hotel response structure is nested
        // take first container element and use it
        const offerContainer = Array.isArray(h.offers) ? h.offers[0] : null;
        const hotelInfo = offerContainer?.hotel || {};

        const lat = Number(hotelInfo.latitude);
        const lng = Number(hotelInfo.longitude);
        if (Number.isNaN(lat) || Number.isNaN(lng)) return;

        const title = hotelInfo.name || "Hotel";

        const marker = new google.maps.Marker({
            position: { lat, lng },
            map,
            title
        });

        // try to extract price from offers
        const price = (offerContainer?.offers && offerContainer.offers[0]?.price) || {};

        // create popup window with name, address, price
        const info = new google.maps.InfoWindow({
            content: `<div><strong>${title}</strong><br/>${hotelInfo.address || ''}<br/>${price.total ? `${price.total} ${price.currency || ''}` : ''}</div>`
        });

        marker.addListener("click", () => info.open({ map, anchor: marker }));

        hotelMarkers.push(marker);
        bounds.extend(marker.getPosition());
    });

    // if we placed at least one marker, fit them inside screen
    if (!bounds.isEmpty()) {
        map.fitBounds(bounds, 60); // 60 is padding, so markers not too close to edge
    }
}

function drawRouteLine(originCoords, destinationCoords) {
    // draw a simple straight line between origin and destination
    // (not a road route, just direct line)
    if (!map || !originCoords || !destinationCoords) return;

    clearRouteLine();

    routePolyline = new google.maps.Polyline({
        path: [
            { lat: originCoords.lat, lng: originCoords.lng },
            { lat: destinationCoords.lat, lng: destinationCoords.lng }
        ],
        geodesic: true,
        strokeColor: '#4285F4',
        strokeOpacity: 0.85,
        strokeWeight: 4
    });

    routePolyline.setMap(map);
}

function requestTripInfo(destLat, destLng) {
    // main function: based on currentMode it calls different backend endpoints

    const departureInput = document.getElementById("departure-input");
    const originValue = departureInput ? departureInput.value.trim() : "";

    const checkinInput = document.getElementById("checkin-input");
    const checkoutInput = document.getElementById("checkout-input");
    const checkOutDate = checkoutInput && checkoutInput.value ? checkoutInput.value : "";

    // if mode is not hotels and user typed a different origin than the geolocated one,
    // then we geocode the typed origin to update appState.userCoords
    if (currentMode !== 'hotels' && originValue && originValue !== appState.originAddress) {
        const geocoder = new google.maps.Geocoder();
        geocoder.geocode({ address: originValue }, (results, status) => {
            if (status === "OK" && results[0]) {
                const loc = results[0].geometry.location;

                // update user coords from typed input
                appState.userCoords = { lat: loc.lat(), lng: loc.lng() };

                // update input to normalized formatted address
                if (departureInput) {
                    departureInput.value = results[0].formatted_address;
                    appState.originAddress = departureInput.value;
                }

                proceed();
            } else {
                appState.showError("Origin not found.");
            }
        });
        return;
    }

    // if we need origin coords but we don’t have it, error
    if (!appState.userCoords && currentMode !== 'hotels') {
        appState.showError("User location not available.");
        return;
    }

    proceed();

    function proceed() {
        // clear errors and show loading state
        appState.clearError();
        appState.setLoading(true);

        const originLat = appState.userCoords?.lat ?? null;
        const originLng = appState.userCoords?.lng ?? null;

        // update destination in appState
        appState.destinationCoords = { lat: destLat, lng: destLng };

        // place marker and draw line (not for hotels mode)
        placeDestinationMarker(destLat, destLng);
        if (appState.userCoords && currentMode !== 'hotels') {
            drawRouteLine(appState.userCoords, appState.destinationCoords);
        } else {
            clearRouteLine();
        }

        // clear airport markers before new search
        airportMarkers.forEach(m => m.setMap(null));
        airportMarkers = [];

        // determine check-in date (fallback to today in ISO)
        const checkInDate = checkinInput && checkinInput.value
            ? checkinInput.value
            : new Date().toISOString().split('T')[0];

        // read adults and rooms inputs (with min 1)
        const adultsInput = document.getElementById("adults-input");
        const roomsInput = document.getElementById("rooms-input");
        const adults = adultsInput ? Math.max(1, Number(adultsInput.value) || 1) : 1;
        const roomQuantity = roomsInput ? Math.max(1, Number(roomsInput.value) || 1) : 1;

        // cache key depends on mode, coords, dates and people/rooms
        const cacheKey = `${currentMode}-${destLat}-${destLng}-${originLat ?? "no-origin"}-${originLng ?? "no-origin"}-${checkInDate}-${checkOutDate || "no-checkout"}-${adults}-${roomQuantity}`;

        // if response is already cached, reuse it (fast)
        if (appState.cache.has(cacheKey)) {
            renderSidebar(appState.cache.get(cacheKey), currentMode);
            showSidebar();
            appState.setLoading(false);
            return;
        }

        // if there is already a request running for same key, attach to same promise
        if (appState.inflight.has(cacheKey)) {
            appState.inflight.get(cacheKey)
                .then(data => {
                    renderSidebar(data, currentMode);
                    showSidebar();
                })
                .catch(err => appState.showError(err.message))
                .finally(() => appState.setLoading(false));
            return;
        }

        // ---------------- flights mode ----------------
        if (currentMode === 'flights') {
            const radiusSelect = document.getElementById('radius-input');
            const radius = radiusSelect && radiusSelect.value ? Number(radiusSelect.value) : 200;

            // first step: find nearby airports for origin and destination
            const flightsPromise = Promise.all([
                fetch(`/nearby-airports?lat=${originLat}&lng=${originLng}&limit=5&radius=${radius}`).then(res => {
                    if (!res.ok) throw new Error('Airport lookup failed');
                    return res.json();
                }),
                fetch(`/nearby-airports?lat=${destLat}&lng=${destLng}&limit=5&radius=${radius}`).then(res => {
                    if (!res.ok) throw new Error('Airport lookup failed');
                    return res.json();
                })
            ])
                .then(([origAirports, destAirports]) => {
                    // backend may return {error: "..."} so handle it
                    if (origAirports.error) throw new Error(origAirports.error);
                    if (destAirports.error) throw new Error(destAirports.error);

                    // if arrays empty -> cannot search flights
                    if (!Array.isArray(origAirports) || !Array.isArray(destAirports) ||
                        !origAirports.length || !destAirports.length) {
                        throw new Error('No airports found within selected radius');
                    }

                    // draw airport markers (origin airports in blue)
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

                    // destination airports in red
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

                    // for now pick first airport in each list (closest probably)
                    const origin = origAirports[0].iata;
                    const destination = destAirports[0].iata;

                    // then call flights search endpoint
                    const url = `/search/flights?origin=${origin}&destination=${destination}&departureDate=${checkInDate}&adults=${adults}`;
                    return fetch(url).then(res => {
                        if (!res.ok) throw new Error('Flight search failed');
                        return res.json();
                    });
                })
                .then(data => {
                    // normalize payload for sidebar renderer
                    const payload = { flights: data };
                    appState.cache.set(cacheKey, payload);
                    renderSidebar(payload, currentMode);
                    showSidebar();
                })
                .catch(err => {
                    console.error(err);

                    // custom message for missing airports coverage
                    if (err.message === 'No airports found within selected radius') {
                        appState.showError("Dataset coverage limited");
                    } else {
                        appState.showError("Oops! " + err.message);
                    }

                    hideSidebar();
                })
                .finally(() => {
                    // always cleanup inflight and loading status
                    appState.inflight.delete(cacheKey);
                    appState.setLoading(false);
                });

            appState.inflight.set(cacheKey, flightsPromise);
            return;
        }

        // ---------------- hotels mode ----------------
        if (currentMode === 'hotels') {
            // search nearby hotels around destination
            const url = `/search/nearby?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}` +
                (checkOutDate ? `&checkOutDate=${checkOutDate}` : "");

            const hotelPromise = fetch(url).then(res => {
                if (!res.ok) throw new Error('Hotel search failed');
                return res.json();
            })
                .then(data => {
                    // build payload with hotels list
                    const payload = { hotels: data.offers || [], coordinates: data.coordinates, meta: data.meta };
                    appState.cache.set(cacheKey, payload);

                    // draw markers + render sidebar
                    drawHotelMarkers(payload.hotels);
                    renderSidebar(payload, currentMode);
                    showSidebar();
                })
                .catch(err => {
                    console.error(err);
                    appState.showError("Oops! " + err.message);
                    hideSidebar();
                })
                .finally(() => {
                    appState.inflight.delete(cacheKey);
                    appState.setLoading(false);
                });

            appState.inflight.set(cacheKey, hotelPromise);
            return;
        }

        // ---------------- trip mode (combined) ----------------
        // this endpoint probably returns flights + hotels + nearby airports etc
        const url = `/trip-info?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${roomQuantity}&originLat=${originLat}&originLng=${originLng}` +
            (checkOutDate ? `&checkOutDate=${checkOutDate}` : "");

        const tripPromise = fetch(url).then(async res => {
            // backend returns json error message in body, so read it
            if (!res.ok) {
                const error = await res.json();
                const msg = error.error || error.message || "Unknown error";
                throw new Error(msg);
            }
            return res.json();
        })
            .then(data => {
                appState.cache.set(cacheKey, data);

                // if hotels exist in response, draw them
                if (data.hotels) {
                    drawHotelMarkers(data.hotels);
                }

                // update sidebar
                renderSidebar(data, currentMode);
                showSidebar();
            })
            .catch(err => {
                console.error("Trip info fetch failed:", err.message);
                appState.showError("Oops! " + err.message);
                hideSidebar();
            })
            .finally(() => {
                appState.inflight.delete(cacheKey);
                appState.setLoading(false);
            });

        appState.inflight.set(cacheKey, tripPromise);
    }
}

function configureRadiusOptions(mode) {
    // change radius options depending on the mode
    const radiusSelect = document.getElementById("radius-input");
    if (!radiusSelect) return;

    const previousValue = radiusSelect.value;
    let options;

    // hotels radius is small (walking distance style)
    if (mode === 'hotels') {
        options = [
            { value: "1", label: "1 km" },
            { value: "2", label: "2 km" },
            { value: "5", label: "5 km" },
            { value: "10", label: "10 km" }
        ];
    } else {
        // flights/trip radius is bigger because airports can be far
        options = [
            { value: "50", label: "50 km" },
            { value: "100", label: "100 km" },
            { value: "200", label: "200 km" }
        ];
    }

    // rebuild select options
    radiusSelect.innerHTML = "";
    options.forEach((opt, idx) => {
        const optionEl = document.createElement("option");
        optionEl.value = opt.value;
        optionEl.textContent = opt.label;

        // try to keep previous selection if possible
        if (opt.value === previousValue || (!previousValue && idx === 0)) {
            optionEl.selected = true;
        }

        radiusSelect.appendChild(optionEl);
    });

    // fallback: if for some reason select has no value, set first
    if (!radiusSelect.value && options.length) {
        radiusSelect.value = options[0].value;
    }
}

function updateModeUI(mode = 'trip') {
    // update the UI labels and placeholders depending on mode
    const departureLabelText = document.getElementById("departure-label-text");
    const destinationLabelText = document.getElementById("destination-label-text");
    const dateLabelText = document.getElementById("date-label-text");
    const adultsLabelText = document.getElementById("adults-label-text");
    const roomsLabelText = document.getElementById("rooms-label-text");
    const radiusLabelText = document.getElementById("radius-label-text");

    // help elements for tooltips
    const departureHelp = document.getElementById("departure-help");
    const destinationHelp = document.getElementById("destination-help");
    const dateHelp = document.getElementById("date-help");
    const adultsHelp = document.getElementById("adults-help");
    const roomsHelp = document.getElementById("rooms-help");
    const radiusHelp = document.getElementById("radius-help");
    const checkoutHelp = document.getElementById("checkout-help");

    // groups to show/hide
    const roomsGroup = document.getElementById("rooms-group");
    const departureGroup = document.getElementById("departure-group");
    const checkoutGroup = document.getElementById("checkout-group");

    // inputs (placeholders changes)
    const departureInput = document.getElementById("departure-input");
    const destinationInput = document.getElementById("destination-input");

    if (!departureLabelText || !destinationLabelText) return;

    if (mode === 'flights') {
        // in flights: no rooms, no checkout, we need departure and arrival
        departureGroup?.classList.remove("hidden-group");
        checkoutGroup?.classList.add("hidden-group");

        departureLabelText.textContent = "Departure";
        destinationLabelText.textContent = "Arrival";
        dateLabelText.textContent = "Departure date";
        adultsLabelText.textContent = "Passengers";
        radiusLabelText.textContent = "Airport search radius";

        // tooltip texts
        destinationHelp?.setAttribute("data-tooltip", "City or airport you are flying to.");
        departureHelp?.setAttribute("data-tooltip", "City or airport you will fly from.");
        dateHelp?.setAttribute("data-tooltip", "When you want to depart.");
        adultsHelp?.setAttribute("data-tooltip", "How many passengers are flying.");
        roomsHelp?.setAttribute("data-tooltip", "Rooms are not needed for flight-only search.");
        radiusHelp?.setAttribute("data-tooltip", "Distance around each city where we look for nearby airports.");

        // hide rooms group in flights
        if (roomsGroup) roomsGroup.classList.add("hidden-group");

        // placeholders
        if (departureInput) departureInput.placeholder = "From (city or airport)";
        if (destinationInput) destinationInput.placeholder = "To (city or airport)";

    } else if (mode === 'hotels') {
        // in hotels: departure is optional, checkout is visible, rooms visible
        departureGroup?.classList.add("hidden-group");
        checkoutGroup?.classList.remove("hidden-group");

        departureLabelText.textContent = "Origin (optional)";
        destinationLabelText.textContent = "Stay near";
        dateLabelText.textContent = "Check-in date";
        adultsLabelText.textContent = "Guests";
        roomsLabelText.textContent = "Rooms";
        radiusLabelText.textContent = "Hotel distance from destination";

        // tooltips for hotel context
        destinationHelp?.setAttribute("data-tooltip", "City or neighborhood where you want to book a hotel.");
        departureHelp?.setAttribute("data-tooltip", "Where you are traveling from (optional, for map context).");
        dateHelp?.setAttribute("data-tooltip", "Hotel check-in date.");
        checkoutHelp?.setAttribute("data-tooltip", "Hotel check-out date.");
        adultsHelp?.setAttribute("data-tooltip", "Number of guests staying.");
        roomsHelp?.setAttribute("data-tooltip", "How many rooms you need.");
        radiusHelp?.setAttribute("data-tooltip", "Maximum distance from your chosen spot to find hotels.");

        if (roomsGroup) roomsGroup.classList.remove("hidden-group");

        if (departureInput) departureInput.placeholder = "Add your starting city (optional)";
        if (destinationInput) destinationInput.placeholder = "Where do you want to stay?";

    } else {
        // trip mode: combined trip, we show departure and destination, rooms visible, checkout hidden
        departureGroup?.classList.remove("hidden-group");
        checkoutGroup?.classList.add("hidden-group");

        departureLabelText.textContent = "Departure";
        destinationLabelText.textContent = "Destination";
        dateLabelText.textContent = "Departure / Check-in date";
        adultsLabelText.textContent = "Travelers";
        roomsLabelText.textContent = "Rooms";
        radiusLabelText.textContent = "Search radius";

        destinationHelp?.setAttribute("data-tooltip", "City or airport for your combined trip.");
        departureHelp?.setAttribute("data-tooltip", "City or airport you start from.");
        dateHelp?.setAttribute("data-tooltip", "Date you leave and check in.");
        adultsHelp?.setAttribute("data-tooltip", "How many people are traveling.");
        roomsHelp?.setAttribute("data-tooltip", "Rooms needed for the stay.");
        radiusHelp?.setAttribute("data-tooltip", "Distance we use to find nearby airports and hotels.");

        if (roomsGroup) roomsGroup.classList.remove("hidden-group");

        if (departureInput) departureInput.placeholder = "From (city or airport)";
        if (destinationInput) destinationInput.placeholder = "Destination city or area";
    }

    // update radius options after switching mode
    configureRadiusOptions(mode);

    // refresh layout
    syncAppHeightWithHeader();
}

function attachTooltipDelays() {
    // this adds a delay before showing tooltip (so it doesn't show instantly)
    document.querySelectorAll(".help-badge").forEach(el => {
        let timer;

        const show = () => {
            timer = setTimeout(() => el.classList.add("show-tooltip"), 800);
        };

        const hide = () => {
            clearTimeout(timer);
            el.classList.remove("show-tooltip");
        };

        // mouse and keyboard (focus) support
        el.addEventListener("mouseenter", show);
        el.addEventListener("focus", show);
        el.addEventListener("mouseleave", hide);
        el.addEventListener("blur", hide);
    });
}

function syncAppHeightWithHeader() {
    // calculate header height and set css variable, so layout can adapt
    const header = document.getElementById("top-bar");
    const app = document.getElementById("app");
    if (!header || !app) return;

    const headerHeight = Math.ceil(header.getBoundingClientRect().height);

    // css variable used in css
    document.documentElement.style.setProperty("--header-height", `${headerHeight}px`);

    // set min height so app does not go under header
    app.style.minHeight = `calc(100vh - ${headerHeight}px)`;
}
