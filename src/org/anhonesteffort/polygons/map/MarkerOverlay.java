package org.anhonesteffort.polygons.map;

import android.view.MotionEvent;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.SimpleLocationOverlay;
import org.anhonesteffort.polygons.database.ZoneDatabase;

/**
 * Programmer: meskio
 * Date: 1/7/14
 */
public class MarkerOverlay extends SimpleLocationOverlay {
  private int id = -1;
  private ZoneMapActivity mapActivity;

  public MarkerOverlay(ZoneMapActivity mapActivity) {
    super(mapActivity);
    this.mapActivity = mapActivity;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
    mapActivity.onPointClick(GoogleGeometryFactory.buildPointRecord(this));
    return false;
  }
/*
  @Override
  public void onMarkerDragStart(Marker dragMarker) {
    mapActivity.onPointMoveStart(GoogleGeometryFactory.buildPointRecord(dragMarker));
  }

  @Override
  public void onMarkerDrag(Marker marker) {
    return;
  }

  @Override
  public void onMarkerDragEnd(Marker dragMarker) {
    mapActivity.onPointMoveStop(GoogleGeometryFactory.buildPointRecord(dragMarker));
  }*/
}
