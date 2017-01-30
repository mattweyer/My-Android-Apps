package com.bronbergdynamics.strainreader;

/**
 * Created by marenco on 1/25/2017.
 */

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.app.Activity;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.OrderedXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;


public class PlottingThread {

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

    public PlottingThread(XYPlot plot)
    {
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

    public void setData(short ch1, short ch2){
        data.ch1 = ch1;
        data.ch2 = ch2;
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

        public static final int CH1 = 0;
        public static final int CH2 = 1;
        private static final int SAMPLE_SIZE = 100;
        private List<Short> ch1List = new LinkedList<Short>();
        private List<Short> ch2List = new LinkedList<Short>();
        public short ch1 = 0;
        public short ch2 = 0;
        private short zero = 0;
        private MyObservable notifier;
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
                    ch1List.remove(0);
                    ch2List.remove(0);
                    ch1List.add(ch1);
                    ch2List.add(ch2);
                    Thread.sleep(20); // decrease or remove to speed up the refresh rate.
                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
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
            if (index >= SAMPLE_SIZE) {
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

    class SampleDynamicSeries implements OrderedXYSeries {
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

        @Override
        public XOrder getXOrder(){
            return XOrder.ASCENDING;
        }
    }

}
