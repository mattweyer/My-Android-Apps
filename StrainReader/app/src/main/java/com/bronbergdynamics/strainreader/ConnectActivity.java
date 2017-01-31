package com.bronbergdynamics.strainreader;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConnectActivity extends AppCompatActivity {
    //threading
    Thread showDataThread;
    short ch1, ch2; // the strain values on each channel
    // buttons and labels etc
    TextView infoLabel;
    TextView dataText;
    Button findButton;
    Button connectButton;
    Button showButton;
    Button closeButton;
    // menu items
    MenuItem plotMenuItem;
    MenuItem freqMenuItem;
    // streaming to file
    File dir;
    File file;
    FileOutputStream fileStream;
    // plotting
    XYPlot plot;
    //bluetooth
    BluetoothService mBluetoothService;
    boolean mBound = false;
    boolean isListening = false;
    boolean isConnected = false;
    // alert dialog box stuff
    private int frequencyIndex = 2;
    private int deviceIndex = 0;
    private byte msg[];

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
        plotMenuItem = menu.findItem(R.id.showPlot);
        plotMenuItem.setEnabled(false);
        plotMenuItem.getIcon().setAlpha(75);
        freqMenuItem = menu.findItem(R.id.changeFrequency);
        freqMenuItem.setEnabled(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.showPlot:
                if (isListening) {
                    Intent intent = new Intent(this, PlotActivity.class);
                    startActivity(intent);
                }
                break;
            case R.id.changeFrequency:
                if (isConnected) showFrequencyDialog(frequencyIndex);
                break;
            case R.id.record:
                boolean isChecked = item.isChecked();
                if (isChecked) stopRecording();
                else startRecording();
                item.setChecked(!isChecked);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
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
            stopRecording();
        }
    }

    /**
     * Just all the stuff to do when the activity is created
     */
    private void init(){
        // UI stuff
        infoLabel = (TextView)findViewById(R.id.label);
        dataText = (TextView)findViewById(R.id.dataText);
        findButton = (Button)findViewById(R.id.find);
        connectButton = (Button)findViewById(R.id.connectButton);
        connectButton.setEnabled(false);
        showButton = (Button)findViewById(R.id.showButton);
        showButton.setEnabled(false);
        closeButton = (Button)findViewById(R.id.closeButton);
        closeButton.setEnabled(false);
        plot = (XYPlot)findViewById(R.id.dynamicXYPlot) ;

        // get the file directory, and make it if it doesn't exist
        dir = new File(Environment.getExternalStorageDirectory() + "/StrainData");
        if(!dir.exists()) dir.mkdirs();
        file = new File(dir, "log.bin");
    }

    /**
     * Select the correct paired device on button press
     * @param view
     */
    public void findDevice(View view) {
        List<BluetoothDevice> deviceList = mBluetoothService.findDevice();
        if(deviceList != null) {
            String labels[] = new String[deviceList.size()];
            int cnt = 0;
            for (BluetoothDevice device : deviceList) {
                labels[cnt++] = device.getName();
            }
            showDeviceDialog(deviceList, labels);
            infoLabel.setText("Device found");
            connectButton.setEnabled(true);
        }
    }

    /**
     * Connect to the selected device
     * @param view
     */
    public void connectDevice(View view) {
        isConnected = mBluetoothService.connectDevice();
        if (isConnected) {
            infoLabel.setText("Device connected");
            connectButton.setEnabled(false);
            findButton.setEnabled(false);
            showButton.setEnabled(true);
            closeButton.setEnabled(true);
            freqMenuItem.setEnabled(true);
        }
    }

    /**
     * Run a thread which constantly listens for incoming messages
     * @param view
     */
    public void listenForData(View view) {
        mBluetoothService.startListening();
        showButton.setEnabled(false);
        isListening = true;
        showDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(isListening) {
                    short strainArray[] = mBluetoothService.getStrain();
                    ch1 = strainArray[0];
                    ch2 = strainArray[1];
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dataText.setText("Channel 1: " + ch1 + "\nChannel 2: " + ch2);
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
        plotMenuItem.setEnabled(true);
        plotMenuItem.getIcon().setAlpha(255);
    }

    /**
     * Close the bluetooth connection
     * @param view
     */
    public void closeConnection(View view) {
        // if we are listening for data stop listening and disconnect, otherwise just disconnect
        if(isListening) {
            mBluetoothService.stopListening();
            isListening = false;
        }
        else {
            mBluetoothService.disconnectDevice();
            isConnected = false;
        }
        //plottingThread.stop();
        infoLabel.setText("Bluetooth Closed");
        closeButton.setEnabled(false);
        showButton.setEnabled(false);
        connectButton.setEnabled(true);
        findButton.setEnabled(true);
        plotMenuItem.setEnabled(false);
        plotMenuItem.getIcon().setAlpha(75);
        freqMenuItem.setEnabled(false);
    }

    /**
     * Start logging to file
     */
    public void startRecording(){
        try{
            fileStream = new FileOutputStream(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mBluetoothService.setFileStream(fileStream);
        mBluetoothService.setRecording(true);
    }

    /**
     * Stop logging to file
     */
    public void stopRecording(){
        mBluetoothService.setRecording(false);
        try{
            fileStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Required for the alert dialog to choose which option was selected
     * @param index
     */
    private void setIndex(int index, String what) {
        switch (what) {
            case "device":
                deviceIndex = index;
                break;
            case "frequency":
                frequencyIndex = index;
                break;
            default:
                break;
        }
    }

    /**
     * Required for the alert dialog to set the msg to send to the boards
     * @param msg
     */
    private void setMsg(byte[] msg) {
        this.msg = msg;
    }

    /**
     * A pop up list of devices that are paired to the phone. Select the device
     * to which you want to connect
     * @param deviceList
     * @param labels
     */
    private void showDeviceDialog(final List<BluetoothDevice> deviceList, String[] labels) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Paired Devices:");

        builder.setSingleChoiceItems(labels, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                setIndex(which, "device");
            }
        });

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mBluetoothService.setDevice(deviceList.get(deviceIndex));
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * A dialog to select the output frequency of the boards
     */
    private int showFrequencyDialog(int index) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Output Frequency:");

        String[] labels = new String[4];
        labels[0] = "2 kHz";
        labels[1] = "1 kHz";
        labels[2] = "500 Hz";
        labels[3] = "250 Hz";

        builder.setSingleChoiceItems(labels, index, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                byte[] msg = {0, 0};  // the message to be sent to the board
                msg[0] = 102;
                setIndex(which, "frequency");
                msg[1] = (byte)(which + 1);
                setMsg(msg);
            }
        });

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mBluetoothService.sendData(msg);
                switch (msg[1]) {
                    case 1:
                        Toast.makeText(ConnectActivity.this, "Frequency Set To 2 kHz", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        Toast.makeText(ConnectActivity.this, "Frequency Set To 1 kHz", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        Toast.makeText(ConnectActivity.this, "Frequency Set To 500 Hz", Toast.LENGTH_SHORT).show();
                        break;
                    case 4:
                        Toast.makeText(ConnectActivity.this, "Frequency Set To 250 Hz", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        break;
                }

            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        return index;
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