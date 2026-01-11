function renderSidebar(data, mode) {
    // debug to see what backend returns
    console.log("Sidebar data:", data);

    const container = document.getElementById("results");
    // clear previous results every time
    container.innerHTML = "";

    // ---------------- HOTELS (for hotels mode OR trip mode) ----------------
    if ((mode === 'hotels' || mode === 'trip') && data.hotels && data.hotels.length) {
        // section title
        const hotelTitle = document.createElement("div");
        hotelTitle.className = "section-title";
        hotelTitle.textContent = "Hotels Nearby";
        container.appendChild(hotelTitle);

        data.hotels.forEach(h => {
            const hotel = document.createElement("div");
            hotel.className = "hotel";

            // the data structure seems nested: h.offers[0].hotel.name and h.offers[0].offers[0].price
            const offerContainer = Array.isArray(h.offers) ? h.offers[0] : null;
            const name = offerContainer?.hotel?.name || "Hotel";

            const firstOffer = Array.isArray(offerContainer?.offers) ? offerContainer.offers[0] : null;
            const price = firstOffer?.price?.total || "N/A";
            const currency = firstOffer?.price?.currency || "";

            // simple html for the card
            hotel.innerHTML = `<strong>${name}</strong><br/>Price: ${price} ${currency}`;
            container.appendChild(hotel);
        });
    }

    // ---------------- FLIGHTS (for flights mode OR trip mode) ----------------
    if ((mode === 'flights' || mode === 'trip') && data.flights && data.flights.length) {
        // section title
        const flightTitle = document.createElement("div");
        flightTitle.className = "section-title";
        flightTitle.textContent = "Available Flights";
        container.appendChild(flightTitle);

        data.flights.forEach(f => {
            const flight = document.createElement("div");
            flight.className = "flight";

            // show flight info in a readable way
            // origin/destination might be missing so we fallback with "-"
            flight.innerHTML = `
      From: ${f.origin || "-"} → To: ${f.destination || "-"}<br/>
      Departure: ${f.departure ? formatDate(f.departure) : "N/A"}<br/>
      Price: ${f.price || "N/A"} ${f.currency || ""}<br/>
      Airline: ${f.airline || "N/A"}
    `;
            container.appendChild(flight);
        });
    }

    // if nothing was appended, show a generic message
    if (container.innerHTML.trim() === "") {
        container.innerHTML = "<p class=\"muted\">No results available for the selected search.</p>";
    }
}

function showSidebar() {
    // show sidebar by removing hidden class
    document.getElementById("sidebar").classList.remove("hidden");
}

function hideSidebar() {
    // hide sidebar by adding hidden class
    document.getElementById("sidebar").classList.add("hidden");
}

function formatDate(dateStr) {
    // format date in a nice 24h format
    // note: dateStr must be a valid date string or it can become "Invalid Date"
    const date = new Date(dateStr);

    const options = {
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    };

    return date.toLocaleString('en-GB', options); // 24h format
}
