function searchCity() {
    const destination = document.getElementById('city-input').value.trim();
    if (!destination) {
        alert('Please enter a destination city');
        return;
    }

    // loading indicator and updates search button
    const loadingIndicator = document.getElementById('loading-indicator');
    const searchBtnSpinner = document.querySelector('#search-btn .spinner');
    const searchBtnText = document.querySelector('#search-btn span');

    loadingIndicator.classList.remove('hidden');
    searchBtnSpinner.classList.remove('hidden');
    searchBtnText.textContent = 'Searching...';

    // Geocode destination
    geocoder.geocode({ address: destination }, (results, status) => {
        if (status === 'OK' && results[0]) {
            const location = results[0].geometry.location;

            // Centers the map on the location
            
            requestTripInfo(location.lat(), location.lng());
        } else {
            alert(`Could not find location: ${status}`);
            hideSidebar();
        }
    }).finally(() => {
        // Reset loading indicators regardless of success or failure
        loadingIndicator.classList.add('hidden');
        searchBtnSpinner.classList.add('hidden');
        searchBtnText.textContent = 'Search';
    });
}