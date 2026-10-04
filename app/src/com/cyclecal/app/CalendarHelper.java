package com.cyclecal.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class CalendarHelper {

    public static class CalInfo {
        public final long id;
        public final String name;
        public final String account;

        public CalInfo(long id, String name, String account) {
            this.id = id;
            this.name = name;
            this.account = account;
        }

        @Override
        public String toString() {
            if (account == null || account.isEmpty() || account.equals(name)) return name;
            return name + "（" + account + "）";
        }
    }

    private CalendarHelper() {
    }

    /** 查询设备上可见且可写的日历账户。 */
    public static List<CalInfo> queryCalendars(Context ctx) {
        List<CalInfo> out = new ArrayList<>();
        String[] proj = {
                Calendars._ID,
                Calendars.CALENDAR_DISPLAY_NAME,
                Calendars.ACCOUNT_NAME,
                Calendars.CALENDAR_ACCESS_LEVEL,
                Calendars.VISIBLE
        };
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    Calendars.CONTENT_URI,
                    proj,
                    Calendars.VISIBLE + "=1 AND " + Calendars.CALENDAR_ACCESS_LEVEL + ">=700",
                    null,
                    Calendars._ID + " ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    if (c.isNull(0)) continue;
                    out.add(new CalInfo(c.getLong(0), c.getString(1), c.getString(2)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /**
     * 批量写入日程。
     * occ：每个元素是某天 00:00 的 Calendar（本地时区）。
     * recurring 为 true 时只写一条并附加 RRULE 循环规则。
     * 返回所有新建事件的 Uri，可用于撤销。
     */
    public static List<Uri> insertSeries(Context ctx, CalInfo cal, String title,
                                         List<Calendar> occ, boolean recurring,
                                         boolean allDay, int hour, int minute,
                                         boolean remind) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        List<Uri> created = new ArrayList<>();
        TimeZone tz = TimeZone.getDefault();
        int interval = intervalDays(occ);

        for (int i = 0; i < occ.size(); i++) {
            Calendar day = occ.get(i);
            ContentValues v = new ContentValues();
            v.put(Events.CALENDAR_ID, cal.id);
            v.put(Events.TITLE, title);
            v.put(Events.DESCRIPTION, "周期日程 · 每 " + interval + " 天一次");

            long dtstart;
            long dtend;
            if (allDay) {
                Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                u.clear();
                u.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH),
                        day.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
                dtstart = u.getTimeInMillis();
                dtend = dtstart + 86400000L;
                v.put(Events.ALL_DAY, 1);
            } else {
                Calendar t = (Calendar) day.clone();
                t.set(Calendar.HOUR_OF_DAY, hour);
                t.set(Calendar.MINUTE, minute);
                t.set(Calendar.SECOND, 0);
                t.set(Calendar.MILLISECOND, 0);
                dtstart = t.getTimeInMillis();
                dtend = dtstart + 3600000L;
                v.put(Events.EVENT_TIMEZONE, tz.getID());
            }
            v.put(Events.DTSTART, dtstart);
            v.put(Events.DTEND, dtend);
            if (recurring) {
                v.put(Events.RRULE, "FREQ=DAILY;INTERVAL=" + interval + ";COUNT=" + occ.size());
            }

            Uri uri = cr.insert(Events.CONTENT_URI, v);
            if (uri == null) {
                for (Uri u2 : created) {
                    try { cr.delete(u2, null, null); } catch (Exception ignored) {}
                }
                throw new IllegalStateException("日历写入被拒绝");
            }
            created.add(uri);
            if (recurring) break;
        }

        if (remind) {
            for (Uri uri : created) {
                try {
                    long id = ContentUris.parseId(uri);
                    ContentValues r = new ContentValues();
                    r.put(Reminders.EVENT_ID, id);
                    r.put(Reminders.METHOD, Reminders.METHOD_ALERT);
                    r.put(Reminders.MINUTES, 1440);
                    cr.insert(Reminders.CONTENT_URI, r);
                } catch (Exception ignored) {
                }
            }
        }
        return created;
    }

    /** 删除一批事件，返回成功删除条数。 */
    public static int deleteAll(Context ctx, List<Uri> uris) {
        int n = 0;
        for (Uri u : uris) {
            try {
                n += ctx.getContentResolver().delete(u, null, null);
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    private static int intervalDays(List<Calendar> occ) {
        if (occ.size() < 2) return 1;
        long ms = occ.get(1).getTimeInMillis() - occ.get(0).getTimeInMillis();
        return Math.max(1, (int) Math.round(ms / 86400000.0));
    }
}
