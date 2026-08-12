
import { useEffect } from 'react';
import { decode } from 'base-64';
import * as SecureStore from 'expo-secure-store';
import { router, usePathname } from 'expo-router';

export function useAuthGuard() {
  const pathname = usePathname();

  useEffect(() => {
    const checkToken = async () => {
      const token = await SecureStore.getItemAsync('userToken');

      // no token = not logged in, kick to login
      if (!token) {
        router.replace('/login');
        return;
      }

      try {
        const payload = JSON.parse(decode(token.split('.')[1]));
        const expiry = payload.exp * 1000;

        if (Date.now() >= expiry) {
          await SecureStore.deleteItemAsync('userToken');
          await SecureStore.deleteItemAsync('userRole');
          router.replace('/login');
        }
      } catch {
        await SecureStore.deleteItemAsync('userToken');
        await SecureStore.deleteItemAsync('userRole');
        router.replace('/login');
      }
    };

    checkToken(); // check immediately when screen loads
    const interval = setInterval(checkToken, 60000); // then check every 60 seconds
    return () => clearInterval(interval); 
  }, [pathname]); 
}