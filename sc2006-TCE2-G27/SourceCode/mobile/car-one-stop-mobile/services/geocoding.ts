export type Suggestion = {
  place_id: string;
  display_name: string;
  lat: string;
  lon: string;
};

export const searchSuggestions = async (query: string): Promise<Suggestion[]> => {
  if (query.trim().length < 3) return [];
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=5&countrycodes=sg`,
      { headers: { 'User-Agent': 'car-one-stop-mobile' } }
    );
    return await response.json();
  } catch (error) {
    console.error('Geocoding error:', error);
    return [];
  }
};
