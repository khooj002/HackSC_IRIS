/**************************************************************************************************
  Filename:       DeviceActivity.java
  Revised:        $Date: 2013-09-05 07:58:48 +0200 (to, 05 sep 2013) $
  Revision:       $Revision: 27616 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (�TI Devices�). 
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

    * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
    * any redistribution and use are licensed by TI for use only with TI Devices.
    * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

    * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
    * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TI�S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI�S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.sensortag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
// import android.util.Log;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.example.ti.ble.common.BluetoothLeService;
import com.example.ti.ble.common.GattInfo;
import com.example.ti.ble.common.HelpView;
import com.example.ti.ble.sensortag.R;
import com.example.ti.util.Point3D;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import android.location.Location;
import android.provider.Settings;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import android.provider.Settings.Secure;

public class DeviceActivity extends ViewPagerActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener{
	// Log
	// private static String TAG = "DeviceActivity";

	// Activity
    LocationClient mLocationClient;
    Location mCurrentLocation;
    LocationRequest mLocationRequest;

	public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;

	private DeviceView mDeviceView = null;

	// BLE
	private BluetoothLeService mBtLeService = null;
	private BluetoothDevice mBluetoothDevice = null;
	private BluetoothGatt mBtGatt = null;
	private List<BluetoothGattService> mServiceList = null;
	private static final int GATT_TIMEOUT = 250; // milliseconds
	private boolean mServicesRdy = false;
	private boolean mIsReceiving = false;

	// SensorTagGatt
	private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
	private BluetoothGattService mOadService = null;
	private BluetoothGattService mConnControlService = null;
	private boolean mMagCalibrateRequest = true;
	private boolean mHeightCalibrateRequest = true;
	private boolean mIsSensorTag2;
	private String mFwRev;

    double currTemp, currLati, currLongi, humid;

    private String androidId;
    public DeviceActivity() {
		mResourceFragmentPager = R.layout.fragment_pager;
		mResourceIdPager = R.id.pager;
		mFwRev = new String("1.5"); // Assuming all SensorTags are up to date until actual FW revision is read
	}

	public static DeviceActivity getInstance() {
		return (DeviceActivity) mThis;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();

        // 3. create LocationClient
        mLocationClient = new LocationClient(this, this, this);
        // 4. create & set LocationRequest for Location update
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(1000 * 5);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(1000 * 1);

		// BLE
		mBtLeService = BluetoothLeService.getInstance();
		mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
		mServiceList = new ArrayList<BluetoothGattService>();

		// Determine type of SensorTagGatt
		String deviceName = mBluetoothDevice.getName();
		mIsSensorTag2 = deviceName.equals("SensorTag2");
		if (mIsSensorTag2)
			PreferenceManager.setDefaultValues(this, R.xml.preferences2, false);
		else
			PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		// Log.i(TAG, "Preferences for: " + deviceName);

		// GUI
		mDeviceView = new DeviceView();
		mSectionsPagerAdapter.addSection(mDeviceView, "Sensors");
		HelpView hw = new HelpView();
		hw.setParameters("help_device.html", R.layout.fragment_help, R.id.webpage);
		mSectionsPagerAdapter.addSection(hw, "Help");

		// GATT database
		Resources res = getResources();
		XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
		new GattInfo(xpp);

		// Initialize sensor list
		updateSensorList();
        TestAlert("OnCreate");

	}

    @Override
    protected void onStart() {
        super.onStart();
        // 1. connect the client.
        mLocationClient.connect();
    }
    @Override
    protected void onStop() {
        super.onStop();
        // 1. disconnecting the client invalidates it.
        mLocationClient.disconnect();
    }


    // GooglePlayServicesClient.OnConnectionFailedListener
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }

    // GooglePlayServicesClient.ConnectionCallbacks
    @Override
    public void onConnected(Bundle arg0) {

        if(mLocationClient != null)
            mLocationClient.requestLocationUpdates(mLocationRequest,  this);

        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();

        if(mLocationClient != null){
            // get location
            mCurrentLocation = mLocationClient.getLastLocation();
            try{

                // set TextView(s)

            }catch(NullPointerException npe){

                Toast.makeText(this, "Failed to Connect", Toast.LENGTH_SHORT).show();

                // switch on location service intent
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        }

    }
    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected.", Toast.LENGTH_SHORT).show();

    }

    // LocationListener
    @Override
    public void onLocationChanged(Location location) {
        Toast.makeText(this, "Location changed.", Toast.LENGTH_SHORT).show();
        mCurrentLocation = mLocationClient.getLastLocation();
        currLati = mCurrentLocation.getLatitude();
        currLongi = mCurrentLocation.getLongitude();
        mDeviceView.updatelocation(currLati, currLongi);
    }
	@Override
	public void onDestroy() {
		super.onDestroy();
		finishActivity(PREF_ACT_REQ);
		finishActivity(FWUPDATE_ACT_REQ);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.optionsMenu = menu;
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.device_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.opt_prefs:
			startPreferenceActivity();
			break;
		case R.id.opt_fwupdate:
			startOadActivity();
			break;
		case R.id.opt_about:
			openAboutDialog();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	protected void onResume() {
		// Log.d(TAG, "onResume");
		super.onResume();
		if (!mIsReceiving) {
			registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			mIsReceiving = true;
		}
	}

	@Override
	protected void onPause() {
		// Log.d(TAG, "onPause");
		super.onPause();
		if (mIsReceiving) {
			unregisterReceiver(mGattUpdateReceiver);
			mIsReceiving = false;
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter fi = new IntentFilter();
		fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
		fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
		fi.addAction(BluetoothLeService.ACTION_DATA_READ);
		return fi;
	}

	void onViewInflated(View view) {
		// Log.d(TAG, "Gatt view ready");
		setBusy(true);

		// Set title bar to device name
		setTitle(mBluetoothDevice.getName());

		// Create GATT object
		mBtGatt = BluetoothLeService.getBtGatt();

		// Start service discovery
		if (!mServicesRdy && mBtGatt != null) {
			if (mBtLeService.getNumServices() == 0)
				discoverServices();
			else {
				displayServices();
				enableDataCollection(true);
			}
		}
	}

	//
	// Application implementation
	//
	private void updateSensorList() {
		mEnabledSensors.clear();

		for (int i = 0; i < Sensor.SENSOR_LIST.length; i++) {
			Sensor sensor = Sensor.SENSOR_LIST[i];
			if (isEnabledByPrefs(sensor)) {
				mEnabledSensors.add(sensor);
			}
		}
	}

	boolean isSensorTag2() {
		return mIsSensorTag2;
	}

	String firmwareRevision() {
		return mFwRev;
	}

	boolean isEnabledByPrefs(final Sensor sensor) {
		String preferenceKeyString = "pref_"
		    + sensor.name().toLowerCase(Locale.ENGLISH) + "_on";

		SharedPreferences prefs = PreferenceManager
		    .getDefaultSharedPreferences(this);

		Boolean defaultValue = true;
		return prefs.getBoolean(preferenceKeyString, defaultValue);
	}

	BluetoothGattService getOadService() {
		return mOadService;
	}

	BluetoothGattService getConnControlService() {
		return mConnControlService;
	}

	private void startOadActivity() {
    // For the moment OAD does not work on Galaxy S3 (disconnects on parameter update)
    if (Build.MODEL.contains("I9300")) {
			Toast.makeText(this, "OAD not available on this Android device",
			    Toast.LENGTH_LONG).show();
			return;
    }
    	
		if (mOadService != null && mConnControlService != null) {
			// Disable sensors and notifications when the OAD dialog is open
			enableDataCollection(false);
			// Launch OAD
			final Intent i = new Intent(this, FwUpdateActivity.class);
			startActivityForResult(i, FWUPDATE_ACT_REQ);
		} else {
			Toast.makeText(this, "OAD not available on this BLE device",
			    Toast.LENGTH_LONG).show();
		}
	}

	private void startPreferenceActivity() {
		// Disable sensors and notifications when the settings dialog is open
		enableDataCollection(false);
		// Launch preferences
		final Intent i = new Intent(this, PreferencesActivity.class);
		i.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT,
		    PreferencesFragment.class.getName());
		i.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
		i.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		startActivityForResult(i, PREF_ACT_REQ);
	}

	private void checkOad() {
		// Check if OAD is supported (needs OAD and Connection Control service)
		mOadService = null;
		mConnControlService = null;

		for (int i = 0; i < mServiceList.size()
		    && (mOadService == null || mConnControlService == null); i++) {
			BluetoothGattService srv = mServiceList.get(i);
			if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
				mOadService = srv;
			}
			if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
				mConnControlService = srv;
			}
		}
	}

	private void discoverServices() {
		if (mBtGatt.discoverServices()) {
			mServiceList.clear();
			setBusy(true);
			setStatus("Service discovery started");
		} else {
			setError("Service discovery start failed");
		}
	}

	private void setBusy(boolean b) {
		mDeviceView.setBusy(b);
	}

	private void displayServices() {
		mServicesRdy = true;

		try {
			mServiceList = mBtLeService.getSupportedGattServices();
		} catch (Exception e) {
			e.printStackTrace();
			mServicesRdy = false;
		}

		// Characteristics descriptor readout done
		if (!mServicesRdy) {
			setError("Failed to read services");
		}
	}

	private void setError(String txt) {
		setBusy(false);
		Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
	}

	private void setStatus(String txt) {
		Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
	}

	private void enableSensors(boolean f) {
		final boolean enable = f;

		for (Sensor sensor : mEnabledSensors) {
			UUID servUuid = sensor.getService();
			UUID confUuid = sensor.getConfig();

			// Skip keys
			if (confUuid == null)
				break;

			if (!mIsSensorTag2) {
				// Barometer calibration
				if (confUuid.equals(SensorTagGatt.UUID_BAR_CONF) && enable) {
					calibrateBarometer();
				}
			}

			BluetoothGattService serv = mBtGatt.getService(servUuid);
			if (serv != null) {
				BluetoothGattCharacteristic charac = serv.getCharacteristic(confUuid);
				byte value = enable ? sensor.getEnableSensorCode()
				    : Sensor.DISABLE_SENSOR_CODE;
				if (mBtLeService.writeCharacteristic(charac, value)) {
					mBtLeService.waitIdle(GATT_TIMEOUT);
				} else {
					setError("Sensor config failed: " + serv.getUuid().toString());
					break;
				}
			}
		}
	}

	private void enableNotifications(boolean f) {
		final boolean enable = f;

		for (Sensor sensor : mEnabledSensors) {
			UUID servUuid = sensor.getService();
			UUID dataUuid = sensor.getData();
			BluetoothGattService serv = mBtGatt.getService(servUuid);
			if (serv != null) {
				BluetoothGattCharacteristic charac = serv.getCharacteristic(dataUuid);

				if (mBtLeService.setCharacteristicNotification(charac, enable)) {
					mBtLeService.waitIdle(GATT_TIMEOUT);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					setError("Sensor notification failed: " + serv.getUuid().toString());
					break;
				}
			}
		}
	}

	private void enableDataCollection(boolean enable) {
		setBusy(true);
		enableSensors(enable);
		enableNotifications(enable);
		setBusy(false);
	}

	/*
	 * Calibrating the barometer includes
	 * 
	 * 1. Write calibration code to configuration characteristic. 2. Read
	 * calibration values from sensor, either with notifications or a normal read.
	 * 3. Use calibration values in formulas when interpreting sensor values.
	 */
	private void calibrateBarometer() {
		if (mIsSensorTag2)
			return;

		UUID servUuid = Sensor.BAROMETER.getService();
		UUID configUuid = Sensor.BAROMETER.getConfig();
		BluetoothGattService serv = mBtGatt.getService(servUuid);
		BluetoothGattCharacteristic config = serv.getCharacteristic(configUuid);

		// Write the calibration code to the configuration registers
		mBtLeService.writeCharacteristic(config, Sensor.CALIBRATE_SENSOR_CODE);
		mBtLeService.waitIdle(GATT_TIMEOUT);
		BluetoothGattCharacteristic calibrationCharacteristic = serv
		    .getCharacteristic(SensorTagGatt.UUID_BAR_CALI);
		mBtLeService.readCharacteristic(calibrationCharacteristic);
		mBtLeService.waitIdle(GATT_TIMEOUT);
	}

	private void getFirmwareRevison() {
		UUID servUuid = SensorTagGatt.UUID_DEVINFO_SERV;
		UUID charUuid = SensorTagGatt.UUID_DEVINFO_FWREV;
		BluetoothGattService serv = mBtGatt.getService(servUuid);
		BluetoothGattCharacteristic charFwrev = serv.getCharacteristic(charUuid);

		// Write the calibration code to the configuration registers
		mBtLeService.readCharacteristic(charFwrev);
		mBtLeService.waitIdle(GATT_TIMEOUT);

	}

	void calibrateMagnetometer() {
		MagnetometerCalibrationCoefficients.INSTANCE.val.x = 0.0;
		MagnetometerCalibrationCoefficients.INSTANCE.val.y = 0.0;
		MagnetometerCalibrationCoefficients.INSTANCE.val.z = 0.0;

		mMagCalibrateRequest = true;
	}

	void calibrateHeight() {
		mHeightCalibrateRequest = true;
	}

	// Activity result handling
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case PREF_ACT_REQ:
			mDeviceView.updateVisibility();
			Toast.makeText(this, "Applying preferences", Toast.LENGTH_SHORT).show();
			if (!mIsReceiving) {
				mIsReceiving = true;
				registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			}

			updateSensorList();
			enableDataCollection(true);
			break;
		case FWUPDATE_ACT_REQ:
			// FW update cancelled so resume
			enableDataCollection(true);
			break;
		default:
			setError("Unknown request code");
			break;
		}
	}

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
			    BluetoothGatt.GATT_SUCCESS);

			if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					setStatus("Service discovery complete");
					displayServices();
					checkOad();
					enableDataCollection(true);
					getFirmwareRevison();
				} else {
					Toast.makeText(getApplication(), "Service discovery failed",
					    Toast.LENGTH_LONG).show();
					return;
				}
			} else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
				// Notification
				byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				onCharacteristicChanged(uuidStr, value);
			} else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
				// Data written
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				onCharacteristicWrite(uuidStr, status);
			} else if (BluetoothLeService.ACTION_DATA_READ.equals(action)) {
				// Data read
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				onCharacteristicsRead(uuidStr, value, status);
			}

			if (status != BluetoothGatt.GATT_SUCCESS) {
				setError("GATT error code: " + status);
			}
		}
	};

	private void onCharacteristicWrite(String uuidStr, int status) {
		// Log.d(TAG, "onCharacteristicWrite: " + uuidStr);
	}

	private void onCharacteristicChanged(String uuidStr, byte[] value) {
		if (mDeviceView != null) {
			if (mMagCalibrateRequest) {
				if (uuidStr.equals(SensorTagGatt.UUID_MAG_DATA.toString())) {
					Point3D v = Sensor.MAGNETOMETER.convert(value);

					MagnetometerCalibrationCoefficients.INSTANCE.val = v;
					mMagCalibrateRequest = false;
					Toast.makeText(this, "Magnetometer calibrated", Toast.LENGTH_SHORT)
					    .show();
				}
			}

			if (mHeightCalibrateRequest) {
				if (uuidStr.equals(SensorTagGatt.UUID_BAR_DATA.toString())) {
					Point3D v = Sensor.BAROMETER.convert(value);

					BarometerCalibrationCoefficients.INSTANCE.heightCalibration = v.x;
					mHeightCalibrateRequest = false;
					Toast.makeText(this, "Height measurement calibrated",
					    Toast.LENGTH_SHORT).show();
				}
			}
            //mDeviceView.updatelocation(mCurrentLocation.getLongitude(), mCurrentLocation.getLatitude());
			mDeviceView.onCharacteristicChanged(uuidStr, value);
            if (uuidStr.equals(SensorTagGatt.UUID_IRT_DATA.toString())) {
                currTemp = Sensor.IR_TEMPERATURE.convert(value).y;
            }
            if (uuidStr.equals(SensorTagGatt.UUID_HUM_DATA.toString())) {
                humid = Sensor.HUMIDITY.convert(value).x;
            }
		}
	}

	private void onCharacteristicsRead(String uuidStr, byte[] value, int status) {
		// Log.i(TAG, "onCharacteristicsRead: " + uuidStr);

		if (uuidStr.equals(SensorTagGatt.UUID_DEVINFO_FWREV.toString())) {
			mFwRev = new String(value, 0, 3);
			Toast.makeText(this, "Firmware revision: " + mFwRev,Toast.LENGTH_LONG).show();
		}

		if (mIsSensorTag2)
			return;

		if (uuidStr.equals(SensorTagGatt.UUID_BAR_CALI.toString())) {
			// Sanity check
			if (value.length != 16)
				return;
			
			// Barometer calibration values are read.
			List<Integer> cal = new ArrayList<Integer>();
			for (int offset = 0; offset < 8; offset += 2) {
				Integer lowerByte = (int) value[offset] & 0xFF;
				Integer upperByte = (int) value[offset + 1] & 0xFF;
				cal.add((upperByte << 8) + lowerByte);
			}

			for (int offset = 8; offset < 16; offset += 2) {
				Integer lowerByte = (int) value[offset] & 0xFF;
				Integer upperByte = (int) value[offset + 1];
				cal.add((upperByte << 8) + lowerByte);
			}

			BarometerCalibrationCoefficients.INSTANCE.barometerCalibrationCoefficients = cal;
		}
	}

    private void TestAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceActivity.this);

        builder.setTitle("Test");
        builder.setPositiveButton("OK", null);
        builder.setMessage(message);

        AlertDialog theAlertDialog = builder.create();
        theAlertDialog.show();
    }


    private void sendTempAndLoc(String id, double temp, double lati, double longi, double humid){
        HttpClient httpClient = new DefaultHttpClient();
        String url = "https://hacksc-iris.azure-mobile.net/api/temperature?";

        List<BasicNameValuePair> params = new LinkedList<BasicNameValuePair>();

        temp = ((temp * 9 / 5.0) + 32); //changing the temp from celsius to fahrenheit

        params.add(new BasicNameValuePair("lat", String.valueOf(lati)));
        params.add(new BasicNameValuePair("long", String.valueOf(longi)));
        params.add(new BasicNameValuePair("device_id", id));
        params.add(new BasicNameValuePair("temp", String.valueOf(temp)));
        params.add(new BasicNameValuePair("humid", String.valueOf(humid)));

        String paramString = URLEncodedUtils.format(params, "utf-8");

        url += paramString;


        //HttpPost httpPost = new HttpPost("https://hacksc-iris.azure-mobile.net/api/temperature?device_id=mikeyoon&temp=97&lat=38&long=88");
        HttpPost httpPost = new HttpPost(url);
        //temp=11&long=22&lat=55
        //InputStream inputStream = null;
        //String result;
        new MyHttpPost().execute(httpPost);
//        try{
//            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
//            nameValuePairs.add(new BasicNameValuePair("device_id", id));
//            nameValuePairs.add(new BasicNameValuePair("temp", Double.toString(temp)));
//            nameValuePairs.add(new BasicNameValuePair("lat", Double.toString(lati)));
//            nameValuePairs.add(new BasicNameValuePair("long", Double.toString(longi)));
//
//            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
//
//            new MyHttpPost().execute(httpPost);
//
//        }catch(IOException e) {
//
//            TestAlert("Send fail");
//        }
    }



    private class MyHttpPost extends AsyncTask<HttpPost, Void, String> {

        @Override
        protected String doInBackground(HttpPost... postUrl) {
            return POST(postUrl[0]);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            //Toast.makeText( getBaseContext(), "Received!", Toast.LENGTH_LONG).show();
            /*try{
                JSONObject jsonLoginResult = new JSONObject(result);
                if( jsonLoginResult.getBoolean("success") ){
                    //will save our auth_token in our SharedPreferences
                    //SharedPreferences.Editor preferencesEditor = savedData.edit();
                    //preferencesEditor.putString("auth_token", jsonLoginResult.getJSONObject("user").getString("auth_token"));
                }
                else{
                    //invalidEntryAlert("Invalid username or password. Please try again.");
                }
            }
            catch(JSONException e){
                //do nothing
            }*/

            TestAlert(result);

        }

        public String POST(HttpPost postUrl){
            InputStream inputStream = null;
            String result = "";
            try {
                // create HttpClient
                HttpClient httpclient = new DefaultHttpClient();

                // make POST request to the given URL
                HttpResponse httpResponse = httpclient.execute(postUrl);

                // receive response as inputStream
                inputStream = httpResponse.getEntity().getContent();

                // convert inputstream to string
                if(inputStream != null)
                    result = convertInputStreamToString(inputStream);
                else
                    result = "Did not work!";

            } catch (Exception e) {
                Log.d("InputStream", e.getLocalizedMessage());
            }

            return result;
        }

        private String convertInputStreamToString(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
            String line = "";
            String result = "";
            while((line = bufferedReader.readLine()) != null)
                result += line;

            inputStream.close();
            return result;
        }
    }

    public void sendButtonOnClick(View view){
        androidId = Secure.getString( getContentResolver(), Secure.ANDROID_ID);
        sendTempAndLoc(androidId, currTemp, currLati, currLongi, humid);
    }

}
