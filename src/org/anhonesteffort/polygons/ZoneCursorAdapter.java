package org.anhonesteffort.polygons;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import org.anhonesteffort.polygons.database.ActionDatabase;
import org.anhonesteffort.polygons.database.DatabaseHelper;
import org.anhonesteffort.polygons.database.ZoneDatabase;
import org.anhonesteffort.polygons.database.model.ZoneRecord;
import org.anhonesteffort.polygons.map.GoogleGeometryFactory;

/**
 * Programmer: rhodey
 * Date: 9/23/13
 */
public class ZoneCursorAdapter extends CursorAdapter {

  public ZoneCursorAdapter(Context context, Cursor zoneCursor) {
    super(context, zoneCursor);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
    LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    return layoutInflater.inflate(R.layout.zone_list_row_layout, null);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(cursor);
    ZoneRecord zone = zoneReader.getCurrent();

    DatabaseHelper databaseHelper = DatabaseHelper.getInstance(context);
    Cursor actionRecords = databaseHelper.getActionDatabase().getActions(zone.getId());
    ActionDatabase.Reader actionReader = new ActionDatabase.Reader(actionRecords);

    int count = 0;
    while (actionReader.getNext() != null) {
      if(actionReader.getCurrent().runOnEnter())
        count++;
      if(actionReader.getCurrent().runOnExit())
        count++;
    }
    actionReader.close();

    int meters;
    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    if(lastLocation != null) {
      meters = (int) databaseHelper.getZoneDatabase().distanceBetween(
          GoogleGeometryFactory.buildPointRecord(
              new LatLng(
                  lastLocation.getLatitude(),
                  lastLocation.getLongitude()
              )), zone.getId());
    }
    else
      meters = -1;

    TextView zoneLabelView = (TextView) view.findViewById(R.id.zone_list_row_label);
    TextView zoneDetailsView = (TextView) view.findViewById(R.id.zone_list_row_details);
    zoneLabelView.setText(zone.getLabel());

    if(count == 1)
      zoneDetailsView.setText(count + " action ready at " + meters + "m away.");
    else
      zoneDetailsView.setText(count + " actions ready at " + meters + "m away.");

    if(databaseHelper.getZoneDatabase().isZoneSelected(zone.getId()))
      view.setBackgroundResource(R.color.holo_blue_light);
    else
      view.setBackgroundResource(0);

    view.setTag(R.integer.zone_list_row_id_tag, Integer.valueOf(zone.getId()));
    view.setTag(R.integer.zone_list_row_select_tag, Boolean.valueOf(databaseHelper.getZoneDatabase().isZoneSelected(zone.getId())));
  }

}
