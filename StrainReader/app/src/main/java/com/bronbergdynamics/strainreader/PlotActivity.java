package com.bronbergdynamics.strainreader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.androidplot.Plot;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class PlotActivity extends AppCompatActivity {
    private Plotter mPlotter;
    private XYPlot plot;

    private BluetoothService mBluetoothService;
    boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plot);

        plot = (XYPlot)findViewById(R.id.dynamicXYPlot);
        mPlotter = new Plotter(plot);
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
        // stop plotting
        mPlotter.stop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
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
            // start plotting
            mPlotter.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };



    public class Plotter {

        // redraws a plot whenever an update is received:
        private class MyPlotUpdater implements Observer {
            Plot plot;

            public MyPlotUpdater(Plot plot) {
                this.plot = plot;
            }

            @Override
            public void update(Observable o, Object arg) {
                plot.redraw();
            }
        }
        private XYPlot dynamicPlot;
        private MyPlotUpdater plotUpdater;
        SampleDynamicXYDatasource data;
        private Thread myThread;

        public Plotter(XYPlot plot) {
            dynamicPlot = plot;

            plotUpdater = new MyPlotUpdater(dynamicPlot);

            // getInstance and position datasets:
            data = new SampleDynamicXYDatasource();
            SampleDynamicSeries ch1Series = new SampleDynamicSeries(data, 0, "Ch. 1");
            SampleDynamicSeries ch2Series = new SampleDynamicSeries(data, 1, "Ch. 2");

            LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                    Color.rgb(0, 200, 0), null, null, null);
            dynamicPlot.addSeries(ch1Series, formatter1);

            LineAndPointFormatter formatter2 =
                    new LineAndPointFormatter(Color.rgb(0, 0, 200), null, null, null);

            //formatter2.getFillPaint().setAlpha(220);
            dynamicPlot.addSeries(ch2Series, formatter2);

            // hook up the plotUpdater to the data model:
            data.addObserver(plotUpdater);

            // uncomment this line to freeze the range boundaries:
            //dynamicPlot.setRangeBoundaries(-350, 350, BoundaryMode.FIXED);

            // axes labels
            dynamicPlot.setRangeLabel("Strain");
        }

        public void start() {
            // kick off the data generating thread:
            myThread = new Thread(data);
            myThread.start();
        }

        public void stop() {
            data.stopThread();
        }


        class SampleDynamicXYDatasource implements Runnable {

            // encapsulates management of the observers watching this datasource for update events:
            class MyObservable extends Observable {
                @Override
                public void notifyObservers() {
                    setChanged();
                    super.notifyObservers();
                }
            }

            static final int CH1 = 0;
            static final int CH2 = 1;
            private static final int SAMPLE_SIZE = 100;
            private List<Short> ch1List = new LinkedList<>();
            private List<Short> ch2List = new LinkedList<>();
            short ch1 = 0;
            short ch2 = 0;
            private short zero = 0;
            private SampleDynamicXYDatasource.MyObservable notifier;
            private boolean keepRunning = false;

            {
                notifier = new MyObservable();
            }

            public SampleDynamicXYDatasource(){
                for(int ii=0; ii<SAMPLE_SIZE; ii++){
                    ch1List.add(zero);
                    ch2List.add(zero);
                }
            }

            public void stopThread() {
                keepRunning = false;
            }

            //@Override
            public void run() {
                try {
                    keepRunning = true;
                    while (keepRunning) {
                        short strainArray[] = mBluetoothService.getStrain();
                        ch1 = strainArray[0];
                        ch2 = strainArray[1];
                        ch1List.add(ch1);
                        ch2List.add(ch2);
                        ch1List.remove(0);
                        ch2List.remove(0);
                        notifier.notifyObservers();
                        Thread.sleep(20); // decrease or remove to speed up the refresh rate.
                    }
                } catch (InterruptedException e) {
                    keepRunning = false;
                    e.printStackTrace();
                }
            }

            public int getItemCount(int series) {
                return SAMPLE_SIZE;
            }

            public Number getX(int series, int index) {
                if (index >= SAMPLE_SIZE) {
                    throw new IllegalArgumentException();
                }
                return index;
            }

            public Number getY(int series, int index) {
                if (index >= ch1List.size() || index >= ch2List.size()) {
                    throw new IllegalArgumentException();
                }
                switch (series) {
                    case CH1:
                        return ch1List.get(index);
                    case CH2:
                        return ch2List.get(index);
                    default:
                        throw new IllegalArgumentException();
                }
            }

            public void addObserver(Observer observer) {
                notifier.addObserver(observer);
            }

            public void removeObserver(Observer observer) {
                notifier.deleteObserver(observer);
            }

        }

        class SampleDynamicSeries implements XYSeries {
            private SampleDynamicXYDatasource datasource;
            private int seriesIndex;
            private String title;

            public SampleDynamicSeries(SampleDynamicXYDatasource datasource, int seriesIndex, String title) {
                this.datasource = datasource;
                this.seriesIndex = seriesIndex;
                this.title = title;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public int size() {
                return datasource.getItemCount(seriesIndex);
            }

            @Override
            public Number getX(int index) {
                return datasource.getX(seriesIndex, index);
            }

            @Override
            public Number getY(int index) {
                return datasource.getY(seriesIndex, index);
            }
        }

    }
}
