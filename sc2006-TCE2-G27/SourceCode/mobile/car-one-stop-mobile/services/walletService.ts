import api from './Registerapi';

export interface WalletBalanceResponse {
  balance: number;
}

export interface TopUpRequest {
  amount: number;
  paymentMethod: 'CARD' | 'PAYNOW';
}

export type TransactionType = 'TOP_UP' | 'BOOKING_FEE';

export interface WalletTransactionResponse {
  transactionId: string;
  type: TransactionType;
  amount: number;
  status: string;
  createdAt: string;
}

const resolveApiBase = (): string => {
  const baseURL = api.defaults.baseURL ?? '';
  if (!baseURL) {
    return '/api';
  }

  return baseURL.replace(/\/auth\/register\/?$/, '').replace(/\/+$/, '');
};

const API_BASE = resolveApiBase();
const WALLET_BASE = `${API_BASE}/wallet`;

export const getWalletBalance = async (): Promise<WalletBalanceResponse> => {
  const { data } = await api.get<WalletBalanceResponse>(`${WALLET_BASE}/balance`);
  return data;
};

export const topUpWallet = async (payload: TopUpRequest): Promise<void> => {
  await api.post(`${WALLET_BASE}/top-up`, payload);
};

export const getWalletTransactions = async (): Promise<WalletTransactionResponse[]> => {
  const { data } = await api.get<WalletTransactionResponse[]>(`${WALLET_BASE}/transactions`);
  return data;
};
