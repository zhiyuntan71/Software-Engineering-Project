import { BG, BORDER, CARD_BG, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { logoutUser } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Platform,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { getAnnouncements, createAnnouncement, deleteAnnouncement, getUsers, banUser, unbanUser } from '@/services/adminService';

// ─── Types ────────────────────────────────────────────────
interface Announcement {
  id: number;
  title: string;
  message: string;
  createdAt: string;
}

interface UserItem {
  id: number;
  username: string;
  email: string;
  role: string;
  banned: boolean;
}

interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number; // current page (0-indexed)
}

type Tab = 'announcements' | 'users';

// ─── Main Dashboard ──────────────────────────────────────
export default function AdminDashboard() {
  const [activeTab, setActiveTab] = useState<Tab>('announcements');
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [showLogoutConfirmModal, setShowLogoutConfirmModal] = useState(false);

  const handleLogOut = () => {
    setShowLogoutConfirmModal(true);
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View style={{ width: 36 }} />
        <Text style={styles.headerTitle}>Admin Dashboard</Text>
        <TouchableOpacity style={styles.logoutIcon} onPress={handleLogOut}>
          <Ionicons name="log-out-outline" size={22} color={PRIMARY} />
        </TouchableOpacity>
      </View>

      {/* Tab Toggle */}
      <View style={styles.tabRow}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'announcements' && styles.tabActive]}
          onPress={() => setActiveTab('announcements')}
        >
          <Ionicons
            name="megaphone-outline"
            size={16}
            color={activeTab === 'announcements' ? '#fff' : SUBTEXT}
            style={{ marginRight: 6 }}
          />
          <Text style={[styles.tabText, activeTab === 'announcements' && styles.tabTextActive]}>
            Announcements
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'users' && styles.tabActive]}
          onPress={() => setActiveTab('users')}
        >
          <Ionicons
            name="people-outline"
            size={16}
            color={activeTab === 'users' ? '#fff' : SUBTEXT}
            style={{ marginRight: 6 }}
          />
          <Text style={[styles.tabText, activeTab === 'users' && styles.tabTextActive]}>
            Users
          </Text>
        </TouchableOpacity>
      </View>

      {/* Content */}
      {activeTab === 'announcements' ? <AnnouncementsTab /> : <UsersTab />}

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showLogoutConfirmModal}
        variant="confirm"
        title="Log Out"
        message="Are you sure you want to log out?"
        primaryLabel="Log Out"
        onPrimary={async () => {
          setShowLogoutConfirmModal(false);
          await logoutUser();
          router.replace('/login');
        }}
        secondaryLabel="Cancel"
        onSecondary={() => setShowLogoutConfirmModal(false)}
        onRequestClose={() => setShowLogoutConfirmModal(false)}
      />
    </SafeAreaView>
  );
}

