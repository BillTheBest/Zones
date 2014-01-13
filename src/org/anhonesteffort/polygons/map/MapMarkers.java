package org.anhonesteffort.polygons.map;

import android.view.MotionEvent;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.util.GeoPoint;
import org.anhonesteffort.polygons.database.ZoneDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmer: meskio
 * Date: 1/7/14
 */
public class MapMarkers extends ItemizedOverlayWithFocus<MapMarkers.OverlayMarker> {

  public class OverlayMarker extends OverlayItem {
    public int id = -1;

    public OverlayMarker(GeoPoint point) {
      super(null, null, point);
    }
  }


  private ZoneMapActivity mapActivity;

  public MapMarkers(final ZoneMapActivity mapActivity) {
    super(mapActivity, new ArrayList<OverlayMarker>(), new ItemizedIconOverlay.OnItemGestureListener<OverlayMarker> () {
      @Override
      public boolean onItemSingleTapUp(final int index, final OverlayMarker item) {
        mapActivity.onPointClick(OsmGeometryFactory.buildPointRecord(item));
        return false;
      }
      
      @Override
      public boolean onItemLongPress(final int index, final OverlayMarker item) {
        return false;
      }
    });
    this.mapActivity = mapActivity;
  }

  public void addMarker(GeoPoint point, int id) {
    final OverlayMarker item = new OverlayMarker(point);
    item.setMarkerHotspot(OverlayItem.HotspotPlace.BOTTOM_CENTER);
    item.id = id;
    this.addItem(item);
  }

  public void clear() {
    removeAllItems();
  }

/*
  @Override
  public void onMarkerDragStart(Marker dragMarker) {
    mapActivity.onPointMoveStart(OsmGeometryFactory.buildPointRecord(dragMarker));
  }

  @Override
  public void onMarkerDrag(Marker marker) {
    return;
  }

  @Override
  public void onMarkerDragEnd(Marker dragMarker) {
    mapActivity.onPointMoveStop(OsmGeometryFactory.buildPointRecord(dragMarker));
  }*/
}
