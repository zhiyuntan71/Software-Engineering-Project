import React from 'react';
import { View, Text } from 'react-native';

// ── RankPin ─────────────────────────────────────────────────────────────────
// Teardrop pin with a rank number, used for top-3 results.
// Shape: filled circle (36×36) + border-trick triangle tip below.
// Total height ~50px.

const RANK_STYLES: Record<1 | 2 | 3, { body: string; inner: string }> = {
  1: { body: '#2ecc71', inner: '#27ae60' },
  2: { body: '#F1C40F', inner: '#d4a800' },
  3: { body: '#F1C40F', inner: '#d4a800' },
};

type RankPinProps = {
  rank: 1 | 2 | 3;
};

export default function RankPin({ rank }: RankPinProps) {
  const { body, inner } = RANK_STYLES[rank];
  return (
    <View style={{ alignItems: 'center' }}>
      {/* Circle body */}
      <View
        style={{
          width: 26.2,
          height: 26.2,
          borderRadius: 14,
          backgroundColor: body,
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        {/* Inner circle */}
        <View
          style={{
            width: 18,
            height: 18,
            borderRadius: 14,
            backgroundColor: inner,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ color: 'white', fontSize: 12, fontWeight: 'bold' }}>
            {rank}
          </Text>
        </View>
      </View>
      {/* Triangle tip */}
      <View
        style={{
          width: 0,
          height: 0,
          borderLeftWidth: 4,
          borderRightWidth: 4,
          borderTopWidth: 14,
          borderLeftColor: 'transparent',
          borderRightColor: 'transparent',
          borderTopColor: body,
          marginTop: -1,
        }}
      />
    </View>
  );
}

// ── SmallPin ─────────────────────────────────────────────────────────────────
// Google Maps-style teardrop pin, used for rank 4+ results.
// Shape: filled circle (22×22) + white inner dot + triangle tip.
// Color defaults to Google Maps red. Size is ~60% of RankPin.

const GOOGLE_MAPS_RED = '#570d69d0';

type SmallPinProps = {
  color?: string;
};

export function SmallPin({ color = GOOGLE_MAPS_RED }: SmallPinProps) {
  return (
    <View style={{ alignItems: 'center' }}>
      <View
        style={{
          width: 18,
          height: 18,
          borderRadius: 11,
          backgroundColor: color,
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <View
          style={{
            width: 7,
            height: 7,
            borderRadius: 3.5,
            backgroundColor: 'white',
          }}
        />
      </View>
      <View
        style={{
          width: 0,
          height: 0,
          borderLeftWidth: 5,
          borderRightWidth: 5,
          borderTopWidth: 8,
          borderLeftColor: 'transparent',
          borderRightColor: 'transparent',
          borderTopColor: color,
          marginTop: -1,
        }}
      />
    </View>
  );
}
