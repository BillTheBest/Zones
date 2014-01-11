package org.anhonesteffort.polygons;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;

import org.anhonesteffort.polygons.database.DatabaseHelper;
import org.anhonesteffort.polygons.database.GeometryChangeListener;
import org.anhonesteffort.polygons.database.LocationSubscriberChangeListener;
import org.anhonesteffort.polygons.database.ZoneDatabase;
import org.anhonesteffort.polygons.database.model.PointRecord;
import org.anhonesteffort.polygons.database.model.ZoneRecord;
import org.anhonesteffort.polygons.location.BetterLocationListener;
import org.anhonesteffort.polygons.location.BetterLocationManager;
import org.anhonesteffort.polygons.map.OsmGeometryFactory;
import org.anhonesteffort.polygons.receiver.BroadcastActionLauncher;
import org.anhonesteffort.polygons.transport.sms.SMSSender;

import java.util.LinkedList;
import java.util.List;

public class ZoneService extends Service implements 
  GeometryChangeListener, LocationSubscriberChangeListener, BetterLocationListener {

  private static final String TAG            = "ZoneService";

  public static final String ZONE_ENTER      = "org.anhonesteffort.polygons.ZoneService.ZONE_ENTER";
  public static final String ZONE_EXIT       = "org.anhonesteffort.polygons.ZoneService.ZONE_EXIT";
  public static final String ZONE_ID         = "org.anhonesteffort.polygons.ZoneService.ZONE_ID";
  public static final String ZONE_LABEL      = "org.anhonesteffort.polygons.ZoneService.ZONE_LABEL";
  public static final String DEVICE_LOCATION = "org.anhonesteffort.polygons.ZoneService.DEVICE_LOCATION";

  public static final int MESSAGE_GEOFENCING_ENABLED  = 1337;
  public static final int MESSAGE_GEOFENCING_DISABLED = 1336;

  private static final int MINIMUM_INTERVAL_MS         = 1500;
  private static final int REGULAR_INTERVAL_MS         = 120000;
  private static final int SUBSCRIBER_INTERVAL_MS      = 30000;
  private static final double AVG_WALKING_VELOCITY_MPS = 1.5;

  private final Messenger mMessenger = new Messenger(new ZoneMessageHandler());
  private DatabaseHelper applicationStorage;
  private BroadcastActionLauncher actionLauncher;
  
  private BetterLocationManager locationManager;
  private Location bestLocation;

  @Override
  public void onCreate() {
    Log.d(TAG, "onCreate()");
    super.onCreate();
    
    // Register for changes in polygon geometries and location subscribers.
    applicationStorage = DatabaseHelper.getInstance(this.getBaseContext());
    applicationStorage.getZoneDatabase().addGeometryChangeListener(this);
    applicationStorage.getActionDatabase().addLocationSubscriberChangeListener(this);
    
    // Use the BetterLocationManager for location updates.
    locationManager = new BetterLocationManager(this.getApplicationContext());
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
    if(settings.getBoolean(PreferencesActivity.PREF_GEOFENCING, true))
      locationManager.requestLocationUpdates(MINIMUM_INTERVAL_MS, this);
    
    // Register the BroadcastActionLauncher for SCREEN_ON broadcasts, cannot be done via ApplicationManifest.
    actionLauncher = new BroadcastActionLauncher();
    this.registerReceiver(actionLauncher, new IntentFilter(Intent.ACTION_SCREEN_ON));
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind()");
    return mMessenger.getBinder();
  }
  
  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");
    super.onDestroy();
    
    // Unregister from changes in polygon geometries, location subscribers, location updates and SCREEN_ON broadcasts.
    applicationStorage.getZoneDatabase().removeGeometryChangeListener(this);
    applicationStorage.getActionDatabase().removeLocationSubscriberChangeListener(this);
    locationManager.removeLocationUpdates(this);
    this.unregisterReceiver(actionLauncher);
  }
  
  private void handleGeofencingPreferenceChange(boolean enabled) {
    Log.d(TAG, "Geofencing preference set to: " + enabled);
    if(enabled)
      locationManager.requestLocationUpdates(MINIMUM_INTERVAL_MS, this);
    else
      locationManager.removeLocationUpdates(this);
  }

  class ZoneMessageHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {

        case MESSAGE_GEOFENCING_ENABLED:
          handleGeofencingPreferenceChange(true);
          break;

        case MESSAGE_GEOFENCING_DISABLED:
          handleGeofencingPreferenceChange(false);
          break;

        default:
          super.handleMessage(msg);
      }
    }
  }

  public void sendPolygonBroadcasts() {
    Log.d(TAG, "sendPolygonBroadcasts()");
    
    // Polygons we occupied before, we occupy now.
    Cursor occupiedCursor = applicationStorage.getZoneDatabase().getZonesOccupied();
    ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(occupiedCursor);

    List<ZoneRecord> zonesIOccupied = new LinkedList<ZoneRecord>();
    while (zoneReader.getNext() != null)
      zonesIOccupied.add(zoneReader.getCurrent());

    PointRecord bestPoint = new PointRecord(0, -1, bestLocation.getLongitude(), bestLocation.getLatitude());
    Cursor occupyCursor = applicationStorage.getZoneDatabase().getZonesContainingPoint(bestPoint);
    zoneReader = new ZoneDatabase.Reader(occupyCursor);

    List<ZoneRecord> zonesIOccupy = new LinkedList<ZoneRecord>();
    while(zoneReader.getNext() != null)
      zonesIOccupy.add(zoneReader.getCurrent());

    // Figure out which zoneDatabase we have left. FIX ME I'M UGLY!
    boolean have;
    for(ZoneRecord zoneOccupied : zonesIOccupied) {
      have = false;
      for(ZoneRecord zone : zonesIOccupy) {
        if(zoneOccupied.getId() == zone.getId())
          have = true;
      }
      if(have == false) {
        applicationStorage.getZoneDatabase().setZoneOccupancy(zoneOccupied.getId(), false);

        // Send the polygon exit broadcast.
        double[] phoneLocation = {bestLocation.getLatitude(), bestLocation.getLongitude()};
        Intent polygonEnterIntent = new Intent(ZONE_EXIT);
        polygonEnterIntent.putExtra(ZONE_ID, zoneOccupied.getId());
        polygonEnterIntent.putExtra(ZONE_LABEL, zoneOccupied.getLabel());
        polygonEnterIntent.putExtra(DEVICE_LOCATION, phoneLocation);
        sendBroadcast(polygonEnterIntent);
      }
    }

    // Figure out which zoneDatabase we have entered.
    for(ZoneRecord zone : zonesIOccupy) {
      have = false;
      for(ZoneRecord zoneOccupied : zonesIOccupied) {
        if(zone.getId() == zoneOccupied.getId())
          have = true;
      }
      if(have == false) {
        applicationStorage.getZoneDatabase().setZoneOccupancy(zone.getId(), true);

        // Send the polygon enter broadcast.
        double[] phoneLocation = {bestLocation.getLatitude(), bestLocation.getLongitude()};
        Intent polygonEnterIntent = new Intent(ZONE_ENTER);
        polygonEnterIntent.putExtra(ZONE_ID, zone.getId());
        polygonEnterIntent.putExtra(ZONE_LABEL, zone.getLabel());
        polygonEnterIntent.putExtra(DEVICE_LOCATION, phoneLocation);
        sendBroadcast(polygonEnterIntent);
      }
    }
  }

  @Override
  public void onBetterLocationAvailable(Location location) {
    Log.d(TAG, "onBetterLocationAvailable()");
    bestLocation = location;
    
    // If we have any location update subscribers oblige them.
    List<String> locationSubscribers = applicationStorage.getActionDatabase().getLocationSubscribers();
    if(locationSubscribers.size() > 0) {
      locationManager.requestLocationUpdates(SUBSCRIBER_INTERVAL_MS, this);
      for(String subscriber : locationSubscribers)
        SMSSender.sendTextMessage(subscriber, Double.toString(bestLocation.getLatitude()) + ", " + Double.toString(bestLocation.getLongitude()));
    }
    
    // Otherwise conserve power by only updating location when reasonable.
    else {
      PointRecord bestPoint = OsmGeometryFactory.buildPointRecord(bestLocation);
      double device_velocity_mps = AVG_WALKING_VELOCITY_MPS;
      double time_to_polygon_ms = REGULAR_INTERVAL_MS * 2;
      double distance_to_polygon_m = applicationStorage.getZoneDatabase().distanceToClosestZone(bestPoint);
      
      // Assume the user is walking if device velocity unknown.
      if(location.getSpeed() > 0)
        device_velocity_mps = location.getSpeed();
      
      // Estimate how many seconds it will take to reach the closest polygon.
      if(distance_to_polygon_m > 0)
        time_to_polygon_ms = (distance_to_polygon_m / device_velocity_mps) * 1000;
      
      Log.d(TAG, "Closest polygon is " + distance_to_polygon_m + "m & " + (long) time_to_polygon_ms + "ms away @" + device_velocity_mps + "m/s.");
      
      // Allow the GPS to sleep for at most 1/2 the time it would take to reach the closest polygon.
      if((long) (time_to_polygon_ms / 2) > REGULAR_INTERVAL_MS)
        locationManager.requestLocationUpdates(REGULAR_INTERVAL_MS, this);
      else if((long) (time_to_polygon_ms / 2) > MINIMUM_INTERVAL_MS)
        locationManager.requestLocationUpdates((long) (time_to_polygon_ms / 2), this);
      else
        locationManager.requestLocationUpdates(MINIMUM_INTERVAL_MS, this);
    }
    
    sendPolygonBroadcasts();
  }

  @Override
  public void onGeometryChange() {
    if(bestLocation != null)
      onBetterLocationAvailable(bestLocation);
  }

  @Override
  public void onSubscriberChange() {
    if(bestLocation != null)
      onBetterLocationAvailable(bestLocation);
  }

  @Override
  public void onLocationsDisabled(List<String> missingProviders) {
    // Nothing to do.
  }

  @Override
  public void onLocationsEnabled() {
    // Nothing to do.
  }
}
