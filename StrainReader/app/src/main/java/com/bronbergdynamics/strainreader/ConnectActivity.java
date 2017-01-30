package com.bronbergdynamics.strainreader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
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

public class ConnectActivity extends AppCompatActivity
{
    //threading
    Thread bluetoothThread;
    Thread showDataThread;
    short ch1, ch2; // the strain values on each channel
    // buttons and labels etc
    TextView infoLabel;
    Button findButton;
    Button connectButton;
    Button showButton;
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
    // plotting
    XYPlot plot;
    PlottingThread plottingThread;
    //bluetooth
    BluetoothService mBluetoothService;
    boolean mBound = false;
    boolean showData = false;

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

    @Override
    protected void onStart() {
        super.onStart();
        // bind to bluetooth service
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        // close the file stream
        if(fileStream != null) {
            try {
                fileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Just all the stuff to do when the activity is created
     */
    private void init(){
        // UI stuff
        infoLabel = (TextView)findViewById(R.id.label);
        findButton = (Button)findViewById(R.id.find);
        connectButton = (Button)findViewById(R.id.connectButton);
        connectButton.setEnabled(false);
        showButton = (Button)findViewById(R.id.showButton);
        showButton.setEnabled(false);
        closeButton = (Button)findViewById(R.id.closeButton);
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
    }

    /**
     * Send off a message based on which button is pressed
     * @param view
     */
    public void sendData(View view) {
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
        infoLabel.setText("Data Sent");
    }

    public void findDevice(View view) {
        boolean isConnected = mBluetoothService.findDevice();
        if(isConnected) infoLabel.setText("Device found");
        findButton.setEnabled(false);
        connectButton.setEnabled(true);
    }

    public void connectDevice(View view) {
        mBluetoothService.connectDevice();
        infoLabel.setText("Device connected");
        connectButton.setEnabled(false);
        showButton.setEnabled(true);
        closeButton.setEnabled(true);
    }

    public void listenForData(View view) {
        mBluetoothService.startListening();
        showButton.setEnabled(false);
        showDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                showData = true;
                while(showData) {
                    short strainArray[] = mBluetoothService.getStrain();
                    ch1 = strainArray[0];
                    ch2 = strainArray[1];
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            infoLabel.setText(ch1 + " , " + ch2);
                        }
                    });
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        showDataThread.start();
    }

    public void closeConnection(View view) {
        // if we are listening for data stop listening and disconnect, otherwise just disconnect
        if(showData) {
            showData = false;
            mBluetoothService.stopListening();
        }
        else mBluetoothService.disconnectDevice();
        //plottingThread.stop();
        infoLabel.setText("Bluetooth Closed");
        closeButton.setEnabled(false);
        showButton.setEnabled(false);
        connectButton.setEnabled(true);
    }

    public void stopRecording(){
        //try{ fileStream.close(); }
        //catch (IOException e) {}
    }

    public void startRecording(){
        //recording = true;
        //try{
        //    fileStream = new FileOutputStream(file);
        //} catch (IOException e) {
        //}
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mBluetoothService = binder.getService();
            mBound = true;
            // initialise bluetooth service
            mBluetoothService.init(ConnectActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}