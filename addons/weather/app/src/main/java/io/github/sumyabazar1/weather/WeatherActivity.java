package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather for one place, full screen: now, the air quality and the next days (options.xml). It reads Open-Meteo only
 * while it is on screen (every {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure). Nothing
 * runs in the background unless a home screen reads {@link NowProvider}, which schedules {@link RefreshJob}. The
 * place is chosen once with a search (or from options.xml places) and kept in the app's preferences.
 */
public final class WeatherActivity extends Activity {
    private static final String PREFS = "place";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;
    private SharedPreferences place;
    private View root;
    private View content;
    private TextView message;
    private boolean shown;
    /** options.xml: days after today, and the extras on the "Feels like" line. */
    private int forecastDays;
    private boolean showHumidity;
    private boolean showWind;
    /** options.xml: degrees Fahrenheit (and mph) instead of Celsius, and the other unit on the "Feels like" line. */
    private boolean fahrenheit;
    private boolean bothUnits;
    /** options.xml: fixed places for the Places pill; empty keeps the city search. */
    private List<Forecast.Place> fixed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather);
        place = place(this);
        forecastDays = Forecast.forecastDays(getResources().getInteger(R.integer.forecast_days));
        showHumidity = getResources().getBoolean(R.bool.show_humidity);
        showWind = getResources().getBoolean(R.bool.show_wind);
        fahrenheit = "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim());
        bothUnits = getResources().getBoolean(R.bool.show_both_units);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        message = findViewById(R.id.message);
        View changeCity = findViewById(R.id.changeCity);
        changeCity.setOnClickListener(v -> pickCity());
        changeCity.requestFocus();
        fixed = Forecast.places(getResources().getStringArray(R.array.places));
        if (!fixed.isEmpty()) {
            // options.xml places: the Places pill replaces the search, and the first place shows until another is picked.
            changeCity.setVisibility(View.GONE);
            View button = findViewById(R.id.placesButton);
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(this::dropPlaces);
            button.requestFocus();
        } else if (!place.contains("name")) {
            message.setText(R.string.pick_first);
            pickCity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (place.contains("name")) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);   // nothing runs while the page is not on screen
    }

    @Override
    protected void onStop() {
        super.onStop();
        // options.xml places_reset: another place is only a look; leaving the page goes back to the first one,
        // and the reading a home screen shows follows it at once.
        if (fixed.isEmpty() || !getResources().getBoolean(R.bool.places_reset) || fixed.get(0).equals(saved())) return;
        keep(fixed.get(0));
        NowProvider.refreshSoon(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void refresh() {
        main.removeCallbacks(tick);
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon, forecastDays);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (isDestroyed()) return;
                if (result != null) {
                    show(result);
                    NowProvider.keep(this, place.getString("name", ""), result, fahrenheit);
                }
                else if (!shown) message.setText(R.string.offline);
                main.postDelayed(tick, result != null ? Forecast.REFRESH_MS : Forecast.RETRY_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        shown = true;
        root.setBackground(Forecast.backdrop(r.code, r.day));
        ((TextView) findViewById(R.id.place)).setText(place.getString("name", ""));
        ((TextView) findViewById(R.id.temp)).setText(Forecast.degrees(r.tempC, fahrenheit) + "°");
        ((TextView) findViewById(R.id.sky)).setText(Forecast.sky(r.code, r.day));
        StringBuilder feels = new StringBuilder();
        if (bothUnits) {
            feels.append(getString(fahrenheit ? R.string.other_unit_c : R.string.other_unit_f,
                    Forecast.degrees(r.tempC, !fahrenheit))).append(" · ");
        }
        feels.append(getString(R.string.feels_like, Forecast.degrees(r.feelsC, fahrenheit)));
        if (showHumidity) feels.append(" · ").append(getString(R.string.humidity, r.humidity));
        if (showWind) {
            feels.append(" · ").append(fahrenheit ? getString(R.string.wind_mph, Forecast.mph(r.windKmh))
                    : getString(R.string.wind, r.windKmh));
        }
        ((TextView) findViewById(R.id.feels)).setText(feels);

        TextView air = findViewById(R.id.airQuality);
        if (r.aqi >= 0) {
            air.setText(getString(R.string.aqi_line, r.aqi, getString(Forecast.aqiLabel(r.aqi))));
            air.setTextColor(Forecast.aqiTextColour(r.aqi));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Forecast.aqiColour(r.aqi));
            pill.setCornerRadius(100 * getResources().getDisplayMetrics().density);
            air.setBackground(pill);
            air.setVisibility(View.VISIBLE);
        } else {
            air.setVisibility(View.GONE);
        }

        LinearLayout days = findViewById(R.id.days);
        days.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        // More than three days: the cards share the row, with short day names, so a week fits on screen.
        boolean compact = r.days.size() > Forecast.MIN_DAYS;
        if (compact) days.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;
        for (int i = 0; i < r.days.size(); i++) {
            Forecast.Day d = r.days.get(i);
            View card = inflater.inflate(R.layout.item_day, days, false);
            ((TextView) card.findViewById(R.id.dayName)).setText(d.date.getDayOfWeek()
                    .getDisplayName(compact ? TextStyle.SHORT : TextStyle.FULL, Locale.getDefault()));
            ((TextView) card.findViewById(R.id.daySky)).setText(Forecast.sky(d.code, true));
            TextView temps = card.findViewById(R.id.dayTemps);
            temps.setText(getString(R.string.high_low, Forecast.degrees(d.maxC, fahrenheit),
                    Forecast.degrees(d.minC, fahrenheit)));
            if (compact) {
                int pad = Math.round(14 * getResources().getDisplayMetrics().density);
                card.setPadding(pad, pad, pad, pad);
                TextView sky = card.findViewById(R.id.daySky);
                sky.setTextSize(15);
                sky.setSingleLine(false);   // "Partly cloudy" on two lines rather than cut; every card two lines tall
                sky.setLines(2);
                temps.setTextSize(21);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(i < r.days.size() - 1 ? Math.round(12 * getResources().getDisplayMetrics().density) : 0);
                card.setLayoutParams(lp);
            }
            days.addView(card);
        }

        ((TextView) findViewById(R.id.updated))
                .setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date())));
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    /** Type a city, pick it from the matches, keep it. Back leaves the current place as it was. */
    private void pickCity() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.pick_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setView(box)
                .setPositiveButton(R.string.pick_search, (d, w) -> search(name.getText().toString()))
                .create();
        name.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            search(name.getText().toString());
            return true;
        });
        dialog.show();
        name.requestFocus();
    }

    private void search(String typed) {
        if (typed.trim().isEmpty()) {
            pickCity();
            return;
        }
        io.execute(() -> {
            List<Forecast.Place> found;
            try {
                found = Forecast.search(typed);
            } catch (Exception e) {
                found = null;
            }
            List<Forecast.Place> places = found;
            main.post(() -> {
                if (isDestroyed()) return;
                if (places == null) {
                    Toast.makeText(this, R.string.offline, Toast.LENGTH_LONG).show();
                } else if (places.isEmpty()) {
                    Toast.makeText(this, getString(R.string.pick_none, typed.trim()), Toast.LENGTH_LONG).show();
                    pickCity();
                } else {
                    choose(places);
                }
            });
        });
    }

    private void choose(List<Forecast.Place> places) {
        CharSequence[] rows = new CharSequence[places.size()];
        for (int i = 0; i < rows.length; i++) {
            Forecast.Place p = places.get(i);
            rows[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setItems(rows, (d, which) -> {
                    keep(places.get(which));
                    message.setText(R.string.loading);
                    refresh();
                })
                .show();
    }

    /**
     * The kept place, shared with {@link NowProvider} and {@link RefreshJob}. With options.xml places, one of
     * them: the first until another is picked.
     */
    static SharedPreferences place(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        List<Forecast.Place> fixed = Forecast.places(context.getResources().getStringArray(R.array.places));
        if (!fixed.isEmpty() && !fixed.contains(saved(prefs))) keep(prefs, fixed.get(0));
        return prefs;
    }

    private void keep(Forecast.Place p) {
        keep(place, p);
    }

    private static void keep(SharedPreferences place, Forecast.Place p) {
        place.edit()
                .putString("name", p.name)
                .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                .apply();
    }

    private Forecast.Place saved() {
        return saved(place);
    }

    private static Forecast.Place saved(SharedPreferences place) {
        return new Forecast.Place(place.getString("name", ""), "",
                Double.longBitsToDouble(place.getLong("lat", 0)), Double.longBitsToDouble(place.getLong("lon", 0)));
    }

    /** The options.xml places in a glass list under the Places pill, the current one first in focus. */
    private void dropPlaces(View anchor) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.dropdown);
        int pad = Math.round(8 * dp);
        list.setPadding(pad, pad, pad, pad);
        PopupWindow popup = new PopupWindow(list, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        Forecast.Place current = saved();
        View focus = null;
        for (Forecast.Place p : fixed) {
            TextView row = new TextView(this);
            row.setText(p.name);
            row.setTextSize(18);
            row.setTextColor(getColorStateList(R.color.pill_text));
            row.setBackgroundResource(R.drawable.pill);
            row.setFocusable(true);
            row.setMinWidth(Math.round(240 * dp));
            row.setPadding(Math.round(20 * dp), Math.round(12 * dp), Math.round(20 * dp), Math.round(12 * dp));
            row.setOnClickListener(v -> {
                popup.dismiss();
                if (p.equals(saved())) return;
                keep(p);
                content.setVisibility(View.INVISIBLE);
                message.setText(R.string.loading);
                message.setVisibility(View.VISIBLE);
                shown = false;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = list.getChildCount() > 0 ? Math.round(4 * dp) : 0;
            list.addView(row, lp);
            if (focus == null || p.equals(current)) focus = row;
        }
        // Right edges lined up with the pill: Gravity.END pushes the list off screen on Android 9.
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.showAsDropDown(anchor, anchor.getWidth() - list.getMeasuredWidth(), Math.round(8 * dp));
        focus.requestFocus();
    }
}
