package org.anhonesteffort.polygons.map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import org.anhonesteffort.polygons.R;
import org.anhonesteffort.polygons.database.DatabaseHelper;
import org.anhonesteffort.polygons.database.ZoneDatabase;
import org.anhonesteffort.polygons.database.model.PointRecord;
import org.anhonesteffort.polygons.database.model.ZoneRecord;

import java.util.LinkedList;
import java.util.List;

public class ZoneMapActivity extends SherlockFragmentActivity {
  private static final String TAG = "org.anhonesteffort.polygons.map.ZoneMapActivity";

  protected static final int   ZONE_FILL_COLOR    = 0x5F880607;
  protected static final float ZONE_STROKE_WIDTH  = 3;
  protected static final float POINT_DEFAULT_HUE  = 0.0f;
  protected static final float POINT_SELECTED_HUE = 210.0f;

  public final static String SAVED_STATE         = "org.anhonesteffort.polygons.map.SAVED_STATE";
  public final static String SELECTED_ZONE_ID    = "org.anhonesteffort.polygons.map.SELECTED_ZONE_ID";
  public final static String SELECTED_ZONE_FOCUS = "org.anhonesteffort.polygons.map.SELECTED_ZONE_FOCUS";
  public final static String SELECTED_POINT_ID   = "org.anhonesteffort.polygons.map.SELECTED_POINT_ID";

  private DatabaseHelper databaseHelper;
  private ActionMode actionMode;
  private NewPointsCallback newPointsCallback;
  private PointEditCallback pointEditCallback;
  private ZoneEditCallback zoneEditCallback;
  private Vibrator vibrator;
  private AlertDialog newZoneDialog;
  private Bundle instanceState;

  public enum DrawState {NAVIGATE, NEW_LABEL, NEW_POINTS, EDIT_ZONE, EDIT_POINT};
  private DrawState myState = DrawState.NAVIGATE;
  private PointRecord lastViewPoint;
  private double last_zoom_level;

