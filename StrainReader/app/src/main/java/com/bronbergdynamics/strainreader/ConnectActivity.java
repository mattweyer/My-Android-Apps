package com.bronbergdynamics.strainreader;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class ConnectActivity extends Activity
{
    // bluetooth
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    //threading
    Thread bluetoothThread;
    Thread showDataThread;
    volatile boolean stopBlue;
    volatile boolean showData;
    Handler timerHandler;
    Runnable timerRunnable;
    // data buffer
    byte[] readBuffer;
    int readBufferPosition;
    short ch1, ch2; // the strain values on each channel
    // plotting variables
    Number[] x = new Number[100];
    Number[] y1 = new Number[100];
    Number[] y2 = new Number[100];
    // buttons and labels etc
    TextView infoLabel;
    TextView dataText;
    RadioButton fastestButton;
    RadioButton fastButton;
    RadioButton slowButton;
    RadioButton slowestButton;
    // streaming to file
    File dir;
    File file;
    FileOutputStream fileStream;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        init();
    }

    private void init(){
        // UI stuff
        infoLabel = (TextView)findViewById(R.id.label);
        dataText = (TextView)findViewById(R.id.dataText);
        fastestButton = (RadioButton)findViewById(R.id.fastestButton);
        fastButton = (RadioButton)findViewById(R.id.fastButton);
        slowButton = (RadioButton)findViewById(R.id.slowButton);
        slowestButton = (RadioButton)findViewById(R.id.slowestButton);

        //threads
        timerHandler = new Handler();

        try {
            dir = new File(Environment.getExternalStorageDirectory() + "/StrainData");
            if(!dir.exists()) dir.mkdirs();
            file = new File(dir, "log.bin");
            fileStream = new FileOutputStream(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onOpenClick(View view){
        boolean btFound = findBT();
        if(btFound) {
            try {
                openBT();
            }
            catch (IOException e){}
        }
    }

    public void onCloseClick(View v)
    {
        try
        {
            closeBT();
        }
        catch (IOException ex){}
    }

    private boolean findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            infoLabel.setText("No bluetooth adapter available");
            return false;
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
            return false;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("Bluetooth2"))
                {
                    mmDevice = device;
                    infoLabel.setText("Bluetooth Device Found");
                    return true;
                }
            }
        }
        infoLabel.setText("Bluetooth Device Not Found");
        return false;
    }

    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();
        showData();

        infoLabel.setText("Bluetooth Opened");
    }

    void beginListenForData()
    {
        final byte[] delimiter = {13, 10}; //This is the ASCII code for a newline character

        stopBlue = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];

        bluetoothThread = new Thread(new Runnable()

        {
            public void run()
            {
                byte[] encodedBytes;
                byte b = 0; // used for reading in each byte in receive buffer
                byte b_prev = 0; // used for storing the previous byte read

                while(!Thread.currentThread().isInterrupted() && !stopBlue)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                b = packetBytes[i];
                                fileStream.write(b);
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
                        stopBlue = true;
                    }
                }
            }
        });

        bluetoothThread.start();
    }

    // this dumps data onto screen
    private void showData()
    {
        showData = true;
        //runs without a timer by reposting this handler at the end of the runnable
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                final String data = Integer.toString(ch1) + " , " + Integer.toString(ch2) + "\n";
                if (dataText.getLineCount() > 20) {
                    String textArr[] = dataText.getText().toString().split("\n", 2);
                    String text = textArr[1] + data;
                    dataText.setText(text);
                }
                else dataText.append(data);
                timerHandler.postDelayed(this, 50);
            }
        };

        timerHandler.post(timerRunnable);
    }

    // define the onPause() function which just makes sure we kill the timerHandler thread when we start a new activity
    @Override
    protected void onPause() {
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            super.onPause();
        }
    }

    // define the onResume() function which just makes sure we kill the timerHandler thread when we start a new activity
    @Override
    protected void onResume() {
        if (timerHandler != null) {
            timerHandler.post(timerRunnable);
            super.onResume();
        }
    }

    public void sendData(View view)
    {
        byte[] msg = {0, 0};
        switch(view.getId())
        {
            case R.id.fastestButton:
                fastButton.setChecked(false);
                slowButton.setChecked(false);
                slowestButton.setChecked(false);
                msg[0] = 102;
                msg[1] = 1;
                break;
            case R.id.fastButton:
                fastestButton.setChecked(false);
                slowButton.setChecked(false);
                slowestButton.setChecked(false);
                msg[0] = 102;
                msg[1] = 2;
                break;
            case R.id.slowButton:
                fastButton.setChecked(false);
                fastButton.setChecked(false);
                slowestButton.setChecked(false);
                msg[0] = 102;
                msg[1] = 3;
                break;
            case R.id.slowestButton:
                fastestButton.setChecked(false);
                fastButton.setChecked(false);
                slowButton.setChecked(false);
                msg[0] = 102;
                msg[1] = 4;
                break;
            default:
                break;
        }
        try {
            mmOutputStream.write(msg);
        }
        catch (IOException ex){}
        infoLabel.setText("Data Sent");
    }

    void closeBT() throws IOException
    {
        stopBlue = true;
        showData = false;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        fileStream.close();
        timerHandler.removeCallbacks(timerRunnable);
        infoLabel.setText("Bluetooth Closed");
    }
}