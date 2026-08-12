import api from './Registerapi';

export interface AnnouncementResponse {
  id: number;
  title: string;
  message: string;
  createdAt: string;
}

export interface UserAdminResponse {
  id: number;
  username: string;
  email: string;
  role: string;
  banned: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
}

// ─── Announcements ────────────────────────────────────────
export const getAnnouncements = async (page: number) => {
  const { data } = await api.get<PageResponse<AnnouncementResponse>>(
    `/admin/announcements?page=${page}&size=10`
  );
  return data;
};

export const createAnnouncement = async (title: string, message: string) => {
  await api.post('/admin/announcements', { title, message });
};

export const deleteAnnouncement = async (id: number) => {
  await api.delete(`/admin/announcements/${id}`);
};

// ─── Users ────────────────────────────────────────────────
export const getUsers = async (page: number, search?: string) => {
  const searchParam = search ? `&search=${encodeURIComponent(search)}` : '';
  const { data } = await api.get<PageResponse<UserAdminResponse>>(
    `/admin/users?page=${page}&size=10${searchParam}`
  );
  return data;
};

export const banUser = async (id: number) => {
  await api.put(`/admin/users/${id}/ban`);
};

export const unbanUser = async (id: number) => {
  await api.put(`/admin/users/${id}/unban`);
};

export const getLatestAnnouncement = async () => {
  const { data } = await api.get<AnnouncementResponse | null>('/announcement/latest');
  return data;
};