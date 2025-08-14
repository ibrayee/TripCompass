function initSimpleMap() { 
    const defaultLocation = { lat: 41.9028, lng: 12.4964 }; // Rome
    const map = new google.maps.Map(document.getElementById('simple-map'), {
        center: defaultLocation,
        zoom: 12,
        mapTypeId: 'roadmap',
        streetViewControl: false,
        fullscreenControl: true
    });
    
    const infoWindow = new google.maps.InfoWindow();
    
    // Add click event listener
    map.addListener('click', (event) => {
        const clickedLocation = event.latLng;
        
        infoWindow.close();
        
        const marker = new google.maps.Marker({
            position: clickedLocation,
            map: map,
            title: 'Selected Location'
        });
        
        // Fetch nearby data
        fetchNearbyData(clickedLocation.lat(), clickedLocation.lng())
            .then(data => {
                const content = `
                    <div class="info-window">
                        <h3>Location Details</h3>
                        <p><strong>Latitude:</strong> ${data.latitude.toFixed(6)}</p>
                        <p><strong>Longitude:</strong> ${data.longitude.toFixed(6)}</p>
                        <p><strong>Nearby Places:</strong></p>
                        <ul>
                            ${data.nearby && data.nearby.length > 0 
                                ? data.nearby.slice(0, 3).map(place => 
                                    `<li>${place.name} (${place.distance}m)</li>`).join('')
                                : '<li>No nearby places found</li>'}
                        </ul>
                    </div>
                `;
                
                infoWindow.setContent(content);
                infoWindow.open(map, marker);
            })
            .catch(error => {
                const content = `
                    <div class="info-window">
                        <h3>Location Details</h3>
                        <p><strong>Latitude:</strong> ${clickedLocation.lat().toFixed(6)}</p>
                        <p><strong>Longitude:</strong> ${clickedLocation.lng().toFixed(6)}</p>
                        <p class="error">Error loading nearby places</p>
                    </div>
                `;
                infoWindow.setContent(content);
                infoWindow.open(map, marker);
                console.error('Error fetching nearby data:', error);
            });
    });
}

async function fetchNearbyData(lat, lng) {
    const response = await fetch(`/search/nearby?lat=${lat}&lng=${lng}`);
    if (!response.ok) {
        throw new Error('Failed to fetch nearby data');
    }
    return await response.json();
}