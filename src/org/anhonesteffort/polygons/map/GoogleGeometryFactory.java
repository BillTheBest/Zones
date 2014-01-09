package org.anhonesteffort.polygons.map;

import android.graphics.Rect;
import android.location.Location;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.anhonesteffort.polygons.database.model.PointRecord;
import org.anhonesteffort.polygons.database.model.ZoneRecord;

public class GoogleGeometryFactory {

  public static PointRecord buildPointRecord(IGeoPoint point) {
    PointRecord newPoint =  new PointRecord(-1, -1, point.getLongitude(), point.getLatitude());
    return newPoint;
  }

  public static PointRecord buildPointRecord(MarkerOverlay marker) {
    PointRecord newPoint =  new PointRecord(marker.getId(), -1,
                                            marker.getMyLocation().getLongitude(),
                                            marker.getMyLocation().getLatitude());
    return newPoint;
  }

  public static PointRecord buildPointRecord(Location point) {
    PointRecord newPoint =  new PointRecord(-1, -1, point.getLongitude(), point.getLatitude());
    return newPoint;
  }

  public static ZoneRecord buildZoneRecord(Rect rect) {
    ZoneRecord zoneRecord = new ZoneRecord(-1, "");
    zoneRecord.getPoints().add(buildPointRecord(new GeoPoint(rect.top, rect.left)));
    zoneRecord.getPoints().add(buildPointRecord(new GeoPoint(rect.bottom, rect.left)));
    zoneRecord.getPoints().add(buildPointRecord(new GeoPoint(rect.bottom, rect.right)));
    zoneRecord.getPoints().add(buildPointRecord(new GeoPoint(rect.top, rect.right)));
    return zoneRecord;
  }

  public static GeoPoint buildCenterGeoPoint(PointRecord[] bounds) {
    double x0 = bounds[0].getX();
    double y0 = bounds[0].getY();
    double x1 = bounds[1].getX();
    double y1 = bounds[1].getY();
    double latitude = y0 + ((y1-y0)/2.0);
    double longitude = x0 + ((x1-x0)/2.0);
    return new GeoPoint(latitude, longitude);
  }
}
