import React, { createContext, useContext, useState } from 'react';

export type BookingStatus = 'success' | 'failed';

export type Booking = {
  id: string;
  carparkName: string;
  space: string;
  date: string;
  time: string;
  amount: string;
  status: BookingStatus;
};

export type TopUp = {
  id: string;
  method: string;
  date: string;
  time: string;
  amount: string;
};

export type ActiveBooking = {
  carparkName: string;
  space: string;
  date: string;
  time: string;
  amount: string;
  timeRemaining: string;
} | null;

export type SavedCard = {
  id: string;
  cardNumber: string;
  expiry: string;
  country: string;
  postal: string;
};

type UserContextType = {
  balance: number;
  setBalance: (val: number) => void;
  username: string;
  setUsername: (val: string) => void;
  carType: string;
  setCarType: (val: string) => void;
  carModel: string;
  setCarModel: (val: string) => void;
  chargingType: string;
  setChargingType: (val: string) => void;
  bookings: Booking[];
  setBookings: (val: Booking[]) => void;
  topups: TopUp[];
  setTopups: (val: TopUp[]) => void;
  activeBooking: ActiveBooking;
  setActiveBooking: (val: ActiveBooking) => void;
  savedCards: SavedCard[];
  setSavedCards: (val: SavedCard[]) => void;
};

const UserContext = createContext<UserContextType | null>(null);

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [balance, setBalance] = useState(10);
  const [username, setUsername] = useState('');
  const [carType, setCarType] = useState('');
  const [carModel, setCarModel] = useState('');
  const [chargingType, setChargingType] = useState('NOT_APPLICABLE');
  const [savedCards, setSavedCards] = useState<SavedCard[]>([]);

  const [bookings, setBookings] = useState<Booking[]>([
    { id: '1', carparkName: 'Landmark Car Park B', space: 'Space 4c', date: '03/09/2019', time: '01:00pm', amount: '$5', status: 'success' },
    { id: '2', carparkName: 'Open Air Car Park D', space: 'Space 4c', date: '02/09/2019', time: '02:00pm', amount: '$5', status: 'success' },
    { id: '3', carparkName: 'Landmark Car Park B', space: 'Space 4c', date: '02/09/2019', time: '02:00pm', amount: '$5', status: 'failed' },
    { id: '4', carparkName: 'Suntec City Car Park', space: 'Space 2a', date: '01/09/2019', time: '10:00am', amount: '$8', status: 'success' },
    { id: '5', carparkName: 'Marina Bay Car Park', space: 'Space 1b', date: '31/08/2019', time: '03:00pm', amount: '$6', status: 'success' },
    { id: '6', carparkName: 'Orchard Road Car Park', space: 'Space 7d', date: '30/08/2019', time: '11:00am', amount: '$10', status: 'failed' },
  ]);

  const [topups, setTopups] = useState<TopUp[]>([
    { id: '1', method: 'PayNow', date: '03/09/2019', time: '12:00pm', amount: '+$20' },
    { id: '2', method: 'Credit Card', date: '01/09/2019', time: '09:00am', amount: '+$50' },
    { id: '3', method: 'PayNow', date: '28/08/2019', time: '06:00pm', amount: '+$10' },
    { id: '4', method: 'Credit Card', date: '20/08/2019', time: '02:00pm', amount: '+$30' },
  ]);

  const [activeBooking, setActiveBooking] = useState<ActiveBooking>({
    carparkName: 'Suntec City Car Park',
    space: 'Space 3b',
    date: '09/03/2026',
    time: '04:00pm',
    amount: '$5',
    timeRemaining: '1h 23m',
  });

  return (
    <UserContext.Provider value={{
      balance, setBalance,
      username, setUsername,
      carType, setCarType,
      carModel, setCarModel,
      chargingType, setChargingType,
      bookings, setBookings,
      topups, setTopups,
      activeBooking, setActiveBooking,
      savedCards, setSavedCards,
    }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error('useUser must be used within UserProvider');
  return ctx;
}
