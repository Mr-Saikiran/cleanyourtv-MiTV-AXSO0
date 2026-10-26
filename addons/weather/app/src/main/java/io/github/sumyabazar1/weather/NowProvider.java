package io.github.sumyabazar1.weather;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The last reading, read-only, for a home screen that shows it (the Clean Your TV launcher's weather_pill):
 * one row of label (the place), temp (whole degrees in the options.xml unit), code (WMO), day (1 or 0) and
 * updated (ms). temp, code and day are null before the first reading. Asking for it also schedules
 * {@link RefreshJob}, so the reading keeps fresh while a home screen uses it.
 */
public final class NowProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.now/now");
    static final String PREFS = "now";
    private static final int JOB = 1, JOB_NOW = 2;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context context = getContext();
        schedule(context);
        SharedPreferences place = WeatherActivity.place(context);
        SharedPreferences now = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        MatrixCursor c = new MatrixCursor(new String[] {"label", "temp", "code", "day", "updated"});
        String label = place.getString("name", context.getString(R.string.app_name));
        boolean fresh = now.contains("temp") && label.equals(now.getString("label", null));
        c.addRow(new Object[] {label,
                fresh ? now.getInt("temp", 0) : null,
                fresh ? now.getInt("code", 0) : null,
                fresh ? (now.getBoolean("day", true) ? 1 : 0) : null,
                fresh ? now.getLong("updated", 0) : null});
        return c;
    }

    /** Keep the reading for {@link #query}, and tell a home screen watching it. */
    static void keep(Context context, String label, Forecast.Report r, boolean fahrenheit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("label", label)
                .putInt("temp", Forecast.degrees(r.tempC, fahrenheit))
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putLong("updated", System.currentTimeMillis())
                .apply();
        context.getContentResolver().notifyChange(URI, null);
    }

    /** One {@link RefreshJob} run as soon as there is a network, for a place that just changed. */
    static void refreshSoon(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        jobs.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(context, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    /** Every {@link Forecast#REFRESH_MS} with any network, until the app's data is cleared. */
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null || jobs.getPendingJob(JOB) != null) return;
        jobs.schedule(new JobInfo.Builder(JOB, new ComponentName(context, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(Forecast.REFRESH_MS)
                .build());
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.io.github.sumyabazar1.weather.now"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException("read-only"); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException("read-only"); }
}
