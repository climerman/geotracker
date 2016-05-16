package xyz.koiduste.geotrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {
    //region variables
    private final static String TAG = "MainActivity";
    private final static int REFRESH_MS = 500;
    private final static String WP_ACTION = "notification-broadcast-addwaypoint";
    private final static String CRESET_ACTION = "notification-broadcast-resettripmeter";

    private GoogleMap mGoogleMap;
    private Menu mOptionsMenu;

    private LocationManager locationManager;
    private NotificationManager mNotificationManager;
    private BroadcastReceiver mBroadcastReceiver;

    private NotificationCompat.Builder mBuilder;
    private RemoteViews mRemoteViews;

    private String provider = "";

    private int markerCount = 0;
    private Location locationPrevious;
    private Location startLocation;
    private Location lastResetLocation;
    private Location lastWaypointLocation;

    private double stepDistance = 0.;

    private double totalDistance = 0.;
    private double totalLine = 0.;

    private double wpDistance = 0.;
    private double wpLine = 0.;

    private double cResetDistance = 0.;
    private double cResetLine = 0.;

    private Polyline mPolyline;
    private PolylineOptions mPolylineOptions;

    private TextView textViewWPCount;
    private TextView textViewSpeed;
    private TextView textViewCResetDistance;
    private TextView textViewCResetLine;
    private TextView textViewWPDistance;
    private TextView textViewWPLine;
    private TextView textViewTotalDistance;
    private TextView textViewTotalLine;
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Criteria criteria = new Criteria();

        // get the location provider (GPS/CEL-towers, WIFI)
        provider = locationManager.getBestProvider(criteria, false);

        //Log.d(TAG, provider);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");

        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");

        }

        locationPrevious = locationManager.getLastKnownLocation(provider);
        lastResetLocation = locationPrevious;
        lastWaypointLocation = locationPrevious;
        startLocation = locationPrevious;

        if (locationPrevious != null) {
            // do something with initial position?
        }


        //region Gauges
        textViewWPCount = (TextView) findViewById(R.id.textview_wpcount);
        textViewSpeed = (TextView) findViewById(R.id.textview_speed);

        textViewCResetDistance = (TextView)findViewById(R.id.textview_creset_distance);
        textViewCResetLine = (TextView)findViewById(R.id.textview_creset_line);

        textViewWPDistance = (TextView)findViewById(R.id.textview_wp_distance);
        textViewWPLine = (TextView)findViewById(R.id.textview_wp_line);

        textViewTotalDistance = (TextView)findViewById(R.id.textview_total_distance);
        textViewTotalLine = (TextView)findViewById(R.id.textview_total_line);
        //endregion

        //region Broadcast Receiver
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(WP_ACTION))
                    buttonAddWayPointClicked(findViewById(R.id.buttonAddWayPoint));
                if (intent.getAction().equals(CRESET_ACTION))
                    buttonCResetClicked(findViewById(R.id.buttonResetTripmeter));
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WP_ACTION);
        intentFilter.addAction(CRESET_ACTION);

        registerReceiver(mBroadcastReceiver, intentFilter);
        //endregion


        buildNotification();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mOptionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.menu_mylocation:
                item.setChecked(!item.isChecked());
                updateMyLocation();
                return true;
            case R.id.menu_trackposition:
                item.setChecked(!item.isChecked());
                updateTrackPosition();
                return true;
            case R.id.menu_keepmapcentered:
                item.setChecked(!item.isChecked());
                return true;
            case R.id.menu_map_type_hybrid:
            case R.id.menu_map_type_none:
            case R.id.menu_map_type_normal:
            case R.id.menu_map_type_satellite:
            case R.id.menu_map_type_terrain:
                item.setChecked(true);
                updateMapType();
                return true;

            case R.id.menu_map_zoom_10:
            case R.id.menu_map_zoom_15:
            case R.id.menu_map_zoom_20:
            case R.id.menu_map_zoom_in:
            case R.id.menu_map_zoom_out:
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomLevel(item.getItemId());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }


    }


    private void updateMapZoomLevel(int itemId) {
        if (!checkReady()) {
            return;
        }

        switch (itemId) {
            case R.id.menu_map_zoom_10:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                break;
            case R.id.menu_map_zoom_15:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                break;
            case R.id.menu_map_zoom_20:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(20));
                break;
            case R.id.menu_map_zoom_in:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomIn());
                break;
            case R.id.menu_map_zoom_out:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomOut());
                break;
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomFitTrack();
                break;
        }
    }

    private void updateMapZoomFitTrack() {
        if (mPolyline == null) {
            return;
        }

        List<LatLng> points = mPolyline.getPoints();

        if (points.size() <= 1) {
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        LatLngBounds bounds = builder.build();
        int padding = 0; // offset from edges of the map in pixels
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

    }

    private void updateTrackPosition() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mPolylineOptions = new PolylineOptions().width(5).color(Color.RED);
            mPolyline = mGoogleMap.addPolyline(mPolylineOptions);
        }
    }

    private void updateMapType() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_map_type_normal).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_hybrid).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_none).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_satellite).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_terrain).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        }

    }

    private boolean checkReady() {
        if (mGoogleMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void updateMyLocation() {
        if (mOptionsMenu.findItem(R.id.menu_mylocation).isChecked()) {
            mGoogleMap.setMyLocationEnabled(true);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        mGoogleMap.setMyLocationEnabled(false);
    }

    @SuppressLint("DefaultLocale")
    public void buttonAddWayPointClicked(View view){
        if (locationPrevious==null){
            return;
        }
        markerCount++;


        wpDistance = 0;
        wpLine = 0;
        updateTextViewWPDistance();
        mNotificationManager.notify(0, mBuilder.build());

        lastWaypointLocation = locationPrevious;

        mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())).title(Integer.toString(markerCount)));
        textViewWPCount.setText(String.format("%d", markerCount));
    }

    public void buttonCResetClicked(View view){
        cResetDistance = 0;
        cResetLine = 0;
        updateTextViewCResetDistance();
        mNotificationManager.notify(0, mBuilder.build());

        lastResetLocation = locationPrevious;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        //mGoogleMap.setMyLocationEnabled(false);

        //LatLng latLngITK = new LatLng(59.3954789, 24.6621282);
        //mGoogleMap.addMarker(new MarkerOptions().position(latLngITK).title("ITK"));
        //mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngITK, 17));

        // set zoom level to 15 - street
        mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        // if there was initial location received, move map to it
        if (locationPrevious != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())));
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (mGoogleMap==null) return;

        if (mOptionsMenu.findItem(R.id.menu_keepmapcentered).isChecked() || locationPrevious == null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(newPoint));
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            List<LatLng> points = mPolyline.getPoints();
            points.add(newPoint);
            mPolyline.setPoints(points);
        }

        stepDistance=locationPrevious.distanceTo(location);
        updateSpeed();

        totalDistance+=stepDistance;
        totalLine = startLocation.distanceTo(location);

        cResetDistance +=stepDistance;
        cResetLine = lastResetLocation.distanceTo(location);

        wpDistance +=stepDistance;
        wpLine = lastWaypointLocation.distanceTo(location);

        updateAllDistanceTextViews();

        locationPrevious = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //TODO! Status changed actions
    }

    @Override
    public void onProviderEnabled(String provider) {
        //TODO! Provider enable actions
    }

    @Override
    public void onProviderDisabled(String provider) {
        //TODO! Provider disable actions
    }


    @Override
    protected void onResume(){
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager!=null){
            locationManager.requestLocationUpdates(provider, REFRESH_MS, 1, this);
        }
    }


    @Override
    protected void onPause(){
        super.onPause();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager!=null){
            locationManager.removeUpdates(this);
        }
    }

    private void updateAllDistanceTextViews(){
        updateTextViewCResetDistance();
        updateTextViewWPDistance();
        updateTextViewTotalDistance();
        mNotificationManager.notify(0, mBuilder.build());
    }

    @SuppressLint("DefaultLocale")
    private void updateTextViewCResetDistance() {
        textViewCResetDistance.setText(String.format("%.2f",cResetDistance));
        textViewCResetLine.setText(String.format("%.2f", cResetLine));
    }

    @SuppressLint("DefaultLocale")
    private void updateTextViewWPDistance() {
        textViewWPDistance.setText(String.format("%.2f", wpDistance));
        //textViewWaypointMetrics.setText(String.format("%.2f", wpDistance));
        textViewWPLine.setText(String.format("%.2f", wpLine));

        mRemoteViews.setTextViewText(R.id.textViewWayPointMetrics, String.format("%.2f", wpDistance));
    }

    @SuppressLint("DefaultLocale")
    private void updateTextViewTotalDistance() {
        textViewTotalDistance.setText(String.format("%.2f",totalDistance));
        //textViewTripmeterMetrics.setText(String.format("%.2f", totalDistance));
        textViewTotalLine.setText(String.format("%.2f",totalLine));

        mRemoteViews.setTextViewText(R.id.textViewTripmeterMetrics, String.format("%.2f", totalDistance));
    }

    @SuppressLint("DefaultLocale")
    private void updateSpeed() {
        textViewSpeed.setText(String.format("%d:%d", (int)Math.floor(getSecondsPerKm()/60), (int)(getSecondsPerKm()-Math.floor(getSecondsPerKm()/60)*60)));
    }

    private int getSecondsPerKm() {
        return Math.round(1000/locationPrevious.getSpeed());
    }

    public void buildNotification() {
        // get the view layout
        mRemoteViews = new RemoteViews(
                getPackageName(), R.layout.notification);

        // define intents
        //Intent intentAddWaypoint = new Intent(this, NotificationActionService.class).setAction(WP_ACTION);
        PendingIntent pIntentAddWaypoint = PendingIntent.getService(
                this,
                0,
                new Intent(WP_ACTION),
                0
        );

        //Intent intentResetTripmeter = new Intent(this, NotificationActionService.class).setAction(CRESET_ACTION);
        PendingIntent pIntentResetTripmeter = PendingIntent.getService(
                this,
                0,
                new Intent(CRESET_ACTION),
                0
        );

        // bring back already running activity
        // in manifest set android:launchMode="singleTop"
        PendingIntent pIntentOpenActivity = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // attach events
        mRemoteViews.setOnClickPendingIntent(R.id.buttonAddWayPoint, pIntentAddWaypoint);
        mRemoteViews.setOnClickPendingIntent(R.id.buttonResetTripmeter, pIntentResetTripmeter);
        mRemoteViews.setOnClickPendingIntent(R.id.buttonOpenActivity, pIntentOpenActivity);
        mRemoteViews.setTextViewText(R.id.textViewTripmeterMetrics, "0");
        mRemoteViews.setTextViewText(R.id.textViewWayPointMetrics, "0");

        // build notification
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setContent(mRemoteViews)
                        .setSmallIcon(R.drawable.ic_my_location_white_48dp);

        // notify
        mNotificationManager.notify(0, mBuilder.build());
    }
}