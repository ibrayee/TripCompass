document.addEventListener('DOMContentLoaded', () => {
    const loadMoreBtn = document.getElementById('loadMore');
    if (loadMoreBtn) {
        loadMoreBtn.addEventListener('click', renderNextHotels);
    }

    const form = document.getElementById('hotelForm');
    if (form) {
        form.addEventListener('submit', e => {
            e.preventDefault();
            fetchHotels();
        });
    } else {
        // Fallback: attempt to fetch immediately if no form present
        fetchHotels();
    }
});

let allHotelData = [];
let offset = 0;
const limit = 3;
function plotHotelsOnMap(hotels) {
    if (!Array.isArray(hotels) || typeof google === 'undefined' || !map) return;
    const bounds = new google.maps.LatLngBounds();
    hotels.forEach(hotelObj => {
        const hotelData = hotelObj.offers?.[0];
        const hotel = hotelData?.hotel || {};
        const lat = hotel.geoCode?.latitude || hotel.latitude;
        const lng = hotel.geoCode?.longitude || hotel.longitude;
        if (lat && lng) {
            const marker = new google.maps.Marker({
                position: { lat: parseFloat(lat), lng: parseFloat(lng) },
                map,
                title: hotel.name || 'Hotel'
            });
            if (typeof markers !== 'undefined') markers.push(marker);
            bounds.extend(marker.getPosition());
        }
    });
    if (!bounds.isEmpty()) {
        map.fitBounds(bounds);
    }
}


async function fetchHotels() {
    const destination = document.getElementById('destination')?.value.trim() || '';
    const checkInDate = document.getElementById('checkInDate')?.value || '';
    const adults = document.getElementById('adults')?.value || '1';
    const rooms = document.getElementById('rooms')?.value || '1';

    // Reset paginazione
    allHotelData = [];
    offset = 0;

    const loadMoreEl = document.getElementById('loadMore');
    if (loadMoreEl) loadMoreEl.style.display = 'none';

    const resultsDiv = document.getElementById('results');
    if (resultsDiv) resultsDiv.innerHTML = '<div class="loading-spinner"></div>';

    if (typeof clearMap === 'function') clearMap();

    let apiKey, geoData, response, data;

    try {
        showLoading?.();

        // 1) Chiave Maps
        const keyRes = await fetch('/config/maps-key');
        if (!keyRes.ok) throw new Error('Failed to load Maps API key');
        ({ apiKey } = await keyRes.json());

        // 2) Geocoding
        const geoRes = await fetch(`https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(destination)}&key=${apiKey}`);
        geoData = await geoRes.json();
        if (geoData.status !== 'OK' || !geoData.results?.length) {
            throw new Error('Destination not found');
        }
        const { lat, lng } = geoData.results[0].geometry.location;

        // 3) Backend search
        const url = `/search/nearby?lat=${lat}&lng=${lng}&checkInDate=${encodeURIComponent(checkInDate)}&adults=${encodeURIComponent(adults)}&roomQuantity=${encodeURIComponent(rooms)}`;
        response = await fetch(url);
        if (!response.ok) throw new Error(`Server error: ${response.status}`);

        data = await response.json();

        if (resultsDiv) resultsDiv.innerHTML = '';

        // Controllo struttura risultati
        if (!data?.offers || !Array.isArray(data.offers) || data.offers.length === 0) {
            if (resultsDiv) {
                resultsDiv.innerHTML = `
          <div class="error-message">
            <h3>No Hotels Found</h3>
            <p>We couldn't find any hotels matching your criteria.</p>
            <p>Try adjusting your search.</p>
          </div>`;
            }
            return;
        }

        // Salva e renderizza
        allHotelData.push(...data.offers);
        renderNextHotels();

        // Mappa
        plotHotelsOnMap(allHotelData.slice(0, limit));

        // Sidebar

        if (loadMoreEl && allHotelData.length > limit) {
            loadMoreEl.style.display = 'block';
        }

    } catch (err) {
        if (resultsDiv) {
            resultsDiv.innerHTML = `
        <div class="error-message">
          <h3>Error Loading Hotels</h3>
          <p>${err.message}</p>
          <p>Please try again later.</p>
        </div>`;
        }
        console.error('Hotel fetch error:', err);
    } finally {
        hideLoading?.();
    }
}


function renderNextHotels() {
    const container = document.getElementById('results');
    const nextHotels = allHotelData.slice(offset, offset + limit);
    offset += limit;

    nextHotels.forEach(hotelObj => {
        const hotelData = hotelObj.offers[0];
        const hotel = hotelData.hotel;
        const offer = hotelData.offers[0];
        const imageUrl = hotel.media?.[0]?.uri || 'https://via.placeholder.com/150?text=No+Image';

        const div = document.createElement('div');
        div.className = 'hotel-card';

        div.innerHTML = `
            <img src="${imageUrl}" alt="${hotel.name}" class="hotel-thumb">
            <h3>${hotel.name}</h3>
            <p>${hotel.address?.lines?.join(', ') || ''}</p>
            <p>${hotel.address?.cityName || ''}, ${hotel.address?.countryCode || ''}</p>
            
            <div class="flex">
                <span>Rating: ${hotel.rating || 'N/A'}</span>
                <span>Room type: ${offer.room?.type || 'Standard'}</span>
            </div>
            
            <p>${offer.room?.description?.text || 'Comfortable accommodation'}</p>
            
            <span class="price-tag">${offer.price.total} ${offer.price.currency}</span>
            <button class="btn-secondary">View Details & Book</button>
        `;

        container.appendChild(div);
    });
    if (offset > limit) {
        plotHotelsOnMap(nextHotels);
    }
    if (offset >= allHotelData.length) {
        document.getElementById('loadMore').style.display = 'none';
    }

}