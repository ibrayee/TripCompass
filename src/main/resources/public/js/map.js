let map, marker, userCoords = null;
let geocoder, infoWindow;

function initMap() {
    // Default to Rome if geolocation fails
    const defaultLocation = { lat: 41.9028, lng: 12.4964 };
    
    map = new google.maps.Map(document.getElementById("map"), {
        center: defaultLocation,
        zoom: 6,
        mapId: "tripcompass_map",
        streetViewControl: false,
        fullscreenControl: false,
        mapTypeControlOptions: {
            mapTypeIds: ["roadmap", "hybrid"]
        }
    });
    
    geocoder = new google.maps.Geocoder();
    infoWindow = new google.maps.InfoWindow();
    
    // Initialize autocomplete
    initAutocomplete();
    
    // Try to get user's location
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            position => {
                userCoords = {
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                };
                map.setCenter(userCoords);
                map.setZoom(12);
                
                // Add user location marker
                new google.maps.Marker({
                    position: userCoords,
                    map: map,
                    title: "You are here!",
                    icon: {
                        path: google.maps.SymbolPath.CIRCLE,
                        scale: 10,
                        fillColor: "#4285F4",
                        fillOpacity: 1,
                        strokeWeight: 2,
                        strokeColor: "#fff"
                    }
                });
            },
            error => {
                console.warn("Geolocation failed:", error.message);
                // Set today's date in the date input
                const today = new Date().toISOString().split('T')[0];
                document.getElementById('checkin-input').value = today;
            }
        );
    } else {
        console.warn("Geolocation not supported");
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('checkin-input').value = today;
    }
    
    // Handle map clicks
    map.addListener("click", event => {
        requestTripInfo(event.latLng.lat(), event.latLng.lng());
    });
}

function initAutocomplete() {
    const cityInput = document.getElementById("city-input");
    if (cityInput) {
        new google.maps.places.Autocomplete(cityInput, {
            types: ["(cities)"],
            fields: ["geometry", "name"]
        });
    }
}

function requestTripInfo(destLat, destLng) {
    // Clear previous marker
    if (marker) marker.setMap(null);
    
    // Add temporary marker
    const tempMarker = new google.maps.Marker({
        position: { lat: destLat, lng: destLng },
        map: map,
        title: "Selected destination",
        icon: {
            path: google.maps.SymbolPath.CIRCLE,
            scale: 12,
            fillColor: "#EA4335",
            fillOpacity: 1,
            strokeWeight: 3,
            strokeColor: "#fff"
        },
        zIndex: 999
    });
    
    // Get form values
    const checkInDate = document.getElementById("checkin-input").value;
    const adults = document.getElementById("adults-input").value || "1";
    const rooms = document.getElementById("rooms-input").value || "1";
    const originText = document.getElementById("origin-input").value.trim();
    
    // Validate inputs
    if (!checkInDate) {
        alert("Please select a check-in date");
        tempMarker.setMap(null);
        return;
    }
    
    // Build API URL
    let url = `/trip-info?lat=${destLat}&lng=${destLng}&checkInDate=${checkInDate}&adults=${adults}&roomQuantity=${rooms}`;
    
    if (originText) {
        url += `&origin=${encodeURIComponent(originText)}`;
    } else if (userCoords) {
        url += `&originLat=${userCoords.lat}&originLng=${userCoords.lng}`;
    } else {
        alert("Please enter an origin or enable location services");
        tempMarker.setMap(null);
        return;
    }
    
    // Show loading indicator
    document.getElementById('loading-indicator').classList.remove('hidden');
    document.querySelector('#search-btn .spinner').classList.remove('hidden');
    document.querySelector('#search-btn span').textContent = 'Searching...';
    
    // Fetch trip info
    fetch(url)
        .then(async response => {
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || "Unknown error occurred");
            }
            return response.json();
        })
        .then(data => {
            renderSidebar(data);
            showSidebar();
            tempMarker.setMap(null);
            
            // Add final marker
            marker = new google.maps.Marker({
                position: { lat: destLat, lng: destLng },
                map: map,
                title: "Destination",
                icon: {
                    path: google.maps.SymbolPath.CIRCLE,
                    scale: 12,
                    fillColor: "#34A853",
                    fillOpacity: 1,
                    strokeWeight: 3,
                    strokeColor: "#fff"
                },
                zIndex: 1000
            });
        })
        .catch(error => {
            console.error("Trip info error:", error.message);
            alert(`Error: ${error.message}`);
            hideSidebar();
        })
        .finally(() => {
            document.getElementById('loading-indicator').classList.add('hidden');
            document.querySelector('#search-btn .spinner').classList.add('hidden');
            document.querySelector('#search-btn span').textContent = 'Search';
        });
}