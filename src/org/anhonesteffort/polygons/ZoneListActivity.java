package org.anhonesteffort.polygons;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.anhonesteffort.polygons.database.DatabaseHelper;
import org.anhonesteffort.polygons.database.ZoneDatabase;
import org.anhonesteffort.polygons.database.model.ZoneRecord;
import org.anhonesteffort.polygons.map.ZoneMapActivity;

public class ZoneListActivity extends SherlockActivity implements
  ListView.OnItemClickListener, ListView.OnItemLongClickListener, ActionMode.Callback {
  
  private static final String TAG                = "ZoneListActivity";
  private static final String RESTORE_SELECTIONS = "org.anhonesteffort.polygons.ZoneListActivity.RESTORE_SELECTIONS";

  private ListView zoneListView;
  private ActionMode mActionMode;
  private DatabaseHelper databaseHelper;
  private AlertDialog zoneLabelDialog;

  private int select_count = 0;
  private boolean select_mode_active = false;
  private boolean list_is_initializing = false;
  private boolean list_is_initialized = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate()");

    setContentView(R.layout.zone_list_layout);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setSubtitle(R.string.menu_title_zone_list);

    databaseHelper = DatabaseHelper.getInstance(this.getBaseContext());
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(TAG, "onPause()");

    if(zoneLabelDialog != null)
      zoneLabelDialog.dismiss();
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume() " + list_is_initialized);

    if(list_is_initialized == false && list_is_initializing == false) {
      databaseHelper.getZoneDatabase().clearSelectedZones();
      initializeList();
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    Log.d(TAG, "onRestoreInstanceState()");

    if(savedInstanceState != null && savedInstanceState.getBoolean(RESTORE_SELECTIONS, false)) {
      initializeList();

      /*select_count = databaseHelper.getZoneDatabase().getZonesSelected().getCount();
      if(select_count > 0) {
        select_mode_active = true;
        mActionMode = startActionMode(this);
        updateActionMode();
      } */
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Log.d(TAG, "onSaveInstanceState()");

    outState.putBoolean(RESTORE_SELECTIONS, select_mode_active);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
    }
    return true;
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = mode.getMenuInflater();
    inflater.inflate(R.menu.zone_list_menu, menu);
    mode.setTitle(getString(R.string.title_zone_list_batch));
    mode.setSubtitle(select_count + " " + getString(R.string.zones_selected));
    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {

      case R.id.delete_zone_button:
        new DeleteSelectedZonesTask().execute();
        break;

      case R.id.edit_zone_label_button:
        showZoneLabelDialog();
        break;
    }
    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    Log.d(TAG, "onDestroyActionMode()");

    for(int i = 0; i < zoneListView.getChildCount(); i++) {
      zoneListView.getChildAt(i).setBackgroundResource(0);
      zoneListView.getChildAt(i).setTag(R.integer.zone_list_row_select_tag, Boolean.FALSE);
    }

    databaseHelper.getZoneDatabase().clearSelectedZones();
    select_count = 0;
    select_mode_active = false;
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if(view.isShown() == false)
      return;

    if(select_mode_active == false) {
      Integer zone_id = (Integer) view.getTag(R.integer.zone_list_row_id_tag);

      Intent intent = new Intent(this, ZoneMapActivity.class);
      intent.putExtra(ZoneMapActivity.SELECTED_ZONE_ID, zone_id);
      intent.putExtra(ZoneMapActivity.SAVED_STATE, ZoneMapActivity.DrawState.EDIT_ZONE.ordinal());
      intent.putExtra(ZoneMapActivity.SELECTED_ZONE_FOCUS, true);
      startActivity(intent);
    }
    else {
      if(view.getTag(R.integer.zone_list_row_select_tag) == Boolean.TRUE)
        unselectZone(view);
      else
        selectZone(view);
    }
  }

  @Override
  public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    if(view.isShown() == false)
      return true;

    if(select_mode_active == false && (view.getTag(R.integer.zone_list_row_select_tag)) == Boolean.FALSE) {
      select_mode_active = true;
      mActionMode = startActionMode(this);
      selectZone(view);
    }
    return true;
  }

  private class InitializeListTask extends AsyncTask<Void, Void, Integer> {

    Cursor zoneCursor;

    protected InitializeListTask() {
      Log.d(TAG, "initializeListTask()");

      list_is_initializing = true;
      list_is_initialized = false;
      select_count = 0;
      select_mode_active = false;
    }

    protected Integer doInBackground(Void... params) {
      zoneCursor = databaseHelper.getZoneDatabase().getZones();
      select_count = databaseHelper.getZoneDatabase().getZonesSelected().getCount();
      return 0;
    }

    protected void onProgressUpdate(Void... progress) {
      // Nothing to do.
    }

    protected void onPostExecute(Integer result) {
      CursorAdapter zoneAdapter = new ZoneCursorAdapter(ZoneListActivity.this, zoneCursor);

      zoneListView = (ListView) findViewById(R.id.list);
      zoneListView.setAdapter(zoneAdapter);
      zoneListView.setOnItemClickListener(ZoneListActivity.this);
      zoneListView.setOnItemLongClickListener(ZoneListActivity.this);

      list_is_initialized = true;
      list_is_initializing = false;

      if(select_count > 0) {
        select_mode_active = true;
        mActionMode = startActionMode(ZoneListActivity.this);
      }
      updateActionMode();
    }
  }

  private class DeleteSelectedZonesTask extends AsyncTask<Void, Void, Integer> {

    protected Integer doInBackground(Void... params) {
      Cursor selectedZones = databaseHelper.getZoneDatabase().getZonesSelected();
      ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(databaseHelper, selectedZones);

      while (zoneReader.getNext() != null)
        databaseHelper.getZoneDatabase().deleteZone(zoneReader.getCurrent().getId());

      zoneReader.close();
      initializeList();
      return 0;
    }

    protected void onProgressUpdate(Void... progress) {
      // Nothing to do.
    }

    protected void onPostExecute(Integer result) {
      // Nothing to do.
    }
  }

  private void initializeList() {
    Log.d(TAG, "initializeList()");

    if (list_is_initializing == false)
      new InitializeListTask().execute();
  }

  private void updateActionMode() {
    if (mActionMode !=  null) {
      if(select_mode_active == false)
        mActionMode.finish();
      else {
        mActionMode.setSubtitle(select_count + " " + getString(R.string.zones_selected));
        mActionMode.getMenu().getItem(0).setVisible((select_count == 1));
      }
    }
  }

  private void selectZone(View view) {
    Log.d(TAG, "selectZone()");

    select_count++;
    databaseHelper.getZoneDatabase().setZoneSelected(((Integer) view.getTag(R.integer.zone_list_row_id_tag)).intValue(), true);
    view.setTag(R.integer.zone_list_row_select_tag, Boolean.TRUE);
    view.setBackgroundResource(R.color.abs__holo_blue_light);
    updateActionMode();
  }

  private void unselectZone(View view) {
    Log.d(TAG, "unselectZone()");

    databaseHelper.getZoneDatabase().setZoneSelected(((Integer) view.getTag(R.integer.zone_list_row_id_tag)).intValue(), false);
    view.setTag(R.integer.zone_list_row_select_tag, Boolean.FALSE);
    view.setBackgroundResource(0);
    if(--select_count < 1)
      select_mode_active = false;

    updateActionMode();
  }

  private void showZoneLabelDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = this.getLayoutInflater();
    final View view = inflater.inflate(R.layout.new_zone_label_layout, null);

    builder.setView(view).setTitle(R.string.title_zone_label_dialog);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog, int id) {
        EditText zoneLabelEdit = (EditText) view.findViewById(R.id.zone_list_row_label);
        Cursor selectedZones = databaseHelper.getZoneDatabase().getZonesSelected();
        ZoneDatabase.Reader zoneReader = new ZoneDatabase.Reader(databaseHelper, selectedZones);
        handleEditZoneLabel(zoneReader.getNext(), zoneLabelEdit.getText().toString());
        zoneReader.close();
      }

    });

    zoneLabelDialog = builder.show();
  }

  private void handleEditZoneLabel(ZoneRecord selectedZone, String newLabel) {
    if (selectedZone != null) {
      ZoneRecord updatedZone = new ZoneRecord(selectedZone.getId(), newLabel, selectedZone.getPoints());
      databaseHelper.getZoneDatabase().updateZone(updatedZone);
      mActionMode.finish();
      initializeList();
    }
  }

}
