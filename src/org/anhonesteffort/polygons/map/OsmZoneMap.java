package org.anhonesteffort.polygons.map;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import org.osmdroid.api.IMap;
import org.osmdroid.api.IPosition;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.api.OnCameraChangeListener;
import org.anhonesteffort.polygons.R;
import org.anhonesteffort.polygons.database.DatabaseHelper;
import org.anhonesteffort.polygons.database.ZoneDatabase;
import org.anhonesteffort.polygons.database.model.PointRecord;
import org.anhonesteffort.polygons.database.model.ZoneRecord;
import org.anhonesteffort.polygons.map.ZoneMapActivity.DrawState;

import java.util.ArrayList;
import java.util.List;
import java.lang.String;

public class OsmZoneMap implements OnCameraChangeListener {
  
  private static final String TAG = "OsmZoneMap";

  private DatabaseHelper databaseHelper;
  private ZoneMapActivity mapActivity;
  
  private MapView mapView;
  private IMap map;
  private boolean map_loaded = false;
  private SparseArray<Polygon> mapPolygons = new SparseArray<Polygon>();
  private List<MarkerOverlay> mapMarkers = new ArrayList<MarkerOverlay>();

  public OsmZoneMap(ZoneMapActivity mapActivity) {
    this.mapActivity = mapActivity;
    databaseHelper = DatabaseHelper.getInstance(mapActivity.getApplicationContext());
    initializeMap();
  }

