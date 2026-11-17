let consent = false;
export function setAnalyticsConsent(accepted) {
  consent = accepted === true;
}
export function trackPageView() {
  if (!consent) return;
}

