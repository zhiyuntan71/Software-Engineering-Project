import React from 'react';
import { Modal, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';

type ModalVariant = 'success' | 'confirm' | 'warning' | 'error' | 'info';
export type StatusModalDetail =
  | string
  | {
      label?: string;
      value: string;
      fullWidth?: boolean;
    };

type StatusModalProps = {
  visible: boolean;
  variant: ModalVariant;
  title: string;
  message?: string;
  details?: StatusModalDetail[];
  primaryLabel: string;
  onPrimary: () => void;
  primaryDisabled?: boolean;
  secondaryLabel?: string;
  onSecondary?: () => void;
  onRequestClose?: () => void;
};

const variantConfig: Record<ModalVariant, { color: string; icon: keyof typeof Ionicons.glyphMap }> = {
  success: { color: '#2E7D32', icon: 'checkmark-circle' },
  confirm: { color: PRIMARY, icon: 'calendar-outline' },
  warning: { color: '#E67E22', icon: 'warning' },
  error: { color: '#D64545', icon: 'close-circle' },
  info: { color: '#2C6E85', icon: 'information-circle' },
};

export default function StatusModal({
  visible,
  variant,
  title,
  message,
  details = [],
  primaryLabel,
  onPrimary,
  primaryDisabled = false,
  secondaryLabel,
  onSecondary,
  onRequestClose,
}: StatusModalProps) {
  const accent = variantConfig[variant];

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onRequestClose ?? onSecondary ?? onPrimary}
    >
      <View style={styles.backdrop}>
        <View style={[styles.card, { borderColor: `${accent.color}55` }]}>
          <View style={[styles.badgeWrap, { borderColor: `${accent.color}44` }]}>
            <View style={[styles.badgeInner, { backgroundColor: `${accent.color}20` }]}>
              <Ionicons name={accent.icon} size={28} color={accent.color} />
            </View>
          </View>

          <Text style={styles.title}>{title}</Text>
          {!!message && <Text style={styles.message}>{message}</Text>}

          {details.length > 0 && (
            <View style={styles.detailsWrap}>
              {details.map((item, idx) => {
                const detail = typeof item === 'string' ? { value: item } : item;
                const shouldFullWidth =
                  detail.fullWidth ?? (detail.value.length > 28 || (detail.label?.length ?? 0) > 16);

                return (
                  <View
                    key={`${detail.label ?? 'detail'}-${detail.value}-${idx}`}
                    style={[styles.detailPill, shouldFullWidth && styles.detailPillFull]}
                  >
                    {!!detail.label && <Text style={styles.detailLabel}>{detail.label}</Text>}
                    <Text style={styles.detailValue}>{detail.value}</Text>
                  </View>
                );
              })}
            </View>
          )}

          <View style={[styles.actions, !secondaryLabel && styles.actionsSingle]}>
            {secondaryLabel && onSecondary && (
              <TouchableOpacity style={styles.secondaryBtn} onPress={onSecondary}>
                <Text style={styles.secondaryText}>{secondaryLabel}</Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity
              style={[
                styles.primaryBtn,
                { backgroundColor: accent.color },
                primaryDisabled && styles.primaryBtnDisabled,
              ]}
              onPress={onPrimary}
              disabled={primaryDisabled}
            >
              <Text style={styles.primaryText}>{primaryLabel}</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(18, 31, 44, 0.35)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  card: {
    width: '100%',
    maxWidth: 360,
    backgroundColor: BG,
    borderRadius: 24,
    borderWidth: 1.5,
    paddingHorizontal: 20,
    paddingTop: 34,
    paddingBottom: 18,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.14,
    shadowRadius: 16,
    elevation: 10,
  },
  badgeWrap: {
    position: 'absolute',
    top: -30,
    alignSelf: 'center',
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: BG,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeInner: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    textAlign: 'center',
    fontSize: 19,
    fontWeight: '800',
    color: TEXT,
  },
  message: {
    marginTop: 8,
    textAlign: 'center',
    fontSize: 14,
    lineHeight: 20,
    color: SUBTEXT,
  },
  detailsWrap: {
    marginTop: 12,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 8,
  },
  detailPill: {
    width: '48.5%',
    borderRadius: 14,
    paddingVertical: 8,
    paddingHorizontal: 10,
    backgroundColor: '#F3F7FA',
    borderWidth: 1,
    borderColor: '#E1ECF2',
    alignItems: 'center',
  },
  detailPillFull: {
    width: '100%',
  },
  detailLabel: {
    fontSize: 13,
    color: SUBTEXT,
    fontWeight: '700',
    marginBottom: 2,
    textTransform: 'uppercase',
  },
  detailValue: {
    fontSize: 15,
    color: '#456474',
    lineHeight: 18,
    textAlign: 'center',
  },
  actions: {
    marginTop: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
  },
  actionsSingle: {
    justifyContent: 'center',
  },
  primaryBtn: {
    flex: 1,
    minHeight: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
  },
  primaryBtnDisabled: {
    opacity: 0.65,
  },
  primaryText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  secondaryBtn: {
    flex: 1,
    minHeight: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderWidth: 1.2,
    borderColor: '#BCD2DE',
    backgroundColor: '#F7FAFC',
  },
  secondaryText: {
    color: TEXT,
    fontSize: 14,
    fontWeight: '700',
  },
});
