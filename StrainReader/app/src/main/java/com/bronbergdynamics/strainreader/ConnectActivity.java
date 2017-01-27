package com.bronbergdynamics.strainreader;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import com.androidplot.xy.XYPlot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class ConnectActivity extends AppCompatActivity
{
    // bluetooth
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    UUID uuid;
    //threading
    Thread bluetoothThread;
    Thread showDataThread;
    volatile boolean stopBlue;
    volatile boolean showData;
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
    Button openButton;
    Button closeButton;
    RadioButton fastestButton;
    RadioButton fastButton;
    RadioButton slowButton;
    RadioButton slowestButton;
    Switch recordSwitch;
    // streaming to file
    File dir;
    File file;
    FileOutputStream fileStream;
    boolean recording = false;
    // plotting
    XYPlot plot;
    PlottingThread plottingThread;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void init(){
        // UI stuff
        infoLabel = (TextView)findViewById(R.id.label);
        openButton = (Button)findViewById(R.id.open);
        closeButton = (Button)findViewById(R.id.close);
        closeButton.setEnabled(false);
        fastestButton = (RadioButton)findViewById(R.id.fastestButton);
        fastButton = (RadioButton)findViewById(R.id.fastButton);
        slowButton = (RadioButton)findViewById(R.id.slowButton);
        slowestButton = (RadioButton)findViewById(R.id.slowestButton);
        recordSwitch = (Switch)findViewById(R.id.recordSwitch);
        plot = (XYPlot)findViewById(R.id.dynamicXYPlot) ;

        recordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }
        });

        plottingThread = new PlottingThread(plot);

        dir = new File(Environment.getExternalStorageDirectory() + "/StrainData");
        if(!dir.exists()) dir.mkdirs();
        file = new File(dir, "log.bin");

        uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
    }

    public void onOpenClick(View view){
        boolean btFound = findBT();
        if(btFound) {
            try {
                openBT();
            }
            catch (IOException e){}
        }
        openButton.setEnabled(false);
        closeButton.setEnabled(true);
    }

    public void onCloseClick(View v)
    {
        try
        {
            closeBT();
        }
        catch (IOException ex){}
        closeButton.setEnabled(false);
        openButton.setEnabled(true);
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
                    try {
                        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                    }
                    catch (IOException e)
                    {
                        // Unable to connect; close the socket and return.
                        try {
                            mmSocket.close();
                        } catch (IOException closeException) {
                            infoLabel.setText("Bluetooth Device Not Found. Could Not Close Socket!");
                        }
                        infoLabel.setText("Bluetooth Device Not Found");
                        return false;
                    }
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
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();
        plottingThread.start();

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
                                if(recording) fileStream.write(b);
                                readBuffer[readBufferPosition++] = b;
                                if (b == delimiter[1] && b_prev == delimiter[0]) {
                                    encodedBytes = new byte[10];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    ch1 = (short) ((encodedBytes[encodedBytes.length - 8] & 0xff) | (encodedBytes[encodedBytes.length - 7] << 8));
                                    ch2 = (short) ((encodedBytes[encodedBytes.length - 4] & 0xff) | (encodedBytes[encodedBytes.length - 3] << 8));
                                    plottingThread.setData(ch1, ch2);
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
        if(fileStream != null) fileStream.close();
        plottingThread.stop();
        infoLabel.setText("Bluetooth Closed");
    }

    public void stopRecording(){
        recording = false;
        try{ fileStream.close(); }
        catch (IOException e) {}
    }

    public void startRecording(){
        recording = true;
        try{
            fileStream = new FileOutputStream(file);
        } catch (IOException e) {
        }
    }
}