function initAutocomplete() {
    const selectors = ['#city-input', '#origin-input', '#hotel-destination'];    selectors.forEach(selector => {
        const input = document.querySelector(selector);
        if (!input) return;

        const options = selector === '#city-input' ? { types: ['(cities)'] } : {};
        const autocomplete = new google.maps.places.Autocomplete(input, {
            ...options,
            fields: ['geometry', 'name']
        });

        autocomplete.addListener('place_changed', () => {
            const place = autocomplete.getPlace();
            if (!place.geometry || !place.geometry.location) return;

            input.dataset.lat = place.geometry.location.lat();
            input.dataset.lng = place.geometry.location.lng();
            input.dataset.place = place.name || '';
        });
    });
}
