function searchCity() {
    const destinationInput = document.getElementById('city-input');
    const destination = destinationInput.value.trim();    if (!destination) {
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

    const lat = destinationInput.dataset.lat;
    const lng = destinationInput.dataset.lng;

    if (lat && lng) {
        requestTripInfo(parseFloat(lat), parseFloat(lng));
        return;
    }

    // Geocode destination if no autocomplete data
    geocoder.geocode({ address: destination }, (results, status) => {
        if (status === 'OK' && results[0]) {
            const location = results[0].geometry.location;

            // Centers the map on the location
            
            requestTripInfo(location.lat(), location.lng());
        } else {
            alert(`Could not find location: ${status}`);
            hideSidebar();
            loadingIndicator.classList.add('hidden');
            searchBtnSpinner.classList.add('hidden');
            searchBtnText.textContent = 'Search';
        }
    }).finally(() => {
        // Reset loading indicators regardless of success or failure
        loadingIndicator.classList.add('hidden');
        searchBtnSpinner.classList.add('hidden');
        searchBtnText.textContent = 'Search';
    });
}