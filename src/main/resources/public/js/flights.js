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
});

async function handleFlightSearch(e) {
    e.preventDefault();
    
    const origin = document.getElementById('origin').value.trim().toUpperCase();
    const destination = document.getElementById('destination').value.trim().toUpperCase();
    const departureDate = document.getElementById('departureDate').value;
    const adults = document.getElementById('adults').value;
    
    const searchBtn = document.getElementById('flight-search-btn');
    searchBtn.querySelector('.spinner').classList.remove('hidden');
    searchBtn.querySelector('span').textContent = 'Searching...';
    searchBtn.disabled = true;
    
    const url = `/search/flights?origin=${origin}&destination=${destination}&departureDate=${departureDate}&adults=${adults}`;
    
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
        const data = await response.json();
        
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
        // Reset button state
        searchBtn.querySelector('.spinner').classList.add('hidden');
        searchBtn.querySelector('span').textContent = 'Search Flights';
        searchBtn.disabled = false;
    }
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