  private ZoneRecord selectedZone;
  private PointRecord selectedPoint;
  private GoogleZoneMap zoneMap;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.zone_map_layout);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setSubtitle(R.string.menu_title_polygon_map);

    databaseHelper = DatabaseHelper.getInstance(getBaseContext());
    zoneMap = new GoogleZoneMap(this);
    
    newPointsCallback = new NewPointsCallback(this);
    pointEditCallback = new PointEditCallback(this);
    zoneEditCallback = new ZoneEditCallback(this, zoneMap);

    vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

    instanceState = savedInstanceState;
    if(savedInstanceState == null)
      instanceState = getIntent().getExtras();
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume()");
    super.onResume();

    // Fix me I'm ridiculous!!!
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
    if(settings.contains(getString(R.string.key_last_known_location))) {
      String location = settings.getString(getString(R.string.key_last_known_location), "");
      PointRecord point = new PointRecord(-1, -1,
                                Double.parseDouble(location.split(":")[1].split(",")[0]),
                                Double.parseDouble(location.split(":")[1].split(",")[1]));
      zoneMap.focusOnPoint(point, Double.parseDouble(location.split(":")[0]));
    }
  }

  @Override
  public void onPause() {
    Log.d(TAG, "onPause()");
    super.onPause();

    if(newZoneDialog != null)
      newZoneDialog.dismiss();

    // Fix me I'm ridiculous!!!
    if(lastViewPoint != null) {
      String positionString = Double.toString(last_zoom_level)
          + ":"
          + Double.toString(lastViewPoint.getX())
          + ","
          + Double.toString(lastViewPoint.getY());
      SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
      settings.edit().putString(getString(R.string.key_last_known_location), positionString).commit();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    Log.d(TAG, "onSavedInstanceState()");

    outState.putInt(SAVED_STATE, myState.ordinal());

    if(selectedZone != null)
      outState.putInt(SELECTED_ZONE_ID, selectedZone.getId());
    if(selectedPoint != null)
      outState.putInt(SELECTED_POINT_ID, selectedPoint.getId());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getSupportMenuInflater().inflate(R.menu.map_nav_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case R.id.new_polygon_button:
        setState(DrawState.NEW_LABEL);
        break;
        
      case android.R.id.home:
        this.finish();
        break;
    }
    return true;
  }

  // Sets the draw state and updates the UI appropriately.
  protected void setState(DrawState newState) {
    Log.d(TAG, "setState(), state: " + newState.name());
    Handler mHandler = new Handler();
    
    switch(newState) {
      case NAVIGATE:
        myState = DrawState.NAVIGATE;
        zoneMap.clearPoints();
        break;

      case NEW_LABEL:
        myState = DrawState.NEW_LABEL;
        showNewZoneDialog();
        break;

      case NEW_POINTS:
        myState = DrawState.NEW_POINTS;
        actionMode = this.getSherlock().startActionMode(newPointsCallback);
        
        for(PointRecord point : selectedZone.getPoints())
          zoneMap.addPoint(point);
        break;

      case EDIT_ZONE:
        if(myState != DrawState.EDIT_ZONE) {
          if(actionMode != null)
            actionMode.finish();
          mHandler.postDelayed(new ActionModeStarter(this, zoneEditCallback), 20);
        }
        else
          actionMode.invalidate();

        myState = DrawState.EDIT_ZONE;
        zoneMap.clearPoints();
        selectedPoint = null;
        for(PointRecord point : selectedZone.getPoints())
          zoneMap.addPoint(point);
        break;
        
      case EDIT_POINT:
        if(myState != DrawState.EDIT_POINT) {
          if(actionMode != null)
            actionMode.finish();
          mHandler.postDelayed(new ActionModeStarter(this, pointEditCallback), 20);
        }
        else
          actionMode.invalidate();
        myState = DrawState.EDIT_POINT;
        
        zoneMap.clearPoints();
        for(PointRecord point : selectedZone.getPoints())
          zoneMap.addPoint(point);
        zoneMap.selectPoint(selectedPoint);
        break;

        default:
          break;
    }
  }
  
  public DrawState getState() {
    return myState;
  }
  
  public void setMode(ActionMode mode) {
    actionMode = mode;
  }

  public ZoneRecord getSelectedZone() {
    return selectedZone;
  }
  
  public PointRecord getSelectedPoint() {
    return selectedPoint;
  }

  public void onMapLoad() {
    Log.d(TAG, "onMapLoad()");
    if(instanceState == null) {
      setState(DrawState.NAVIGATE);
      return;
    }

    if(instanceState.containsKey(SELECTED_ZONE_ID))
      selectedZone = databaseHelper.getZoneDatabase().getZone(instanceState.getInt(SELECTED_ZONE_ID));
    if(instanceState.containsKey(SELECTED_ZONE_FOCUS))
      zoneMap.focusOnZone(selectedZone);
    if(instanceState.containsKey(SELECTED_POINT_ID))
      selectedPoint = databaseHelper.getZoneDatabase().getPoint(instanceState.getInt(SELECTED_POINT_ID));
    if(instanceState.containsKey(SAVED_STATE))
      setState(DrawState.values()[instanceState.getInt(SAVED_STATE)]);
  }

  public void onMapViewChange(PointRecord viewCenter, double view_zoom) {
    lastViewPoint = viewCenter;
    last_zoom_level = view_zoom;

    if(myState == DrawState.NEW_POINTS || myState == DrawState.EDIT_ZONE || myState == DrawState.EDIT_POINT) {
      for(PointRecord point : selectedZone.getPoints())
        zoneMap.addPoint(point);
      
      if(myState == DrawState.EDIT_POINT)
        zoneMap.selectPoint(selectedPoint);
    }
  }

  public void onMapClick(PointRecord clickPoint) {
    Log.d(TAG, "onMapClick()");

    if(myState == DrawState.NEW_POINTS || myState == DrawState.EDIT_ZONE || myState == DrawState.EDIT_POINT) {
      clickPoint = databaseHelper.getZoneDatabase().addPoint(clickPoint, selectedZone.getId());
      selectedZone.getPoints().add(clickPoint);
      zoneMap.addPoint(clickPoint);
    }

    if(myState == DrawState.EDIT_ZONE || myState == DrawState.EDIT_POINT) {
      updateSelectedZone();
      setState(DrawState.EDIT_ZONE);
    }
  }

  public void onMapLongClick(PointRecord clickPoint) {
    Log.d(TAG, "onMapLongClick()");
    if(myState == DrawState.NAVIGATE || myState == DrawState.EDIT_ZONE || myState == DrawState.EDIT_POINT) {

      Cursor zoneCursor = databaseHelper.getZoneDatabase().getZonesContainingPoint(clickPoint);
      ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(zoneCursor);

      List<ZoneRecord> selectedZones = new LinkedList<ZoneRecord>();
      while(zoneReader.getNext() != null)
        selectedZones.add(zoneReader.getCurrent());

      if(selectedZones.isEmpty() == false) {
        selectedZone = selectedZones.get(0);
        setState(DrawState.EDIT_ZONE);
        vibrator.vibrate(50);
      }
      else {
        setState(DrawState.NAVIGATE);
        if(actionMode != null)
          actionMode.finish();
      }
    }
  }

  public void onPointClick(PointRecord clickPoint) {
    // Nothing to do.
  }
  
  public void onPointMoveStart(PointRecord clickPoint) {
    vibrator.vibrate(30);
  }
  
  public void onPointMoveStop(PointRecord clickPoint) {
    Log.d(TAG, "onPointMoveStop() " + clickPoint.getId());

    selectedPoint = clickPoint;
    selectedZone.removePoint(selectedPoint.getId());
    selectedZone.getPoints().add(selectedPoint);

    if(myState == DrawState.EDIT_ZONE || myState == DrawState.EDIT_POINT) {
      updateSelectedZone();
      setState(DrawState.EDIT_POINT);
    }
    else
      databaseHelper.getZoneDatabase().updateZone(selectedZone);
  }

  // Redraws the selected polygon on map, updates in database.
  public void updateSelectedZone() {
    Log.d(TAG, "updateSelectedZone()");
    
    switch(selectedZone.makeValid()) {
      case ZoneRecord.OK:
        zoneMap.removeZone(selectedZone.getId());
        databaseHelper.getZoneDatabase().updateZone(selectedZone);
        zoneMap.addZone(selectedZone);
        break;

      case ZoneRecord.TOO_FEW_POINTS:
        if(myState == DrawState.NEW_POINTS)
          databaseHelper.getZoneDatabase().deleteZone(selectedZone.getId());
        else {
          selectedZone = databaseHelper.getZoneDatabase().getZone(selectedZone.getId());
          zoneMap.removeZone(selectedZone.getId());
          zoneMap.addZone(selectedZone);
        }
        Toast.makeText(this, this.getString(R.string.error_too_few_points), Toast.LENGTH_SHORT).show();
        break;

      case ZoneRecord.TOO_MANY_POINTS:
        if(myState == DrawState.NEW_POINTS) {
          databaseHelper.getZoneDatabase().deleteZone(selectedZone.getId());
        }
        else {
          selectedZone.getPoints().remove(selectedZone.getPoints().size() - 1);
          databaseHelper.getZoneDatabase().updateZone(selectedZone);
          zoneMap.removeZone(selectedZone.getId());
          zoneMap.addZone(selectedZone);
          
          if(actionMode != null)
            actionMode.finish();
          setState(DrawState.NAVIGATE);
        }
        Toast.makeText(this, this.getString(R.string.error_too_many_points), Toast.LENGTH_SHORT).show();
        break;

      default:
        zoneMap.removeZone(selectedZone.getId());
        databaseHelper.getZoneDatabase().deleteZone(selectedZone.getId());
        
        setState(DrawState.NAVIGATE);
        if(actionMode != null)
          actionMode.finish();
        Toast.makeText(this, this.getString(R.string.error_polygon_not_normal), Toast.LENGTH_SHORT).show();
    }
  }

  private void showNewZoneDialog() {
    if(newZoneDialog != null)
      newZoneDialog.dismiss();
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = this.getLayoutInflater();
    final View view = inflater.inflate(R.layout.new_zone_label_layout, null);

    builder.setView(view).setTitle(R.string.title_zone_label_dialog);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        EditText polygonLabelEdit = (EditText) view.findViewById(R.id.zone_list_row_label);
        if(polygonLabelEdit.getText().length() < 1 || 
            databaseHelper.getZoneDatabase().isLabelAvailable(polygonLabelEdit.getText().toString()) == false) {
          Toast.makeText(ZoneMapActivity.this, ZoneMapActivity.this.getString(R.string.error_zone_label), Toast.LENGTH_SHORT).show();
          setState(DrawState.NEW_LABEL);
        }
        else {
          selectedZone = databaseHelper.getZoneDatabase().addZone(polygonLabelEdit.getText().toString());
          setState(DrawState.NEW_POINTS);
        }
      }
    });
    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface arg0) {
        setState(DrawState.NAVIGATE);
      }
    });
    newZoneDialog = builder.show();
  }
}
