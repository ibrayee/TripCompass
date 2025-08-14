function renderSidebar(data) {
    const container = document.getElementById("results");
    if (!container) return;
    
    container.innerHTML = "";
    
    if (!data || (!data.hotels && !data.flights)) {
        container.innerHTML = `
            <div class="error-message">
                <h3>No Results Found</h3>
                <p>We couldn't find any travel options for your selected destination.</p>
                <p>Try adjusting your search criteria or selecting a different location.</p>
            </div>
        `;
        return;
    }
    
    // Create header with destination info
    const header = document.createElement('div');
    header.className = 'trip-header';
    header.innerHTML = `
        <h2>Trip to ${data.destinationName || 'Your Destination'}</h2>
        <p><strong>From:</strong> ${data.originAirport || 'N/A'} 
           <strong>To:</strong> ${data.destinationAirport || 'N/A'}</p>
        <p><strong>Date:</strong> ${formatDate(data.checkInDate)}</p>
    `;
    container.appendChild(header);
    
    // Hotels section
    if (data.hotels && data.hotels.length > 0) {
        const hotelTitle = document.createElement("div");
        hotelTitle.className = "section-title";
        hotelTitle.textContent = `Recommended Hotels`;
        container.appendChild(hotelTitle);
        
        data.hotels.forEach(hotel => {
            const hotelDiv = document.createElement("div");
            hotelDiv.className = "hotel-card";
            
            const name = hotel.hotel?.name || "Unknown Hotel";
            const address = hotel.hotel?.address || {};
            const price = hotel.price?.total || "N/A";
            const currency = hotel.price?.currency || "";
            const rating = hotel.hotel?.rating || "N/A";
            
            hotelDiv.innerHTML = `
                <h3>${name}</h3>
                <p>${address.lines?.join(', ') || ''}</p>
                <p>${address.cityName || ''}, ${address.countryCode || ''}</p>
                <div class="flex">
                    <span>Rating: ${rating}</span>
                    <span>Rooms: ${hotel.roomQuantity || 1}</span>
                </div>
                <span class="price-tag">${price} ${currency}</span>
                <button class="btn-secondary">View Details & Book</button>
            `;
            container.appendChild(hotelDiv);
        });
    }
    
    // Flights section
    if (data.flights && data.flights.length > 0) {
        const flightTitle = document.createElement("div");
        flightTitle.className = "section-title";
        flightTitle.textContent = `Available Flights`;
        container.appendChild(flightTitle);
        
        data.flights.forEach(flight => {
            const flightDiv = document.createElement("div");
            flightDiv.className = "flight-card";
            
            flightDiv.innerHTML = `
                <h3>${flight.airline} Flight</h3>
                <div class="flex">
                    <div>
                        <p><strong>${flight.origin} → ${flight.destination}</strong></p>
                        <p>Departure: ${formatDateTime(flight.departure)}</p>
                        <p>Arrival: ${formatDateTime(flight.arrival)}</p>
                    </div>
                    <div>
                        <p>Duration: ${flight.duration}</p>
                        <p>Seats: ${flight.seats || 'N/A'}</p>
                    </div>
                </div>
                <span class="price-tag">${flight.price} ${flight.currency}</span>
                <button class="btn-secondary">Select Flight</button>
            `;
            container.appendChild(flightDiv);
        });
    }
}

function showSidebar() {
    const sidebar = document.getElementById("sidebar");
    if (sidebar) {
        sidebar.classList.remove("hidden");
    }
}

function hideSidebar() {
    const sidebar = document.getElementById("sidebar");
    if (sidebar) {
        sidebar.classList.add("hidden");
    }
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
        weekday: 'short',
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
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