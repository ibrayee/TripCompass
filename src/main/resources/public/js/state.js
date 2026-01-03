window.appState = {
    userCoords: null,
    destinationCoords: null,
    originAddress: "",
    loading: false,
    cache: new Map(),
    inflight: new Map(),
    setLoading(isLoading) {
        this.loading = isLoading;
        const btn = document.getElementById("search-btn");
        if (btn) {
            btn.disabled = isLoading;
            btn.textContent = isLoading ? "Loading..." : "Search";
        }
        const banner = document.getElementById("status-banner");
        if (banner) {
            if (isLoading) {
                banner.classList.remove("error");
                banner.textContent = "Loading results...";
                banner.classList.add("visible");
            } else if (!banner.classList.contains("error")) {
                banner.classList.remove("visible");
                banner.textContent = "";
            }
        }
    },
    showError(message) {
        const banner = document.getElementById("status-banner");
        if (banner) {
            banner.textContent = message;
            banner.classList.add("visible", "error");
        } else {
            alert(message);
        }
    },
    clearError() {
        const banner = document.getElementById("status-banner");
        if (banner) {
            banner.textContent = "";
            banner.classList.remove("error");
            if (!this.loading) {
                banner.classList.remove("visible");
            }
        }
    }
};