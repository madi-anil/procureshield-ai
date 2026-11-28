import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import axios from "axios";
import {
  Bell,
  CheckCheck,
  CheckCircle2,
  Clock,
  FileSpreadsheet,
  Loader2,
  AlertCircle,
  X,
} from "lucide-react";
import { apiClient } from "../api/apiClient";
import { AppLayout } from "../components/AppLayout";
import type { NotificationItem } from "../types/notifications";

interface NotificationsListPayload {
  notifications?: NotificationItem[];
  items?: NotificationItem[];
  content?: NotificationItem[];
}

interface ApiErrorPayload {
  message?: string;
}

interface ToastMessage {
  type: "success" | "error";
  text: string;
}

function getNotificationId(item: NotificationItem): number {
  return item.notificationId ?? item.id ?? 0;
}

function getTypeBadge(notificationType?: string) {
  if (notificationType === "SLA_RENEWAL_ALERT") {
    return {
      label: "SLA Renewal Alert",
      className: "bg-amber-50 text-amber-800 border-amber-200",
      icon: Clock,
    };
  }
  return {
    label: "PO Status Update",
    className: "bg-blue-50 text-blue-700 border-blue-200",
    icon: FileSpreadsheet,
  };
}

function extractApiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorPayload>(err)) {
    return err.response?.data?.message || fallback;
  }
  return fallback;
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadOnly, setUnreadOnly] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [markingId, setMarkingId] = useState<number | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  async function loadNotifications(onlyUnread: boolean) {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<NotificationsListPayload | NotificationItem[]>(
        "/notifications",
        { params: { unreadOnly: onlyUnread } }
      );
      const list = Array.isArray(res.data)
        ? res.data
        : (res.data.notifications ?? res.data.items ?? res.data.content ?? []);
      setNotifications(list);
    } catch (err) {
      setError(extractApiMessage(err, "Could not load notifications."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadNotifications(unreadOnly);
  }, [unreadOnly]);

  async function handleMarkAsRead(item: NotificationItem) {
    const id = getNotificationId(item);
    if (!id) return;

    setMarkingId(id);
    setToast(null);
    try {
      await apiClient.patch(`/notifications/${id}/read`);
      if (unreadOnly) {
        setNotifications((prev) => prev.filter((n) => getNotificationId(n) !== id));
      } else {
        setNotifications((prev) =>
          prev.map((n) => (getNotificationId(n) === id ? { ...n, isRead: true } : n))
        );
      }
      setToast({
        type: "success",
        text: "Notification marked as read.",
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Could not mark notification as read."),
      });
    } finally {
      setMarkingId(null);
    }
  }

  return (
    <AppLayout>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Notifications & Alerts</h1>
            <p className="text-sm text-slate-500">
              Review Purchase Order status changes and upcoming SLA auto-renewal windows.
            </p>
          </div>

          <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1 self-start">
            <button
              type="button"
              onClick={() => setUnreadOnly(true)}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                unreadOnly
                  ? "bg-blue-600 text-white"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Unread Only
            </button>
            <button
              type="button"
              onClick={() => setUnreadOnly(false)}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                !unreadOnly
                  ? "bg-blue-600 text-white"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              All Alerts
            </button>
          </div>
        </div>

        {toast && (
          <div
            role="status"
            className={`rounded-xl border p-3.5 flex items-center justify-between gap-3 text-sm ${
              toast.type === "success"
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : "bg-red-50 border-red-200 text-red-800"
            }`}
          >
            <div className="flex items-center gap-2">
              {toast.type === "success" ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
              ) : (
                <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
              )}
              <span>{toast.text}</span>
            </div>
            <button
              type="button"
              onClick={() => setToast(null)}
              aria-label="Dismiss notification"
              className="text-slate-500 hover:text-slate-800 cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {loading && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading notifications...</span>
          </div>
        )}

        {!loading && error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-center gap-2.5 text-sm text-red-700"
          >
            <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
            <span>{error}</span>
          </div>
        )}

        {!loading && !error && notifications.length === 0 && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 text-center space-y-2">
            <Bell className="h-8 w-8 text-slate-400 mx-auto" />
            <p className="text-sm font-medium text-slate-700">
              {unreadOnly ? "No unread notifications" : "No notifications found"}
            </p>
            <p className="text-xs text-slate-500">
              Purchase Order approvals and contract renewal warnings will appear here.
            </p>
          </div>
        )}

        {!loading && !error && notifications.length > 0 && (
          <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
            {notifications.map((item) => {
              const id = getNotificationId(item);
              const badge = getTypeBadge(item.notificationType);
              const BadgeIcon = badge.icon;
              const isBusy = markingId === id;

              return (
                <div
                  key={id || item.title}
                  className={`p-4 sm:p-5 flex flex-col sm:flex-row sm:items-start justify-between gap-4 ${
                    item.isRead ? "bg-white" : "bg-blue-50/30"
                  }`}
                >
                  <div className="space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2.5 py-0.5 rounded-full border ${badge.className}`}
                      >
                        <BadgeIcon className="h-3 w-3" />
                        <span>{badge.label}</span>
                      </span>
                      {!item.isRead && (
                        <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-blue-600 text-white">
                          Unread
                        </span>
                      )}
                      {item.createdAt && (
                        <span className="text-xs text-slate-400">{item.createdAt}</span>
                      )}
                    </div>

                    <h2 className="text-sm font-bold text-slate-900">{item.title}</h2>
                    <p className="text-xs text-slate-600 leading-relaxed">{item.message}</p>

                    {item.notificationType === "PO_STATUS_CHANGE" && (
                      <div className="pt-1">
                        <Link
                          to="/orders"
                          className="text-xs font-semibold text-blue-600 hover:text-blue-700"
                        >
                          View Purchase Orders →
                        </Link>
                      </div>
                    )}
                  </div>

                  {!item.isRead && (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => void handleMarkAsRead(item)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 disabled:opacity-40 text-slate-700 text-xs font-semibold px-3 py-2 shrink-0 transition-colors cursor-pointer"
                    >
                      <CheckCheck className="h-4 w-4 text-emerald-600" />
                      <span>Mark as Read</span>
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </AppLayout>
  );
}