  // Set up the OSM and all listeners.
  private void initializeMap() {
    if(mapView == null) {
      mapView = (MapView) mapActivity.findViewById(R.id.map);
      map = new OsmdroidMapWrapper(mapView);
      if(mapView != null) {
        mapView.setMultiTouchControls(true);
        map.setZoom(3);
        map.setMyLocationEnabled(true);
        map.setOnCameraChangeListener(this);
        setGestureDetector();
        
        //TODO: mapView.setOnMarkerDragListener(this);
      }
      else {
        Log.e(TAG, "Map failed to load! Why?!");
        AlertDialog.Builder builder = new AlertDialog.Builder(mapActivity);
        builder.setTitle(R.string.error_map_load);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mapActivity.startActivity(intent);
          }
        });
        builder.show();
      }
    }
  }

  private void setGestureDetector() {
    final GestureDetector gd = new GestureDetector(new MapGestureDetectorListener());
    mapView.setOnTouchListener(new OnTouchListener(){
      @Override
      public boolean onTouch(final View v, final MotionEvent e) {
        return gd.onTouchEvent(e);
      }
    });
  }

  public void addPoint(PointRecord point) {
    MarkerOverlay marker = buildMarkerOverlay(point);
    mapView.getOverlays().add(marker);
    mapView.invalidate();
    mapMarkers.add(marker);
  }

  private MarkerOverlay buildMarkerOverlay(PointRecord point) {
    GeoPoint position = new GeoPoint(point.getY(), point.getX());
    MarkerOverlay marker = new MarkerOverlay(mapActivity);
    marker.setId(point.getId());
    marker.setLocation(position);
    return marker;
  }

  public void selectPoint(PointRecord point) {
    for(MarkerOverlay marker : mapMarkers) {
      PointRecord mapPoint = OsmGeometryFactory.buildPointRecord(marker);
      if(point.getId() == mapPoint.getId())
      {}
        //TODO
        //marker.setIcon(BitmapDescriptorFactory.defaultMarker(ZoneMapActivity.POINT_SELECTED_HUE));
    }
  }

  public void addZone(ZoneRecord mapZone) {
    Polygon mapPolygon = buildPolygon(mapZone);
    mapView.getOverlays().add(mapPolygon);
    mapView.invalidate();
    mapPolygons.append(mapZone.getId(), mapPolygon);
  }

  public void removeZone(int zone_id) {
    Log.d(TAG, "removeZone(), zone_id: " + zone_id);
    if(mapPolygons.get(zone_id, null) != null) {
      List<Overlay> overlays = mapView.getOverlays();
      overlays.remove(mapPolygons.get(zone_id));
      mapPolygons.remove(zone_id);
    }
  }

  public void focusOnPoint(PointRecord point, double zoom) {
    map.setCenter(point.getY(), point.getX());
    map.setZoom((float) zoom);
  }

  public void focusOnZone(ZoneRecord zoneRecord) {
    PointRecord[] zoneBounds = databaseHelper.getZoneDatabase().getZoneBounds(zoneRecord.getId());
    if(zoneBounds[0].getX() == -1)
      return;

    // FIXME: there is no 'moveCamera' on osmdroid
    GeoPoint center = OsmGeometryFactory.buildCenterGeoPoint(zoneBounds);
    map.setCenter(center.getLatitude(), center.getLongitude());
  }

  @Override
  public void onCameraChange(IPosition position) {
    Log.d(TAG, "onCameraChange()");
    if(map_loaded == false) {
      map_loaded = true;
      mapActivity.onMapLoad();
    }

    PointRecord mapCenter = new PointRecord(0, -1, position.getLongitude(), position.getLatitude());
    addZonesWithinBounds(mapView.getScreenRect(new Rect()));
    mapActivity.onMapViewChange(mapCenter, position.getZoomLevel());
  }

  // Clear the map and redraw all zoneDatabase within provided bounds.
  private void addZonesWithinBounds(Rect bounds) {
    Log.d(TAG, "addZonesWithinBounds()");

    ZoneRecord visibleArea;
    Polygon mapPolygon;

    mapMarkers.clear();
    mapPolygons.clear();
    map.clear();

    visibleArea = OsmGeometryFactory.buildZoneRecord(bounds);
    Cursor visibleZones = databaseHelper.getZoneDatabase().getZonesIntersecting(visibleArea);
    ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(visibleZones);

    while(zoneReader.getNext() != null) {
      if(mapActivity.getState() != DrawState.NEW_POINTS ||
          zoneReader.getCurrent().getId() != mapActivity.getSelectedZone().getId()) {

        ZoneRecord mapZone = zoneReader.getCurrent();
        mapPolygon = buildPolygon(mapZone);
        mapView.getOverlays().add(mapPolygon);
        mapPolygons.put(mapZone.getId(), mapPolygon);
      }
    }
    visibleZones.close();
    mapView.invalidate();
  }

  public void clearPoints() {
    List<Overlay> overlays = mapView.getOverlays();
    for(Overlay marker : mapMarkers) {
      overlays.remove(marker);
    }
    mapMarkers.clear();
    mapView.invalidate();
  }

  private Polygon buildPolygon(ZoneRecord zoneRecord) {
    Polygon polygon = new Polygon(mapActivity);
    polygon.setFillColor(ZoneMapActivity.ZONE_FILL_COLOR);
    polygon.setStrokeWidth(ZoneMapActivity.ZONE_STROKE_WIDTH);

    List<PointRecord> points = zoneRecord.getPoints();
    List<GeoPoint> geoPoints = new ArrayList();
    for(PointRecord point : points) {
        geoPoints.add(buildGeoPoint(point));
    }
    // In the next version of OsmBonuscPack won't be necesary to add the
    // first point again:
    // http://code.google.com/p/osmbonuspack/issues/detail?id=46
    geoPoints.add(buildGeoPoint(points.get(0)));
    polygon.setPoints(geoPoints);

    return polygon;
  }

  public static GeoPoint buildGeoPoint(PointRecord point) {
    return new GeoPoint(point.getY(), point.getX());
  }

  private class MapGestureDetectorListener extends GestureDetector.SimpleOnGestureListener {
    public MapGestureDetectorListener() {
      super();
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      Log.d(TAG, "onSingleTapConfirmed()");
      GeoPoint clickPoint = getGeoPoint(e);
      mapActivity.onMapClick(OsmGeometryFactory.buildPointRecord(clickPoint));
      return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
      Log.d(TAG, "onLongPress()");
      GeoPoint clickPoint = getGeoPoint(e);
      mapActivity.onMapLongClick(OsmGeometryFactory.buildPointRecord(clickPoint));
    }

    private GeoPoint getGeoPoint(MotionEvent e) {
      final int eventX = (int) e.getX();
      final int eventY = (int) e.getY();
      final Projection pj = mapView.getProjection();
      return (GeoPoint) pj.fromPixels(eventX, eventY);
    }
  }
}
