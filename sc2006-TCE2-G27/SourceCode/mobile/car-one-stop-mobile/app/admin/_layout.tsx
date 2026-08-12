import { BG } from '@/constants/theme';
import { Stack } from 'expo-router';
import { useAuthGuard } from '@/hooks/useAuthGuard';

export default function AdminLayout() {
    useAuthGuard();
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: BG } }}>
      <Stack.Screen name="dashboard" />
    </Stack>
  );
}
