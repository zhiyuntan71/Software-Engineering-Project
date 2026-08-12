// Keys must match your Java CarType enum constants EXACTLY
export const CAR_MODELS: Record<string, string[]> = {
  SEDAN:    ['TOYOTA_CAMRY', 'HONDA_CIVIC', 'BMW_3_SERIES', 'MERCEDES_C_CLASS', 'NISSAN_ALTIMA'],
  SUV:      ['TOYOTA_RAV4', 'HONDA_CRV', 'BMW_X5', 'HYUNDAI_TUCSON', 'FORD_EXPLORER'],
  ELECTRIC: ['TESLA_MODEL_3', 'TESLA_MODEL_Y', 'BYD_ATTO_3', 'HYUNDAI_IONIQ_5', 'BMW_IX'],
};

// Must match your Java ChargingType enum constants EXACTLY
export const CHARGING_TYPES: string[] = [
  'NOT_APPLICABLE',
  'TYPE_1_J1772',
  'TYPE_2_MENNEKES',
  'CCS',
  'CHADEMO',
  'TESLA_SUPERCHARGER',
];

// Update these to match your actual Java enum names
