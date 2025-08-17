function showLoading() {
    document.getElementById('loading-overlay')?.classList.remove('hidden');
}

function hideLoading() {
    document.getElementById('loading-overlay')?.classList.add('hidden');
}

window.showLoading = showLoading;
window.hideLoading = hideLoading;

export { showLoading, hideLoading };