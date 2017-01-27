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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
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
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private Thread bluetoothThread;
    BluetoothRunnable listener;
    private OutputStream mmOutputStream;
    private InputStream mmInputStream;
    private UUID uuid;
    private String DeviceName = "Bluetooth2";
    private int service_id;
    private FileOutputStream fileStream;

    private Activity connectActivity;

    public BluetoothService(Activity bindingActivity, FileOutputStream fileStream) {
        connectActivity = bindingActivity;
        this.fileStream = fileStream;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // return the binder for clients containing current service instance
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Bluetooth Service Started", Toast.LENGTH_LONG);
        service_id = startId;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        listener.stop();
        super.onDestroy();
    }

    private boolean findDevice() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Toast.makeText(this, "No Bluetooth Adapter Available", Toast.LENGTH_LONG).show();
            return false;
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            connectActivity.startActivityForResult(enableBluetooth, 0);
            return false;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals(DeviceName))
                {
                    mmDevice = device;
                    try {
                        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                    }
                    catch (IOException e)
                    {
                        // Unable to connect; close the socket and return.
                        try {
                            mmSocket.close();
                        } catch (IOException closeException) {
                            Toast.makeText(this, "Could Not Close Socket!", Toast.LENGTH_LONG).show();
                        }
                        Toast.makeText(this, "Bluetooth Device Not Found", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    Toast.makeText(this, "Bluetooth Device Found", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        }
        Toast.makeText(this, "Bluetooth Device Not Found", Toast.LENGTH_LONG).show();
        return false;
    }

    public void connectDevice() {
        try {
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Toast.makeText(this, "Bluetooth Opened", Toast.LENGTH_LONG).show();
    }

    public void streamData() {
        listener = new BluetoothRunnable(service_id, fileStream);
        bluetoothThread = new Thread(listener);
        bluetoothThread.start();
    }

    final class BluetoothRunnable implements Runnable {
        final byte[] delimiter = {13, 10}; //This is the ASCII code for a newline character

        private boolean runBlue = false;
        private boolean recording = false;
        private int readBufferPosition = 0;
        private byte readBuffer[] = new byte[1024];
        private short ch1;
        private short ch2;
        private FileOutputStream fileStream;

        private int service_id;
        BluetoothRunnable(int service_id, FileOutputStream fileStream)
        {
            this.fileStream = fileStream;
            this.service_id = service_id;
        }

        @Override
        public void run() {
            byte[] encodedBytes;
            byte b = 0; // used for reading in each byte in receive buffer
            byte b_prev = 0; // used for storing the previous byte read

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
