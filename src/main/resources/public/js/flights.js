document.addEventListener('DOMContentLoaded', () => {
    const flightForm = document.getElementById('flightForm');
    if (flightForm) {
        flightForm.addEventListener('submit', handleFlightSearch);
        
        // Sets date to today
        const today = new Date().toISOString().split('T')[0];
        const dateInput = document.getElementById('departureDate');
        if (dateInput) {
            dateInput.min = today;
            dateInput.value = today;
        }
    }
    setupIataAutocomplete('origin');
    setupIataAutocomplete('destination');
});

async function handleFlightSearch(e) {
    e.preventDefault();
    
    const origin = document.getElementById('origin').value.trim().toUpperCase();
    const destination = document.getElementById('destination').value.trim().toUpperCase();
    const departureDate = document.getElementById('departureDate').value;
    const adults = document.getElementById('adults').value;
    const iataRegex = /^[A-Z]{3}$/;
    if (!iataRegex.test(origin) || !iataRegex.test(destination)) {
        document.getElementById('results').innerHTML = `
            <div class="error-message">
                <h3>Invalid Airport Code</h3>
                <p>Please enter valid three-letter IATA codes for both origin and destination.</p>
            </div>
        `;
        return;
    }
    const searchBtn = document.getElementById('flight-search-btn');
    searchBtn.querySelector('.spinner').classList.remove('hidden');
    searchBtn.querySelector('span').textContent = 'Searching...';
    searchBtn.disabled = true;
    
    const url = `/search/flights?origin=${origin}&destination=${destination}&departureDate=${departureDate}&adults=${adults}`;
    
    try {
        showLoading();
        const response = await fetch(url);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || `Server error: ${response.status}`);        }

        const resultsDiv = document.getElementById('results');
        resultsDiv.innerHTML = '';
        
        if (!data.length) {
            resultsDiv.innerHTML = `
                <div class="error-message">
                    <h3>No Flights Found</h3>
                    <p>We couldn't find any flights matching your criteria.</p>
                    <p>Try adjusting your search dates or airports.</p>
                </div>
            `;
            return;
        }
        
        const directMatches = data.filter(flight => flight.destination === destination);
        const fallback = data.filter(flight => flight.destination !== destination);
        
        if (directMatches.length > 0) {
            directMatches.forEach(flight => displayFlight(flight, resultsDiv));
        } else {
            const msg = document.createElement('div');
            msg.className = 'info-message';
            msg.innerHTML = `
                <h3>No Direct Flights to ${destination}</h3>
                <p>We found these alternative flights to nearby airports:</p>
            `;
            resultsDiv.appendChild(msg);
            fallback.forEach(flight => displayFlight(flight, resultsDiv));
        }
    } catch (err) {
        document.getElementById('results').innerHTML = `
            <div class="error-message">
                <h3>Error Loading Flights</h3>
                <p>${err.message}</p>
                <p>Please try again later.</p>
            </div>
        `;
        console.error('Flight search error:', err);
    } finally {
        hideLoading();
        // Reset button state
        searchBtn.querySelector('.spinner').classList.add('hidden');
        searchBtn.querySelector('span').textContent = 'Search Flights';
        searchBtn.disabled = false;
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
        <button class="btn-secondary">Select Flight</button>
    `;
    
    container.appendChild(div);
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