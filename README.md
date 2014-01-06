Zones
==================
  
Zones is a "find my phone" type Android app with a geofencing twist. All devices
running Android 2.2 and higher with Google Play Services installed are supported.
  
Find My Phone
-------------
While most find my phone type apps require the user to access a web application,
Zones is controlled entirely by commands within SMS (text) messages. SMS commands
are authenticated by a user-defined password following the command. The commands below 
are currently supported:  
  
**Start location updates** - Subscribe the sender of this message to GPS location updates.  
  
**Stop location updates** - Unsubscribe the sender of this message from GPS location
updates.  
  
**Audio alarm** - Sound a loud alarm for the length of time defined in settings.  
  
**Battery level** - Reply with an SMS describing the device's battery level.  
  
**Super lock** - Lock the device screen and prevent it from turning on, effectively
disabling shutdown, airplane mode and silent mode.  
  
**Super unlock** - Allow the device screen to turn on again.  
  
**Clear call history** - Delete the history of all outgoing, incoming and missed calls.  
  
**Clear contacts** - Delete all contacts. Note that contacts may be re-synced if contact
syncing is enabled.  

**Factory reset** - Perform a full factory reset. All user data and applications will be
lost!  
  
Geofencing
----------
The geofencing aspect of Zones starts with allowing the user to draw zones on a map.
The user can then assign actions to these zones and decide whether they should be run
upon entry or exit of the zone. The actions below are currently supported:  
  
**Audio alarm** - Sound a loud alarm for the length of time defined in settings.  
  
**SMS alert** - Send an alert to the phone number defined in settings.  
  
**Email alert** - Send an alert to the email address defined in settings.  
  
**Super lock** - Lock the device screen and prevent it from turning on, effectively
disabling shutdown, airplane mode and silent mode.  
  
**Super unlock** - Allow the device screen to turn on again.  
  
**Clear call history** - Delete the history of all outgoing, incoming and missed calls.  
  
**Clear contacts** - Delete all contacts. Note that contacts may be re-synced if contact
syncing is enabled.  

**Factory reset** - Perform a full factory reset. All user data and applications will be
lost!  
  
DOs and DON'Ts
--------------
DO set a screenlock password on your device.  
DO set up full disk encryption on your device.  
DO draw zones around police stations and factory reset on entry.  
**DO NOT** use the factory reset SMS command or action unless you're serious.  

Building
--------
1) Clone the repo into a working directory.  
  
2) Import as an Android project in your IDE of choice.  
  
3) Replace the Google Maps API key inside AndroidManifest.xml by following [this tutorial](https://developers.google.com/maps/documentation/android/start#the_google_maps_api_key)

4) Build & run!

License
-------

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
