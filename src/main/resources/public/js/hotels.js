document.addEventListener('DOMContentLoaded', () => {
    const loadMoreBtn = document.getElementById('loadMore');
    if (loadMoreBtn) {
        loadMoreBtn.addEventListener('click', renderNextHotels);
        fetchHotels();
    }
});

const allHotelData = [];
let offset = 0;
const limit = 3;

async function fetchHotels() {
    const url = '/search/nearby?lat=48.860698&lng=2.349385&checkInDate=2025-07-10&adults=2&roomQuantity=1';
    
    // Show loading state
    const resultsDiv = document.getElementById('results');
    resultsDiv.innerHTML = '<div class="loading-spinner"></div>';
    
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
        const data = await response.json();
        
        resultsDiv.innerHTML = '';
        
        if (!data.offers || data.offers.length === 0) {
            resultsDiv.innerHTML = `
                <div class="error-message">
                    <h3>No Hotels Found</h3>
                    <p>We couldn't find any hotels matching your criteria.</p>
                    <p>adjust your search</p>
                </div>
            `;
            return;
        }
        
        allHotelData.push(...data.offers);
        renderNextHotels();
        
        if (allHotelData.length > limit) {
            document.getElementById('loadMore').style.display = 'block';
        }
    } catch (err) {
        resultsDiv.innerHTML = `
            <div class="error-message">
                <h3>Error Loading Hotels</h3>
                <p>${err.message}</p>
                <p>Please try again later.</p>
            </div>
        `;
        console.error('Hotel fetch error:', err);
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
        
        const div = document.createElement('div');
        div.className = 'hotel-card';
        
        div.innerHTML = `
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
    
    if (offset >= allHotelData.length) {
        document.getElementById('loadMore').style.display = 'none';
    }
}