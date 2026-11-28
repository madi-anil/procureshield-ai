export type NotificationType = "PO_STATUS_CHANGE" | "SLA_RENEWAL_ALERT" | string;

export interface NotificationItem {
  notificationId?: number;
  id?: number;
  title: string;
  message: string;
  notificationType?: NotificationType;
  isRead: boolean;
  createdAt?: string;
}