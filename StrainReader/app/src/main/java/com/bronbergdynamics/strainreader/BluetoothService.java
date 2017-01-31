package com.bronbergdynamics.strainreader;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    // bluetooth components
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private Thread bluetoothThread;
    BluetoothRunnable listener;
    private OutputStream mmOutputStream;
    private InputStream mmInputStream;
    private UUID uuid;
    private String DeviceName = "Bluetooth2";

    // not sure if this is needed but used to identify instance of the service
    private int service_id;

    // an output steam to write to file
    private FileOutputStream fileStream;

    // current values for channels 1 & 2 read from bluetooth device
    private short ch1;
    private short ch2;

    // logicals to determine whether to listen for data or whether to recordMenuItem data
    private boolean runBlue = false;
    private boolean recording = false;

    // the activity from which the service is started
    private Activity connectActivity;


    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(this, "Bluetooth Service Started", Toast.LENGTH_SHORT).show();
        // return the binder for clients containing current service instance
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (runBlue) {
            try {
                mmOutputStream.close();
                mmInputStream.close();
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            listener.stop();
        }
        Toast.makeText(this, "Bluetooth Service Stopped", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    /**
     * Constructor which sets the activity to the activity as well as the file stream
     * @param bindingActivity
     */
    public void init(Activity bindingActivity) {
        connectActivity = bindingActivity;
        uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
    }

    /**
     * Find the device and return true/false based on whether the device is found
     * @return
     */
    public List<BluetoothDevice> findDevice() {
        List<BluetoothDevice> deviceList = new ArrayList<>();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Toast.makeText(this, "No Bluetooth Adapter Available", Toast.LENGTH_SHORT).show();
            return null;
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            connectActivity.startActivityForResult(enableBluetooth, 0);
            return null;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                deviceList.add(device);
            }
            return deviceList;
        }
        Toast.makeText(this, "Bluetooth Device Not Found", Toast.LENGTH_SHORT).show();
        return null;
    }

    /**
     * Set the services's device
     * @param device
     */
    public void setDevice(BluetoothDevice device) {
        mmDevice = device;
        Toast.makeText(this, "Device Set (" + mmDevice.getName() + ")", Toast.LENGTH_SHORT).show();
    }

    /**
     * Connect to bluetooth device
     */
    public boolean connectDevice() {
        try {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
            Toast.makeText(this, "Bluetooth Device Connected", Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            // Unable to connect; close the socket and return.
            Toast.makeText(this, "Could Not Connect!", Toast.LENGTH_SHORT).show();
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Toast.makeText(this, "Could Not Close Socket!", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    /**
     * Disconnect the bluetooth device
     */
    public void disconnectDevice() {
        try {
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start listening for incoming messages
     */
    public void startListening() {
        listener = new BluetoothRunnable(service_id);
        bluetoothThread = new Thread(listener);
        bluetoothThread.start();
    }

    /**
     * Stop listening for incoming messages
     */
    public void stopListening() {
        disconnectDevice();
        listener.stop();
    }

    /**
     * Send a message to the bluetooth device
     * @param msg
     */
    public void sendData(byte[] msg) {
        try {
            mmOutputStream.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return the current values for each strain channel
     * @return
     */
    public short[] getStrain() {
        short strainArray[] = {ch1, ch2};
        return strainArray;
    }
    /**
     * Method to set the name of the device the app should look for
     * @param DeviceName
     */
    public void setDeviceName(String DeviceName) {
        this.DeviceName = DeviceName;
    }

    /**
     * Set the file output stream for writing to file
     * @param fileStream
     */
    public void setFileStream(FileOutputStream fileStream) {
        this.fileStream = fileStream;
    }

    /**
     * Method to turn recording on or off
     * @param onOff
     */
    public void setRecording(boolean onOff) {
        recording = onOff;
        if (recording) Toast.makeText(this, "Started Recording", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "Stopped Recording", Toast.LENGTH_SHORT).show();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        BluetoothService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BluetoothService.this;
        }
    }

    /**
     * Class used to listen for bluetooth messages in a separate thread
     */
    final class BluetoothRunnable implements Runnable {
        final byte[] delimiter = {13, 10}; //This is the ASCII code for a newline character
        private int readBufferPosition = 0;
        private byte readBuffer[] = new byte[1024];

        private int service_id;
        BluetoothRunnable(int service_id)
        {
            this.service_id = service_id;
        }

        @Override
        public void run() {
            byte[] encodedBytes;
            byte b = 0; // used for reading in each byte in receive buffer
            byte b_prev = 0; // used for storing the previous byte read

            runBlue = true;

            while(!Thread.currentThread().isInterrupted() && runBlue)
            {
                try
                {
                    int bytesAvailable = mmInputStream.available();
                    if(bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        mmInputStream.read(packetBytes);
                        for (int i = 0; i < bytesAvailable; i++) {
                            b = packetBytes[i];
                            if(recording && fileStream != null) fileStream.write(b);
                            readBuffer[readBufferPosition++] = b;
                            if (b == delimiter[1] && b_prev == delimiter[0]) {
                                encodedBytes = new byte[10];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                ch1 = (short) ((encodedBytes[encodedBytes.length - 8] & 0xff) | (encodedBytes[encodedBytes.length - 7] << 8));
                                ch2 = (short) ((encodedBytes[encodedBytes.length - 4] & 0xff) | (encodedBytes[encodedBytes.length - 3] << 8));
                                readBufferPosition = 0;
                            }
                            b_prev = b;
                        }
                    }
                }
                catch (IOException ex)
                {
                    runBlue = false;
                }
            }
        }

        public void stop() {
            runBlue = false;
        }
    }
}
