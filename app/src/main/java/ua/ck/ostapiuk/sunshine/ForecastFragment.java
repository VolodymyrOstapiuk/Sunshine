package ua.ck.ostapiuk.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import ua.ck.ostapiuk.sunshine.R;

public class ForecastFragment extends Fragment {
    private ArrayAdapter<String> adapter;
    private static final String TAG = ForecastFragment.class.getName();
    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        String[] itemsArray = {
                "Today - Sunny - 88/63",
                "Tomorrow - Foggy - 75/44",
                "Wednesday - Rainy - 77/39",
                "Thursday - Asteroids - 75/65",
                "Friday - Heavy Rain - 78/49",
                "Saturday - Hot - 77/35",
                "Sunday - Sunny - 73/45"
        };
        List<String> items = new ArrayList<String>(Arrays.asList(itemsArray));
         adapter = new ArrayAdapter<String>(getActivity(),
                R.layout.list_item_forecast, R.id.list_item_forecast_textview, items);
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getActivity(),DetailActivity.class);
                intent.putExtra("Forecast",adapter.getItem(position));
                startActivity(intent);
            }
        });
        return rootView;
    }
    public class FetchWeatherTask extends AsyncTask<String,Void,String[]>{
        private final String TAG = FetchWeatherTask.class.getName();
        @Override
        protected String[] doInBackground(String... params) {
            if (params.length == 0){
                return null;
            }
            HttpURLConnection httpURLConnection = null;
            BufferedReader reader = null;
            String forecastJson = null;
            String format = "json";
            String units = "metric";
            int numDays = 7;
            try {
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                Uri uri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM,format)
                        .appendQueryParameter(UNITS_PARAM,units)
                        .appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                        .build();
                URL url = new URL(uri.toString());
                Log.i(TAG,uri.toString());
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.connect();
                InputStream inputStream = httpURLConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                if (buffer.length() == 0) {
                    return null;
                }
                forecastJson = buffer.toString();
                return getWeatherDataFromJson(forecastJson, numDays);
            } catch (IOException e) {
                Log.e(TAG, "Error", e);
                return null;
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing stream");
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            if (strings!=null) {
                adapter.clear();

           for (String string : strings){
               adapter.add(string);
           }
            adapter.notifyDataSetChanged();
        }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
       inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh){
            updateWeather();
        }
        return super.onOptionsItemSelected(item);
    }

        private String getReadableDateString(long time){
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }
        private String formatHighLows(double high, double low,String unitType){
            if (unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(TAG, "Unit type not found: " + unitType);
            }
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            return roundedHigh + "/" + roundedLow;
        }
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.optJSONArray(OWM_LIST);
            Time dayTime = new Time();
            dayTime.setToNow();
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
            dayTime = new Time();
            String[] resultStrs = new String[numDays];
            SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                getString(R.string.pref_units_key),
                getString(R.string.pref_units_metric));
            for(int i = 0; i < weatherArray.length(); i++) {
                String day;
                String description;
                String highAndLow;
                JSONObject dayForecast = weatherArray.optJSONObject(i);
                long dateTime;
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);
                JSONObject weatherObject = dayForecast.optJSONArray(OWM_WEATHER).optJSONObject(0);
                description = weatherObject.optString(OWM_DESCRIPTION);
                JSONObject temperatureObject = dayForecast.optJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.optDouble(OWM_MAX);
                double low = temperatureObject.optDouble(OWM_MIN);
                highAndLow = formatHighLows(high, low,unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;


            }
            return resultStrs;
        }
    private void updateWeather(){
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = preferences.getString(getString(R.string.pref_location_key),
            getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }
    }

