const ACCOUNT_KEY = 'XFSOF3U2QgWBNA+Qzrcjfw==';
const BASE_URL = 'https://datamall2.mytransport.sg/ltaodataservice';

export const ltaFetch = async (endpoint: string, params?: Record<string, string>) => {
  const url = new URL(`${BASE_URL}/${endpoint}`);
  if (params) Object.entries(params).forEach(([k, v]) => url.searchParams.append(k, v));

  const response = await fetch(url.toString(), {
    method: 'GET',
    headers: { AccountKey: ACCOUNT_KEY },
  });
  return response.json();
};
