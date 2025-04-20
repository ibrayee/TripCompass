function renderSidebar(data) {
    const container = document.getElementById("results");
    container.innerHTML = "";

    // Hotels
    const hotelTitle = document.createElement("div");
    hotelTitle.className = "section-title";
    hotelTitle.textContent = "Hotels Nearby";
    container.appendChild(hotelTitle);

    data.hotels.forEach(h => {
        const hotel = document.createElement("div");
        hotel.className = "hotel";

        const name = h.offers[0].hotel.name;
        const price = h.offers[0].offers[0].price.total;
        const currency = h.offers[0].offers[0].price.currency;

        hotel.innerHTML = `<strong>${name}</strong><br/>Price: ${price} ${currency}`;
        container.appendChild(hotel);
    });

    // Flights
    const flightTitle = document.createElement("div");
    flightTitle.className = "section-title";
    flightTitle.textContent = "Available Flights";
    container.appendChild(flightTitle);

    data.flights.forEach(f => {
        const flight = document.createElement("div");
        flight.className = "flight";

        flight.innerHTML = `
      From: ${f.origin} → To: ${f.destination}<br/>
      Departure: ${formatDate(f.departure)}<br/>
      Price: ${f.price} ${f.currency}<br/>
      Airline: ${f.airline}
    `;
        container.appendChild(flight);
    });
}

function showSidebar() {
    document.getElementById("sidebar").classList.remove("hidden");
}

function hideSidebar() {
    document.getElementById("sidebar").classList.add("hidden");
}

function formatDate(dateStr) {
    const date = new Date(dateStr);
    const options = {
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    };
    return date.toLocaleString('en-GB', options); // 24h format
}
