function renderSidebar(data, mode) {
    console.log("Sidebar data:", data);
    const container = document.getElementById("results");
    container.innerHTML = "";

    if ((mode === 'hotels' || mode === 'trip') && data.hotels && data.hotels.length) {
        const hotelTitle = document.createElement("div");
        hotelTitle.className = "section-title";
        hotelTitle.textContent = "Hotels Nearby";
        container.appendChild(hotelTitle);

        data.hotels.forEach(h => {
            const hotel = document.createElement("div");
            hotel.className = "hotel";

            const offerContainer = Array.isArray(h.offers) ? h.offers[0] : null;
            const name = offerContainer?.hotel?.name || "Hotel";
            const firstOffer = Array.isArray(offerContainer?.offers) ? offerContainer.offers[0] : null;
            const price = firstOffer?.price?.total || "N/A";
            const currency = firstOffer?.price?.currency || "";

            hotel.innerHTML = `<strong>${name}</strong><br/>Price: ${price} ${currency}`;
            container.appendChild(hotel);
        });
    }
    if ((mode === 'flights' || mode === 'trip') && data.flights && data.flights.length) {
        const flightTitle = document.createElement("div");
        flightTitle.className = "section-title";
        flightTitle.textContent = "Available Flights";
        container.appendChild(flightTitle);

        data.flights.forEach(f => {
            const flight = document.createElement("div");
            flight.className = "flight";

            flight.innerHTML = `
      From: ${f.origin || "-"} → To: ${f.destination || "-"}<br/>
      Departure: ${f.departure ? formatDate(f.departure) : "N/A"}<br/>
      Price: ${f.price || "N/A"} ${f.currency || ""}<br/>
      Airline: ${f.airline || "N/A"}
    `;
            container.appendChild(flight);
        });
    }

    if (container.innerHTML.trim() === "") {
        container.innerHTML = "<p class=\"muted\">No results available for the selected search.</p>";
    }
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