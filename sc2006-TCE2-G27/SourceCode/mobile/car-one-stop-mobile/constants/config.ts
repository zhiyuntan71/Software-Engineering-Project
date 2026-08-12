// =====================================
// NGROK / PHYSICAL PHONE BACKEND OVERRIDE
// =====================================
// Set EXPO_PUBLIC_BACKEND_URL when testing on a physical phone via ngrok.
// Example: https://abc123.ngrok-free.app
const NGROK_OR_PHONE_BACKEND_URL = process.env.EXPO_PUBLIC_BACKEND_URL;

// =====================================
// FALLBACK TO EXISTING EMULATOR URL
// =====================================
const EMULATOR_FALLBACK_BACKEND_URL = 'http://10.0.2.2:8080';

// =====================================
// CENTRALIZED BACKEND URL SOURCE OF TRUTH
// =====================================
const RAW_BACKEND_URL = NGROK_OR_PHONE_BACKEND_URL || EMULATOR_FALLBACK_BACKEND_URL;
export const BACKEND_URL = RAW_BACKEND_URL.replace(/\/+$/, '');
export const API_BASE_URL = `${BACKEND_URL}/api`;

export const SEARCH_RADIUS_KM = 1;
export const SEARCH_DEBOUNCE_MS = 400;
export const DEFAULT_REGION = {
  latitude: 1.3521,
  longitude: 103.8198,
  latitudeDelta: 0.1,
  longitudeDelta: 0.1,
};
