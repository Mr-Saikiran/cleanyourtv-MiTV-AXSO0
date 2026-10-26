package io.github.sumyabazar1.weather;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

/**
 * Scheduled by {@link NowProvider} once a home screen asks for the reading: fetches the weather now at the kept
 * place for it, one request per run. A failed run is retried by the scheduler's own back-off.
 */
public final class RefreshJob extends JobService {
    private Thread work;

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences place = WeatherActivity.place(this);
        if (!place.contains("name")) return false;   // no place chosen yet: nothing to fetch
        String name = place.getString("name", "");
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        boolean fahrenheit = "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim());
        work = new Thread(() -> {
            boolean failed = false;
            try {
                NowProvider.keep(this, name, Forecast.fetch(lat, lon, Forecast.MIN_DAYS), fahrenheit);
            } catch (Exception e) {
                failed = true;
            }
            jobFinished(params, failed);
        }, "weather-refresh");
        work.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (work != null) work.interrupt();
        return true;
    }
}