// ─── Announcements Tab ────────────────────────────────────
function AnnouncementsTab() {
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [feedbackVariant, setFeedbackVariant] = useState<'success' | 'confirm' | 'warning' | 'error' | 'info'>('info');
  const [feedbackTitle, setFeedbackTitle] = useState('');
  const [feedbackMessage, setFeedbackMessage] = useState('');

  const openFeedbackModal = (
    variant: 'success' | 'confirm' | 'warning' | 'error' | 'info',
    title: string,
    message: string
  ) => {
    setFeedbackVariant(variant);
    setFeedbackTitle(title);
    setFeedbackMessage(message);
    setShowFeedbackModal(true);
  };

  const fetchAnnouncements = useCallback(async (p: number) => {
    setLoading(true);
    try {
      const data = await getAnnouncements(p);
      setAnnouncements(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch {
      openFeedbackModal('error', 'Error', 'Failed to load announcements');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnnouncements(0);
  }, [fetchAnnouncements]);

  const handleCreate = async () => {
    if (!title.trim() || !message.trim()) {
      openFeedbackModal('warning', 'Error', 'Please fill in both fields');
      return;
    }
    setSubmitting(true);
    try {
      await createAnnouncement(title.trim(), message.trim());
      setTitle('');
      setMessage('');
      setShowModal(false);
      fetchAnnouncements(0);
      openFeedbackModal('success', 'Success', 'Announcement created');
    } catch {
      openFeedbackModal('error', 'Error', 'Failed to create announcement');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = (id: number) => {
    setPendingDeleteId(id);
  };

  const renderAnnouncement = ({ item }: { item: Announcement }) => (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle} numberOfLines={1}>{item.title}</Text>
        <TouchableOpacity onPress={() => handleDelete(item.id)}>
          <Ionicons name="trash-outline" size={18} color="#E74C3C" />
        </TouchableOpacity>
      </View>
      <Text style={styles.cardBody}>{item.message}</Text>
      <Text style={styles.cardDate}>
        {new Date(item.createdAt).toLocaleDateString('en-SG', {
          day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
        })}
      </Text>
    </View>
  );

  return (
    <View style={{ flex: 1 }}>
      {/* Create button */}
      <TouchableOpacity style={styles.createBtn} onPress={() => setShowModal(true)}>
        <Ionicons name="add-circle-outline" size={20} color="#fff" style={{ marginRight: 8 }} />
        <Text style={styles.createBtnText}>New Announcement</Text>
      </TouchableOpacity>

      {/* List */}
      {loading ? (
        <ActivityIndicator size="large" color={PRIMARY} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={announcements}
          keyExtractor={(item) => item.id.toString()}
          renderItem={renderAnnouncement}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={<Text style={styles.emptyText}>No announcements yet</Text>}
        />
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <PaginationControls page={page} totalPages={totalPages} onPageChange={fetchAnnouncements} />
      )}

      {/* Create Modal */}
      <Modal visible={showModal} transparent animationType="fade" onRequestClose={() => setShowModal(false)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowModal(false)}>
          <View style={styles.modalBox} onStartShouldSetResponder={() => true}>
            <Text style={styles.modalTitle}>New Announcement</Text>

            <Text style={styles.inputLabel}>Title</Text>
            <TextInput
              style={styles.input}
              value={title}
              onChangeText={setTitle}
              placeholder="Announcement title"
              placeholderTextColor={SUBTEXT}
            />

            <Text style={styles.inputLabel}>Message</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              value={message}
              onChangeText={setMessage}
              placeholder="Write your announcement..."
              placeholderTextColor={SUBTEXT}
              multiline
              textAlignVertical="top"
            />

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.cancelBtn}
                onPress={() => { setShowModal(false); setTitle(''); setMessage(''); }}
              >
                <Text style={styles.cancelBtnText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.submitBtn, submitting && { opacity: 0.6 }]}
                onPress={handleCreate}
                disabled={submitting}
              >
                {submitting ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.submitBtnText}>Post</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </TouchableOpacity>
      </Modal>

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={pendingDeleteId !== null}
        variant="warning"
        title="Delete"
        message="Delete this announcement?"
        primaryLabel="Delete"
        onPrimary={async () => {
          const id = pendingDeleteId;
          setPendingDeleteId(null);
          if (id === null) return;
          try {
            await deleteAnnouncement(id);
            fetchAnnouncements(page);
          } catch {
            openFeedbackModal('error', 'Error', 'Failed to delete');
          }
        }}
        secondaryLabel="Cancel"
        onSecondary={() => setPendingDeleteId(null)}
        onRequestClose={() => setPendingDeleteId(null)}
      />

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showFeedbackModal}
        variant={feedbackVariant}
        title={feedbackTitle}
        message={feedbackMessage}
        primaryLabel="OK"
        onPrimary={() => setShowFeedbackModal(false)}
        onRequestClose={() => setShowFeedbackModal(false)}
      />
    </View>
  );
}

// ─── Users Tab ────────────────────────────────────────────
function UsersTab() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [pendingUserAction, setPendingUserAction] = useState<{ user: UserItem; action: 'Ban' | 'Unban' } | null>(null);
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [feedbackVariant, setFeedbackVariant] = useState<'success' | 'confirm' | 'warning' | 'error' | 'info'>('info');
  const [feedbackTitle, setFeedbackTitle] = useState('');
  const [feedbackMessage, setFeedbackMessage] = useState('');

  const openFeedbackModal = (
    variant: 'success' | 'confirm' | 'warning' | 'error' | 'info',
    title: string,
    message: string
  ) => {
    setFeedbackVariant(variant);
    setFeedbackTitle(title);
    setFeedbackMessage(message);
    setShowFeedbackModal(true);
  };

  const fetchUsers = useCallback(async (p: number, query?: string) => {
    setLoading(true);
    try {
      const data = await getUsers(p, query);
      setUsers(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch {
      openFeedbackModal('error', 'Error', 'Failed to load users');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers(0);
  }, [fetchUsers]);

  const handleSearch = () => {
    fetchUsers(0, search);
  };

  const handleToggleBan = (user: UserItem) => {
    const action = user.banned ? 'Unban' : 'Ban';
    setPendingUserAction({ user, action });
  };

  const renderUser = ({ item }: { item: UserItem }) => (
    <View style={[styles.card, item.banned && styles.cardBanned]}>
      <View style={styles.userRow}>
        <View style={styles.userAvatar}>
          <Ionicons
            name="person-circle-outline"
            size={40}
            color={item.banned ? '#E74C3C' : PRIMARY}
          />
        </View>
        <View style={styles.userInfo}>
          <View style={styles.userNameRow}>
            <Text style={styles.userName} numberOfLines={1}>{item.username}</Text>
            {item.banned && (
              <View style={styles.bannedBadge}>
                <Text style={styles.bannedBadgeText}>BANNED</Text>
              </View>
            )}
          </View>
          <Text style={styles.userEmail} numberOfLines={1}>{item.email}</Text>
        </View>
        <TouchableOpacity
          style={[styles.banBtn, item.banned && styles.unbanBtn]}
          onPress={() => handleToggleBan(item)}
        >
          <Ionicons
            name={item.banned ? 'checkmark-circle-outline' : 'ban-outline'}
            size={16}
            color={item.banned ? '#27AE60' : '#E74C3C'}
            style={{ marginRight: 4 }}
          />
          <Text style={[styles.banBtnText, item.banned && styles.unbanBtnText]}>
            {item.banned ? 'Unban' : 'Ban'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <View style={{ flex: 1 }}>
      {/* Search Bar */}
      <View style={styles.searchRow}>
        <View style={styles.searchBox}>
          <Ionicons name="search-outline" size={18} color={SUBTEXT} style={{ marginRight: 8 }} />
          <TextInput
            style={styles.searchInput}
            value={search}
            onChangeText={setSearch}
            placeholder="Search by username or email"
            placeholderTextColor={SUBTEXT}
            onSubmitEditing={handleSearch}
            returnKeyType="search"
          />
          {search.length > 0 && (
            <TouchableOpacity onPress={() => { setSearch(''); fetchUsers(0); }}>
              <Ionicons name="close-circle" size={18} color={SUBTEXT} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* List */}
      {loading ? (
        <ActivityIndicator size="large" color={PRIMARY} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={users}
          keyExtractor={(item) => item.id.toString()}
          renderItem={renderUser}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={<Text style={styles.emptyText}>No users found</Text>}
        />
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <PaginationControls page={page} totalPages={totalPages} onPageChange={(p) => fetchUsers(p, search)} />
      )}

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={pendingUserAction !== null}
        variant="confirm"
        title={pendingUserAction ? `${pendingUserAction.action} User` : 'Confirm User Action'}
        message={
          pendingUserAction
            ? `${pendingUserAction.action} ${pendingUserAction.user.username} (${pendingUserAction.user.email})?`
            : ''
        }
        primaryLabel={pendingUserAction?.action ?? 'Confirm'}
        onPrimary={async () => {
          const pending = pendingUserAction;
          setPendingUserAction(null);
          if (!pending) return;
          try {
            if (pending.user.banned) {
              await unbanUser(pending.user.id);
            } else {
              await banUser(pending.user.id);
            }
            fetchUsers(page, search);
          } catch {
            openFeedbackModal('error', 'Error', `Failed to ${pending.action.toLowerCase()} user`);
          }
        }}
        secondaryLabel="Cancel"
        onSecondary={() => setPendingUserAction(null)}
        onRequestClose={() => setPendingUserAction(null)}
      />

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showFeedbackModal}
        variant={feedbackVariant}
        title={feedbackTitle}
        message={feedbackMessage}
        primaryLabel="OK"
        onPrimary={() => setShowFeedbackModal(false)}
        onRequestClose={() => setShowFeedbackModal(false)}
      />
    </View>
  );
}

// ─── Pagination Controls ──────────────────────────────────
function PaginationControls({
  page, totalPages, onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (p: number) => void;
}) {
  return (
    <View style={styles.paginationRow}>
      <TouchableOpacity
        style={[styles.pageBtn, page === 0 && styles.pageBtnDisabled]}
        onPress={() => page > 0 && onPageChange(page - 1)}
        disabled={page === 0}
      >
        <Ionicons name="chevron-back" size={18} color={page === 0 ? SUBTEXT : PRIMARY} />
      </TouchableOpacity>

      <Text style={styles.pageText}>
        {page + 1} / {totalPages}
      </Text>

      <TouchableOpacity
        style={[styles.pageBtn, page >= totalPages - 1 && styles.pageBtnDisabled]}
        onPress={() => page < totalPages - 1 && onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
      >
        <Ionicons name="chevron-forward" size={18} color={page >= totalPages - 1 ? SUBTEXT : PRIMARY} />
      </TouchableOpacity>
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BG,
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 10 : 50,
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  headerTitle: { fontSize: 18, fontWeight: '700', color: TEXT },
  logoutIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: INPUT_BG,
    borderWidth: 1,
    borderColor: PRIMARY,
    alignItems: 'center',
    justifyContent: 'center',
  },

  // Tabs
  tabRow: {
    flexDirection: 'row',
    marginHorizontal: 20,
    backgroundColor: CARD_BG,
    borderRadius: 12,
    padding: 4,
    marginBottom: 16,
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 10,
  },
  tabActive: {
    backgroundColor: PRIMARY,
  },
  tabText: { fontSize: 14, fontWeight: '600', color: SUBTEXT },
  tabTextActive: { color: '#fff' },

  // Create Button
  createBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY,
    marginHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 12,
    marginBottom: 12,
  },
  createBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },

  // Cards
  card: {
    backgroundColor: CARD_BG,
    borderRadius: 14,
    padding: 14,
    marginHorizontal: 20,
    marginBottom: 10,
  },
  cardBanned: {
    borderWidth: 1,
    borderColor: '#E74C3C40',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  cardTitle: { fontSize: 15, fontWeight: '700', color: TEXT, flex: 1, marginRight: 8 },
  cardBody: { fontSize: 13, color: SUBTEXT, lineHeight: 19, marginBottom: 8 },
  cardDate: { fontSize: 11, color: SUBTEXT },

  // Users
  userRow: { flexDirection: 'row', alignItems: 'center' },
  userAvatar: { marginRight: 12 },
  userInfo: { flex: 1, marginRight: 8 },
  userNameRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  userName: { fontSize: 15, fontWeight: '600', color: TEXT, flexShrink: 1 },
  userEmail: { fontSize: 12, color: SUBTEXT, marginTop: 2 },
  bannedBadge: {
    backgroundColor: '#E74C3C20',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  bannedBadgeText: { fontSize: 10, fontWeight: '700', color: '#E74C3C' },
  banBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E74C3C40',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  unbanBtn: {
    borderColor: '#27AE6040',
  },
  banBtnText: { fontSize: 12, fontWeight: '600', color: '#E74C3C' },
  unbanBtnText: { color: '#27AE60' },

  // Search
  searchRow: { paddingHorizontal: 20, marginBottom: 12 },
  searchBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: CARD_BG,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  searchInput: { flex: 1, fontSize: 14, color: TEXT },

  // List
  listContent: { paddingBottom: 16 },
  emptyText: { textAlign: 'center', color: SUBTEXT, marginTop: 40, fontSize: 14 },

  // Pagination
  paginationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    paddingBottom: 24,
    gap: 16,
  },
  pageBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: CARD_BG,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pageBtnDisabled: { opacity: 0.4 },
  pageText: { fontSize: 14, fontWeight: '600', color: TEXT },

  // Modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    paddingHorizontal: 28,
  },
  modalBox: {
    backgroundColor: BG,
    borderRadius: 16,
    padding: 20,
  },
  modalTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: TEXT,
    marginBottom: 16,
  },
  inputLabel: { fontSize: 13, fontWeight: '600', color: TEXT, marginBottom: 6 },
  input: {
    backgroundColor: CARD_BG,
    borderRadius: 10,
    padding: 12,
    fontSize: 14,
    color: TEXT,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: BORDER,
  },
  textArea: { height: 100 },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
    marginTop: 4,
  },
  cancelBtn: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: BORDER,
  },
  cancelBtnText: { fontSize: 14, fontWeight: '600', color: SUBTEXT },
  submitBtn: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 10,
    backgroundColor: PRIMARY,
  },
  submitBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
});
