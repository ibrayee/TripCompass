// this file is responsible for loading google maps dynamically
// instead of hardcoding the api key in html, we fetch it from backend
// this is more secure and better practice

fetch('/config/maps-key')
    .then(res => {
        // if backend response is not ok, something went wrong
        if (!res.ok) {
            throw new Error('Failed to retrieve API key');
        }
        // convert response to json
        return res.json();
    })
    .then(data => {
        // create script tag dynamically
        const script = document.createElement('script');

        // build google maps url with api key from backend
        // callback=initMap means google will call initMap() after loading
        // libraries=marker is used for advanced markers
        script.src = `https://maps.googleapis.com/maps/api/js?key=${data.apiKey}&callback=initMap&libraries=marker`;

        // async and defer so it does not block page loading
        script.async = true;
        script.defer = true;

        // add script to html head
        document.head.appendChild(script);
    })
    .catch(err => {
        // if something fails (network, backend, wrong key)
        // we log the error in console
        console.error('Google Maps failed to load', err);
    });
