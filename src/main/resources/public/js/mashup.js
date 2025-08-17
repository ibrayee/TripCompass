let map;
let markers = [];
let polylines = [];
const PLACEHOLDER_IMG = 'https://via.placeholder.com/150?text=No+Image';

function initMap() {
    map = new google.maps.Map(document.getElementById('map'), {
        center: { lat: 48.8566, lng: 2.3522 },
        zoom: 4,
        mapId: 'tripcompass_map',
        streetViewControl: false,
        fullscreenControl: false
    });
}

function clearMap() {
    polylines.forEach(p => p.setMap(null));
    polylines = [];
    markers.forEach(m => m.setMap(null));
    markers = [];
}

function decodePolyline(encoded) {
    let points = [];
    let index = 0, len = encoded.length;
    let lat = 0, lng = 0;

    while (index < len) {
        let b, shift = 0, result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlat = (result & 1) ? ~(result >> 1) : (result >> 1);
        lat += dlat;

        shift = 0;
        result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlng = (result & 1) ? ~(result >> 1) : (result >> 1);
        lng += dlng;

        points.push({ lat: lat / 1e5, lng: lng / 1e5 });
    }
    return points;
}

function drawPolyline(encoded, color) {
    const path = decodePolyline(encoded);
    const poly = new google.maps.Polyline({
        path,
        geodesic: true,
        strokeColor: color || '#FF0000',
        strokeOpacity: 0.7,
        strokeWeight: 4,
        map
    });
    polylines.push(poly);
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        month: 'short',
        day: 'numeric'
    });
}

function displayFlight(flight, container) {
    const div = document.createElement('div');
    div.className = 'flight-card';
    div.innerHTML = `
        <h3>${flight.airline} Flight</h3>
        <div class="flex">
            <div>
                <p><strong>${flight.origin} → ${flight.destination}</strong></p>
                <p>Departure: ${formatDateTime(flight.departure)}</p>
                <p>Arrival: ${formatDateTime(flight.arrival)}</p>
            </div>
            <div>
                <p>Duration: ${flight.duration}</p>
                <p>Class: ${flight.class || 'Economy'}</p>
            </div>
        </div>
        <span class="price-tag">${flight.price} ${flight.currency}</span>
    `;
    container.appendChild(div);
}

function renderHotel(hotelObj, container) {
    const hotelData = hotelObj.offers[0];
    const hotel = hotelData.hotel;
    const offer = hotelData.offers[0];
    const imageUrl = hotel.media?.[0]?.uri || PLACEHOLDER_IMG;
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
    `;
    container.appendChild(div);
}

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
            markers.push(marker);
            bounds.extend(marker.getPosition());
        }
    });
    if (!bounds.isEmpty()) {
        map.fitBounds(bounds);
    }
}

function setupIataAutocomplete(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const listId = `${inputId}-options`;
    let dataList = document.getElementById(listId);
    if (!dataList) {
        dataList = document.createElement('datalist');
        dataList.id = listId;
        document.body.appendChild(dataList);
        input.setAttribute('list', listId);
    }
    input.addEventListener('input', async () => {
        const query = input.value.trim().toUpperCase();
        input.value = query;
        if (query.length < 2) {
            dataList.innerHTML = '';
            return;
        }
        try {
            showLoading();
            const res = await fetch(`/search/locations?keyword=${encodeURIComponent(query)}`);
            if (!res.ok) return;
            const locations = await res.json();
            dataList.innerHTML = locations
                .filter(loc => loc.iataCode)
                .map(loc => `<option value="${loc.iataCode}">${loc.name || ''}</option>`)
                .join('');
        } catch (err) {
            console.error('Autocomplete error', err);
        } finally {
            hideLoading();
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    const dateInput = document.getElementById('departureDate');
    if (dateInput) {
        dateInput.min = today;
        dateInput.value = today;
    }
    setupIataAutocomplete('origin');
    setupIataAutocomplete('destination');
    const form = document.getElementById('mashup-form');
    if (form) {
        form.addEventListener('submit', handleMashupSearch);
    }
});

async function handleMashupSearch(e) {
    e.preventDefault();
    clearMap();
    const origin = document.getElementById('origin').value.trim().toUpperCase();
    const destination = document.getElementById('destination').value.trim().toUpperCase();
    const departureDate = document.getElementById('departureDate').value;
    const adults = document.getElementById('adults').value;
    const includeHotels = document.getElementById('includeHotels').checked;
    const flightContainer = document.getElementById('flight-results');
    const hotelSection = document.getElementById('hotel-section');
    const hotelContainer = document.getElementById('hotel-results');
    flightContainer.innerHTML = '';
    hotelContainer.innerHTML = '';
    hotelSection.style.display = 'none';
    const searchBtn = document.getElementById('mashup-search-btn');
    searchBtn.querySelector('.spinner').classList.remove('hidden');
    searchBtn.querySelector('span').textContent = 'Searching...';
    searchBtn.disabled = true;
    try {
        showLoading();
        const routeRes = await fetch(`/mashupJavalin/flightsAndPolyline?startPlace=${encodeURIComponent(origin)}&endPlace=${encodeURIComponent(destination)}`);
        if (routeRes.ok) {
            const routeData = await routeRes.json();
            if (routeData.polyline) {
                drawPolyline(routeData.polyline, '#FF0000');
            }
        }
        const flightRes = await fetch(`/search/flights?origin=${origin}&destination=${destination}&departureDate=${departureDate}&adults=${adults}`);
        const flightData = await flightRes.json();
        if (flightRes.ok && Array.isArray(flightData) && flightData.length) {
            flightData.forEach(f => displayFlight(f, flightContainer));
        } else {
            flightContainer.innerHTML = '<div class="error-message"><h3>No Flights Found</h3></div>';
        }
        if (includeHotels) {
            const keyRes = await fetch('/config/maps-key');
            if (keyRes.ok) {
                const { apiKey } = await keyRes.json();
                const geoRes = await fetch(`https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(destination)}&key=${apiKey}`);
                const geoData = await geoRes.json();
                if (geoData.status === 'OK' && geoData.results.length) {
                    const { lat, lng } = geoData.results[0].geometry.location;
                    const hotelRes = await fetch(`/search/nearby?lat=${lat}&lng=${lng}&checkInDate=${departureDate}&adults=${adults}&roomQuantity=1`);
                    if (hotelRes.ok) {
                        const hotelData = await hotelRes.json();
                        const offers = hotelData.offers || [];
                        if (offers.length) {
                            hotelSection.style.display = 'block';
                            offers.forEach(h => renderHotel(h, hotelContainer));
                            plotHotelsOnMap(offers);
                        }
                    }
                }
            }
        }
    } catch (err) {
        console.error('Mashup search error:', err);
        flightContainer.innerHTML = `<div class="error-message"><h3>Error</h3><p>${err.message}</p></div>`;
    } finally {
        hideLoading();
        searchBtn.querySelector('.spinner').classList.add('hidden');
        searchBtn.querySelector('span').textContent = 'Search';
        searchBtn.disabled = false;
    }
}