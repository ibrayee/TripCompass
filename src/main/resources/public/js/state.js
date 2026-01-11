// global app state stored in window, so other files can access it easily
// (example: map.js, sidebar.js, searchBar.js)
window.appState = {
    // user origin coordinates (from geolocation or typed input)
    userCoords: null,

    // destination coordinates (from map click or autocomplete)
    destinationCoords: null,

    // keep the formatted origin address (used to detect if user changed it)
    originAddress: "",

    // global loading flag to disable UI while fetching
    loading: false,

    // cache results to avoid calling backend again for same query
    // key is a string like "trip-lat-lng-originLat-originLng-date-adults-rooms..."
    cache: new Map(),

    // inflight requests map: if the same request is already running,
    // we reuse the same promise instead of sending 2 requests
    inflight: new Map(),

    setLoading(isLoading) {
        // update loading state
        this.loading = isLoading;

        // disable search button during loading, and change the text
        const btn = document.getElementById("search-btn");
        if (btn) {
            btn.disabled = isLoading;
            btn.textContent = isLoading ? "Loading..." : "Search";
        }

        // update a status banner at top (or somewhere in UI)
        const banner = document.getElementById("status-banner");
        if (banner) {
            if (isLoading) {
                // when loading starts, remove error styling and show loading message
                banner.classList.remove("error");
                banner.textContent = "Loading results...";
                banner.classList.add("visible");

            } else if (!banner.classList.contains("error")) {
                // when loading finishes, hide banner ONLY if it is not an error
                // so error message stays visible
                banner.classList.remove("visible");
                banner.textContent = "";
            }
        }
    },

    showError(message) {
        // show an error message to the user
        const banner = document.getElementById("status-banner");
        if (banner) {
            banner.textContent = message;
            banner.classList.add("visible", "error");
        } else {
            // fallback if banner does not exist in html
            alert(message);
        }
    },

    clearError() {
        // clear error message and hide banner (only if not loading)
        const banner = document.getElementById("status-banner");
        if (banner) {
            banner.textContent = "";
            banner.classList.remove("error");

            // hide banner only when app is not in loading state
            // so loading message can still show
            if (!this.loading) {
                banner.classList.remove("visible");
            }
        }
    }
};
