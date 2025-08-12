fetch('/config/maps-key')
    .then(res => {
        if (!res.ok) {
            throw new Error('Failed to retrieve API key');
        }
        return res.json();
    })
    .then(data => {
        const script = document.createElement('script');
        script.src = `https://maps.googleapis.com/maps/api/js?key=${data.apiKey}&callback=initMap&libraries=marker`;
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
    })
    .catch(err => {
        console.error('Google Maps failed to load', err);
    });